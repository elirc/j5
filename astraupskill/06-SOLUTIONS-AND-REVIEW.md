# Solution guidance and review findings

Read this after you've attempted the [practice exercises](05-PRACTICE.md). The first half describes solution directions. The application doesn't contain them yet, and your code can differ as long as the Check passes and you can explain the tradeoff. The second half lists defects and limits in the delivered code. Each one cites file:line and was reproduced on 2026-10-06 against scratch copies of the data, never `data/expenses.txt`.

## Solution directions

**1. Save now.** Have `saveExpenses` return a `boolean`, or a small result object. Option 10 prints the real outcome and returns `true`, so the loop continues. Leave the in-memory list alone on failure so the user can retry. A weak solution prints "Saved" after calling a method that swallowed the exception. That is exactly what the current `void saveExpenses` invites.

**2. Plain decimals.** Validate the text before calling `new BigDecimal`, for example with `value.matches("[0-9]+([.][0-9]+)?")`, and throw the same `NumberFormatException` path so the existing message appears. Don't add a cap on fraction digits in the regex, because the scale check right after it already gives the better "at most 2 decimal places" message. Escaping trap: inside a Java string literal, write the regex as `"\\d+(\\.\\d+)?"` or use character classes. `"\d"` does not compile.

**3. Exit codes.** Make `run` return whether the session started, and call `System.exit(1)` in `main` when it didn't. Call it outside the try-with-resources body, or accept that `close()` is skipped. For a failed save on exit there are two defensible answers. You can exit non-zero, which is honest, or exit zero after writing the rejected snapshot to a recovery file. Write down which one you chose and why.

**4. Durable ids.** Keep the counter in the same file as the rows, for example a first line `# format=2 nextId=3`, so one `ATOMIC_MOVE` still covers both. Writing the counter to a second file creates the dual-write problem described in [02-CONCEPTS.md](02-CONCEPTS.md). Define three cases: a missing header (old format) falls back to `max + 1`, a malformed header rejects the whole load like any other corrupt line, and the counter never moves backwards. Never reset an invalid counter to 1.

**5. Bad id message.** Write one `parseId` helper that both flows call. It catches `NumberFormatException` and rethrows `IllegalArgumentException("Expense id must be a whole number.")`. Two call sites are the cue to extract a helper.

**6. Paging.** Order the list before slicing it:

```java
// Sketch for the exercise, not an existing method:
matching.stream()
    .sorted(Comparator.comparing(Expense::getDate).thenComparingLong(Expense::getId))
    .skip(offset).limit(limit).toList();
```

Validate `offset` and `limit` first. Offset paging can still skip or repeat a row if the list changes between requests. A `(date, id)` cursor fixes that, and the cursor is exactly the index a database version would need.

**7. Version conflict.** Compare the expected version and replace the entry in one operation. In this single-threaded CLI that's simply a check before `expenses.set`. A web adapter would need a lock or a database `UPDATE ... WHERE id = ? AND version = ?` that inspects the affected-row count. If old rows have no version, load them as version 1.

## Review findings in the delivered code

| # | Severity | Finding | Evidence |
|---|---|---|---|
| 1 | High | **A failed save on exit throws away the whole session and still exits 0.** The only write happens in `run`'s `finally` (`App.java:45-46`). `saveExpenses` prints "Could not save expenses: ..." and returns (`App.java:62-69`). With a read-only data file, a newly added expense was lost, the old file stayed intact, and the exit status was 0. Nothing offers a retry or a recovery file. | Verified with a read-only scratch file: the add, then 7, printed the save error and the file was unchanged. |
| 2 | Medium | **A refused startup exits 0.** `if (!loadExpenses()) return;` (`App.java:34`) returns to `main` (`App.java:29`), which ends normally. Scripts can't detect the refusal. | `data/corrupt-example.txt` copy: the message printed, exit status 0. Exercise 3. |
| 3 | Medium | **Scientific notation and signs are accepted as amounts.** `new BigDecimal(value)` (`ExpenseManager.java:127`) parses `1e2` as 100 and `+5` as 5. A typo in an exponent changes the amount by orders of magnitude. | `1e2` listed as `$100.00`. Exercise 2. |
| 4 | Medium | **Ids are reissued across restarts.** `nextId = highestId + 1` on load (`ExpenseManager.java:34`). The in-session test (`RegressionTests.java:91`) can't catch this because it never restarts. | Added ids 1 and 2, deleted 2, exited. The next add printed "Added expense #2." Exercise 4. |
| 5 | Low | **Load errors lose their reason.** `parseLine` catches `Exception` and rethrows a fixed "Could not parse expense record." (`ExpenseFileStorage.java:100-101`), even though `Expense` has precise messages such as "Amount must use at most 2 decimal places." Passing `ex.getMessage()` (or the cause) along costs nothing. | A saved amount of `1.001` printed `Invalid saved record on line 1: Could not parse expense record.` |
| 6 | Low | **User comments are deleted by the next save.** `load` skips `#` lines (`ExpenseFileStorage.java:38`) and `save` writes only its own header (`ExpenseFileStorage.java:61`). Even a session that changes nothing rewrites the file on exit. | A file starting `# my comment` came back with only the generated header. |
| 7 | Low | **Atomic doesn't mean durable.** `Files.write` then `ATOMIC_MOVE` (`ExpenseFileStorage.java:68-70`) never forces the temp file to disk, so after a power cut the rename can survive while the data doesn't. This is already listed as a limit in [VERIFICATION.md](VERIFICATION.md). | Reading the code; not testable without killing power. |
| 8 | Nit | `getExpensesByCategory` normalizes both sides and still compares with `equalsIgnoreCase` (`ExpenseManager.java:56-60`). It's harmless, but it hides which layer owns case rules. | Reading the code. |

Things the code gets right that are worth copying: validate the whole candidate before mutating (`ExpenseManager.java:22-35`, `updateExpense` at 111-112), fail closed on a corrupt load (`ExpenseFileStorage.java:46-47` together with `App.java:34`), and serialize everything before creating the temp file (`ExpenseFileStorage.java:57-66`).

## Review rubric

Ask the author to demonstrate an invalid update, a refused startup (and its exit status), a failed save, and a restart after deleting the highest id. Then read the tests that pin each one. Give concurrency credit only for a test where two writers actually overlap, and durability credit only for a test that reloads from a new process or a new `ExpenseFileStorage` instead of re-reading the same object. A complete explanation names the authoritative state (the manager's list), the validation layer (the `Expense` constructor plus the manager's parsers), the commit point (the move in `save`, reached only from `run`'s `finally`), the error contract (printed messages, and with exercise 3, exit codes), and the remaining limits (no multi-process coordination, no per-command persistence).
