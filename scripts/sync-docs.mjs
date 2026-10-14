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

const authHeader = 'Basic ' + Buffer.from(`${email}:${apiToken}`).toString('base64');

// Each synced page carries this property, holding the doc's path relative to the docs folder.
// It ties a doc to its page even after the title changes, without writing anything back to the repo.
const SOURCE_PROPERTY = 'docs_sync_source';
// A hash of the title and converted body as last published, so unchanged docs are skipped instead of adding an empty page version.
const HASH_PROPERTY = 'docs_sync_hash';

// Property reads run this many at a time: fast enough for ~100 pages, small enough to stay clear of Confluence's rate limits.
const PROPERTY_READS_PER_BATCH = 10;
const MAX_RETRIES = 3;

await syncDocs();

async function syncDocs() {
  const markdownFiles = await findMarkdownFiles(docsDir);
  console.log(`Found ${markdownFiles.length} markdown file(s) in ${docsDir}${dryRun ? ' (dry run)' : ''}`);

  const docs = [];
  for (const file of markdownFiles) {
    const markdown = await readFile(file, 'utf8');
    const { title, content } = splitTitle(markdown, file);
    docs.push({ file, source: path.relative(docsDir, file), title, content });
  }
  const titlesByFile = new Map(docs.map((doc) => [doc.file, doc.title]));

  const syncedPagesBySource = await findSyncedPages();

  for (const doc of docs) {
    const body = toStorageFormat(doc.content, doc.file, titlesByFile);
    await publishPage(doc, body, syncedPagesBySource.get(doc.source));
  }
}

async function findMarkdownFiles(dir) {
  const entries = await readdir(dir, { recursive: true });
  return entries
    .filter((entry) => entry.endsWith('.md'))
    .map((entry) => path.join(dir, entry))
    .sort();
}

// Only a heading at the very top is the page title; a heading further down belongs to a section and stays in the body.
function splitTitle(markdown, file) {
  const topHeading = markdown.match(/^\s*#{1,6}\s+(.+)\n?/);
  if (topHeading) {
    return { title: topHeading[1].trim(), content: markdown.slice(topHeading[0].length) };
  }
  return { title: path.basename(file, '.md'), content: markdown };
}

// Confluence storage format is XHTML: void tags must be self-closed, or the API rejects the body as malformed.
function toStorageFormat(markdown, file, titlesByFile) {
  const converter = new Marked({
    renderer: {
      link(token) {
        const targetTitle = titlesByFile.get(linkedDocPath(file, token.href));
        if (!targetTitle) {
          return false;
        }
        const linkText = this.parser.parseInline(token.tokens);
        return `<ac:link><ri:page ri:content-title="${escapeXml(targetTitle)}" /><ac:link-body>${linkText}</ac:link-body></ac:link>`;
      },
    },
  });
  const html = converter.parse(markdown);
  return html.replace(/<(br|hr|img)([^>]*?)\s*\/?>/g, '<$1$2 />');
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

async function publishPage(doc, body, syncedPage) {
  const hash = contentHash(doc.title, body);
  if (syncedPage && syncedPage.hashProperty?.value === hash) {
    console.log(`Unchanged "${doc.title}" (page ${syncedPage.page.id}) from ${doc.file}`);
    return;
  }

  const target = syncedPage ?? (await findPageByTitle(doc.title));

  if (dryRun) {
    console.log(`[dry run] would ${target ? `update page ${target.page.id}` : 'create a page'} as "${doc.title}" from ${doc.file}`);
    return;
  }

  let pageId;
  if (target) {
    pageId = target.page.id;
    const nextVersion = target.page.version.number + 1;
    await updatePage(pageId, doc.title, body, nextVersion);
    console.log(`Updated "${doc.title}" (page ${pageId}, version ${nextVersion}) from ${doc.file}`);
  } else {
    const created = await createPage(doc.title, body);
    pageId = created.id;
    console.log(`Created "${doc.title}" (page ${pageId}) from ${doc.file}`);
  }

  // A page found by title may be linked to an older path: the doc was moved, so the link is re-pointed rather than duplicated.
  if (target?.sourceProperty?.value !== doc.source) {
    await saveProperty(pageId, SOURCE_PROPERTY, doc.source, target?.sourceProperty);
    console.log(`Linked page ${pageId} to ${doc.source}`);
  }
  await saveProperty(pageId, HASH_PROPERTY, hash, target?.hashProperty);
}

function contentHash(title, body) {
  return createHash('sha256').update(JSON.stringify([title, body])).digest('hex');
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

function createPage(title, body) {
  return confluence('POST', '/wiki/api/v2/pages', {
    spaceId,
    status: 'current',
    title,
    body: { representation: 'storage', value: body },
  });
}

function updatePage(pageId, title, body, versionNumber) {
  return confluence('PUT', `/wiki/api/v2/pages/${pageId}`, {
    id: pageId,
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
