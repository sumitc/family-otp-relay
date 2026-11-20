import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const escapeHtml = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const inline = (s) =>
  escapeHtml(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

// Tiny renderer: headings, bullets, paragraphs, fenced code, <details> blocks.
export function render(md) {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let code = false;
  let list = false;
  let para = [];

  const flushPara = () => {
    if (para.length) {
      out.push(`<p>${para.map(inline).join(' ')}</p>`);
      para = [];
    }
  };
  const closeList = () => {
    if (list) {
      out.push('</ul>');
      list = false;
    }
  };

  for (const line of lines) {
    if (line.startsWith('```')) {
      flushPara();
      closeList();
      out.push(code ? '</code></pre>' : '<pre><code>');
      code = !code;
      continue;
    }
    if (code) {
      out.push(escapeHtml(line));
      continue;
    }
    if (!line.trim()) {
      flushPara();
      closeList();
      continue;
    }
    const heading = line.match(/^(#{1,3})\s+(.*)$/);
    if (heading) {
      flushPara();
      closeList();
      const level = heading[1].length;
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      continue;
    }
    if (line.startsWith('- ')) {
      flushPara();
      if (!list) {
        out.push('<ul>');
        list = true;
      }
      out.push(`<li>${inline(line.slice(2))}</li>`);
      continue;
    }
    const summary = line.match(/^<summary>(.*)<\/summary>$/);
    if (summary) {
      flushPara();
      closeList();
      out.push(`<summary>${inline(summary[1])}</summary>`);
      continue;
    }
    if (/^<\/?details>$/.test(line.trim())) {
      flushPara();
      closeList();
      out.push(line.trim());
      continue;
    }
    para.push(line.trim());
  }
  flushPara();
  closeList();
  if (code) throw new Error('Unclosed fenced code block');

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Family OTP Relay</title>
<style>
*{box-sizing:border-box;min-width:0}
html,body{margin:0;overflow-x:hidden;max-width:100%}
body{font:16px/1.55 system-ui,sans-serif;color:#1c1917;background:#fff8f1}
main{max-width:44rem;margin:0 auto;padding:1.25rem 1rem 3rem}
h1{font-size:1.75rem;margin:0 0 .5rem}
h2{font-size:1.15rem;margin:1.75rem 0 .5rem}
h3{font-size:1rem}
p,li{margin:.45rem 0}
ul{padding-left:1.15rem}
code{background:#f3e9df;padding:.1rem .3rem;border-radius:4px;overflow-wrap:anywhere}
pre{background:#f3e9df;padding:.75rem;overflow-x:auto;border-radius:8px}
pre code{background:none;padding:0}
details{border:1px solid #e4d3c4;border-radius:8px;padding:.5rem .75rem;margin:.6rem 0}
summary{cursor:pointer;font-weight:600}
strong{color:#c64309}
</style>
</head>
<body><main>
${out.join('\n')}
</main></body>
</html>
`;
}

export function build() {
  const md = readFileSync('README.md', 'utf8');
  mkdirSync('dist', { recursive: true });
  writeFileSync('dist/index.html', render(md));
  console.log('Built dist/index.html');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  build();
}
