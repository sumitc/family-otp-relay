import { readFileSync } from 'node:fs';
import { render } from './build.mjs';

const md = readFileSync('README.md', 'utf8').replace(/\r\n/g, '\n');
const lines = md.split('\n');
const errors = [];
const check = (ok, message) => {
  if (!ok) errors.push(message);
};

// Mobile-first rules learned for this repo.
lines.forEach((line, i) => {
  check(line.length <= 88, `line ${i + 1} is ${line.length} chars (max 88)`);
  check(!/^\s*\|.*\|\s*$/.test(line), `line ${i + 1} looks like a table row`);
});

const blocks = md.split(/\n\s*\n/);
let codeLines = 0;
let inCode = false;
let codeBlocks = 0;
for (const block of blocks) {
  const trimmed = block.trim();
  if (!trimmed) continue;
  if (trimmed.startsWith('```')) {
    codeBlocks += 1;
    codeLines = Math.max(codeLines, trimmed.split('\n').length - 2);
    continue;
  }
  if (/^<|^\s*- |^#/.test(trimmed)) continue;
  const text = trimmed.replace(/\n/g, ' ');
  check(text.length <= 240, `paragraph is ${text.length} chars (max 240)`);
  const sentences = (text.match(/[.!?](?:\s|$)/g) || []).length;
  check(sentences <= 2, `paragraph has ${sentences} sentences (max 2)`);
}

for (const block of blocks) {
  if (block.trim().startsWith('```')) {
    const n = block.trim().split('\n').length - 2;
    check(n <= 6, `code block has ${n} lines (max 6)`);
  }
}

check((md.match(/<details>/g) || []).length >= 2, 'need at least 2 details blocks');
check(md.includes('# Family OTP Relay'), 'missing title');

const html = render(md);
check(/name="viewport"[^>]*width=device-width/.test(html), 'missing viewport meta');
check(!html.includes('<table'), 'rendered HTML has a table');
check(html.includes('<h1>Family OTP Relay</h1>'), 'rendered HTML missing h1');
check(html.includes('<details>'), 'rendered HTML missing details');

if (errors.length) {
  console.error(`FAIL (${errors.length}):`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`PASS — ${lines.length} lines, ${codeBlocks} code block(s), mobile rules OK`);
