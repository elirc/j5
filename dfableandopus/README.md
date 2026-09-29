# Interview pack: Expense Tracker (j5)

Job-readiness material built on the Java command-line expense tracker in `src/expensetracker/`. The sibling course `../astraupskill/` explains how the program works; this folder is about answering for it in a hiring loop for CRUD web roles.

## The pitch

I found and fixed a data-loss bug in a file-backed expense tracker: an expense saved with an empty note vanished on the next load. The cause was a single `line.trim()` applied to the whole record before parsing, which ate the trailing tab that marks the empty sixth field, so a six-field record arrived at the parser as five fields and was discarded. The repair separated line *inspection* from line *parsing* (`src/expensetracker/ExpenseFileStorage.java:36`), turned partial loads into a hard `IOException` that names the line number, and made saving atomic: validate a snapshot, write a temp sibling, `ATOMIC_MOVE` over the destination. Four regression tests failed against the original code; 23 pass now, plus five cross-process CLI scenarios for 28 checks total.

## Target stacks and off-stack framing

The code is Java 17 with no frameworks. Most CRUD openings want Node/TypeScript or .NET, so answer in Java and translate immediately. `../astraupskill/08-SAME-RULES-IN-EXPRESS.md` already carries the translation: the `Expense` constructor and `parseAmount` become a Zod schema, the atomic move becomes one `UPDATE ... WHERE id = ? AND version = ?`, and the CLI's error vocabulary becomes 400, 404, 409, 428 and 204. Use those mappings verbatim; they were written against this project's actual error strings.

## Two-week use

- Days 1-2: screening questions in `01-INTERVIEW-QUESTIONS.md` with `ExpenseManager.java` open.
- Days 3-5: the deep-dive group, especially the trim bug and the money-precision rules.
- Days 6-7: `03-CODE-READING-DRILL.md`, timed, without opening the source first.
- Days 8-9: rehearse `02-STORIES-AND-RESUME.md`; the trim bug is your strongest story, so tell it until it is 90 seconds.
- Days 10-12: `04-TAKE-HOME.md` end to end against `test/expensetracker/RegressionTests.java`.
- Days 13-14: `05-SYSTEM-DESIGN-FOLLOWUPS.md`, plus `06-FLASHCARDS.md` daily throughout.

## Files

- [01-INTERVIEW-QUESTIONS.md](01-INTERVIEW-QUESTIONS.md)
- [02-STORIES-AND-RESUME.md](02-STORIES-AND-RESUME.md)
- [03-CODE-READING-DRILL.md](03-CODE-READING-DRILL.md)
- [04-TAKE-HOME.md](04-TAKE-HOME.md)
- [05-SYSTEM-DESIGN-FOLLOWUPS.md](05-SYSTEM-DESIGN-FOLLOWUPS.md)
- [06-FLASHCARDS.md](06-FLASHCARDS.md)
