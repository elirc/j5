# Verification record and limits

The implementation was reviewed and tested in an isolated copy before it was delivered to this project's folder. Existing user data was not used as a fixture or replaced. This page is a frozen record of that delivery, plus one dated re-run at the end. The tests and the commands in this repository are the evidence; nothing outside the repository is needed.

## Executed checks

The environment used Microsoft OpenJDK 17.0.10. The project runner compiled production and test source with `--release 17 -Xlint:all`.

Four initial regression tests failed against the original implementation: an empty optional note disappeared after saving and reloading; a malformed row did not reject the whole load; duplicate IDs replaced existing manager state; and the model accepted sub-cent precision. After the changes, all **23 Java regression tests passed**. They use real temporary files and cover escaped text, invalid saved precision, duplicate identities, missing files, preservation after invalid saves, atomic replacement cleanup, CRUD behavior, ID exhaustion, locale-independent category normalization, and collection isolation.

A separate verification script ran **five CLI scenarios across Java processes**, for **28 checks total**. These confirmed empty-note reload, persisted update, deletion cancellation followed by confirmed deletion, EOF during an unfinished command, and unchanged file bytes when startup encounters corruption. The project-local `run.ps1 -Test` command runs the 23 Java cases. The original orchestration script for the five scenarios is not in this repository. [04-TESTING-AND-DEBUGGING.md](04-TESTING-AND-DEBUGGING.md) now contains a `node --test` file that re-creates all five.

## What the checks do not establish

This is a single-process CLI learning project. No web framework, browser UI, authenticated owner model, database, or HTTP contract was implemented or tested. The [practice ladder](05-PRACTICE.md) proposes those next steps explicitly. The manager is not thread-safe, and multiple application instances can overwrite each other's complete file snapshots.

Changes remain in memory until normal exit, EOF handling, or an explicit call to storage. A force-killed process can lose unsaved edits. Atomic replacement is requested without a non-atomic fallback; a filesystem that does not support it produces a visible save failure. The tests cover ordinary replacement and a failed move, not power-loss durability or every network filesystem's behavior.

Identity allocation is guarded against numeric wraparound, but its counter is reconstructed from the remaining saved records. Deleting the highest ID and restarting can reuse it. The course includes a migration exercise to address that limitation. Category normalization intentionally changes case and collapses spaces; it is a simple demonstration policy rather than a comprehensive internationalized taxonomy.

Strict loading may now refuse a file that the old implementation partly accepted. This is intentional preservation behavior: repair a separate copy and review rejected records rather than silently dropping them. There is no automatic salvage command. The CLI prints startup/save failures but does not yet expose a structured process-exit contract for automation. These limits should remain visible in a review so a learner can distinguish verified behavior from future work.

## Re-run on 2026-10-06 (documentation review)

The source and tests were compiled with JDK 17.0.10 into a scratch directory outside the project, so `out/` was not created. `RegressionTests` printed `RESULT passed=23 failed=0`; the count matches the 23 `test(...)` calls in `test/expensetracker/RegressionTests.java`. The chapter 04 scenario file passed 5 of 5.

The same session confirmed four behaviors the original record does not mention. They are written up with file:line references in [06-SOLUTIONS-AND-REVIEW.md](06-SOLUTIONS-AND-REVIEW.md):

- A refused startup exits with status 0.
- A failed save on exit prints an error, exits 0, and loses the session's changes. The previous file stays intact.
- Amounts such as `1e2` and `+5` are accepted, and `1e2` becomes 100.00.
- `#` comment lines a user adds to the data file are removed by the next save.
