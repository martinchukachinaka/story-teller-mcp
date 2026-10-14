import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { Marked } from 'marked';

const baseUrl = requireEnv('CONFLUENCE_BASE_URL');
const email = requireEnv('CONFLUENCE_EMAIL');
const apiToken = requireEnv('CONFLUENCE_API_TOKEN');
const spaceId = requireEnv('CONFLUENCE_SPACE_ID');
const docsDir = process.env.DOCS_DIR ?? 'docs';
const dryRun = process.env.DRY_RUN === 'true';
// Optional, e.g. https://github.com/owner/repo/blob/main: links to repo files outside the docs folder point there.
const repoBlobUrl = process.env.REPO_BLOB_URL?.replace(/\/$/, '');

const authHeader = 'Basic ' + Buffer.from(`${email}:${apiToken}`).toString('base64');

// Each synced page carries this property, holding the doc's path relative to the docs folder ("contact/contact.md"),
// or the folder's path for a folder page ("contact/"). It ties a page to its source even after the title changes,
// without writing anything back to the repo.
const SOURCE_PROPERTY = 'docs_sync_source';
// A hash of the title, parent and converted body as last published, so unchanged pages are skipped instead of adding an empty version.
const HASH_PROPERTY = 'docs_sync_hash';

const ROOT_FOLDER = '';
const FOLDER_INDEX_NAMES = ['index.md', 'readme.md'];
const FOLDER_PLACEHOLDER_BODY = '<ac:structured-macro ac:name="children" ac:schema-version="2" />';

// Property reads run this many at a time: fast enough for ~100 pages, small enough to stay clear of Confluence's rate limits.
const PROPERTY_READS_PER_BATCH = 10;
const MAX_RETRIES = 3;

// Languages the Confluence code macro highlights. Any other (mermaid, say) shows as plain text, titled with its name.
const CODE_MACRO_LANGUAGES = new Set(['bash', 'java', 'json', 'sql', 'xml', 'text']);

await syncDocs();

async function syncDocs() {
  const markdownFiles = await findMarkdownFiles(docsDir);
  console.log(`Found ${markdownFiles.length} markdown file(s) in ${docsDir}${dryRun ? ' (dry run)' : ''}`);
  if (markdownFiles.length === 0) {
    throw new Error(`No markdown files in ${docsDir}; refusing to sync, as that would trash every synced page`);
  }

  const docs = [];
  for (const file of markdownFiles) {
    const markdown = await readFile(file, 'utf8');
    const { title, content } = splitTitle(markdown, file);
    docs.push({ file, source: path.relative(docsDir, file), title, content });
  }
  const titlesByFile = new Map(docs.map((doc) => [doc.file, doc.title]));

  const pageTree = buildPageTree(docs, titlesByFile);
  failOnDuplicateTitles(pageTree);

  const syncedPagesBySource = await findSyncedPages();
  const pageIdsBySource = new Map([[ROOT_FOLDER, await spaceHomepageId()]]);

  for (const node of pageTree) {
    const parentId = pageIdsBySource.get(node.parentFolder);
    const pageId = await publishPage(node, parentId, syncedPagesBySource.get(node.source));
    pageIdsBySource.set(node.source, pageId);
  }

  await trashRemovedPages(syncedPagesBySource, new Set(pageIdsBySource.values()));
}

async function findMarkdownFiles(dir) {
  const entries = await readdir(dir, { recursive: true });
  return entries
    .filter((entry) => entry.endsWith('.md'))
    .map((entry) => path.join(dir, entry))
    .sort();
}

// Every folder becomes a page, so the page tree in Confluence mirrors the folders in docs/. A folder's index.md or
// README.md supplies that page's title and content (with both, the other one is an ordinary child page); without one,
// the page is a placeholder listing its children. The root folder has no page of its own, so its index or README is an
// ordinary page.
// Sorted by depth so each parent is published before its children and its page ID is known.
function buildPageTree(docs, titlesByFile) {
  const nodes = [];
  const indexDocsByFolder = new Map();

  for (const doc of docs) {
    const folder = parentFolderOf(doc.source);
    const isFolderIndex = FOLDER_INDEX_NAMES.includes(path.basename(doc.source).toLowerCase());
    if (folder !== ROOT_FOLDER && isFolderIndex && !indexDocsByFolder.has(folder)) {
      indexDocsByFolder.set(folder, doc);
      continue;
    }
    nodes.push({
      source: doc.source,
      file: doc.file,
      title: doc.title,
      body: toStorageFormat(doc.content, doc.file, titlesByFile),
      parentFolder: folder,
    });
  }

  for (const folder of foldersContaining(docs)) {
    const indexDoc = indexDocsByFolder.get(folder);
    nodes.push({
      source: folder,
      file: indexDoc ? indexDoc.file : path.join(docsDir, folder),
      title: indexDoc ? indexDoc.title : path.basename(folder),
      body: indexDoc ? toStorageFormat(indexDoc.content, indexDoc.file, titlesByFile) : FOLDER_PLACEHOLDER_BODY,
      parentFolder: parentFolderOf(folder),
    });
  }

  return nodes.sort((a, b) => depthOf(a.source) - depthOf(b.source));
}

function foldersContaining(docs) {
  const folders = new Set();
  for (const doc of docs) {
    let folder = parentFolderOf(doc.source);
    while (folder !== ROOT_FOLDER) {
      folders.add(folder);
      folder = parentFolderOf(folder);
    }
  }
  return folders;
}

// Works for both kinds of source: "contact/contact.md" and "guides/setup/" both have the parent folder they sit in.
function parentFolderOf(source) {
  const parent = path.dirname(source.replace(/\/$/, ''));
  return parent === '.' ? ROOT_FOLDER : `${parent}/`;
}

function depthOf(source) {
  return source.split('/').filter(Boolean).length;
}

// Confluence titles are unique per space, so two pages with the same title would overwrite each other's links.
function failOnDuplicateTitles(pageTree) {
  const sourcesByTitle = new Map();
  for (const node of pageTree) {
    const title = node.title.toLowerCase();
    const clashingSource = sourcesByTitle.get(title);
    if (clashingSource !== undefined) {
      throw new Error(`"${node.title}" is the title of both ${clashingSource} and ${node.source}; page titles must be unique in a Confluence space`);
    }
    sourcesByTitle.set(title, node.source);
  }
}

// Only a heading at the very top is the page title; a heading further down belongs to a section and stays in the body.
function splitTitle(markdown, file) {
  const topHeading = markdown.match(/^\s*#{1,6}\s+(.+)\n?/);
  if (topHeading) {
    return { title: topHeading[1].trim(), content: markdown.slice(topHeading[0].length) };
  }
  return { title: path.basename(file, '.md'), content: markdown };
}

// Confluence storage format is XHTML: void tags must be self-closed, or the API rejects the body as malformed. They are
// closed where they are produced rather than over the finished page, so code blocks keep their contents exactly.
function toStorageFormat(markdown, file, titlesByFile) {
  const converter = new Marked({
    renderer: {
      link(token) {
        const linkText = this.parser.parseInline(token.tokens);
        const targetTitle = titlesByFile.get(linkedDocPath(file, token.href));
        if (targetTitle) {
          return `<ac:link><ri:page ri:content-title="${escapeXml(targetTitle)}" /><ac:link-body>${linkText}</ac:link-body></ac:link>`;
        }
        const repoUrl = repoFileUrl(file, token.href);
        if (repoUrl) {
          return `<a href="${escapeXml(repoUrl)}">${linkText}</a>`;
        }
        return false;
      },
      code(token) {
        return codeMacro(token.text, token.lang);
      },
      br() {
        return '<br />';
      },
      hr() {
        return '<hr />\n';
      },
      image(token) {
        return `<img src="${escapeXml(token.href)}" alt="${escapeXml(token.text)}" />`;
      },
      html(token) {
        return closeVoidTags(token.text);
      },
    },
  });
  return converter.parse(markdown);
}

function closeVoidTags(html) {
  return html.replace(/<(br|hr|img)([^>]*?)\s*\/?>/g, '<$1$2 />');
}

function codeMacro(code, language) {
  let parameter = '';
  if (CODE_MACRO_LANGUAGES.has(language)) {
    parameter = `<ac:parameter ac:name="language">${language}</ac:parameter>`;
  } else if (language) {
    parameter = `<ac:parameter ac:name="title">${escapeXml(language)}</ac:parameter>`;
  }
  // A CDATA section ends at the first "]]>", so one inside the code is split across two sections.
  const cdata = code.replaceAll(']]>', ']]]]><![CDATA[>');
  return `<ac:structured-macro ac:name="code">${parameter}<ac:plain-text-body><![CDATA[${cdata}]]></ac:plain-text-body></ac:structured-macro>\n`;
}

// Confluence has no copy of files outside the docs folder (source code, the root README), so links to them go to GitHub.
// Anything with a scheme (https:, mailto:), an anchor in the same page, or a path leaving the repo is left alone.
function repoFileUrl(fromFile, href) {
  if (!repoBlobUrl || /^[a-z][a-z0-9+.-]*:/i.test(href) || href.startsWith('#')) {
    return undefined;
  }
  const [filePart, anchor] = href.split('#');
  const repoPath = path.relative(process.cwd(), path.join(path.dirname(fromFile), decodeURIComponent(filePart)));
  if (repoPath.startsWith('..')) {
    return undefined;
  }
  const url = `${repoBlobUrl}/${encodeURI(repoPath.split(path.sep).join('/'))}`;
  return anchor ? `${url}#${anchor}` : url;
}

// Anchors are dropped: Confluence generates its own heading anchors, so "#install" would not match.
function linkedDocPath(fromFile, href) {
  const hrefWithoutAnchor = decodeURIComponent(href.split('#')[0]);
  return path.join(path.dirname(fromFile), hrefWithoutAnchor);
}

function escapeXml(text) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

// Confluence cannot search by property value without an app, so this reads the properties of every page in the space.
async function findSyncedPages() {
  const syncedPagesBySource = new Map();
  let nextPath = `/wiki/api/v2/spaces/${spaceId}/pages?status=current&limit=250`;

  while (nextPath) {
    const result = await confluence('GET', nextPath);
    for (let start = 0; start < result.results.length; start += PROPERTY_READS_PER_BATCH) {
      const batch = result.results.slice(start, start + PROPERTY_READS_PER_BATCH);
      const propertiesOfBatch = await Promise.all(batch.map((page) => syncPropertiesOf(page.id)));

      for (let i = 0; i < batch.length; i++) {
        const { sourceProperty, hashProperty } = propertiesOfBatch[i];
        if (sourceProperty) {
          syncedPagesBySource.set(sourceProperty.value, { page: batch[i], sourceProperty, hashProperty });
        }
      }
    }
    nextPath = result._links.next;
  }
  return syncedPagesBySource;
}

async function syncPropertiesOf(pageId) {
  const result = await confluence('GET', `/wiki/api/v2/pages/${pageId}/properties?limit=250`);
  let sourceProperty;
  let hashProperty;
  for (const property of result.results) {
    if (property.key === SOURCE_PROPERTY) {
      sourceProperty = property;
    }
    if (property.key === HASH_PROPERTY) {
      hashProperty = property;
    }
  }
  return { sourceProperty, hashProperty };
}

async function spaceHomepageId() {
  const space = await confluence('GET', `/wiki/api/v2/spaces/${spaceId}`);
  return space.homepageId;
}

async function publishPage(node, parentId, syncedPage) {
  const hash = contentHash(node.title, parentId, node.body);
  if (syncedPage && syncedPage.hashProperty?.value === hash) {
    console.log(`Unchanged "${node.title}" (page ${syncedPage.page.id}) from ${node.file}`);
    return syncedPage.page.id;
  }

  const target = syncedPage ?? (await findPageByTitle(node.title));

  if (dryRun) {
    const parent = node.parentFolder || 'the space home page';
    console.log(`[dry run] would ${target ? `update page ${target.page.id}` : 'create a page'} as "${node.title}" under ${parent} from ${node.file}`);
    return target?.page.id;
  }

  let pageId;
  if (target) {
    pageId = target.page.id;
    const nextVersion = target.page.version.number + 1;
    await updatePage(pageId, parentId, node.title, node.body, nextVersion);
    console.log(`Updated "${node.title}" (page ${pageId}, version ${nextVersion}) from ${node.file}`);
  } else {
    const created = await createPage(parentId, node.title, node.body);
    pageId = created.id;
    console.log(`Created "${node.title}" (page ${pageId}) from ${node.file}`);
  }

  // A page found by title may be linked to an older path: the doc was moved, so the link is re-pointed rather than duplicated.
  if (target?.sourceProperty?.value !== node.source) {
    await saveProperty(pageId, SOURCE_PROPERTY, node.source, target?.sourceProperty);
    console.log(`Linked page ${pageId} to ${node.source}`);
  }
  await saveProperty(pageId, HASH_PROPERTY, hash, target?.hashProperty);
  return pageId;
}

// A synced page that was not published this run lost its doc or folder. Checking published page IDs rather than sources
// keeps a moved doc safe: its page was re-linked to the new path above. Trashed pages can still be restored from the
// space's trash. Deepest first, so a folder page is never trashed while it still has synced children.
async function trashRemovedPages(syncedPagesBySource, publishedPageIds) {
  const removedPages = [...syncedPagesBySource.entries()]
    .filter(([, synced]) => !publishedPageIds.has(synced.page.id))
    .sort(([sourceA], [sourceB]) => depthOf(sourceB) - depthOf(sourceA));

  for (const [source, synced] of removedPages) {
    if (dryRun) {
      console.log(`[dry run] would trash "${synced.page.title}" (page ${synced.page.id}); ${source} no longer exists`);
      continue;
    }
    await confluence('DELETE', `/wiki/api/v2/pages/${synced.page.id}`);
    console.log(`Trashed "${synced.page.title}" (page ${synced.page.id}); ${source} no longer exists`);
  }
}

function contentHash(title, parentId, body) {
  return createHash('sha256').update(JSON.stringify([title, parentId, body])).digest('hex');
}

function saveProperty(pageId, key, value, existing) {
  if (existing) {
    return confluence('PUT', `/wiki/api/v2/pages/${pageId}/properties/${existing.id}`, {
      key,
      value,
      version: { number: existing.version.number + 1 },
    });
  }
  return confluence('POST', `/wiki/api/v2/pages/${pageId}/properties`, { key, value });
}

async function findPageByTitle(title) {
  const query = new URLSearchParams({ 'space-id': spaceId, title, status: 'current' });
  const result = await confluence('GET', `/wiki/api/v2/pages?${query}`);
  const page = result.results[0];
  if (!page) {
    return undefined;
  }
  const { sourceProperty, hashProperty } = await syncPropertiesOf(page.id);
  return { page, sourceProperty, hashProperty };
}

function createPage(parentId, title, body) {
  return confluence('POST', '/wiki/api/v2/pages', {
    spaceId,
    parentId,
    status: 'current',
    title,
    body: { representation: 'storage', value: body },
  });
}

function updatePage(pageId, parentId, title, body, versionNumber) {
  return confluence('PUT', `/wiki/api/v2/pages/${pageId}`, {
    id: pageId,
    parentId,
    status: 'current',
    title,
    body: { representation: 'storage', value: body },
    version: { number: versionNumber },
  });
}

async function confluence(method, apiPath, payload) {
  for (let attempt = 1; ; attempt++) {
    const response = await fetch(baseUrl + apiPath, {
      method,
      headers: {
        Authorization: authHeader,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: payload ? JSON.stringify(payload) : undefined,
    });

    if (response.status === 204) {
      return undefined;
    }
    if (response.ok) {
      return response.json();
    }

    const retryable = response.status === 429 || response.status >= 500;
    if (!retryable || attempt > MAX_RETRIES) {
      throw new Error(`${method} ${apiPath} failed with HTTP ${response.status}: ${await response.text()}`);
    }

    const delayMs = retryDelayMs(response);
    console.log(`${method} ${apiPath} got HTTP ${response.status}; retry ${attempt} of ${MAX_RETRIES} in ${delayMs} ms`);
    await sleep(delayMs);
  }
}

// Retry-After may also be an HTTP date; anything that isn't a positive number of seconds falls back to a short random wait.
function retryDelayMs(response) {
  const retryAfterSeconds = Number(response.headers.get('Retry-After'));
  if (retryAfterSeconds > 0) {
    return retryAfterSeconds * 1000;
  }
  return Math.round(1000 + Math.random() * 1000);
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable ${name}`);
  }
  return value;
}
