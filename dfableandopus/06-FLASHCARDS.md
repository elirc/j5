# Flashcards: Expense Tracker

36 cards. Cover the right column, answer aloud, then check. Every answer names a real symbol, file or value in this project.

| Question | Answer |
|---|---|
| What are the six fields of `Expense`? | `id`, `title`, `amount`, `category`, `date`, `note`, all `final` (`src/expensetracker/Expense.java:10-15`) |
| Which type holds money, and at what scale? | `BigDecimal` at `setScale(2, RoundingMode.UNNECESSARY)` (`src/expensetracker/Expense.java:24`) |
| What message does a sub-cent amount produce? | "Amount must use at most 2 decimal places." |
| What message does a zero or negative amount produce? | "Amount must be greater than zero." (`src/expensetracker/Expense.java:34`) |
| Which date format does the domain accept? | `yyyy-MM-dd`, parsed by `LocalDate.parse` in `parseDate` (`src/expensetracker/ExpenseManager.java:141`) |
| What does a `null` note become? | `""`, via `note == null ? "" : note.trim()` (`src/expensetracker/Expense.java:30`) |
| Which method reports whether a note is worth printing? | `Expense.hasNote()`, `!note.isBlank()` (line 62) |
| What was the original bug in one sentence? | `load` trimmed the line before parsing, eating the trailing tab of an empty note, so a six-field record arrived as five and was dropped |
| What is the fix, in one sentence? | Inspect `trimmedLine`, parse the original `line` (`src/expensetracker/ExpenseFileStorage.java:36` and `43`) |
| Which check rejected the shortened record? | `fields.size() != 6` in `parseLine` (`src/expensetracker/ExpenseFileStorage.java:88`) |
| Which test pins the fix? | "empty optional note survives a save and load" (`test/expensetracker/RegressionTests.java:23`) |
| How many regression tests pass now? | 23 Java cases, plus 5 cross-process CLI scenarios, 28 checks total |
| How many failed against the original code? | 4 |
| What happens when the data file is missing? | `load` returns an empty list (`src/expensetracker/ExpenseFileStorage.java:26-28`) |
| What happens when one row is corrupt? | `IOException("Invalid saved record on line N: ...")`, the whole load fails (line 47) |
| What does `App` print after a failed load? | "Startup stopped. The saved file was not changed; repair a copy before retrying." (`src/expensetracker/App.java:57`) |
| Why does a failed load protect the file? | `loadExpenses` returns false, so `run` returns before the `finally` that calls `saveExpenses()` (`src/expensetracker/App.java:34` and `46`) |
| Which line performs the only durable write? | `Files.move(temporary, filePath, ATOMIC_MOVE, REPLACE_EXISTING)` (`src/expensetracker/ExpenseFileStorage.java:70`) |
| Is there a non-atomic fallback? | No. An unsupported filesystem produces a visible save failure |
| What cleans up the temp file? | `finally { Files.deleteIfExists(temporary); }` (line 72) |
| Which test counts files in the directory? | "repeated save replaces file and removes temporary sibling" (`test/expensetracker/RegressionTests.java:62`), expecting exactly 1 |
| Why does `save` build a throwaway `ExpenseManager`? | To reuse the duplicate-id check in `loadExpenses` as a pre-write gate (`src/expensetracker/ExpenseFileStorage.java:58-59`) |
| In what order does `save` do its work? | copy snapshot, validate, serialize, create temp, write, atomic move, delete temp |
| What are the four escape sequences? | `\\`, `\t`, `\n`, `\r`, produced by `escape` (`src/expensetracker/ExpenseFileStorage.java:105`) |
| What does `unescape` do with an unknown sequence? | Throws "Unknown escape sequence." (line 126) |
| What does a trailing lone backslash produce? | "Incomplete escape sequence." (line 137) |
| Why not `line.split("\t")`? | A real tab inside a value would split the record; `splitPreservingEscapes` splits only on physical tabs and keeps escape pairs (line 144) |
| Where is the escaped-tab fixture? | `data/expenses.txt:4`, note `Gift for\tthe office` |
| How is `nextId` restored on load? | `nextId = highestId == Long.MAX_VALUE ? 0 : highestId + 1` (`src/expensetracker/ExpenseManager.java:34`) |
| What happens when the id space is exhausted? | `addExpense` throws `IllegalStateException("Expense id space is exhausted.")` (line 38) |
| Does deleting recycle ids within a session? | No. Test at `test/expensetracker/RegressionTests.java:91` |
| Does deleting recycle ids across a restart? | Yes, if you delete the highest id. Named as a limit in `astraupskill/VERIFICATION.md` |
| Why does `updateExpense` build a replacement first? | So a bad field cannot partially apply a good one; `expenses.set(i, replacement)` runs only after construction (`src/expensetracker/ExpenseManager.java:111-112`) |
| Which exception means "that row is not here"? | `NoSuchElementException("Expense #N does not exist.")` (line 116) |
| Why `Locale.ROOT` in `normalizeCategory`? | A Turkish default locale would lowercase `I` to `ı` and split one category into two buckets (`src/expensetracker/Expense.java:71-72`) |
| Which locale does the test force? | `tr-TR`, expecting `"Income Tax"` to total `3.00` (`test/expensetracker/RegressionTests.java:109`) |
| Why does `getAllExpenses` copy? | `new ArrayList<>(expenses)` stops a caller mutating the manager (`src/expensetracker/ExpenseManager.java:52`) |
| What status codes does the Express port use? | 400 invalid field, 404 unknown id, 409 stale version, 428 missing `If-Match`, 204 on success (`astraupskill/08-SAME-RULES-IN-EXPRESS.md`) |
| What replaces the delete confirmation in a service? | Authorization. The server cannot rely on a client prompt |
| What command runs the suite? | `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test`, which compiles with `--release 17 -Xlint:all` (`run.ps1:8`) |
