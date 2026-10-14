import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { marked } from 'marked';

const baseUrl = requireEnv('CONFLUENCE_BASE_URL');
const email = requireEnv('CONFLUENCE_EMAIL');
const apiToken = requireEnv('CONFLUENCE_API_TOKEN');
const spaceId = requireEnv('CONFLUENCE_SPACE_ID');
const docsDir = process.env.DOCS_DIR ?? 'docs';
const dryRun = process.env.DRY_RUN === 'true';

const authHeader = 'Basic ' + Buffer.from(`${email}:${apiToken}`).toString('base64');

await syncDocs();

async function syncDocs() {
  const markdownFiles = await findMarkdownFiles(docsDir);
  console.log(`Found ${markdownFiles.length} markdown file(s) in ${docsDir}${dryRun ? ' (dry run)' : ''}`);

  for (const file of markdownFiles) {
    const markdown = await readFile(file, 'utf8');
    const title = titleOf(markdown, file);
    const body = toStorageFormat(markdown);
    await publishPage(title, body, file);
  }
}

async function findMarkdownFiles(dir) {
  const entries = await readdir(dir, { recursive: true });
  return entries
    .filter((entry) => entry.endsWith('.md'))
    .map((entry) => path.join(dir, entry))
    .sort();
}

function titleOf(markdown, file) {
  const heading = markdown.match(/^#{1,6}\s+(.+)$/m);
  if (heading) {
    return heading[1].trim();
  }
  return path.basename(file, '.md');
}

// Confluence storage format is XHTML: void tags must be self-closed, or the API rejects the body as malformed.
function toStorageFormat(markdown) {
  const html = marked.parse(markdown);
  return html.replace(/<(br|hr|img)([^>]*?)\s*\/?>/g, '<$1$2 />');
}

async function publishPage(title, body, file) {
  const existing = await findPage(title);

  if (dryRun) {
    console.log(`[dry run] would ${existing ? 'update' : 'create'} "${title}" from ${file}`);
    return;
  }

  if (existing) {
    const nextVersion = existing.version.number + 1;
    await updatePage(existing.id, title, body, nextVersion);
    console.log(`Updated "${title}" (page ${existing.id}, version ${nextVersion}) from ${file}`);
  } else {
    const created = await createPage(title, body);
    console.log(`Created "${title}" (page ${created.id}) from ${file}`);
  }
}

async function findPage(title) {
  const query = new URLSearchParams({ 'space-id': spaceId, title, status: 'current' });
  const result = await confluence('GET', `/wiki/api/v2/pages?${query}`);
  return result.results[0];
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
  const response = await fetch(baseUrl + apiPath, {
    method,
    headers: {
      Authorization: authHeader,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: payload ? JSON.stringify(payload) : undefined,
  });

  if (!response.ok) {
    throw new Error(`${method} ${apiPath} failed with HTTP ${response.status}: ${await response.text()}`);
  }
  return response.json();
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable ${name}`);
  }
  return value;
}
