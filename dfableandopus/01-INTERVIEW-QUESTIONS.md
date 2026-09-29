# Interview questions: Expense Tracker

27 questions with model answers. Marked **junior** or **mid**. Every technical answer points at a real file and line.

## Group A: screening (7)

### A1. What is the data model? **junior**

One entity. `Expense` (`src/expensetracker/Expense.java:9`) holds `long id`, `String title`, `BigDecimal amount`, `String category`, `LocalDate date`, `String note`. All six fields are `final`; there are no setters, so an expense is replaced rather than mutated. `ExpenseManager` (`src/expensetracker/ExpenseManager.java:18`) owns the only live collection, `private final List<Expense> expenses`, plus a `nextId` counter. `ExpenseFileStorage` maps that list to and from a tab-separated file.

### A2. Trace one user action end to end. **junior**

Menu 1. `App.addExpenseFlow` (`src/expensetracker/App.java:119`) prompts for five strings, all raw text. It calls `expenseManager.addExpense(...)` at line 130, which parses and normalizes at `src/expensetracker/ExpenseManager.java:39-43` and then constructs `new Expense(nextId, ...)` at line 45. The constructor validates again. Only after a valid object exists does line 46 add it to the list and line 47 advance `nextId`. `App` catches `IllegalArgumentException | IllegalStateException` at line 132 and prints "Could not add expense: " plus the message. Nothing is written to disk yet: persistence happens in the `finally` block of `run` (`src/expensetracker/App.java:46`).

### A3. Where does validation live? **junior**

In two places, on purpose. `ExpenseManager` parses strings into typed values (`parseAmount` at line 123, `parseDate` at 141, `requireText` at 161), because parsing text is an input concern. The `Expense` constructor then re-asserts the domain rules on the typed values: positive id, non-blank title, exactly two decimal places via `setScale(2, RoundingMode.UNNECESSARY)`, positive amount, non-null date. That means a record read straight off disk by `ExpenseFileStorage.parseLine` (`src/expensetracker/ExpenseFileStorage.java:85`), which never goes through `ExpenseManager`, is still validated. That is the point of duplicating the check.

### A4. Why `BigDecimal` and not `double`? **junior**

Because money must not carry binary rounding error, and because the scale is a business rule, not a display choice. `setScale(2, RoundingMode.UNNECESSARY)` (`src/expensetracker/Expense.java:24`) throws `ArithmeticException` on anything finer than a cent, which the constructor converts into "Amount must use at most 2 decimal places." (line 26). The regression suite pins it: "domain rejects sub-cent precision" (`test/expensetracker/RegressionTests.java:37`) constructs an expense with `new BigDecimal("0.001")` and expects rejection.

### A5. What is the persistence format? **junior**

One header comment line plus one record per line, six tab-separated fields: id, title, amount, category, date, note (`ExpenseFileStorage.toLine`, line 76). Text fields are escaped so a tab or newline inside a value cannot split the record: `escape` at line 105 turns `\` into `\\`, tab into `\t`, and newline and carriage return into `\n` and `\r`. You can see it in the shipped data: `data/expenses.txt:4` has the note `Gift for\tthe office`.

### A6. What happens when the file does not exist versus when it is corrupt? **mid**

Different outcomes, deliberately. Missing is empty: `load` returns an empty list at `src/expensetracker/ExpenseFileStorage.java:26-28`, and the app starts with zero expenses. Corrupt is fatal: any unparseable row throws `IOException("Invalid saved record on line N: ...")` at line 47, `App.loadExpenses` catches it, prints "Startup stopped. The saved file was not changed; repair a copy before retrying." (`src/expensetracker/App.java:57`) and returns false, so `run` never reaches the menu and never reaches the save in its `finally`. Conflating those two would turn a corrupt file into an empty file on the next exit.

### A7. When is data actually written? **mid**

Only in `App.run`'s `finally` block, `saveExpenses()` at `src/expensetracker/App.java:46`. That covers option 7 "Save and exit", a `NoSuchElementException` from EOF on stdin (caught at line 43 with "Input ended. Saving completed changes."), and any other way out of the loop. The limit to state plainly: a force-killed process loses everything since startup, and `astraupskill/VERIFICATION.md` says so.

## Group B: deep dive (10)

### B1. Describe the bug the worked change fixed. **mid**

An expense saved with an empty note disappeared on reload. The serialized record ends with a tab and nothing after it, which is how an empty sixth field is represented. The old loader ran `line.trim()` and handed the *trimmed* string to the parser. Trim removed the trailing tab, `splitPreservingEscapes` then produced five fields, and `parseLine`'s check `fields.size() != 6` (`src/expensetracker/ExpenseFileStorage.java:88`) rejected the record. The old code caught that and continued, so the load looked successful while silently dropping data.

### B2. What is the fix, in one sentence? **mid**

Inspect the trimmed line, parse the original. `ExpenseFileStorage.load` computes `trimmedLine` at line 36 and uses it only for the blank-and-comment skip at line 38, then calls `parseLine(line)` at line 43 with the untouched physical line. The general rule to say out loud: normalization that is correct for recognizing a line is destructive when applied to structured data.

### B3. Why didn't manual testing catch it? **junior**

Because everybody types a note. The bug only fires when an optional field is present-but-empty, which is exactly the case a human tester skips. The regression test "empty optional note survives a save and load" (`test/expensetracker/RegressionTests.java:23`) saves `expense(1,"")`, loads, and asserts both the count and that `getNote()` is `""`. Optional fields need deliberate empty-value coverage, in any stack: the equivalent web bug is coercing `""` to missing with a truthiness check.

### B4. What breaks if you change line 43 back to `parseLine(trimmedLine)`? **mid**

Two suite cases fail immediately. "empty optional note survives a save and load" returns zero records. "escaped text round trips" (`test/expensetracker/RegressionTests.java:38`) stores a note ending in `\ttab`, which survives, but the underlying class of bug is back for any trailing whitespace in a title. It also introduces a silent asymmetry: a note of `" x "` is stored escaped but a note of `""` is not recoverable, so save-then-load stops being a fixed point.

### B5. Explain `splitPreservingEscapes`. Why not `line.split("\t")`? **mid**

`String.split("\t")` would also cut on the literal two characters `\` `t` only if you unescaped first, and unescaping first is wrong because a real embedded tab from the value would then split the record. `splitPreservingEscapes` (`src/expensetracker/ExpenseFileStorage.java:144`) walks the line one char at a time: on a backslash it sets `escaping`; on the next char, it re-appends *both* characters and clears the flag, so the escape pair survives into the field for `unescape` to decode later; it splits only on a *physical* tab that is not part of an escape pair. Note also line 171: a line ending in a lone backslash keeps that backslash rather than dropping it, so `unescape` can report "Incomplete escape sequence." rather than silently losing a character.

### B6. Why does `save` construct a whole `ExpenseManager` just to throw it away? **mid**

`ExpenseFileStorage.save` at line 58 does `var validator = new ExpenseManager(); validator.loadExpenses(snapshot);`. That reuses `loadExpenses`'s duplicate-id check (`src/expensetracker/ExpenseManager.java:28`) as a pre-write validation gate, so an invalid in-memory list can never reach the file. The ordering matters: the snapshot is copied at line 57, validated at 59, serialized into `lines` at 60-62, and only then is a temp file created at line 66. Every failure mode above line 66 leaves the destination byte-identical, which "invalid save preserves existing bytes" (`test/expensetracker/RegressionTests.java:57`) asserts by comparing `Files.readString(file)` before and after.

### B7. What does the atomic move buy, and what does it not? **mid**

`Files.move(temporary, filePath, ATOMIC_MOVE, REPLACE_EXISTING)` (`src/expensetracker/ExpenseFileStorage.java:70`) means a concurrent reader sees either the whole old file or the whole new file, never a truncated one. The previous implementation opened a writer on the destination, which truncates before the first record is serialized. What it does not buy: durability against power loss, and safety between two running instances. `VERIFICATION.md` states both, and adds that atomic replacement is requested with no non-atomic fallback, so an unsupported filesystem produces a visible save failure instead of a silent downgrade.

### B8. What breaks if you delete the `finally { Files.deleteIfExists(temporary); }` at line 71? **mid**

Every failed save leaks a `.expenses-*.tmp` file into the data directory, and repeated failures fill it. Two tests catch it: "repeated save replaces file and removes temporary sibling" (`test/expensetracker/RegressionTests.java:62`) counts the files in the directory and expects exactly 1, and "failed atomic move cleans temporary sibling" (line 66) creates a *directory* where the file should be so the move fails, then asserts the count is still 1 and that an unrelated file inside survives.

### B9. How is identity allocated, and where does it go wrong? **mid**

`nextId` starts at 1 and is reconstructed on load as `highestId + 1` (`src/expensetracker/ExpenseManager.java:34`). Within a session, delete does not recycle: "delete is repeatable and does not recycle ids in session" (`test/expensetracker/RegressionTests.java:91`) deletes id 1 and expects the next add to be 2. The guard against wraparound is the `Long.MAX_VALUE` check on lines 34 and 47 plus the `nextId == 0` throw at line 38, tested at `test/expensetracker/RegressionTests.java:104`. The honest limit, named in `VERIFICATION.md`: because the counter is rebuilt from the *remaining* records, deleting the highest id and restarting reuses it. A database sequence does not have that property, which is a good reason to prefer one.

### B10. Why does `updateExpense` build a replacement before touching the list? **mid**

`ExpenseManager.updateExpense` (line 107) finds the index, then constructs `new Expense(id, title, parseAmount(amount), category, parseDate(date), note)` at line 111 and only calls `expenses.set(i, replacement)` at line 112 if construction succeeded. So a bad date cannot partially apply a good title. "invalid update preserves previous value" (`test/expensetracker/RegressionTests.java:83`) passes `2026-02-30`, expects `IllegalArgumentException`, and then asserts the stored note is still `"original"` and the total is still `12.30`. Note also the distinction at line 116: a missing id throws `NoSuchElementException`, not `IllegalArgumentException`, because "you sent nonsense" and "that row is not here" are different answers.

## Group C: behavioral, STAR (5)

### C1. Tell me about a bug you found that nobody had reported. **mid**

*Situation:* the tracker looked fine in manual testing but I had not tested the optional note as empty. *Task:* find out whether optional-but-empty was handled. *Action:* wrote the smallest failing case first, save one expense with `note = ""`, load, assert one record; it returned zero. Traced it to `line.trim()` being applied before parsing and destroying the trailing tab. Fixed by inspecting the trimmed line and parsing the original. *Result:* that case became the first regression test in the suite, and the final run is 23 passing Java tests plus five cross-process CLI scenarios, 28 checks.

### C2. Tell me about a time you made failure louder. **mid**

*Situation:* the old loader caught a bad row, printed a message and carried on, so a partially-loaded list looked like a successful load, and the save-on-exit would then write that shorter list back over the file. *Task:* make corruption non-destructive. *Action:* made `load` throw `IOException` naming the line number, and made `App.loadExpenses` return false so the menu and the save-on-exit are never reached. *Result:* a CLI scenario in `VERIFICATION.md` confirms the file bytes are unchanged when startup encounters corruption, and the user is told to repair a copy.

### C3. Tell me about a trade-off you made that annoys users. **mid**

*Situation:* strict whole-file rejection means a file the old build partly accepted now refuses to open at all. *Task:* decide between salvage and preservation. *Action:* chose preservation and documented it, in `VERIFICATION.md`: "repair a separate copy and review rejected records rather than silently dropping them. There is no automatic salvage command." *Result:* no data is destroyed by a load, and the missing salvage tool is a known, written-down gap rather than a surprise. In a service, that is the difference between a failed migration you can retry and one you cannot.

### C4. Tell me about a subtle correctness bug you prevented with a test. **mid**

*Situation:* category grouping uppercases the first letter of each word (`Expense.normalizeCategory`, line 66). *Task:* make sure that is not locale-dependent. *Action:* the calls use `toUpperCase(Locale.ROOT)` and `toLowerCase(Locale.ROOT)` explicitly, and the test at `test/expensetracker/RegressionTests.java:109` sets the default locale to `tr-TR`, normalizes `" INCOME   tax "` and `income tax`, and asserts both land in a single bucket keyed `"Income Tax"` totalling `3.00`. *Result:* the Turkish dotless-i class of bug cannot regress silently.

### C5. Tell me about defending an object's internals. **junior**

*Situation:* `getAllExpenses()` originally handing out the live list would let any caller clear the manager. *Task:* make reads safe. *Action:* `getAllExpenses` returns `new ArrayList<>(expenses)` (`src/expensetracker/ExpenseManager.java:52`) and `loadExpenses` copies its argument into a candidate list, validates the whole candidate, and only then clears and refills (`lines 23-33`). *Result:* "collection reads cannot mutate manager" (`test/expensetracker/RegressionTests.java:115`) calls `.clear()` on the returned list and still sees one expense, and "duplicate load does not replace current state" (line 32) proves a rejected load leaves the previous state intact.

## Group D: the follow-ups (5)

### D1. "You validate in the manager and again in the constructor. Isn't that duplication?" **mid**

They validate different things. The manager converts `String` to `BigDecimal`/`LocalDate` and reports input-shaped messages ("Amount must be a valid number.", `src/expensetracker/ExpenseManager.java:137`). The constructor asserts invariants on already-typed values. The proof they are not redundant is `ExpenseFileStorage.parseLine`, which builds an `Expense` directly from the file at line 99 without ever touching `ExpenseManager`. If the domain rules lived only in the manager, a hand-edited file could inject a negative amount.

### D2. "Your delete returns boolean but the CLI asks for confirmation. Why split them?" **junior**

`ExpenseManager.deleteExpense` is `expenses.removeIf(...)` returning whether anything went (line 119). Confirmation lives in `App.deleteExpenseFlow` (`src/expensetracker/App.java:156`), which checks existence first, then requires the literal `yes` at line 163. Keeping interaction policy out of the service means an HTTP adapter can apply its own policy. `astraupskill/08-SAME-RULES-IN-EXPRESS.md` puts it bluntly in the mapping table: the server cannot rely on a client prompt, and authorization goes where the confirmation was.

### D3. "Two people run this at once. What happens?" **mid**

The last one to exit wins, completely. Each process loaded a full snapshot at startup and writes a full snapshot at exit, and the atomic move makes that overwrite clean rather than torn, which is worse for the loser, not better. `VERIFICATION.md` states it: the manager is not thread-safe and multiple instances can overwrite each other's snapshots. The fix is not a lock on the file, it is per-row optimistic concurrency, which is what exercise 4 of the practice ladder and the `If-Match` plus `WHERE version = ?` pattern in chapter 08 are about.

### D4. "Map your error messages to HTTP." **junior**

Blank title, bad number, sub-cent amount, bad date: 400, from the `IllegalArgumentException` family. `NoSuchElementException("Expense #N does not exist.")` (`src/expensetracker/ExpenseManager.java:116`): 404. A version conflict, which the CLI has no equivalent for: 409. A request missing `If-Match`: 428. A successful update: 204. Those are exactly the codes in `astraupskill/08-SAME-RULES-IN-EXPRESS.md`, and it keeps the Java message strings so the two can be diffed.

### D5. "Your escape scheme is hand-rolled. Why not CSV or JSON?" **mid**

For a six-column file that a learner should be able to open in Notepad and understand, a tab-separated line with four escape sequences is smaller than a CSV quoting implementation and much smaller than a JSON parser. It is also easy to get subtly wrong, which is why `unescape` rejects an unknown sequence (line 126) and an incomplete one (line 137) rather than guessing, with a test for each (`test/expensetracker/RegressionTests.java:53`). At production scale the answer flips: use the database and stop owning a serializer.
