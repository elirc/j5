# Trace lab: create, replace, delete, reload, and reject corruption

This lab uses the delivered CLI. It is safe to repeat with a dedicated learning file. Use a new filename if a previous run already contains records, because the example IDs assume an empty dataset. Do not point a corruption experiment at your normal expense file. The automatic regression harness creates its own temporary data; this manual exercise lets you see the same boundaries directly.

Start from the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-expenses.txt
```

Choose 1 and enter `Lunch`, `12.30`, `food`, `2026-01-02`, and a blank note. The application reports the assigned ID. Choose 2 and inspect the normalized category and two-place amount. Choose 7 to save and exit. Read the file with `Get-Content`; the final tab introduces the empty note. Restart with the same command. The loaded count should be one, and listing should still show Lunch. Record why passing the original line into the parser is essential here.

Choose 8 and enter the displayed ID, `Bus`, `2.50`, `Travel`, `2026-02-30`, and a blank note. The date is impossible. Listing must still show Lunch at 12.30. Repeat with `2026-02-03`; now the ID remains the same while title, amount, category, and date change. Choose 5 and 6 to verify that summaries derive from current records rather than retaining a cached Lunch total.

Choose 9 with the same ID, answer `no`, and list again. Bus must remain. Choose 9 again and answer `yes`; the record disappears. Exit, restart, and verify the empty state survived. Explain the difference between confirmation in the CLI and `deleteExpense` in the manager: the service performs a deterministic operation, while the adapter asks the human before invoking it.

For the corruption trace, first add and save one record again, then exit. Copy the dataset to a separate file:

```powershell
Copy-Item -LiteralPath .\data\trace-expenses.txt -Destination .\data\trace-corrupt.txt
Add-Content -LiteralPath .\data\trace-corrupt.txt -Value 'broken'
$beforeHash = (Get-FileHash -LiteralPath .\data\trace-corrupt.txt).Hash
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-corrupt.txt
$LASTEXITCODE
$afterHash = (Get-FileHash -LiteralPath .\data\trace-corrupt.txt).Hash
$beforeHash -eq $afterHash
```

The application should identify the invalid line, stop startup, and leave the file hash unchanged. It should not offer the normal menu or claim that expenses were saved. Keep the valid source copy for recovery. This is fail-closed loading, not an automatic repair feature. Now look at the `$LASTEXITCODE` line: it prints `0`. `run.ps1` passes on Java's exit status, and `main` returns normally after a refused startup (`App.java:34`), so a script can't tell this run apart from a successful one. Practice exercise 3 closes that gap.

Finally, inspect the [regression suite](../test/expensetracker/RegressionTests.java) and map each observation to a named test. Write a five-sentence review: what input triggered the original loss, which parser boundary changed, why failed updates preserve state, when a successful edit becomes durable, and what would happen if two processes saved different snapshots. If any answer is vague, revisit the relevant method before beginning the HTTP exercise.
