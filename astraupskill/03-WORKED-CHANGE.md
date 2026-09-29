# Worked change: stop empty notes from deleting saved expenses

Reproduce the smallest failure before reading the solution. Create one expense whose note is the empty string, save it, load it, and assert that one record comes back. The original application returned zero records. A typical manual test with a note such as `lunch with team` did not expose this bug, which is why optional fields deserve deliberate coverage.

The saved record consists of six fields. Written with visible markers, it looks like this:

```text
1<TAB>Lunch<TAB>12.30<TAB>Food<TAB>2026-01-02<TAB><NEWLINE>
```

The last tab is meaningful: it introduces an empty sixth field. The old loader called `line.trim()` and passed that result into the parser. Trimming removed the trailing tab. The parser then saw five fields and rejected the record. The surrounding catch printed a message and continued, making the returned collection look successfully loaded despite missing data.

The correction separates inspection from parsing:

```java
String trimmedLine = line.trim();
if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
    continue;
}
Expense expense = parseLine(line); // original physical separators
```

This is a general parsing lesson: normalization appropriate for recognizing an empty line can be destructive when applied to structured data. In a web app, the analogous mistake might be trimming an entire signed payload, coercing an empty field to a missing field, or replacing zero with a fallback using a truthiness check. Normalize individual fields according to their contract.

The second correction changes failure policy. A malformed row now throws `IOException` with its line number instead of returning a partial collection. Duplicate IDs are checked while reading. The application responds by stopping before the menu and before save-on-exit. These layers work together: strict parsing without changing startup behavior could still lead to an empty collection overwriting the original.

The third correction changes saving. Previously, opening the destination writer could truncate existing content before every record was serialized. Now the method validates and builds all lines, writes a temporary sibling, and atomically replaces the target. Invalid input never touches the destination. A failed move leaves the prior destination in place in the tested scenarios and cleans the temporary sibling. Unsupported atomic replacement produces an explicit save failure; the code does not silently downgrade to a weaker operation.

The CRUD extension follows the same validate-before-mutation principle. `updateExpense` finds the ID, constructs a complete replacement, then changes exactly one list entry. A bad date does not partially change title or amount. `deleteExpense` returns a boolean, while the menu checks existence and asks for `yes` before calling it. Service methods remain independent of interactive confirmation so a later adapter can apply its own interaction policy.

Review the [tests](../test/expensetracker/RegressionTests.java) named `empty optional note survives a save and load`, `corrupt record rejects the entire file`, and `invalid update preserves previous value`. Each asserts an externally observable contract. The first four regression cases failed against the original source; the final suite passes. A useful commit explanation would lead with preserved data and accepted behavior, then describe these implementation details as the reason the behavior is reliable.
