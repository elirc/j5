package expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Locale;
import java.math.RoundingMode;

public class Expense {
    private final long id;
    private final String title;
    private final BigDecimal amount;
    private final String category;
    private final LocalDate date;
    private final String note;

    public Expense(long id, String title, BigDecimal amount, String category, LocalDate date, String note) {
        if (id <= 0) {
            throw new IllegalArgumentException("Expense id must be positive.");
        }

        this.title = requireText(title, "Title");
        try {
            this.amount = Objects.requireNonNull(amount, "Amount cannot be null.").setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount must use at most 2 decimal places.", ex);
        }
        this.category = normalizeCategory(category);
        this.date = Objects.requireNonNull(date, "Date cannot be null.");
        this.note = note == null ? "" : note.trim();
        this.id = id;

        if (this.amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getNote() {
        return note;
    }

    public boolean hasNote() {
        return !note.isBlank();
    }

    static String normalizeCategory(String category) {
        String[] words = requireText(category, "Category").split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            result.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return value.trim();
    }
}
