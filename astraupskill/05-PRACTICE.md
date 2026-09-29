# Practice ladder toward a CRUD web application

Complete one exercise at a time. For each, write the behavior contract, add a failing test, implement the smallest coherent change, and explain what is still outside its guarantee. The exercises below are proposals; they are not included features. Keep learning data separate from any existing expense file.

## 1. Add an explicit save command

The current menu saves on exit or EOF. Add a command that saves without ending the session and reports whether saving succeeded. Acceptance example: add Lunch, save, add Tea, then simulate ending the process without another save. A new instance should see Lunch but not Tea. Make the failure result visible rather than printing success unconditionally. Hint: `saveExpenses` currently returns no result, so consider how the menu can distinguish success from failure. Do not claim that this command creates automatic per-operation durability.

## 2. Preserve identity allocation across restarts

Today `nextId` is reconstructed from the maximum remaining ID. Deleting the largest record and restarting can reuse that ID. Propose a versioned file header containing the next allocation value, or a different identity type. Acceptance example: create IDs 1 and 2, delete 2, save, restart, create another record; it must not receive 2. Also test an old-format file, a corrupt header, and exhausted identity space. Hint: an optional new header needs an explicit migration rule; silently guessing after a corrupt header defeats the guarantee.

## 3. Add a filtered, deterministic page

Implement a query that accepts a month, offset, and limit and returns records ordered by date then ID. Use two expenses on the same date to force a tie. Reject negative offset and unreasonable page sizes. An empty page is a valid result. Explain why an in-memory list scan is acceptable at this scale and why a database implementation would need a matching filter/order/index strategy. Do not optimize by storing a second mutable aggregate before you can keep it consistent.

## 4. Add optimistic concurrency to updates

Introduce a version value and require the caller to supply the version it read. Two callers read version 1. The first update succeeds and produces version 2; the second receives a conflict and leaves the first update intact. Add a deterministic competing-update test. Merely checking then replacing without synchronization is insufficient when requests run on different threads. Decide whether a lock, a transactional repository, or another mechanism owns the compare-and-update boundary.

## 5. Build the first HTTP adapter

Expose create, list, get-by-ID, replacement update, and delete operations. Keep transport parsing outside the domain. Define a consistent response body for invalid fields, missing IDs, and stale versions. For example, a proposed `PUT /expenses/1` sends the full editable record, including an empty note when the user wants to clear it. Distinguish that from a future partial `PATCH` contract. Add actual HTTP tests for status, body, and persisted reload; a service unit test alone cannot verify routing or JSON behavior.

## 6. Move persistence into a database-backed unit of work

Replace save-on-exit with a transaction per accepted mutation, preserving the service's validation behavior. Add rollback tests, unique identity constraints, and version conflict checks. Then add owner scoping before exposing real personal records: every read and write must use the authenticated owner, not a caller-supplied owner field. Demonstrate that one user's identifier cannot retrieve another user's expense. Authentication, authorization, persistence, and concurrency are separate responsibilities; explain where each is enforced in your final design.

Finish by reviewing the [solution guidance](06-SOLUTIONS-AND-REVIEW.md) and recording a short demonstration. A mid-level result includes failure behavior and operational limits, not just a screenshot of a successful form submission.
