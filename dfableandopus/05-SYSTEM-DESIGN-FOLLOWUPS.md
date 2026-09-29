# Scaling questions: from one file to a service

Eight questions an interviewer asks once you have described the expense tracker. Each answer starts from what the code does today and names the cost of the change.

## What today looks like

```text
  one process, one file, whole-snapshot writes

  App.run                 ExpenseManager            ExpenseFileStorage        disk
  [1]                     (in-memory List)          [2]
     |                          |                        |                     |
     |-- loadExpenses() ------->|<-- storage.load() -----|<-- read all lines --|
     |   [3]                    |    validate every row  |                     |
     |                          |                        |                     |
     |-- menu 1/8/9 ----------->| mutate List in memory  |                     |
     |   nothing on disk yet    |                        |                     |
     |                          |                        |                     |
     |-- finally saveExpenses ->|-- getAllExpenses() --->| copy + validate     |
     |   [4]                    |                        | serialize all rows  |
     |                          |                        | write .tmp sibling -|--> [.expenses-*.tmp]
     |                          |                        | ATOMIC_MOVE --------|--> [expenses.txt]
```

[1] `src/expensetracker/App.java:33`  [2] `src/expensetracker/ExpenseFileStorage.java:55`  [3] `src/expensetracker/App.java:52`  [4] `src/expensetracker/App.java:46`

Read amplification and write amplification are both "the whole dataset". Every question below is a consequence of that.

## 1. Two people now use this at once. What happens, and what do you change?

Today the last process to exit wins completely. Each one read a full snapshot at startup and writes a full snapshot at exit; the `ATOMIC_MOVE` on `src/expensetracker/ExpenseFileStorage.java:70` makes that overwrite clean rather than torn, which protects the reader from a half-file but does nothing for the lost edits. `astraupskill/VERIFICATION.md` states it plainly: the manager is not thread-safe and multiple instances can overwrite each other's complete snapshots.

The change is not a file lock. A lock over a whole-file write serialises every user behind every other user. It is per-row optimistic concurrency: a `version` column, a caller that sends the version it read, and a conditional update. Practice exercise 4 in `astraupskill/05-PRACTICE.md` sets this up, and `astraupskill/08-SAME-RULES-IN-EXPRESS.md` shows the shape: `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`, 409 when `changes === 0` and the row exists, 428 when the caller sent no `If-Match`. The trade-off is that clients must now carry and return a version, and a stale client gets an error instead of a silent overwrite. That is the point.

## 2. One million expenses. Where does it break first?

Startup. `ExpenseFileStorage.load` reads the whole file and constructs an `Expense` per line (`src/expensetracker/ExpenseFileStorage.java:34-49`), and `ExpenseManager.loadExpenses` walks the candidate again to build the duplicate-id set (line 26). Saving is worse: `save` serializes every record for a one-field edit. A million rows is a multi-second pause on both ends and the whole dataset resident in memory.

Move to a database and let writes become per-row. Keep the validation, because `Expense`'s constructor is where the invariants live and they are not a storage concern. The honest part of the answer is that this is not a tuning problem, it is a different persistence model, and the file format stops being the source of truth.

## 3. Pagination at that size?

`getAllExpenses` returns a copy of everything (`src/expensetracker/ExpenseManager.java:52`) and `getExpensesByMonth` scans the list (line 68), which is fine at six rows and unusable at a million. Practice exercise 3 already specifies the contract: month, offset and limit, ordered by date then id, with a deliberate tie on the same date to prove the order is total.

Offset pagination is the easy version and it degrades: `OFFSET 900000` still walks the skipped rows, and a concurrent insert shifts the window so a user can see a row twice or miss one. Keyset pagination on `(date, id)` fixes both and costs you the ability to jump to page 500. For an expense list, where people scroll recent-first, keyset is the right default. Either way you need an index that matches the order clause, or you have traded a list scan for a sort.

## 4. Multi-tenant. Where does the owner go?

Nowhere today, and that is the first thing to fix before real personal data touches it. There is no user concept in `Expense` (`src/expensetracker/Expense.java:9`) at all; the whole file belongs to whoever runs the process.

An `owner_id` on the row, taken from the authenticated session and never from the request body, applied in the `WHERE` clause of every read and every write. Practice exercise 6 says the same thing and adds the test that matters: one user's identifier must not retrieve another user's expense. The trade-off is that "forgot the owner filter" becomes a data leak that looks like a working feature in every single-user test, so the enforcement belongs in one repository layer, not in each handler.

## 5. This runs as a CLI. What has to happen before it can be an HTTP service?

The domain is close to ready and the transport layer does not exist. The service already refuses to mix concerns: `ExpenseManager.deleteExpense` just returns whether anything was removed (`src/expensetracker/ExpenseManager.java:119`) while the confirmation prompt lives in `App.deleteExpenseFlow` (`src/expensetracker/App.java:163`). That split is exactly what lets a different adapter apply a different policy, and the mapping table in `astraupskill/08-SAME-RULES-IN-EXPRESS.md` says the server replaces the confirmation with authorization rather than with a prompt.

What you add: status codes for the exception families the domain already distinguishes. `IllegalArgumentException` from `parseAmount` or `parseDate` is 400; `NoSuchElementException("Expense #N does not exist.")` (`src/expensetracker/ExpenseManager.java:116`) is 404; version conflicts are 409; a missing precondition header is 428; a successful update is 204. You also add request-body strictness, which the CLI never needed because a prompt cannot send an unknown field.

## 6. Where do background jobs enter, and what is the first one?

There are none today; every effect happens on the user's thread between two prompts. The first real one is the export or report that the current `getSpendingByCategory` (`src/expensetracker/ExpenseManager.java:91`) computes synchronously by walking every expense. At a million rows per tenant that becomes a job: enqueue, compute, store the result, notify.

The trap to name before the interviewer does is dual writes. The moment a mutation both writes the row and enqueues a job, two systems can disagree; `astraupskill/02-CONCEPTS.md` defines that term for this project. Today the equivalent risk is tiny because there is exactly one write and it is the atomic move. Keep it that way: write the job to the same transaction as the row, and let a relay publish it.

## 7. Observability. What would you instrument first?

The application currently prints to stdout and nothing else, and `astraupskill/VERIFICATION.md` names the gap: there is no structured process-exit contract, so a script cannot distinguish a failed startup from a clean one. That is the first fix, and it is also the cheapest.

After that, instrument the boundaries the code already treats as significant: count rejected loads by reason (the message from `src/expensetracker/ExpenseFileStorage.java:47` already carries the line number), count failed saves, and count version conflicts once they exist. Conflict rate is the metric that tells you whether your concurrency model is right, and it is invisible in logs unless you emit it deliberately. Resist logging the record contents; expense titles and notes are personal data.

## 8. Migrating the file into the target stack. How do you make it safe?

Read-only, idempotent, and verified by a round trip. The file is the sole source of truth, so the import must not modify it. `astraupskill/08-SAME-RULES-IN-EXPRESS.md` ends with exactly this exercise, and it picks the right proof: load `data/expenses.txt` and show that the escaped-tab note on `data/expenses.txt:4` survives into the database and back out unchanged. That row exists in the fixture for this reason.

Two details decide whether the migration is correct. Money must land as integer cents with a positive check, matching `setScale(2, RoundingMode.UNNECESSARY)` at `src/expensetracker/Expense.java:24`, so that a value the file accepted cannot be silently rounded on the way in. And the identity sequence must be set past the highest imported id, because the database allocates ids and the file's `nextId` rule (`src/expensetracker/ExpenseManager.java:34`) does not travel with the data. Run the import twice in staging; if the second run is not a no-op, you do not have a migration, you have an insert script.
