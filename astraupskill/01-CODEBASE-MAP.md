# Trace the live data through four classes

Start in [App.java](../src/expensetracker/App.java), specifically `main`. It chooses a file path, creates `ExpenseManager` and `ExpenseFileStorage`, then runs the menu. These constructor calls are a small composition root: the application decides which objects collaborate. A future web server could build the same service and a different adapter, although sharing this manager across request threads would require a concurrency design first.

`App.run` first calls `loadExpenses`. Success allows the menu to start. Failure prints a diagnostic and returns before entering the save-on-exit `finally` block. This order matters: loading a broken file must not initialize an empty session that later overwrites the original. During a valid session, EOF is handled as the end of input. Fully completed changes are saved; a command interrupted halfway through collecting its fields has not yet called the manager.

[ExpenseManager.java](../src/expensetracker/ExpenseManager.java) owns the live `List<Expense>` and `nextId`. Follow these operations:

| User action | Manager method | Main result |
|---|---|---|
| Add | `addExpense` | New validated value and allocated ID |
| List | `getAllExpenses` | Copy of the collection |
| Look up ID | `findExpense` | `Optional<Expense>` |
| Update | `updateExpense` | Replacement value with the same ID |
| Delete | `deleteExpense` | Whether anything was removed |
| Summarize | `getTotalSpending`, `getSpendingByCategory` | Values derived from current records |
| Restore | `loadExpenses` | Validated replacement collection |

The model in [Expense.java](../src/expensetracker/Expense.java) is immutable: every field is final, and there are no setters. A list copy alone would not protect state if its elements were mutable. Here, callers can hold the old expense after an update without observing an object that changes underneath them. The manager replaces the list entry with a new valid instance.

[ExpenseFileStorage.java](../src/expensetracker/ExpenseFileStorage.java) translates between model objects and six tab-separated fields. `escape` writes backslashes and control characters as visible escape sequences. `splitPreservingEscapes` finds physical tabs without discarding escape pairs; `unescape` decodes each text field. The loader skips comments and genuinely blank lines, but parses each record's original line so a final empty field remains present.

Saving snapshots the input, validates duplicate identities, and serializes all records before creating a temporary sibling file. Only after writing succeeds does `Files.move` attempt atomic replacement of the destination. The cleanup block removes a leftover temporary file after a failed move. No database, repository framework, dependency injection library, or background autosave is hidden behind this code.

The [regression harness](../test/expensetracker/RegressionTests.java) constructs objects directly and uses real temporary files. Read `update and delete survive persisted reload` first: it joins model, manager, and storage in one observable scenario. Then read the smaller tests to understand which layer rejects each invalid input. Draw the path `menu -> manager -> Expense -> list`, followed later by `list -> storage -> file`; the time gap before saving is an intentional limitation you should be able to explain.
