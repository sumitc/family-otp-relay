import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { pathToFileURL } from 'node:url';

/** Serve dist/ over loopback HTTP and assert the docs page really loads. */
export async function checkPage(root = 'dist') {
  const errors = [];
  const types = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
  };
  const server = createServer((req, res) => {
    const path = decodeURIComponent((req.url || '/').split('?')[0]);
    const rel = path === '/' ? 'index.html' : normalize(path).replace(/^(\.\.[/\\])+/, '');
    const file = join(root, rel);
    try {
      const body = readFileSync(file);
      res.writeHead(200, {
        'content-type': types[extname(file)] || 'application/octet-stream',
      });
      res.end(body);
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('not found');
    }
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const base = `http://127.0.0.1:${port}`;
  try {
    const res = await fetch(`${base}/`);
    if (res.status !== 200) {
      errors.push(`GET / returned ${res.status}`);
    }
    const type = res.headers.get('content-type') || '';
    if (!type.includes('text/html')) {
      errors.push(`GET / content-type was ${type}`);
    }
    const body = await res.text();
    for (const marker of [
      '<!doctype html>',
      'name="viewport"',
      '<h1>Family OTP Relay</h1>',
      '</html>',
    ]) {
      if (!body.toLowerCase().includes(marker.toLowerCase())) {
        errors.push(`page HTML missing ${marker}`);
      }
    }
    if (body.includes('<table')) {
      errors.push('page HTML contains a table');
    }
    for (const match of body.matchAll(/(?:src|href)="([^"]+)"/g)) {
      const target = match[1];
      if (/^(https?:|#|data:)/.test(target)) continue;
      const asset = await fetch(`${base}/${target}`);
      if (asset.status !== 200) {
        errors.push(`asset ${target} returned ${asset.status}`);
      }
    }
  } catch (error) {
    errors.push(`page load failed: ${error.message}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
  return errors;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const errors = await checkPage();
  if (errors.length) {
    console.error(`PAGE CHECK FAIL (${errors.length}):`);
    for (const error of errors) console.error(`  - ${error}`);
    process.exit(1);
  }
  console.log('PASS — docs page loads over HTTP');
}
