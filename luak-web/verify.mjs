// Loads luak-web.js as a page would - a plain script, no module system - and
// checks it gives the answers the JVM runtime gives.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const bundle = process.argv[2];
const sandbox = { console };
sandbox.globalThis = sandbox;
sandbox.window = sandbox;
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(bundle, 'utf8'), sandbox, { filename: 'luak-web.js' });

const { LuakWeb } = sandbox;
assert.ok(LuakWeb, 'the bundle puts LuakWeb on globalThis');
assert.match(String(LuakWeb.version), /\d/);

const ok = [
  "print('😀 ─ │')",
  'local x = 10 // 3',
  'local x <const> = 1',
  'local <const> a, b = 1, 2',
  'global score = 0',
  'global *',
  "print('\\u{48}')",
  '',
];
for (const source of ok) assert.equal(LuakWeb.check(source, 'main.lua'), null, source);

const bad = [
  ['x = = 1', 1, "unexpected symbol near '='"],
  ['if x then\nprint(1)\n', 3, "'end' expected (to close 'if' at line 1) near <eof>"],
  ['local x <const> = 1\nx = 2', 2, "attempt to assign to const variable 'x'"],
  ['goto nowhere', 1, "no visible label 'nowhere' for <goto> at line 1"],
  ['global y = 1\nprint(z)', 2, "variable 'print' not declared"],
];
for (const [source, line, message] of bad) {
  const problem = LuakWeb.check(source, 'main.lua');
  assert.ok(problem, source);
  assert.equal(problem.line, line, source);
  assert.equal(problem.message, message, source);
  assert.match(problem.raw, /^\[string "main\.lua"\]:/);
}

console.log(`luak-web ${LuakWeb.version} OK (${(fs.statSync(bundle).size / 1024).toFixed(0)} KB)`);
