// Loads luak-web.js as a page would - a plain script, no module system - and
// checks it gives the answers the JVM runtime gives.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const bundle = process.argv[2];
const code = fs.readFileSync(bundle, 'utf8');
const sandbox = { console };
sandbox.globalThis = sandbox;
sandbox.window = sandbox;
vm.createContext(sandbox);
vm.runInContext(code, sandbox, { filename: 'luak-web.js' });

// A page with an AMD loader (Monaco's, RequireJS) must still get LuakWeb.
const amd = { console };
amd.globalThis = amd;
amd.window = amd;
amd.define = Object.assign(() => {}, { amd: {} });
vm.createContext(amd);
vm.runInContext(code, amd, { filename: 'luak-web.js' });
assert.ok(amd.LuakWeb, 'LuakWeb appears even when the page has an AMD loader');

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

// The syntax tree: luaparse's shape, only for source Luak compiles.
// Copied out of the sandbox, whose arrays are not this realm's.
const tree = JSON.parse(JSON.stringify(LuakWeb.parse("global *\nlocal name <const> = 'año 😀'\nfunction f(...args) return #args end\n-- done", 'main.lua')));
assert.equal(tree.type, 'Chunk');
assert.deepEqual(tree.body.map((s) => s.type), ['GlobalStatement', 'LocalStatement', 'FunctionDeclaration']);
assert.equal(tree.body[0].wildcard, true);
assert.equal(tree.body[1].init[0].value, 'año 😀');
assert.deepEqual(tree.body[1].attributes, ['const']);
assert.deepEqual(tree.body[1].range, [9, 38]);
assert.deepEqual(tree.body[1].loc, { start: { line: 2, column: 0 }, end: { line: 2, column: 29 } });
assert.equal(tree.body[2].parameters[0].name.name, 'args');
assert.equal(tree.comments[0].value, ' done');
assert.throws(() => LuakWeb.parse('local x <const> = 1\nx = 2', 'main.lua'),
  (e) => e.name === 'Error' && e.line === 2 && e.message === "attempt to assign to const variable 'x'");

console.log(`luak-web ${LuakWeb.version} OK (${(fs.statSync(bundle).size / 1024).toFixed(0)} KB)`);
