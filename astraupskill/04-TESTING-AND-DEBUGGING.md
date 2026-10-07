# Test boundaries, not just happy-path menu output

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test` from the project root. The harness uses a small named-test wrapper, prints PASS or FAIL, and exits nonzero if any check fails. It does not require Maven, Gradle, JUnit, network access, or a database. That simplicity keeps attention on arranging a meaningful state and observing the result. A future test-framework migration should preserve these contracts rather than replace them with assertions about private implementation details.

The 23 tests cover several different failure boundaries. Domain cases check precision and category normalization. Manager cases verify identity, invalid-update preservation, copy isolation, exhaustion, and deletion. Storage cases use real temporary files to exercise empty fields, escapes, corruption, duplicate IDs, repeated replacement, and failed moves. The end-to-end Java case updates and deletes records, saves them, constructs a new manager, and inspects the restored state.

For example, this assertion is stronger than merely expecting an exception:

```java
rejects(IllegalArgumentException.class,
    () -> manager.updateExpense(1, "Bus", "2.50", "Travel", "2026-02-30", "changed"));
equal("original", manager.findExpense(1).orElseThrow().getNote());
equal(new BigDecimal("12.30"), manager.getTotalSpending());
```

It checks the failure and proves that existing state survived. Likewise, `invalid save preserves existing bytes` snapshots the prior file and compares its complete contents after rejection. The failed-move test uses a nonempty directory at the destination path, then checks that its existing child remains and no temporary sibling leaked. These tests cover predictable filesystem failures; they do not emulate a sudden machine power cut.

When a save/reload test fails, inspect the physical file before changing the manager. Use `Get-Content -LiteralPath .\data\learning-expenses.txt`, then render tabs visibly if needed:

```powershell
(Get-Content -LiteralPath .\data\learning-expenses.txt) -replace "`t", '<TAB>'
```

The end of a record with an empty note must still contain the sixth-field separator. Put breakpoints in `load`, `parseLine`, and `splitPreservingEscapes`. Compare `line` with `trimmedLine` and count fields. A breakpoint in `App.loadExpenses` then shows whether a load error can reach the menu or the save block.

For update failures, break before `new Expense` and before `expenses.set`. The latter must not execute if construction fails. Hold a reference to the old immutable expense and verify its fields remain unchanged even after a later successful replacement.

## Reproduce the five CLI scenarios yourself

The Java suite builds objects directly, so it never checks the menu, process restarts, or EOF handling. The original delivery recorded five cross-process CLI scenarios, but its scripts are not in this repository. The test below re-creates all five. It uses the `practice/harness.mjs` helper from [05-PRACTICE.md](05-PRACTICE.md), which compiles into a temp directory and points every run at a temp data file. Save it as `practice/cli-scenarios.test.mjs` and run `node --test practice/cli-scenarios.test.mjs` from the project root.

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, copyFileSync } from 'node:fs';
import { join } from 'node:path';
import { cli, dataFile, add } from './harness.mjs';

test('empty note survives a restart', () => {
  const f = dataFile('note.txt');
  cli(f, ...add('Lunch', '12.30'), '7');
  assert.match(readFileSync(f, 'utf8'), /\t2026-01-02\t\r?\n/);   // trailing tab = empty sixth field
  assert.match(cli(f, '7').out, /Loaded 1 expense\(s\)\./);
});

test('update persists across processes', () => {
  const f = dataFile('update.txt');
  cli(f, ...add('Lunch', '12.30'), '7');
  cli(f, '8', '1', 'Bus', '2.50', ' TRAVEL ', '2026-02-03', '', '7');
  assert.match(cli(f, '2', '7').out, /1\s+Bus\s+\$2\.50\s+Travel\s+2026-02-03/);
});

test('cancelled delete keeps the record; confirmed delete removes it', () => {
  const f = dataFile('delete.txt');
  cli(f, ...add('Lunch', '12.30'), '9', '1', 'no', '7');
  assert.match(cli(f, '7').out, /Loaded 1 expense/);
  cli(f, '9', '1', 'yes', '7');
  assert.match(cli(f, '7').out, /Loaded 0 expense/);
});

test('EOF mid-command saves completed work only', () => {
  const f = dataFile('eof.txt');
  const r = cli(f, ...add('Done', '1'), '1', 'Half');          // second add never finishes
  assert.match(r.out, /Input ended\. Saving completed changes\./);
  const rows = readFileSync(f, 'utf8').split(/\r?\n/).filter((l) => l && !l.startsWith('#'));
  assert.equal(rows.length, 1);
});

test('corrupt startup leaves the file byte-identical', () => {
  const f = dataFile('corrupt.txt');
  copyFileSync(join(process.env.J5_ROOT ?? process.cwd(), 'data', 'corrupt-example.txt'), f);
  const before = readFileSync(f);
  const r = cli(f, '1', 'x', '1', 'f', '2026-01-01', '', '7');
  assert.match(r.out, /Invalid saved record on line 3/);
  assert.doesNotMatch(r.out, /Expenses saved to disk/);
  assert.deepEqual(readFileSync(f), before);
});
```

On 2026-10-06 all five passed against the delivered code (`# pass 5`). Two things to notice. First, the corrupt fixture fails on **line 3**, because the header comment is line 1 and line numbers count every physical line. Second, the refused startup still exits with status 0. That gap is practice exercise 3.

If compilation fails, check `javac -version` before suspecting the code. Never weaken validation to work around a tooling problem.

## Without PowerShell

```bash
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out expensetracker.RegressionTests
```

