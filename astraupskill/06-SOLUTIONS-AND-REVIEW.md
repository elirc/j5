# Solution guidance and a practical review rubric

Use these notes after attempting the [practice exercises](05-PRACTICE.md). They describe design directions, not finished code that the application already contains. Your implementation can differ if it satisfies the same observable contracts and you can explain its tradeoffs.

For explicit save, a useful result is a boolean or a small outcome object returned from the adapter's save operation. The menu should remain open after failure and should preserve in-memory edits so the user can retry. Test a writable temporary file and a destination that cannot be replaced. A weak solution always prints `Saved` after calling a method that swallowed an exception. A stronger solution reports the actual outcome and avoids clearing the current collection.

For durable allocation (see "Dual write" in [02-CONCEPTS.md](02-CONCEPTS.md)), keep the saved records and next-ID metadata in the same atomic snapshot. Writing the counter to a second unrelated file introduces a two-write consistency problem. A versioned header might be `# format=2 nextId=3`, but define escaping, range validation, and old-format migration before implementing it. Never reset an invalid counter to one. If you choose UUIDs instead, explain why the public ID type and existing saved records need migration rather than silently changing a field's meaning.

For paging, order before slicing:

```java
// Sketch for an exercise, not an existing method:
matching.stream()
    .sorted(Comparator.comparing(Expense::getDate)
        .thenComparingLong(Expense::getId))
    .skip(offset)
    .limit(limit)
    .toList();
```

The tie-breaker makes equal dates deterministic. Validate offset and limit before processing. If the collection changes between page requests, offset pagination can still skip or repeat records. Explain this limitation and consider a date-and-ID cursor only when the exercise needs it.

For optimistic concurrency, compare the expected version and replace the value within one protected operation. A Java lock can demonstrate the concept for one process. A database adapter might perform `UPDATE ... WHERE id = ? AND owner_id = ? AND version = ?`, then inspect the affected-row count. A zero count must be interpreted carefully: missing, forbidden, or stale state may have different internal causes even if your public error policy deliberately hides some distinctions.

For the HTTP exercise, write a contract table before coding controllers. A reasonable proposed mapping is create 201 with a readable Location, get 200 or 404, invalid body 400, stale version 409, and delete 204 under a documented repeated-delete policy. These are design choices for the exercise, not existing endpoints. Keep amounts as validated decimal strings or another explicitly documented representation so the browser does not accidentally decide the domain's rounding policy.

During review, ask the author to demonstrate an invalid update, a stale edit, a denied cross-owner lookup, and a persistence failure. Then inspect the tests that preserve those behaviors. Award credit for a clear boundary even when the implementation is small. Do not award concurrency credit for a test that starts tasks sequentially, or durability credit for a test that reads the same in-memory object immediately after writing.

A useful final explanation names the authoritative state, the validation layer, the commit point, the error contract, and the remaining limitation. In this delivered CLI, those answers are the manager's collection, the domain and manager, successful atomic save replacement, printed adapter diagnostics or method exceptions, and the absence of multi-process coordination and per-command persistence.
