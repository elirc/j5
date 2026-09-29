# Stories and resume lines: Expense Tracker

## Story 1: the expense that vanished because its note was empty

**Situation.** A file-backed expense tracker. Save an expense, restart, and it is there. Save an expense with the optional note left blank, restart, and it is gone. Nobody had reported it because manual testers always type something in the note box.

**Task.** Reproduce it as the smallest possible failing test, then fix it without weakening the parser.

**Action.** The saved record is six tab-separated fields and ends with a tab followed by nothing, which is how an empty sixth field is spelled. The loader was calling `line.trim()` and handing the trimmed string to the parser, which removed that final tab; `parseLine`'s strict `fields.size() != 6` check then rejected the record, and the surrounding catch swallowed it so the load still looked successful. The fix splits the two jobs: `trimmedLine` is used only to recognize blank lines and `#` comments, and the untouched physical line goes to `parseLine` (`src/expensetracker/ExpenseFileStorage.java:36-43`).

**Result.** The failing case became test one of the suite, "empty optional note survives a save and load" (`test/expensetracker/RegressionTests.java:23`). Four regression tests failed against the original implementation; 23 pass now, plus five cross-process CLI scenarios, for 28 checks recorded in `astraupskill/VERIFICATION.md`.

## Story 2: a bad file used to become a shorter file

**Situation.** The same investigation exposed something worse than the missing note. The old loader caught a malformed row, printed a message and carried on. The application then held a partial list, and on exit it wrote that partial list back over the file. A single damaged line quietly ate everything after it.

**Task.** Make a corrupt file non-destructive without adding a recovery mode I could not test.

**Action.** Three layers, because any one alone is insufficient. `load` now throws `IOException("Invalid saved record on line N: ...")` and checks for duplicate ids while reading (`src/expensetracker/ExpenseFileStorage.java:44-47`). `App.loadExpenses` catches that, prints "Startup stopped. The saved file was not changed; repair a copy before retrying." and returns false (`src/expensetracker/App.java:57`), so `run` never enters the menu loop and therefore never reaches the `saveExpenses()` in its `finally`. And `save` validates and serializes a snapshot completely before creating a temp file, so nothing invalid can reach the destination.

**Result.** A CLI scenario in `VERIFICATION.md` starts the app against a corrupt file and confirms the file bytes are unchanged afterwards. The cost is explicit and written down: strict loading refuses files the old build partly accepted, and there is no automatic salvage command.

## Story 3: making the save itself atomic

**Situation.** Even with valid data, the original save opened a writer directly on `data/expenses.txt`, which truncates the file before the first record is serialized. Any exception during serialization left the user with a partial file and no copy of the original.

**Task.** Make the destination move from one complete state to another with no observable state in between.

**Action.** `ExpenseFileStorage.save` now takes an immutable snapshot, validates it by running it through a throwaway `ExpenseManager` (line 58), builds every output line in memory, writes a temp sibling created in the same directory, and calls `Files.move(temporary, filePath, ATOMIC_MOVE, REPLACE_EXISTING)` at line 70, with a `finally` that deletes the temp if it survived. Atomic replacement is requested with no fallback, so an unsupported filesystem fails visibly rather than silently downgrading to a truncating write.

**Result.** Two tests hold the line: "repeated save replaces file and removes temporary sibling" counts the directory contents and expects exactly one file, and "failed atomic move cleans temporary sibling" (`test/expensetracker/RegressionTests.java:66`) forces the move to fail and checks that no debris and no collateral damage remain.

## Resume bullets

- Diagnosed and fixed a silent data-loss defect in a file-backed Java expense tracker where any record with an empty optional field was discarded on load; root cause was whole-line trimming before field splitting, and the fix separated line inspection from line parsing.
- Converted partial, best-effort file loading into whole-file validation that reports the offending line number and aborts startup before any write, so a single corrupt row can no longer truncate the saved dataset on exit.
- Made persistence atomic by validating a snapshot, serializing to a temporary sibling and replacing the destination with `ATOMIC_MOVE`, with no silent fallback to a truncating write and guaranteed temp-file cleanup on failure.
- Built a 23-case dependency-free regression suite over real temporary files covering escaped text round-trips, duplicate identities, sub-cent precision, identity exhaustion and locale-independent normalization; 4 of the cases failed against the pre-fix implementation.
- Moved money handling to `BigDecimal` with a fixed scale of 2 and `RoundingMode.UNNECESSARY`, so sub-cent amounts are rejected at the domain boundary rather than silently rounded at display time.

## 60-second spoken summary

"j5 is a Java 17 command-line expense tracker that stores records in a tab-separated file. The story worth telling is the bug I found in it. An expense whose optional note was blank disappeared after a restart. The record ends with a tab and nothing after it, which is how you write an empty last field, and the loader was trimming the whole line before splitting it. Trim ate that tab, the parser counted five fields instead of six, rejected the record, and the catch block above it swallowed the error, so the load reported success while dropping data. The fix was to inspect the trimmed line but parse the original. That opened up a second problem: the old code loaded partially and then saved that partial list back over the file, so one bad line could eat the rest. Now a bad row aborts startup with its line number and the file is never touched. Saving writes a temp file and does an atomic rename. Twenty-three regression tests, four of which failed against the original code, plus five cross-process CLI scenarios."

## What I would do next

From `astraupskill/05-PRACTICE.md` and the limits in `VERIFICATION.md`:

1. Per-row optimistic concurrency with a version field, which is practice exercise 4 and the thing that makes two simultaneous instances safe. Today the last process to exit overwrites the other's whole snapshot.
2. A durable identity counter. `nextId` is rebuilt as `highestId + 1` from the surviving records (`src/expensetracker/ExpenseManager.java:34`), so deleting the highest id and restarting reuses it. The course includes a migration exercise for this.
3. A structured process-exit contract. The CLI prints startup and save failures but a script cannot distinguish them from success.
4. Build the Express plus SQLite port in chapter 08 and write its four tests: invalid amount is 400 with the row unchanged, unknown id is 404, stale `If-Match` is 409, a good update is 204 with a moved `ETag`.
