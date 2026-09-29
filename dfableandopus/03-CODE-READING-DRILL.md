# Code reading drill: Expense Tracker

Six drills for a shared-screen round. Read the excerpt, answer out loud, then open the `<details>`. Excerpts are copied from this repository at the line ranges given. Drills 3 and 6 contain deliberately altered code.

## Drill 1: explain this function

From `src/expensetracker/ExpenseFileStorage.java:144-169`.

```java
    private List<String> splitPreservingEscapes(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);

            if (escaping) {
                currentPart.append('\\').append(current);
                escaping = false;
                continue;
            }

            if (current == '\\') {
                escaping = true;
                continue;
            }

            if (current == '\t') {
                parts.add(currentPart.toString());
                currentPart.setLength(0);
            } else {
                currentPart.append(current);
            }
        }
```

Say what it does, why the appended `'\\'` on line 153 is not a bug, and what happens to a line that ends with a lone backslash.

<details>
<summary>Answer</summary>

It splits a record into fields on physical tab characters while leaving escape pairs intact for a later pass. The two-character sequence backslash-`t` is a *literal* tab inside a value; a real `0x09` is a separator. The state machine distinguishes them: seeing a backslash sets `escaping` and consumes nothing, and the next character is re-emitted together with its backslash on line 153, so the pair survives into the field. `unescape` (`src/expensetracker/ExpenseFileStorage.java:113`) decodes it afterwards. If you unescaped first and split second, a note containing a tab would split the record and you would get seven fields.

The lone trailing backslash is handled after the loop, at lines 171-173: `if (escaping) currentPart.append('\\');`. Keeping it means `unescape` sees an unterminated escape and throws "Incomplete escape sequence." (line 137). Dropping it would silently lose a character the user typed. The test "unknown escape is rejected without dropping its slash" (`test/expensetracker/RegressionTests.java:53`) covers the neighbouring case.
</details>

## Drill 2: explain this loop, then name the bug it used to have

From `src/expensetracker/ExpenseFileStorage.java:34-49`.

```java
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                try {
                    Expense expense = parseLine(line);
                    if (!ids.add(expense.getId())) throw new IllegalArgumentException("Duplicate expense id: " + expense.getId());
                    expenses.add(expense);
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid saved record on line " + lineNumber + ": " + ex.getMessage(), ex);
                }
            }
```

<details>
<summary>Answer</summary>

Two separate ideas share one loop. `trimmedLine` exists only to *recognize* a line: blank, or a `#` header comment like the first line of `data/expenses.txt`. The value passed to `parseLine` on line 43 is the untouched `line`. That distinction is the fix. Previously the trimmed string was parsed, and because a record with an empty note ends in a tab with nothing after it, trimming removed the sixth field; `parseLine`'s `fields.size() != 6` check (line 88) then rejected a perfectly good row.

The second idea is that rejection is fatal, not skipped. A bad row throws `IOException` naming `lineNumber`, so a half-loaded list never exists. Duplicate ids are caught in the same place with the same consequence. `App.loadExpenses` turns that into a false return (`src/expensetracker/App.java:58`) and `run` exits before the `finally` that saves.
</details>

## Drill 3: spot the bug

This version of `save` is **not** the code in the repository. One thing has been changed. Find it and say what fails.

```java
    public void save(List<Expense> expenses) throws IOException {
        List<Expense> snapshot = List.copyOf(expenses);
        Path parent = filePath.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".expenses-", ".tmp");
        var validator = new ExpenseManager();
        validator.loadExpenses(snapshot);
        List<String> lines = new ArrayList<>();
        lines.add("# id\ttitle\tamount\tcategory\tdate\tnote");
        for (Expense expense : snapshot) lines.add(toLine(expense));
        try {
            Files.write(temporary, lines);
            Files.move(temporary, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
```

<details>
<summary>Answer</summary>

The mutation moved `Files.createTempFile` above the validation and serialization. In the real code (`src/expensetracker/ExpenseFileStorage.java:55-73`) the order is: copy the snapshot (57), validate it through a throwaway `ExpenseManager` (58-59), build every output line (60-62), and only then create the temp file (66).

The destination file is still safe either way, because the move never happens. The damage is debris: `validator.loadExpenses(snapshot)` throws `IllegalArgumentException` on a duplicate id, and that throw now happens *after* the temp file exists and *outside* the `try`, so the `finally` that deletes it is never entered. Every rejected save leaves a `.expenses-*.tmp` file behind. "repeated save replaces file and removes temporary sibling" (`test/expensetracker/RegressionTests.java:62`) asserts the directory holds exactly one file, so the leak is visible as soon as a rejected save precedes it.
</details>

## Drill 4: predict the result

The user picks menu option 8, enters id `1` for an existing expense, then title `Bus`, amount `2.50`, category `Travel`, date `2026-02-30`, note `changed`. What does the console print, and what is the stored state afterwards?

<details>
<summary>Answer</summary>

Console: `Could not update expense: Date must use the format yyyy-MM-dd.`

`App.updateExpenseFlow` collects all five values first, then calls `expenseManager.updateExpense` (`src/expensetracker/App.java:149`). Inside, the replacement is constructed on one line, `new Expense(id, title, parseAmount(amount), category, parseDate(date), note)` (`src/expensetracker/ExpenseManager.java:111`); `parseDate` rejects February 30 because `LocalDate.parse` throws `DateTimeParseException`, remapped to `IllegalArgumentException` with that message (line 147). The `expenses.set(i, replacement)` on line 112 is never reached, so the row keeps its old title, amount, category, date and note, and the total is unchanged. `App` catches the exception at line 151 and prints the prefix.

The interviewer's follow-up is usually "and the id?" The answer is that this is a replacement, not a patch: the expense keeps its identity because `id` is passed straight through, which "update keeps identity and changes aggregates" (`test/expensetracker/RegressionTests.java:76`) asserts for the success path.
</details>

## Drill 5: predict the output under a hostile locale

The JVM default locale is `tr-TR`. One expense is loaded with category `" INCOME   tax "`, then `addExpense` is called with category `income tax`. What does `getSpendingByCategory()` contain?

<details>
<summary>Answer</summary>

One entry, key `"Income Tax"`, value `3.00`. Both strings reach `Expense.normalizeCategory` (`src/expensetracker/Expense.java:66`), which trims through `requireText`, splits on `\\s+` so the run of spaces collapses, and rebuilds each word with `toUpperCase(Locale.ROOT)` on the first character and `toLowerCase(Locale.ROOT)` on the rest. `Locale.ROOT` is the load-bearing part: under Turkish rules a default-locale `toLowerCase` maps `I` to the dotless `ı`, so the two spellings would normalize to different keys and `getSpendingByCategory` (`src/expensetracker/ExpenseManager.java:91`) would show two buckets of `1.00` and `2.00`. The test "category normalization is shared and locale independent" (`test/expensetracker/RegressionTests.java:109`) sets the default locale to `tr-TR` for exactly this reason and restores it in a `finally`.

Say the limit too: this is a case-and-spacing policy, not a taxonomy. `astraupskill/VERIFICATION.md` calls it a simple demonstration policy, and `getExpensesByCategory` compares with `equalsIgnoreCase` on top of it (`src/expensetracker/ExpenseManager.java:60`).
</details>

## Drill 6: review this diff

A teammate proposes a note search. Three defects. Find them.

```diff
--- a/src/expensetracker/ExpenseManager.java
+++ b/src/expensetracker/ExpenseManager.java
@@
+    public List<Expense> searchByNote(String term) {
+        String needle = term.toLowerCase();
+        List<Expense> matches = expenses;
+        for (Expense expense : expenses) {
+            if (!expense.getNote().toLowerCase().contains(needle)) {
+                matches.remove(expense);
+            }
+        }
+        return matches;
+    }
```

<details>
<summary>Answer</summary>

1. `matches = expenses` aliases the live collection, so `matches.remove(...)` deletes real expenses. Every other read path in this class returns a fresh list: `getAllExpenses` is `new ArrayList<>(expenses)` (`src/expensetracker/ExpenseManager.java:52`) and `getExpensesByCategory` builds a separate `matches` (line 57). "collection reads cannot mutate manager" (`test/expensetracker/RegressionTests.java:115`) exists to stop this.
2. Removing from the same list you are iterating throws `ConcurrentModificationException` on the next step. Build a new list and add to it instead.
3. `toLowerCase()` with no argument uses the default locale, the exact defect drill 5 is about. The rest of the class is explicit: `Locale.ROOT` in `Expense.normalizeCategory` (`src/expensetracker/Expense.java:71-72`).

A fourth point earns extra credit rather than being a defect: `term` is not run through `requireText`, so a blank search matches every record instead of reporting "Term cannot be blank.", which is how every other string input in this class behaves (`src/expensetracker/ExpenseManager.java:161`).
</details>
