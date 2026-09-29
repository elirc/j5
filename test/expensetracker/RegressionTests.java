package expensetracker;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
public class RegressionTests {
    private static int passed, failed;
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) { try { check.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable e) { failed++; System.out.println("FAIL " + name + ": " + e); } }
    static void equal(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + "; got " + actual); }
    static void rejects(Class<? extends Throwable> type, Check check) throws Exception { try { check.run(); } catch (Throwable e) { if (type.isInstance(e)) return; throw e; } throw new AssertionError("Expected " + type.getSimpleName()); }
    static Expense expense(long id, String note) { return new Expense(id,"Lunch",new BigDecimal("12.30"),"Food",LocalDate.of(2026,1,2),note); }
    static void withFile(CheckWithPath check) throws Exception {
        Path directory = Files.createTempDirectory("j5-regression-");
        try { check.run(directory.resolve("expenses.txt")); }
        finally { try (var files=Files.list(directory)) { for(Path file:files.toList()) Files.delete(file); } Files.delete(directory); }
    }
    interface CheckWithPath { void run(Path file) throws Exception; }
    public static void main(String[] args) throws Exception {
        test("empty optional note survives a save and load", () -> withFile(file -> {
            var storage=new ExpenseFileStorage(file);storage.save(List.of(expense(1,"")));
            equal(1,storage.load().size());equal("",storage.load().get(0).getNote());
        }));
        test("corrupt record rejects the entire file", () -> withFile(file -> {
            var storage=new ExpenseFileStorage(file);storage.save(List.of(expense(1,"ok")));
            Files.writeString(file,Files.readString(file)+"broken\n");
            rejects(IOException.class,storage::load);
        }));
        test("duplicate load does not replace current state", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(9,"original")));
            rejects(IllegalArgumentException.class,()->manager.loadExpenses(List.of(expense(1,"a"),expense(1,"b"))));
            equal(9L,manager.getAllExpenses().get(0).getId());
        });
        test("domain rejects sub-cent precision", () -> rejects(IllegalArgumentException.class,()->new Expense(1,"x",new BigDecimal("0.001"),"Food",LocalDate.now(),"")));
        test("escaped text round trips", () -> withFile(file -> {
            var value=new Expense(2,"Bus\tpass",new BigDecimal("3"),"travel",LocalDate.of(2026,1,2),"path\\file\nnext\rline\ttab");
            var storage=new ExpenseFileStorage(file);storage.save(List.of(value));var found=storage.load().get(0);
            equal(value.getTitle(),found.getTitle());equal(value.getNote(),found.getNote());equal(new BigDecimal("3.00"),found.getAmount());
        }));
        test("missing file means empty collection", () -> withFile(file -> equal(0,new ExpenseFileStorage(file).load().size())));
        test("duplicate saved ids reject entire file", () -> withFile(file -> {
            var storage=new ExpenseFileStorage(file);storage.save(List.of(expense(1,"ok")));
            String record=Files.readAllLines(file).get(1);Files.writeString(file,Files.readString(file)+record+"\n");
            rejects(IOException.class,storage::load);
        }));
        test("invalid precision in saved data is rejected", () -> withFile(file -> {
            Files.writeString(file,"1\tLunch\t1.001\tFood\t2026-01-02\t\n");
            rejects(IOException.class,new ExpenseFileStorage(file)::load);
        }));
        test("unknown escape is rejected without dropping its slash", () -> withFile(file -> {
            Files.writeString(file,"1\tLunch\t1.00\tFood\t2026-01-02\tbad\\q\n");
            rejects(IOException.class,new ExpenseFileStorage(file)::load);
        }));
        test("invalid save preserves existing bytes", () -> withFile(file -> {
            var storage=new ExpenseFileStorage(file);storage.save(List.of(expense(3,"keep")));String before=Files.readString(file);
            rejects(IllegalArgumentException.class,()->storage.save(List.of(expense(1,"a"),expense(1,"b"))));
            equal(before,Files.readString(file));
        }));
        test("repeated save replaces file and removes temporary sibling", () -> withFile(file -> {
            var storage=new ExpenseFileStorage(file);storage.save(List.of(expense(1,"old")));storage.save(List.of(expense(2,"new")));
            equal(2L,storage.load().get(0).getId());try(var files=Files.list(file.getParent())) { equal(1L,files.count()); }
        }));
        test("failed atomic move cleans temporary sibling", () -> withFile(file -> {
            Files.createDirectory(file);Files.writeString(file.resolve("keep.txt"),"keep");
            try { rejects(IOException.class,()->new ExpenseFileStorage(file).save(List.of(expense(1,""))));
                equal("keep",Files.readString(file.resolve("keep.txt")));try(var files=Files.list(file.getParent())) { equal(1L,files.count()); }
            } finally { Files.delete(file.resolve("keep.txt"));Files.delete(file); }
        }));
        test("invalid add leaves count and next identity unchanged", () -> {
            var manager=new ExpenseManager();rejects(IllegalArgumentException.class,()->manager.addExpense("x","1.001","Food","2026-01-02",""));
            equal(0,manager.getAllExpenses().size());equal(1L,manager.addExpense("x","1","Food","2026-01-02","").getId());
        });
        test("update keeps identity and changes aggregates", () -> {
            var manager=new ExpenseManager();var old=manager.addExpense("Lunch","12.30","food","2026-01-02","old");
            var updated=manager.updateExpense(old.getId(),"Bus","2.50"," TRAVEL ","2026-02-03","");
            equal(old.getId(),updated.getId());equal("Lunch",old.getTitle());equal("Bus",manager.findExpense(1).orElseThrow().getTitle());
            equal(new BigDecimal("2.50"),manager.getTotalSpending());equal(0,manager.getExpensesByCategory("Food").size());equal(1,manager.getExpensesByMonth("2026-02").size());
            equal(2L,manager.addExpense("Tea","1","Food","2026-01-02","").getId());
        });
        test("invalid update preserves previous value", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(1,"original")));
            rejects(IllegalArgumentException.class,()->manager.updateExpense(1,"Bus","2.50","Travel","2026-02-30","changed"));
            equal("original",manager.findExpense(1).orElseThrow().getNote());equal(new BigDecimal("12.30"),manager.getTotalSpending());
        });
        test("missing update is distinct from invalid input", () -> {
            var manager=new ExpenseManager();rejects(java.util.NoSuchElementException.class,()->manager.updateExpense(99,"x","1","Food","2026-01-02",""));equal(0,manager.getAllExpenses().size());
        });
        test("delete is repeatable and does not recycle ids in session", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(1,"")));equal(true,manager.deleteExpense(1));equal(false,manager.deleteExpense(1));
            equal(0,manager.getTotalSpending().signum());equal(2L,manager.addExpense("x","1","Food","2026-01-02","").getId());
        });
        test("null load does not clear existing state", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(8,"")));
            rejects(NullPointerException.class,()->manager.loadExpenses(java.util.Arrays.asList(expense(1,""),null)));
            equal(8L,manager.getAllExpenses().get(0).getId());
        });
        test("highest loaded id controls allocation", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(8,""),expense(2,"")));
            equal(9L,manager.addExpense("x","1","Food","2026-01-02","").getId());
        });
        test("exhausted identity never wraps to negative", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(Long.MAX_VALUE-1,"")));
            equal(Long.MAX_VALUE,manager.addExpense("x","1","Food","2026-01-02","").getId());
            rejects(IllegalStateException.class,()->manager.addExpense("x","1","Food","2026-01-02",""));equal(2,manager.getAllExpenses().size());
        });
        test("category normalization is shared and locale independent", () -> {
            var old=java.util.Locale.getDefault();try { java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
                var manager=new ExpenseManager();manager.loadExpenses(List.of(new Expense(1,"x",new BigDecimal("1")," INCOME   tax ",LocalDate.now(),"")));
                manager.addExpense("y","2","income tax","2026-01-02","");equal(new BigDecimal("3.00"),manager.getSpendingByCategory().get("Income Tax"));
            } finally { java.util.Locale.setDefault(old); }
        });
        test("collection reads cannot mutate manager", () -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(1,"")));manager.getAllExpenses().clear();equal(1,manager.getAllExpenses().size());
        });
        test("update and delete survive persisted reload", () -> withFile(file -> {
            var manager=new ExpenseManager();manager.loadExpenses(List.of(expense(1,""),expense(2,"")));
            manager.updateExpense(1,"Bus","2.00","Travel","2026-02-01","");manager.deleteExpense(2);
            var storage=new ExpenseFileStorage(file);storage.save(manager.getAllExpenses());var reloaded=new ExpenseManager();reloaded.loadExpenses(storage.load());
            equal(1,reloaded.getAllExpenses().size());equal("Bus",reloaded.findExpense(1).orElseThrow().getTitle());equal(false,reloaded.findExpense(2).isPresent());
        }));
        System.out.println("RESULT passed="+passed+" failed="+failed);
        if(failed!=0) System.exit(1);
    }
}
