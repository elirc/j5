# Expense tracker: reliable CRUD before a web framework

This course uses the four Java classes that actually run this project. You will create, read, update, delete, save, and reload expenses, then reason about which guarantees survive when the same behavior becomes a web API. The application is still a local command-line program. An HTTP adapter, authentication, database, and browser interface are later exercises, not features silently implied by the word CRUD.

The useful starting question is: can a user trust that a successfully saved expense will still exist next time? The original implementation failed this question for the ordinary case of an empty optional note. The new regression suite reproduces that defect and protects the correction. Learning to find a small destructive edge case is more valuable here than memorizing a framework's folder layout.

## Prerequisites and glossary

**Prerequisites:** Java collections and `Optional`, `BigDecimal` versus `double`, reading and writing files with `java.nio.file`, and running a script from PowerShell. No web framework knowledge is required.

| Term | Meaning here | Where in this project |
|---|---|---|
| the live list | The single in-memory collection every menu action reads and writes. | the `expenses` field in `src/expensetracker/ExpenseManager.java` |
| identity allocation | Ids handed out once and never reused, even after a delete. | `nextId` in `ExpenseManager.java` |
| replacement update | An edit builds a new `Expense` and swaps it in, so a rejected edit changes nothing. | `updateExpense` in `ExpenseManager.java` |
| money precision | Amounts are `BigDecimal`, parsed and scaled once at the boundary. | `parseAmount` in `ExpenseManager.java`, the `amount` field in `Expense.java` |
| normalization | Categories are trimmed, whitespace-collapsed and title-cased once so grouping is predictable. | `Expense.normalizeCategory` (`Expense.java:66`); the manager's `normalizeCategory` only delegates to it |
| whole-file validation | One bad line rejects the entire load instead of half-restoring data. | `load()` in `src/expensetracker/ExpenseFileStorage.java` |
| escaping | Tabs and newlines inside a field are encoded so a record cannot split itself. | `escape`, `unescape` and `splitPreservingEscapes` in `ExpenseFileStorage.java` |
| empty optional field | A note that is absent must survive a save and reload unchanged. | `hasNote()` in `Expense.java`, the note column in `toLine`/`parseLine` |
| atomic save | Write a temporary file, then move it over the real one. | `Files.move(temporary, filePath, ATOMIC_MOVE, REPLACE_EXISTING)` in `save()` |
| commit boundary | The point where memory becomes the file; before it, nothing is durable. | the `finally` block in `App.run` (`App.java:45-46`); menu 7 only ends the loop, and EOF reaches the same block |
| regression test | A check that fails without the fix and would catch its removal. | `test/expensetracker/RegressionTests.java` |

## Run and test

Use JDK 17 or newer. Set `JAVA_HOME` to your installed JDK directory if Java is not on PATH; do not copy another person's installation path. From the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\learning-expenses.txt
```

The script compiles with `--release 17 -Xlint:all`. `-Test` runs 23 dependency-free regression tests with temporary files. The second command starts an isolated learning dataset, so your normal expense file is not used. Relative `DataPath` values resolve from your current directory. With no override, the script uses the project's `data/expenses.txt`. Direct invocation of `App` instead defaults relative to the Java process's working directory.

Add Lunch, amount `12.30`, category `food`, date `2026-01-02`, and a blank note. Choose 7 to save and exit. Restart with the same path and list expenses. Now use 8 to replace its editable fields and 9 to delete it after confirmation. Menu 7 retains its existing exit role; the new operations are appended.

## Suggested sequence and evidence

1. Read the [code map](01-CODEBASE-MAP.md) and locate the only live list.
2. Study [invariants and boundaries](02-CONCEPTS.md), then predict a rejected update's result.
3. Follow the [worked change](03-WORKED-CHANGE.md) through parsing and saving.
4. Run the [testing guide](04-TESTING-AND-DEBUGGING.md) and [trace lab](07-TRACE-LAB.md).
5. Complete the [practice ladder](05-PRACTICE.md), using [review guidance](06-SOLUTIONS-AND-REVIEW.md) after your own attempt.
6. Read [verification and limits](VERIFICATION.md) before claiming production readiness.

The practice Checks and the CLI scenario test in chapter 04 share one small `practice/harness.mjs`. It needs only Node 18+ and the JDK, compiles into a temp directory, and never touches your data file.

Allow several focused sessions rather than finishing every exercise at once. A useful review artifact contains the input, expected result, actual observation, relevant source method, and a regression test. Explain why a failed update cannot partly change a stored expense, why a missing file differs from a corrupt file, and why atomic file replacement does not make two simultaneous application instances safe. Those explanations connect this small project to mid-level CRUD development.

The implemented improvements include immutable replacement updates, explicit deletion confirmation, domain-level money precision, whole-file validation, guarded identity allocation, consistent category normalization, and atomic save replacement. They preserve the project's small scale while making its behavior teachable and testable.
