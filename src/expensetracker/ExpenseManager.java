package expensetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.NoSuchElementException;

public class ExpenseManager {
    private final List<Expense> expenses = new ArrayList<>();
    private long nextId = 1;

    public void loadExpenses(Collection<Expense> loadedExpenses) {
        List<Expense> candidate = new ArrayList<>(Objects.requireNonNull(loadedExpenses));
        var ids = new HashSet<Long>();
        long highestId = 0;
        for (Expense expense : candidate) {
            Objects.requireNonNull(expense, "Saved expense cannot be null.");
            if (!ids.add(expense.getId())) throw new IllegalArgumentException("Duplicate expense id: " + expense.getId());
            highestId = Math.max(highestId, expense.getId());
        }
        // Validate the entire candidate before replacing the current state.
        expenses.clear();
        expenses.addAll(candidate);
        nextId = highestId == Long.MAX_VALUE ? 0 : highestId + 1;
    }

    public Expense addExpense(String title, String amountText, String category, String dateText, String note) {
        if (nextId == 0) throw new IllegalStateException("Expense id space is exhausted.");
        String cleanTitle = requireText(title, "Title");
        BigDecimal amount = parseAmount(amountText);
        String cleanCategory = normalizeCategory(category);
        LocalDate date = parseDate(dateText);
        String cleanNote = note == null ? "" : note.trim();

        Expense expense = new Expense(nextId, cleanTitle, amount, cleanCategory, date, cleanNote);
        expenses.add(expense);
        nextId = nextId == Long.MAX_VALUE ? 0 : nextId + 1;
        return expense;
    }

    public List<Expense> getAllExpenses() {
        return new ArrayList<>(expenses);
    }

    public List<Expense> getExpensesByCategory(String category) {
        String requestedCategory = normalizeCategory(category);
        List<Expense> matches = new ArrayList<>();

        for (Expense expense : expenses) {
            if (expense.getCategory().equalsIgnoreCase(requestedCategory)) {
                matches.add(expense);
            }
        }

        return matches;
    }

    public List<Expense> getExpensesByMonth(String yearMonthText) {
        YearMonth requestedMonth = parseYearMonth(yearMonthText);
        List<Expense> matches = new ArrayList<>();

        for (Expense expense : expenses) {
            if (YearMonth.from(expense.getDate()).equals(requestedMonth)) {
                matches.add(expense);
            }
        }

        return matches;
    }

    public BigDecimal getTotalSpending() {
        BigDecimal total = BigDecimal.ZERO;

        for (Expense expense : expenses) {
            total = total.add(expense.getAmount());
        }

        return total;
    }

    public Map<String, BigDecimal> getSpendingByCategory() {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Expense expense : expenses) {
            String category = expense.getCategory();
            BigDecimal currentTotal = totals.getOrDefault(category, BigDecimal.ZERO);
            totals.put(category, currentTotal.add(expense.getAmount()));
        }

        return totals;
    }

    public Optional<Expense> findExpense(long id) {
        return expenses.stream().filter(expense -> expense.getId() == id).findFirst();
    }

    public Expense updateExpense(long id, String title, String amount, String category, String date, String note) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId() == id) {
                // Build a valid replacement before touching the live collection.
                Expense replacement = new Expense(id, title, parseAmount(amount), category, parseDate(date), note);
                expenses.set(i, replacement);
                return replacement;
            }
        }
        throw new NoSuchElementException("Expense #" + id + " does not exist.");
    }

    public boolean deleteExpense(long id) {
        return expenses.removeIf(expense -> expense.getId() == id);
    }

    private BigDecimal parseAmount(String amountText) {
        String value = requireText(amountText, "Amount");

        try {
            BigDecimal amount = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);

            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }

            return amount;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount must use at most 2 decimal places.");
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount must be a valid number.");
        }
    }

    private LocalDate parseDate(String dateText) {
        String value = requireText(dateText, "Date");

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Date must use the format yyyy-MM-dd.");
        }
    }

    private YearMonth parseYearMonth(String yearMonthText) {
        String value = requireText(yearMonthText, "Month");

        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Month must use the format yyyy-MM.");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value.trim();
    }

    private String normalizeCategory(String category) {
        return Expense.normalizeCategory(category);
    }
}
