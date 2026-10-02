package shelfwise.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import shelfwise.model.Book;
import shelfwise.model.Member;
import shelfwise.policy.DemandAdaptivePolicy;
import shelfwise.service.LibraryException;
import shelfwise.service.LibraryService;
import shelfwise.store.*;

/** Builds a demo database: the catalogue, 300 members and 60 days of real service-level activity. */
public final class Seeder {
    static final String[] FIRST = {"Aarav", "Diya", "Karthik", "Meera", "Rohan", "Ananya", "Vikram", "Nila", "Arjun", "Kavya",
            "Surya", "Divya", "Pranav", "Ishita", "Harish", "Lakshmi", "Naveen", "Priya", "Rahul", "Sneha",
            "Gokul", "Janani", "Manoj", "Swathi", "Ajay", "Keerthana", "Bala", "Revathi", "Dinesh", "Yamini"};
    static final String[] INITIAL = {"A", "B", "K", "M", "R", "S", "V", "P", "N", "G"};

    private Seeder() {}

    public static LibraryService seed(Path dbPath, Path csv, int days, long seed) throws Exception {
        Files.deleteIfExists(dbPath);
        Files.deleteIfExists(Path.of(dbPath + "-wal"));
        Files.deleteIfExists(Path.of(dbPath + "-shm"));
        Database db = new Database(dbPath.toString());
        List<Book> books = Catalogue.load(csv, 1000).books();
        Random rng = new Random(seed);
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            BookDao bd = new BookDao(db);
            for (Book b : books) bd.insert(c, b);
            MemberDao md = new MemberDao(db);
            for (int i = 1; i <= 300; i++) {
                String name = FIRST[rng.nextInt(FIRST.length)] + " " + INITIAL[rng.nextInt(INITIAL.length)];
                md.insert(c, Member.of(i, name, i <= 270 ? "STUDENT" : "FACULTY"));
            }
            c.commit();
        }
        LibraryService svc = new LibraryService(db, new DemandAdaptivePolicy());
        double[] cum = new double[books.size()];
        double run = 0;
        for (int i = 0; i < books.size(); i++) { run += books.get(i).popularity(); cum[i] = run; }
        LocalDate start = LocalDate.of(2026, 8, 3);
        for (int d = 0; d < days; d++) {
            LocalDate today = start.plusDays(d);
            for (var l : svc.openLoans()) {          // borrowers return when they finish reading
                long held = today.toEpochDay() - l.issued().toEpochDay();
                if (held >= 3 && rng.nextDouble() < (today.isAfter(l.due()) ? 0.35 : 0.09)) {
                    var res = svc.returnBook(l.loanId(), today);
                    if (res.nextMemberId() > 0) {
                        try { svc.issue(res.nextMemberId(), l.book().id(), today); } catch (LibraryException ignored) { }
                    }
                }
            }
            for (int r = 0; r < 14; r++) {
                int m = 1 + rng.nextInt(300);
                double x = rng.nextDouble() * run;
                int i = java.util.Arrays.binarySearch(cum, x);
                int b = books.get(i >= 0 ? i : -i - 1).id();
                try { svc.issue(m, b, today); } catch (LibraryException ignored) { /* limit reached etc. */ }
            }
        }
        return svc;
    }
}
