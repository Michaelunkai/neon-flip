#!/usr/bin/env node
/**
 * store-listing/check-lengths.js — verify Google Play field limits for every listing file.
 * Usage: node store-listing/check-lengths.js   (run from repo root or anywhere)
 * Limits: title ≤30, short description ≤80, full description ≤4000, release notes ≤500.
 */
const fs = require('fs');
const path = require('path');

const dir = __dirname;
const LIMITS = { 'App title': 30, 'Short description': 80, 'Full description': 4000, 'Release notes': 500 };

// Split into "## heading" sections; return the first fenced code block of the first
// section whose heading contains any of the given keywords (accent-insensitive).
function grab(md, keywords) {
  const lines = md.split('\n');
  const kws = keywords.map(k => k.toLowerCase());
  const strip = s => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  let inSection = false;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^##\s/.test(line)) {
      const h = strip(line);
      inSection = kws.some(k => h.includes(strip(k)));
      continue;
    }
    if (inSection && /^```/.test(line.trim())) {
      const buf = [];
      for (i = i + 1; i < lines.length && !/^```\s*$/.test(lines[i].trim()); i++) buf.push(lines[i]);
      return buf.join('\n');
    }
  }
  return null;
}

let allPass = true;
const rows = [];
for (const f of fs.readdirSync(dir).filter(f => /^listing(-[a-z-]+)?\.md$/i.test(f)).sort()) {
  const md = fs.readFileSync(path.join(dir, f), 'utf8');
  const fields = [
    ['App title', grab(md, ['title', 'titulo', 'judul', 'başlık', 'название'])],
    ['Short description', grab(md, ['short description', 'corta', 'curta', 'singkat', 'kısa', 'краткое'])],
    ['Full description', grab(md, ['full description', 'completa', 'lengkap', 'tam açıklama', 'полное'])],
    ['Release notes', /^listing(-en)?\.md$/i.test(f) ? grab(md, ['release notes']) : null],
  ];
  for (const [label, text] of fields) {
    if (!text) {
      const optional = label === 'Release notes' && !/^listing(-en)?\.md$/i.test(f);
      rows.push([f, label, '—', '—', optional ? 'n/a (EN canonical)' : 'MISSING']);
      if (!optional) allPass = false;
      continue;
    }
    const n = text.length;
    const limit = LIMITS[label];
    const ok = n <= limit;
    if (!ok) allPass = false;
    rows.push([f, label, String(n), String(limit), ok ? 'PASS' : 'FAIL']);
  }
}

const pad = (s, w) => String(s).padEnd(w);
console.log(pad('FILE', 16) + pad('FIELD', 20) + pad('COUNT', 8) + pad('LIMIT', 8) + 'STATUS');
console.log('-'.repeat(60));
for (const r of rows) console.log(pad(r[0], 16) + pad(r[1], 20) + pad(r[2], 8) + pad(r[3], 8) + r[4]);
console.log('-'.repeat(60));
console.log(allPass ? 'ALL PASS' : 'SOME FIELDS FAIL — fix before pasting into Play Console');
process.exit(allPass ? 0 : 1);
