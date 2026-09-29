# Take-home: durable identity and an explicit save

Time box: 3 to 4 hours. Work in this repository. No new dependencies, no build tool, no network.

## The brief as a company would write it

Our expense tracker persists to a tab-separated file and allocates ids from `nextId`, which `ExpenseManager.loadExpenses` rebuilds as `highestId + 1` (`src/expensetracker/ExpenseManager.java:34`). A support ticket says an old expense "came back with someone else's name on it". We reproduced it: delete the highest-numbered expense, exit, restart, add a new expense. It gets the deleted id. Anything that recorded the old id, an exported report or a receipt photo filed by number, now points at the wrong record.

We also want the user to be able to save without quitting. Today the only write is `saveExpenses()` in the `finally` block of `App.run` (`src/expensetracker/App.java:46`), so a force-killed process loses the session.

Deliver both, with tests.

## Scope

**1. A durable next-identity value in the file.**

The header line written by `ExpenseFileStorage.save` is `# id\ttitle\tamount\tcategory\tdate\tnote` (`src/expensetracker/ExpenseFileStorage.java:61`), and the loader skips any line starting with `#` (line 38). Carry the allocation value in the file, in a form of your choosing, and decide explicitly what happens to a file that does not have one. Reading a pre-existing file such as `data/expenses.txt` must keep working and must fall back to today's `highestId + 1` behaviour. A header that is present but unreadable is corruption and must be treated like any other corrupt record: fatal, named by line number, destination untouched.

**2. A "Save now" menu option.**

Add it to `App.showMenu` and `App.handleMenuChoice` (`src/expensetracker/App.java:71` and `85`). It must report success or failure distinctly. `saveExpenses` currently returns `void` and prints "Expenses saved to disk." unconditionally after `storage.save` returns (line 65), so a failure path that prints success is an automatic fail on this exercise.

## Acceptance criteria

1. Create ids 1 and 2, delete 2, save, construct a fresh `ExpenseManager` and `ExpenseFileStorage` over the same file, load, add an expense. Its id is 3, not 2.
2. Loading `data/expenses.txt` unchanged still yields 6 expenses and a next id of 7.
3. Loading a file whose allocation header is malformed throws `IOException`, the message names the line number, and the file bytes are identical afterwards.
4. Exhausted identity still refuses to wrap: the behaviour pinned by "exhausted identity never wraps to negative" (`test/expensetracker/RegressionTests.java:104`) must not regress.
5. "Save now" writes the file mid-session; a later reload in the same process sees the saved state; a save failure prints a failure message and the session continues.
6. All 23 existing cases in `test/expensetracker/RegressionTests.java` still pass. You may edit existing cases only where the file format genuinely changed, and you must say which and why.

## What to submit

- The diff, as a patch or a branch.
- A `NOTES.md` of at most one page: the format decision and its migration rule, what you did not do, and the one thing you would fix first with another hour.
- The captured output of the test run, the full `PASS`/`FAIL` list plus the final `RESULT passed=N failed=0` line printed by `test/expensetracker/RegressionTests.java:124`.

Run the suite with:

```
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
```

or, without PowerShell:

```
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out expensetracker.RegressionTests
```

Add your new cases to `test/expensetracker/RegressionTests.java` using the existing helpers: `test(name, check)` at line 11, `equal` at 13, `rejects` at 14, and `withFile` at 16, which creates a temp directory and deletes every file in it afterwards. Use `withFile`; do not write into `data/`.

## Grading rubric

| Criterion | Strong | Weak |
|---|---|---|
| Migration rule | Old files load, the absence of the header is an explicit documented case, and a present-but-broken header is fatal | Guesses a value after a broken header, or silently rewrites files it cannot read |
| Failure never destroys | Every new failure path leaves the destination byte-identical, proven by reading the file before and after as `test/expensetracker/RegressionTests.java:57` does | Only the happy path is tested |
| Test quality | New cases name a behaviour, arrange real state through `withFile`, and assert the observable result; at least one fails against the pre-change code | Asserts on private fields, or tests only that no exception was thrown |
| Save-now contract | `saveExpenses` returns or throws something the menu can branch on; failure prints a failure | Keeps the `void` signature and prints success after a caught `IOException` |
| Boundary discipline | Format knowledge stays inside `ExpenseFileStorage`; `ExpenseManager` learns a next-id value, not a file layout | `App` or `ExpenseManager` parses header text |
| Regression safety | All 23 prior cases pass unmodified, or each edit is justified in `NOTES.md` | Deleted or loosened an existing case to go green |
| Honesty | `NOTES.md` names what is still unsafe, for example that two processes still overwrite each other's snapshots | Claims durability or concurrency safety that the code does not have |

## Reviewer's notes

I open the tests first, and I look for a case that would have failed before your change. If every new test passes on both sides of the diff, the feature is not pinned.

Second, the corrupt-header path. I will hand-edit a saved file, run your loader, and then compare the bytes. `ExpenseFileStorage.load` throws before any write today and `App.loadExpenses` returns false so the save in `run`'s `finally` is never reached (`src/expensetracker/App.java:50-59`); if your change makes a corrupt header recoverable-by-guessing, or lets startup proceed to a save, that is the end of the review.

Third, ordering inside `save`. The current method validates and serializes everything before it creates the temp file (`src/expensetracker/ExpenseFileStorage.java:57-66`), and the `ATOMIC_MOVE` on line 70 has no fallback. If your header work moves I/O above validation, or catches the move's failure and retries with a plain write, you have removed the property the file format exists to protect.

Last, `NOTES.md`. A candidate who writes "the allocation value is only as durable as the last save, so a crash after a delete can still reuse an id" is telling me they understand the boundary of their own fix. That reads better here than a longer diff.
