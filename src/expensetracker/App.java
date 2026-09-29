package expensetracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.NoSuchElementException;

public class App implements AutoCloseable {
    private final ExpenseManager expenseManager;
    private final ExpenseFileStorage storage;
    private final Scanner scanner;

    public App(ExpenseManager expenseManager, ExpenseFileStorage storage) {
        this.expenseManager = expenseManager;
        this.storage = storage;
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        Path filePath = args.length == 0 ? Path.of("data", "expenses.txt") : Path.of(args[0]);
        ExpenseManager expenseManager = new ExpenseManager();
        ExpenseFileStorage storage = new ExpenseFileStorage(filePath);

        try (App app = new App(expenseManager, storage)) {
            app.run();
        }
    }

    public void run() {
        if (!loadExpenses()) return;

        try {
            boolean running = true;
            while (running) {
                showMenu();
                String choice = prompt("Choose an option").trim();
                running = handleMenuChoice(choice);
            }
        } catch (NoSuchElementException ex) {
            System.out.println("Input ended. Saving completed changes.");
        } finally {
            saveExpenses();
        }
    }

    private boolean loadExpenses() {
        try {
            expenseManager.loadExpenses(storage.load());
            System.out.println("Loaded " + expenseManager.getAllExpenses().size() + " expense(s).");
            return true;
        } catch (IOException ex) {
            System.out.println("Could not load saved expenses: " + ex.getMessage());
            System.out.println("Startup stopped. The saved file was not changed; repair a copy before retrying.");
            return false;
        }
    }

    private void saveExpenses() {
        try {
            storage.save(expenseManager.getAllExpenses());
            System.out.println("Expenses saved to disk.");
        } catch (IOException ex) {
            System.out.println("Could not save expenses: " + ex.getMessage());
        }
    }

    private void showMenu() {
        System.out.println();
        System.out.println("Expense Tracker");
        System.out.println("1. Add expense");
        System.out.println("2. List expenses");
        System.out.println("3. Filter by category");
        System.out.println("4. Filter by month");
        System.out.println("5. Show total spending");
        System.out.println("6. Show spending by category");
        System.out.println("7. Save and exit");
        System.out.println("8. Update expense");
        System.out.println("9. Delete expense");
    }

    private boolean handleMenuChoice(String choice) {
        switch (choice) {
            case "1":
                addExpenseFlow();
                return true;
            case "2":
                printExpenses(expenseManager.getAllExpenses());
                return true;
            case "3":
                filterByCategoryFlow();
                return true;
            case "4":
                filterByMonthFlow();
                return true;
            case "5":
                showTotalSpendingFlow();
                return true;
            case "6":
                showSpendingByCategoryFlow();
                return true;
            case "7":
                return false;
            case "8":
                updateExpenseFlow();
                return true;
            case "9":
                deleteExpenseFlow();
                return true;
            default:
                System.out.println("Invalid menu option. Please choose a number from 1 to 9.");
                return true;
        }
    }

    private void addExpenseFlow() {
        System.out.println();
        System.out.println("Add Expense");

        String title = prompt("Title");
        String amount = prompt("Amount");
        String category = prompt("Category");
        String date = prompt("Date (yyyy-MM-dd)");
        String note = prompt("Note (optional)");

        try {
            Expense expense = expenseManager.addExpense(title, amount, category, date, note);
            System.out.println("Added expense #" + expense.getId() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.out.println("Could not add expense: " + ex.getMessage());
        }
    }

    private void updateExpenseFlow() {
        try {
            long id = Long.parseLong(prompt("Expense id"));
            if (expenseManager.findExpense(id).isEmpty()) {
                System.out.println("Expense #" + id + " does not exist.");
                return;
            }
            String title = prompt("New title");
            String amount = prompt("New amount");
            String category = prompt("New category");
            String date = prompt("New date (yyyy-MM-dd)");
            String note = prompt("New note (optional; blank clears it)");
            expenseManager.updateExpense(id, title, amount, category, date, note);
            System.out.println("Updated expense #" + id + ".");
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not update expense: " + ex.getMessage());
        }
    }

    private void deleteExpenseFlow() {
        try {
            long id = Long.parseLong(prompt("Expense id"));
            if (expenseManager.findExpense(id).isEmpty()) {
                System.out.println("Expense #" + id + " does not exist.");
                return;
            }
            if (!prompt("Delete expense #" + id + "? Type yes to confirm").equalsIgnoreCase("yes")) {
                System.out.println("Deletion cancelled.");
                return;
            }
            expenseManager.deleteExpense(id);
            System.out.println("Deleted expense #" + id + ".");
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not delete expense: " + ex.getMessage());
        }
    }

    private void filterByCategoryFlow() {
        String category = prompt("Category");

        try {
            printExpenses(expenseManager.getExpensesByCategory(category));
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not filter by category: " + ex.getMessage());
        }
    }

    private void filterByMonthFlow() {
        String month = prompt("Month (yyyy-MM)");

        try {
            printExpenses(expenseManager.getExpensesByMonth(month));
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not filter by month: " + ex.getMessage());
        }
    }

    private void showTotalSpendingFlow() {
        BigDecimal total = expenseManager.getTotalSpending();
        System.out.println("Total spending: $" + formatAmount(total));
    }

    private void showSpendingByCategoryFlow() {
        printCategorySummary(expenseManager.getSpendingByCategory());
    }

    private void printExpenses(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            System.out.println("No expenses found.");
            return;
        }

        System.out.println();
        System.out.printf("%-4s %-20s %-10s %-15s %-12s %-20s%n",
                "ID", "Title", "Amount", "Category", "Date", "Note");
        System.out.println("-------------------------------------------------------------------------------");

        for (Expense expense : expenses) {
            String noteText = expense.hasNote() ? expense.getNote() : "-";
            System.out.printf("%-4d %-20s $%-9s %-15s %-12s %-20s%n",
                    expense.getId(),
                    expense.getTitle(),
                    formatAmount(expense.getAmount()),
                    expense.getCategory(),
                    expense.getDate(),
                    noteText);
        }
    }

    private void printCategorySummary(Map<String, BigDecimal> totals) {
        if (totals.isEmpty()) {
            System.out.println("No expenses found.");
            return;
        }

        System.out.println();
        System.out.println("Spending By Category");
        System.out.println("--------------------");

        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            System.out.printf("%-15s $%s%n", entry.getKey(), formatAmount(entry.getValue()));
        }
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    @Override
    public void close() {
        scanner.close();
    }
}
