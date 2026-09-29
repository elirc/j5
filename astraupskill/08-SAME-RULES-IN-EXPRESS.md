# 08. The same rules in Express

[Course index](README.md)

`ExpenseManager.updateExpense` builds a valid replacement before touching the list, and `ExpenseFileStorage.save` serializes a snapshot to a temp file and moves it atomically. In TypeScript + Express + SQLite the validation becomes a Zod schema and the atomic move becomes a transaction; the rules do not change. About sixty lines:

```ts
import express from 'express';
import { z } from 'zod';
import { DatabaseSync } from 'node:sqlite';

const db = new DatabaseSync('expenses.sqlite');
db.exec(`CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY, title TEXT NOT NULL, amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
  category TEXT NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', version INTEGER NOT NULL DEFAULT 1)`);

const money = z.string().regex(/^\d+(\.\d{1,2})?$/, 'Amount must use at most 2 decimal places.').transform((s) => Math.round(Number(s) * 100)).refine((c) => c > 0, 'Amount must be greater than zero.');
const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine((s) => !Number.isNaN(Date.parse(s)) && new Date(s).toISOString().startsWith(s), 'Date must be a real calendar day.');
const expenseBody = z.object({ title: z.string().trim().min(1).max(120), amount: money, category: z.string().trim().min(1).max(60), date: day, note: z.string().max(500).default('') }).strict();

const app = express(); app.use(express.json({ limit: '16kb' }));

app.put('/expenses/:id', (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: 'Invalid id.' });
  const parsed = expenseBody.safeParse(req.body);                                  // parseAmount/parseDate, as one schema
  if (!parsed.success) return res.status(400).json({ error: parsed.error.issues[0].message });
  const expected = Number(req.get('if-match')?.replace(/"/g, ''));                 // exercise 4's version, as a header
  if (!Number.isInteger(expected)) return res.status(428).json({ error: 'Send If-Match with the version you loaded.' });
  const e = parsed.data;
  const result = db.prepare('UPDATE expenses SET title = ?, amount_cents = ?, category = ?, date = ?, note = ?, version = version + 1 WHERE id = ? AND version = ?')
    .run(e.title, e.amount, e.category, e.date, e.note, id, expected);          // the atomic move, as one statement
  if (result.changes === 0) {
    return db.prepare('SELECT 1 FROM expenses WHERE id = ?').get(id)
      ? res.status(409).json({ error: 'Expense changed since you loaded it. Reload and retry.' })
      : res.status(404).json({ error: `Expense #${id} does not exist.` });
  }
  res.status(204).set('ETag', `"${expected + 1}"`).end();
});

app.delete('/expenses/:id', (req, res) => {
  const result = db.prepare('DELETE FROM expenses WHERE id = ?').run(Number(req.params.id));
  res.status(result.changes ? 204 : 404).end();
});
```

## Mapping

| Java | Express |
|---|---|
| `parseAmount` (scale 2, positive) and `parseDate` | the `money` and `day` schemas; `.strict()` refuses unknown fields the CLI could never send |
| "build a valid replacement before touching the live collection" | `safeParse` before `UPDATE`; a 400 touches nothing |
| `NoSuchElementException` | 404 after a `changes === 0` that is not a version conflict |
| `save`: snapshot, temp file, `ATOMIC_MOVE` | the single `UPDATE` inside SQLite's implicit transaction; several rows would be `BEGIN IMMEDIATE ... COMMIT` |
| (practice exercise 4) optimistic concurrency | `If-Match` + `WHERE version = ?`, 409 on `changes === 0` |
| the CLI's confirmation prompt before delete | nothing: the server cannot rely on a client prompt; authorization goes here instead |

## Exercise

Write the four tests: invalid amount is 400 and the row is unchanged; unknown id is 404; stale `If-Match` is 409; a good update is 204 and the `ETag` moved. Then load `data/expenses.txt` into the table with one script and prove the escaped-tab note round-trips.
