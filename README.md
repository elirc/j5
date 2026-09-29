> **Updated learning path:** [Start the detailed CRUD and persistence course](astraupskill/README.md). Update and confirmed delete are implemented as menu options 8 and 9. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test` for 23 regression tests. Loading now rejects corrupt or duplicate records without saving; saving validates first and atomically replaces the file. See the course verification record for limits and five additional CLI verification scenarios.

# Expense Tracker

A small command-line Java project for recording expenses, filtering them, and viewing spending summaries.

This project is intentionally small and beginner-friendly. It uses:

- plain Java
- classes and objects
- collections
- file I/O
- `BigDecimal` for money
- `LocalDate` and `YearMonth` for dates
- separation of concerns

## Project Overview

The app starts by loading saved expenses from a local file if one exists. It then shows a simple menu in the terminal. You can add, list, update, and delete expenses, filter them, and view spending summaries. When you exit, the app saves everything back to disk.

The code is split into four small classes:

- `App`: handles the menu and user interaction
- `Expense`: represents one expense object
- `ExpenseManager`: contains business logic such as validation, filtering, and totals
- `ExpenseFileStorage`: handles saving and loading data from a file

This structure keeps responsibilities clear:

- the CLI talks to the user
- the manager handles rules and calculations
- the storage class handles files
- the domain class represents data

## Why Plain Text Instead Of JSON Or CSV

The file format is a simple plain-text tab-separated format.

Why this choice:

- it is easy to open and inspect in a text editor
- Java can read and write it with built-in classes
- it avoids adding frameworks or libraries
- it is simpler than JSON for a beginner

Tradeoff:

- JSON is more standard for nested data, but it is more work without a library
- CSV is common, but proper CSV escaping rules can be confusing for a beginner
- plain text is simple and readable, but less standardized

For this project, learnability matters more than perfection.

## Project Structure

```text
src/
  expensetracker/
    App.java
    Expense.java
    ExpenseManager.java
    ExpenseFileStorage.java
data/
  expenses.txt    <- created automatically when you save
```

## How To Compile And Run

Use Java 17 or newer.

Compile:

```powershell
javac -d out src/expensetracker/*.java
```

Run:

```powershell
java -cp out expensetracker.App
```

## File-By-File Explanation

### `src/expensetracker/App.java`

This is the entry point of the program. It creates the manager and storage objects, loads saved data, shows the menu, and reacts to user choices.

It is responsible for:

- showing the menu
- reading input from the user
- printing results
- catching invalid input and showing friendly messages
- saving on exit

It is not responsible for:

- calculating totals
- filtering data
- validating expense rules
- reading and writing file details

Those jobs are delegated to other classes.

### `src/expensetracker/Expense.java`

This is the domain model. One object of this class represents one expense.

Each expense has:

- `id`
- `title`
- `amount`
- `category`
- `date`
- `note`

The constructor validates the basic rules so an `Expense` object cannot be created in an invalid state.

### `src/expensetracker/ExpenseManager.java`

This class contains the business logic.

It stores the list of expenses in memory and provides methods to:

- add an expense
- list expenses
- filter by category
- filter by month
- compute total spending
- compute totals by category
- load expenses into memory

This is where validation and reporting logic belong, because those rules are about the app's behavior, not about the menu.

### `src/expensetracker/ExpenseFileStorage.java`

This class handles persistence.

It knows:

- where the file is
- how to turn an `Expense` into a line of text
- how to turn a line of text back into an `Expense`

This keeps file I/O separate from business logic.

## Class-By-Class Explanation

### `App`

`App` coordinates the whole program. It is the class closest to the user.

It owns:

- an `ExpenseManager`
- an `ExpenseFileStorage`
- a `Scanner`

It asks the manager to do work and asks the storage class to save and load data.

### `Expense`

`Expense` is a simple data class. It models one real-world expense entry.

It is immutable after creation because all fields are `final`. That means once an expense is created, its values do not change. This makes the program easier to reason about.

### `ExpenseManager`

`ExpenseManager` is the middle layer between the CLI and the stored data.

It protects the rules of the system, such as:

- amount must be positive
- category cannot be blank
- dates must be valid
- ids must stay unique

It also contains the reporting logic for totals and summaries.

### `ExpenseFileStorage`

`ExpenseFileStorage` deals with the filesystem only.

It does not know anything about menu choices or user interaction. It only knows how to save and load expenses.

## Method-By-Method Explanation

### `App.main`

Creates the objects the program needs and starts the app.

### `App.run`

Loads saved data, starts the menu loop, and saves data when the app exits.

### `App.loadExpenses`

Loads expenses from disk and gives them to the manager. If loading fails, it reports the error and stops before the menu or any save, preserving the original file.

### `App.saveExpenses`

Saves the current in-memory expenses to disk. If saving fails, it shows an error instead of crashing.

### `App.showMenu`

Prints the available menu options.

### `App.handleMenuChoice`

Receives the user's menu selection and calls the correct method.

### `App.addExpenseFlow`

Prompts the user for title, amount, category, date, and note. It then asks the manager to create and store the expense.

### `App.filterByCategoryFlow`

Asks the user for a category and prints matching expenses.

### `App.filterByMonthFlow`

Asks the user for a year-month value like `2026-03` and prints matching expenses.

### `App.showTotalSpendingFlow`

Prints the total of all expense amounts.

### `App.showSpendingByCategoryFlow`

Prints one total for each category.

### `App.printExpenses`

Prints a readable table-like list of expense objects.

### `App.printCategorySummary`

Prints each category and its total amount.

### `App.prompt`

Shows a label and reads one line of user input.

### `App.formatAmount`

Formats a `BigDecimal` as money with two decimal places.

### `App.close`

Closes the `Scanner`.

### `Expense.Expense`

Constructs a valid expense object and checks that the provided values make sense.

### `Expense` getters

Return the field values of an expense.

### `Expense.hasNote`

Returns `true` if the note is not blank.

### `ExpenseManager.loadExpenses`

Replaces the in-memory list with data loaded from storage and calculates the next available id.

### `ExpenseManager.addExpense`

Validates the raw user input, creates a new `Expense`, adds it to the list, and returns it.

### `ExpenseManager.getAllExpenses`

Returns a copy of the full expense list.

### `ExpenseManager.getExpensesByCategory`

Returns all expenses whose category matches the requested category, ignoring letter case.

### `ExpenseManager.getExpensesByMonth`

Returns all expenses whose date belongs to the requested `YearMonth`.

### `ExpenseManager.getTotalSpending`

Loops through all expenses and adds their amounts together.

### `ExpenseManager.getSpendingByCategory`

Builds a map where each category name points to its total amount.

### Identity allocation in `ExpenseManager.loadExpenses`

Finds the highest existing id and returns the next id value.

### `ExpenseManager.parseAmount`

Converts text into a `BigDecimal`, forces two decimal places, and rejects zero or negative values.

### `ExpenseManager.parseDate`

Converts text into a `LocalDate` and rejects invalid date strings.

### `ExpenseManager.parseYearMonth`

Converts text like `2026-03` into a `YearMonth`.

### `ExpenseManager.requireText`

Checks that required text values are not blank.

### `ExpenseManager.normalizeCategory`

Trims the category and stores it in a consistent form.

### `ExpenseFileStorage.load`

Reads original record lines, skips blank lines and comments, rejects any malformed or duplicate record with an IOException, and returns only a completely validated list.

### `ExpenseFileStorage.save`

Writes all current expenses to the file in plain text form.

### `ExpenseFileStorage.toLine`

Converts one `Expense` into one tab-separated text line.

### `ExpenseFileStorage.parseLine`

Converts one stored line back into an `Expense`.

### `ExpenseFileStorage.escape`

Escapes special characters before saving to keep the file format safe.

### `ExpenseFileStorage.unescape`

Restores escaped text back to the original value when loading.

### `ExpenseFileStorage.splitPreservingEscapes`

Splits a stored line into fields without breaking escaped tab characters.

## How Adding An Expense Works

When the user chooses `1. Add expense`, the flow is:

1. `App` asks the user for the title, amount, category, date, and note.
2. `App` sends those raw strings to `ExpenseManager.addExpense`.
3. `ExpenseManager` validates the text:
   - title must not be blank
   - amount must be a positive number
   - amount must have at most two decimal places
   - category must not be blank
   - date must be a valid date in `yyyy-MM-dd` format
4. If validation passes, the manager creates a new `Expense` object using the next unique id.
5. The expense is added to the in-memory list.
6. The app prints a success message.
7. When the user exits, the current list is saved to disk.

## How Category And Month Filtering Work

### Category filtering

The manager loops through all expenses and compares each expense's category to the user-provided category.

The comparison ignores letter case, so `food` and `Food` are treated as the same category when filtering.

### Month filtering

The manager converts user input like `2026-03` into a `YearMonth`.

Then it loops through all expenses and converts each expense date into its own `YearMonth`.

If they match, that expense belongs in the filtered result.

Example:

- expense date: `2026-03-12`
- filter month: `2026-03`
- result: match

## How Totals And Category Summaries Are Computed

### Total spending

The manager starts with `BigDecimal.ZERO`.

Then it loops through all expenses and keeps adding each amount:

```java
BigDecimal total = BigDecimal.ZERO;
for (Expense expense : expenses) {
    total = total.add(expense.getAmount());
}
```

### Spending by category

The manager uses a map:

- key: category name
- value: total amount for that category

For each expense, it looks up the current total for that category, adds the new amount, and stores the updated total back into the map.

This is a common aggregation pattern in programming.

## Why `BigDecimal` Is Preferred For Money

`double` uses binary floating-point math. Many decimal values cannot be represented exactly in binary, which can lead to small rounding errors.

Example idea:

- `0.1 + 0.2` with floating-point types may not behave exactly as expected

`BigDecimal` stores decimal numbers more precisely for financial-style calculations.

That makes it a better fit for money.

In this project, the amount is also forced to two decimal places so values stay consistent.

## How Saving And Loading Works

### Saving

When the app exits, `App.saveExpenses` calls `ExpenseFileStorage.save`.

The storage class:

1. snapshots and validates the collection, then serializes every record
2. creates the destination directory if needed
3. writes the header and records to a temporary sibling file
4. atomically replaces the destination, or reports a save error without a non-atomic fallback
5. cleans up a leftover temporary file

### Loading

When the app starts, `App.loadExpenses` calls `ExpenseFileStorage.load`.

The storage class:

1. checks whether the file exists
2. if it does not exist, returns an empty list
3. if it exists, reads the lines
4. skips comments and blank lines
5. parses each expense line into an `Expense`
6. rejects the entire load on a malformed or duplicate record; otherwise returns the complete list to the manager

## Why The Project Was Structured This Way

This structure teaches an important beginner lesson: not all code belongs in `main`.

If everything were written inside one giant method, the project would quickly become hard to read and hard to change.

By separating concerns:

- `App` focuses on user interaction
- `ExpenseManager` focuses on rules and calculations
- `ExpenseFileStorage` focuses on persistence
- `Expense` focuses on representing data

This makes the project:

- easier to understand
- easier to test later
- easier to extend
- easier to debug

## What A Beginner Should Study First

Study these topics in this order:

1. classes and objects
2. constructors and fields
3. lists and maps
4. method parameters and return values
5. loops and conditionals
6. exception handling
7. file I/O with `Files`
8. `BigDecimal`
9. `LocalDate` and `YearMonth`

Then come back to the project and trace one feature end to end, such as adding an expense.

## Three Small Exercises To Extend The Project

1. Extend the implemented deletion flow with an undo design.
2. Extend the implemented replacement update with version conflict detection.
3. Add sorting options such as newest date first or highest amount first.

Each of these extends the project without changing its basic structure.
