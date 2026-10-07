# Practice ladder: Goal + Check

Each exercise names a behavior to add and a **Check** you run. The checks are dependency-free `node --test` files (Node 18+ and JDK 17+, nothing to install). They compile `src/` and `test/` into a fresh temp directory and drive the real CLI through stdin, so they never touch `data/expenses.txt` or create `out/`.

Make a `practice/` folder in the project root. It's yours, so keep it out of commits by adding `practice/` to `.git/info/exclude`. Create `practice/harness.mjs` once:

```js
// practice/harness.mjs: compile once into a temp dir, then drive the real CLI.
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const root = process.env.J5_ROOT ?? process.cwd();          // run from the project root
const work = mkdtempSync(join(tmpdir(), 'j5-practice-'));
const classes = join(work, 'classes');
const javaFiles = (dir) => readdirSync(join(root, dir)).filter((f) => f.endsWith('.java')).map((f) => join(root, dir, f));
execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes,
  ...javaFiles('src/expensetracker'), ...javaFiles('test/expensetracker')]);

export const dataFile = (name) => join(work, name);         // never your real data/expenses.txt
export function cli(file, ...lines) {                       // each argument is one line of stdin
  const r = spawnSync('java', ['-cp', classes, 'expensetracker.App', file],
    { input: lines.map((l) => l + '\n').join(''), encoding: 'utf8' });
  return { out: r.stdout, status: r.status };
}
export const regression = () => spawnSync('java', ['-cp', classes, 'expensetracker.RegressionTests'], { encoding: 'utf8' });
export const add = (title, amount, category = 'food', date = '2026-01-02', note = '') => ['1', title, amount, category, date, note];
```

Run one exercise with `node --test practice/ex2-plain-decimals.test.mjs`, or all of them with `node --test "practice/*.test.mjs"`. Each test file compiles once, and every `cli` call starts a JVM, so expect a few seconds per file. After any change to `ExpenseManager` or the storage class, also run `run.ps1 -Test`; all 23 existing regression cases must still pass.

Status on the delivered code (re-run 2026-10-06): every Check below fails until you implement it, except the guard cases that are marked as already passing. Each Check was also run against a scratch solution and passed, apart from exercises 4 and 7, which have no reference solution.

## 1. A save command that does not exit

**Goal:** Add menu option 10, "Save now". It calls the save path, reports the real outcome, and keeps the menu open. Today the only write is the `finally` block in `App.run` (`src/expensetracker/App.java:45-46`), and `saveExpenses` returns nothing (`App.java:62-69`). Before you can report failure honestly, it has to return a result.

**Check** (`practice/ex1-save-command.test.mjs`):

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { cli, dataFile, add } from './harness.mjs';

test('10 saves without exiting, and the menu keeps running', () => {
  const f = dataFile('ex1.txt');
  const r = cli(f, ...add('Lunch', '12.30'), '10', '2', '7');
  const firstSave = r.out.indexOf('Expenses saved to disk.');
  assert.ok(r.out.split('Expenses saved to disk.').length - 1 >= 2, 'expected one save from option 10 and one at exit');
  assert.match(r.out.slice(firstSave), /Lunch/, 'option 2 must still run after option 10');
  assert.match(readFileSync(f, 'utf8'), /Lunch/);
});
```

## 2. Accept plain decimals only

**Goal:** `parseAmount` passes user text straight to `new BigDecimal(value)` (`src/expensetracker/ExpenseManager.java:127`). BigDecimal also accepts scientific notation and a leading sign, so typing `1e2` adds an expense of **$100.00**, which you can see in menu 2. Restrict input to digits with an optional fraction, and report the existing "Amount must be a valid number." message. Keep `12.300` accepted, because the scale check already handles it.

**Check** (`practice/ex2-plain-decimals.test.mjs`). The `1_000` case and the `12.300` case already pass. They are guards, so don't over-tighten the rule.

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { cli, dataFile, add } from './harness.mjs';

for (const amount of ['1e2', '+5', '1_000']) {
  test(`amount ${amount} is rejected`, () => {
    const r = cli(dataFile(`ex2-${amount}.txt`), ...add('X', amount), '2', '7');
    assert.match(r.out, /Could not add expense: Amount must be a valid number\./);
    assert.match(r.out, /No expenses found\./);
  });
}
test('ordinary decimals still work', () => {
  assert.match(cli(dataFile('ex2-ok.txt'), ...add('X', '12.300'), '7').out, /Added expense #1\./);
});
```

## 3. An exit-code contract

**Goal:** When startup refuses a corrupt file, `run` returns early (`App.java:34`), `main` finishes normally, and the process exits **0**. A script or CI job can't tell that apart from success. Make a refused startup exit non-zero. Deciding what a failed save on exit should return is part of the exercise; write your decision down.

**Check** (`practice/ex3-exit-codes.test.mjs`). The second test already passes.

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync } from 'node:fs';
import { join } from 'node:path';
import { cli, dataFile, add } from './harness.mjs';

test('refused startup exits non-zero', () => {
  const f = dataFile('ex3-corrupt.txt');
  copyFileSync(join(process.env.J5_ROOT ?? process.cwd(), 'data', 'corrupt-example.txt'), f);
  assert.notEqual(cli(f, '7').status, 0);
});
test('normal session exits zero', () => {
  assert.equal(cli(dataFile('ex3-ok.txt'), ...add('X', '1'), '7').status, 0);
});
```

## 4. Ids that survive a restart

**Goal:** `loadExpenses` rebuilds `nextId` as `highestId + 1` (`ExpenseManager.java:34`). If you delete the highest id and restart, that id comes back. Persist the allocation watermark in the same file, for example a versioned header line, so a single atomic move still covers both the rows and the counter (see "Dual write" in [02-CONCEPTS.md](02-CONCEPTS.md)). Also define what an old-format file (no header) and a corrupt header mean, and add `RegressionTests` cases for both.

**Check** (`practice/ex4-durable-ids.test.mjs`):

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { cli, dataFile, add } from './harness.mjs';

test('a deleted highest id is not reissued after restart', () => {
  const f = dataFile('ex4.txt');
  cli(f, ...add('A', '1'), ...add('B', '1'), '9', '2', 'yes', '7');
  assert.match(cli(f, ...add('C', '1'), '7').out, /Added expense #3\./);
});
```

## 5. A human message for a bad id

**Goal:** Options 8 and 9 call `Long.parseLong` directly (`App.java:139` and `App.java:158`). `NumberFormatException` is an `IllegalArgumentException`, so the existing catch prints the JDK's text: `Could not update expense: For input string: "abc"`. Translate it into a message the user can act on.

**Check** (`practice/ex5-id-message.test.mjs`):

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { cli, dataFile } from './harness.mjs';

for (const option of ['8', '9']) {
  test(`option ${option} explains a non-numeric id`, () => {
    const r = cli(dataFile(`ex5-${option}.txt`), option, 'abc', '7');
    assert.doesNotMatch(r.out, /For input string/);
    assert.match(r.out, /Expense id must be a whole number\./);
  });
}
```

## 6. A deterministic page

**Goal:** Add `ExpenseManager.page(String month, int offset, int limit)`, ordered by date and then id. Reject a negative offset and a limit outside 1..100. An empty page is a valid result. Write three cases in `test/expensetracker/RegressionTests.java` using the exact names below. Use two expenses on the same date so the tie-breaker matters.

**Check** (`practice/ex6-paging.test.mjs`). It runs the real regression class and needs your named PASS lines:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { regression } from './harness.mjs';

test('your paging tests exist and pass', () => {
  const r = regression();
  for (const name of ['page orders same-date ties by id', 'page rejects negative offset', 'page past the end is empty'])
    assert.match(r.stdout, new RegExp(`^PASS ${name}$`, 'm'), `missing PASS ${name}`);
  assert.equal(r.status, 0);
});
```

## 7. Optimistic concurrency in the manager

**Goal:** Give `Expense` a version and make `updateExpense` take the version the caller read. A stale version throws a dedicated exception and leaves the winning value in place. A successful update stores `version + 1`. This is the in-memory version of the `WHERE version = ?` update in [08-SAME-RULES-IN-EXPRESS.md](08-SAME-RULES-IN-EXPRESS.md). The file format and all existing tests must keep working, so decide how version-less rows load.

**Check** (`practice/ex7-version-conflict.test.mjs`):

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { regression } from './harness.mjs';

test('your conflict tests exist and pass', () => {
  const r = regression();
  for (const name of ['stale version update is rejected and keeps the winner', 'update increments version'])
    assert.match(r.stdout, new RegExp(`^PASS ${name}$`, 'm'), `missing PASS ${name}`);
  assert.equal(r.status, 0);
});
```

## Beyond this repository

The HTTP adapter and the database unit of work are worked through in [08-SAME-RULES-IN-EXPRESS.md](08-SAME-RULES-IN-EXPRESS.md). Do them after exercise 7, because the version column there is the same idea. Owner scoping (every query filtered by the authenticated user, never by a caller-supplied owner field) only means something once accounts exist, and this CLI has none.

When you're done, compare your work with the [solution guidance](06-SOLUTIONS-AND-REVIEW.md).
