# Invariants, identity, snapshots, and commit boundaries

An invariant is a rule that must hold for every accepted state. In this project an expense has a positive ID, nonblank title and category, a valid date, and a positive amount representable at two decimal places. An optional note may be empty. The constructor enforces these rules because records can enter from either the menu or a file; validating only menu input would leave the file-loading path unprotected.

Money uses decimal arithmetic:

```java
new BigDecimal("12.30"); // decimal input is preserved
new BigDecimal("1.001").setScale(2, RoundingMode.UNNECESSARY);
// throws: silently rounding would change the accepted input
```

`12.300` is accepted because reducing its scale to two requires no rounding. `0.001` is rejected, and zero or negative values are rejected separately. This is a teaching application's single-currency rule, not a universal claim that every currency has two fractional digits. When designing a real expense service, make currency and supported precision explicit.

Identity answers which record this is; editable fields answer what it currently contains. Changing Lunch to Bus must retain its ID so references remain meaningful. `updateExpense` constructs a candidate before calling `expenses.set`. If parsing the date fails, the old object stays in the collection and all derived totals remain unchanged. This pattern is the small-scale equivalent of validating a command before committing a database transaction.

```java
Expense old = manager.findExpense(1).orElseThrow();
manager.updateExpense(1, "Bus", "2.50", "Travel", "2026-02-03", "");
// old still describes the previous immutable value.
// manager.findExpense(1) now returns the replacement.
```

A copied list is a collection snapshot, not a durable commit. Clearing the returned list cannot remove the manager's entries. Saving writes the current collection to disk, while updates before exit remain in memory. A forced process termination can lose those unsaved edits. There is also no persisted identity counter: after a restart, the next ID is reconstructed from remaining records. A deleted highest ID can therefore be reused across sessions; preventing that requires a format change or an independent identity strategy.

Whole-file validation establishes another boundary. Duplicate IDs or a malformed line reject the entire load. Returning only the good rows would make a partially recovered dataset look complete. A repair workflow should instead preserve the original, show diagnostics, and ask a person to review a separate recovered copy. That workflow is proposed practice, not automatic behavior.

Atomic replacement prevents readers from observing a partially rewritten destination on filesystems that support the requested operation. It does not provide a cross-process lock, compare-and-swap version check, or guaranteed survival of power loss. Two app instances can still overwrite each other's snapshots. Map this distinction to web development: a transaction protects a particular unit of work, while optimistic concurrency protects a user's edit against someone else's intervening update. You need to identify both boundaries before choosing a database library.

Finally, normalization is a policy. Categories collapse whitespace and use `Locale.ROOT` case conversion on both construction and menu entry, so saved data and newly entered data aggregate consistently. This is intentionally a simple label policy; it is not a full internationalized category identity system.

## Dual write, or two-write consistency

A *dual write* is one logical change that needs two separate durable writes: for example the expense rows in `expenses.txt` and a next-id counter kept in a second file. Two writes can fail independently, so after a crash you can have the rows without the counter (ids get reused) or the counter without the rows (ids skip). The fix is never "write more carefully"; it is to make it one write: keep both in the same file (one atomic move), or in a database, one transaction. `06-SOLUTIONS-AND-REVIEW.md` uses this term for practice exercise 2.

