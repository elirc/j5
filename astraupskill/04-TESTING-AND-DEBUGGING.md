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

The delivery verification also exercised five CLI scenarios in separate Java processes, including EOF and corrupt-startup preservation. Those orchestration checks are recorded in the central implementation evidence; the project's portable runner executes the 23 Java cases. Do not confuse a manually described lab with an automatically executed browser test: this project currently has neither a browser nor an HTTP server.

If `--release 17` reports a compiler-resource error in a restricted environment, first verify the JDK installation and its filesystem access. The reviewed full-access verification passed here. Do not weaken application validation to work around a tooling problem, and do not treat a compilation failure as a passing test run.

## Without PowerShell

```bash
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out expensetracker.RegressionTests
```

