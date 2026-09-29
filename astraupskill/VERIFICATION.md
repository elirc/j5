# Verification record and limits

The implementation was reviewed and tested in an isolated copy before delivery to this project's original folder. Existing user data was not used as a fixture or replaced. Delivery is recorded in the repository's `astrdocs/REMAINING_PROJECTS_IMPLEMENTATION_REPORT.md` (that report lives outside this project, in the delivery workspace; it is not needed to verify anything here: the `-Test` run above is the evidence) and finite hash-verified manifest for project ID `d9a27fa7f308`; original changed files are backed up by that workflow.

## Executed checks

The environment used Microsoft OpenJDK 17.0.10. The project runner compiled production and test source with `--release 17 -Xlint:all`. The initial restricted compiler invocation could not close compiler resources; the reviewed full-access invocation succeeded. This was a tooling-access issue, not a reason to omit the compilation gate.

Four initial regression tests failed against the original implementation: an empty optional note disappeared after saving and reloading; a malformed row did not reject the whole load; duplicate IDs replaced existing manager state; and the model accepted sub-cent precision. After the changes, all **23 Java regression tests passed**. They use real temporary files and cover escaped text, invalid saved precision, duplicate identities, missing files, preservation after invalid saves, atomic replacement cleanup, CRUD behavior, ID exhaustion, locale-independent category normalization, and collection isolation.

A separate verification script ran **five CLI scenarios across Java processes**, for **28 checks total**. These confirmed empty-note reload, persisted update, deletion cancellation followed by confirmed deletion, EOF during an unfinished command, and unchanged file bytes when startup encounters corruption. The project-local `run.ps1 -Test` command runs the 23 Java cases; the five orchestration scenarios and logs are retained in the central implementation evidence under `astra-remaining-improvements/checks/j5/`.

## What the checks do not establish

This is a single-process CLI learning project. No web framework, browser UI, authenticated owner model, database, or HTTP contract was implemented or tested. The [practice ladder](05-PRACTICE.md) proposes those next steps explicitly. The manager is not thread-safe, and multiple application instances can overwrite each other's complete file snapshots.

Changes remain in memory until normal exit, EOF handling, or an explicit call to storage. A force-killed process can lose unsaved edits. Atomic replacement is requested without a non-atomic fallback; a filesystem that does not support it produces a visible save failure. The tests cover ordinary replacement and a failed move, not power-loss durability or every network filesystem's behavior.

Identity allocation is guarded against numeric wraparound, but its counter is reconstructed from the remaining saved records. Deleting the highest ID and restarting can reuse it. The course includes a migration exercise to address that limitation. Category normalization intentionally changes case and collapses spaces; it is a simple demonstration policy rather than a comprehensive internationalized taxonomy.

Strict loading may now refuse a file that the old implementation partly accepted. This is intentional preservation behavior: repair a separate copy and review rejected records rather than silently dropping them. There is no automatic salvage command. The CLI prints startup/save failures but does not yet expose a structured process-exit contract for automation. These limits should remain visible in a review so a learner can distinguish verified behavior from future work.
