package shelfwise.app;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import shelfwise.ledger.HashChain;
import shelfwise.ledger.LedgerEntry;
import shelfwise.model.Book;
import shelfwise.policy.*;
import shelfwise.search.FuzzySearch;
import shelfwise.sim.CirculationSimulator;
import shelfwise.store.*;

/** Runs every experiment in the report and writes results/results.json. Deterministic (seeded). */
public final class Experiments {
    private static final StringBuilder json = new StringBuilder("{\n");

    public static void main(String[] args) throws Exception {
        Path csv = Path.of("data/books.csv");
        Files.createDirectories(Path.of("results"));
        var report = Catalogue.load(csv, 1000);
        put("catalogue", String.format(Locale.ROOT,
                "{\"rows\":%d,\"valid_as_is\":%d,\"repaired_leading_zeros\":%d,\"rejected\":%d,\"kept\":%d,\"copies\":%d}",
                report.rows(), report.validAsIs(), report.repairedLeadingZeros(), report.rejected(), report.books().size(),
                report.books().stream().mapToInt(Book::copies).sum()));
        System.out.println("catalogue " + report.rows() + " rows, valid " + report.validAsIs() + ", repaired " + report.repairedLeadingZeros());

        simulation(report.books());
        race();
        ledger();
        search(report.books());
        json.setLength(json.length() - 2);
        json.append("\n}\n");
        Files.writeString(Path.of("results/results.json"), json.toString());
        System.out.println(json);
    }

    // ------------------------------------------------------------- E1 simulation
    static void simulation(List<Book> books) {
        long[] seeds = {1, 2, 3, 4, 5};
        StringBuilder sb = new StringBuilder("{");
        LoanPolicy[] policies = {new FixedLoanPolicy(), new DemandAdaptivePolicy(7, 0.5)};
        for (LoanPolicy p : policies) sb.append(q(p.name())).append(":").append(simAgg(books, seeds, p)).append(",");
        StringBuilder sens = new StringBuilder("[");
        for (int min : new int[]{5, 7, 10}) for (double k : new double[]{0.25, 0.5, 1.0}) {
            sens.append("{\"min\":").append(min).append(",\"k\":").append(k).append(",\"r\":")
                    .append(simAgg(books, seeds, new DemandAdaptivePolicy(min, k))).append("},");
        }
        sens.setLength(sens.length() - 1);
        sb.append("\"sensitivity\":").append(sens).append("]}");
        put("simulation", sb.toString());
        System.out.println("simulation done");
    }

    static String simAgg(List<Book> books, long[] seeds, LoanPolicy p) {
        List<CirculationSimulator.Result> rs = new ArrayList<>();
        for (long s : seeds) rs.add(new CirculationSimulator(CirculationSimulator.Config.standard(s), books).run(p));
        return String.format(Locale.ROOT,
                "{\"mean_wait\":%.3f,\"mean_wait_sd\":%.3f,\"p90_wait\":%.2f,\"requests\":%.1f,\"queued_share\":%.4f,"
                        + "\"served_from_queue\":%.1f,\"mean_loan_days\":%.3f,\"contested_share\":%.4f,\"unfinished_share\":%.4f,"
                        + "\"uncontested_mean_days\":%.3f,\"fines\":%.1f,\"late_share\":%.4f}",
                avg(rs, r -> r.meanWait()), sd(rs, r -> r.meanWait()), avg(rs, r -> r.p90Wait()), avg(rs, r -> r.requests()),
                avg(rs, r -> (double) r.queued() / r.requests()), avg(rs, r -> r.servedFromQueue()), avg(rs, r -> r.meanLoanDays()),
                avg(rs, r -> r.contestedShare()), avg(rs, r -> r.unfinishedShare()), avg(rs, r -> r.uncontestedMeanDays()),
                avg(rs, r -> r.fineRupees()), avg(rs, r -> r.lateShare()));
    }

    // ------------------------------------------------------------- E2 race
    static void race() throws Exception {
        int trials = 2000, threads = 16;
        int naiveOversold = 0, safeOversold = 0, naiveExtra = 0;
        for (int t = 0; t < trials; t++) {
            int n = raceInMemory(threads, false);
            if (n > 1) { naiveOversold++; naiveExtra += n - 1; }
            if (raceInMemory(threads, true) > 1) safeOversold++;
        }
        int dbTrials = 150, dbNaive = 0, dbSafe = 0;
        Path tmp = Files.createTempFile("race", ".db");
        Database db = new Database(tmp.toString());
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO books(id,isbn13,title,authors,year,popularity,copies,available) VALUES(1,'9780000000002','Race','T',2026,1,1,1)");
        }
        for (int t = 0; t < dbTrials; t++) {
            if (raceDb(db, 8, false) > 1) dbNaive++;
            if (raceDb(db, 8, true) > 1) dbSafe++;
        }
        put("race", String.format(Locale.ROOT,
                "{\"trials\":%d,\"threads\":%d,\"naive_oversold\":%d,\"naive_extra_copies\":%d,\"safe_oversold\":%d,"
                        + "\"db_trials\":%d,\"db_threads\":8,\"db_naive_oversold\":%d,\"db_safe_oversold\":%d}",
                trials, threads, naiveOversold, naiveExtra, safeOversold, dbTrials, dbNaive, dbSafe));
        System.out.println("race: naive " + naiveOversold + "/" + trials + ", safe " + safeOversold + "; db naive " + dbNaive + ", safe " + dbSafe);
    }

    /** One copy, many librarians clicking Issue at the same instant. Returns how many loans were created. */
    static int raceInMemory(int threads, boolean safe) throws Exception {
        int[] available = {1};
        AtomicInteger atomic = new AtomicInteger(1);
        AtomicInteger issued = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) fs.add(ex.submit(() -> {
            go.await();
            if (safe) {
                int cur;
                do { cur = atomic.get(); if (cur == 0) return null; } while (!atomic.compareAndSet(cur, cur - 1));
                issued.incrementAndGet();
            } else if (available[0] > 0) {          // check ...
                Thread.yield();                     // ... another thread can run here ...
                available[0] = available[0] - 1;    // ... then act
                issued.incrementAndGet();
            }
            return null;
        }));
        go.countDown();
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        return issued.get();
    }

    static int raceDb(Database db, int threads, boolean safe) throws Exception {
        try (Connection c = db.connect(); Statement s = c.createStatement()) { s.executeUpdate("UPDATE books SET available=1 WHERE id=1"); }
        AtomicInteger issued = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<?>> fs = new ArrayList<>();
        BookDao dao = new BookDao(db);
        for (int i = 0; i < threads; i++) fs.add(ex.submit(() -> {
            try (Connection c = db.connect()) {
                go.await();
                if (safe) {
                    if (dao.takeCopy(c, 1)) issued.incrementAndGet();
                } else {
                    int avail;
                    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT available FROM books WHERE id=1")) {
                        rs.next(); avail = rs.getInt(1);
                    }
                    if (avail > 0) {
                        Thread.sleep(1);
                        try (PreparedStatement ps = c.prepareStatement("UPDATE books SET available=? WHERE id=1")) {
                            ps.setInt(1, avail - 1); ps.executeUpdate();
                        }
                        issued.incrementAndGet();
                    }
                }
            }
            return null;
        }));
        go.countDown();
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        return issued.get();
    }

    // ------------------------------------------------------------- E3 ledger
    static void ledger() {
        Random rng = new Random(7);
        int n = 5000;
        List<LedgerEntry> chain = new ArrayList<>();
        String prev = HashChain.GENESIS;
        long t0 = System.nanoTime();
        for (int i = 1; i <= n; i++) {
            String ts = LocalDate.of(2026, 1, 1).plusDays(i / 40).toString();
            String payload = "loan=" + i + ";book=" + (1 + rng.nextInt(1000)) + ";member=" + (1 + rng.nextInt(300)) + ";fine=" + rng.nextInt(3) * 2;
            String type = i % 2 == 0 ? "RETURN" : "ISSUE";
            String h = HashChain.hash(i, ts, type, payload, prev);
            chain.add(new LedgerEntry(i, ts, type, payload, prev, h));
            prev = h;
        }
        double buildMs = (System.nanoTime() - t0) / 1e6;
        t0 = System.nanoTime();
        int intact = HashChain.firstBroken(chain);
        double verifyMs = (System.nanoTime() - t0) / 1e6;
        String[] kinds = {"edit_fine", "delete", "swap", "edit_date"};
        Map<String, int[]> det = new LinkedHashMap<>();
        for (String k : kinds) det.put(k, new int[3]); // trials, detected, located exactly
        for (int t = 0; t < 1000; t++) {
            String k = kinds[t % kinds.length];
            List<LedgerEntry> c = new ArrayList<>(chain);
            int i = 1 + rng.nextInt(n - 2);
            int expect = i;
            switch (k) {
                case "edit_fine" -> { var e = c.get(i); c.set(i, new LedgerEntry(e.seq(), e.timestamp(), e.type(), e.payload().replaceAll("fine=\\d+", "fine=0").concat(e.payload().endsWith("fine=0") ? " " : ""), e.prevHash(), e.hash())); }
                case "delete" -> c.remove(i);
                case "swap" -> { Collections.swap(c, i, i + 1); }
                default -> { var e = c.get(i); c.set(i, new LedgerEntry(e.seq(), "2025-12-31", e.type(), e.payload(), e.prevHash(), e.hash())); }
            }
            int got = HashChain.firstBroken(c);
            int[] a = det.get(k);
            a[0]++;
            if (got >= 0) a[1]++;
            if (got == expect) a[2]++;
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "{\"entries\":%d,\"build_ms\":%.2f,\"verify_ms\":%.2f,\"intact_result\":%d,\"tamper\":{", n, buildMs, verifyMs, intact));
        for (var e : det.entrySet()) sb.append(q(e.getKey())).append(String.format(Locale.ROOT,
                ":{\"trials\":%d,\"detected\":%d,\"located\":%d},", e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        sb.setLength(sb.length() - 1);
        sb.append("}}");
        put("ledger", sb.toString());
        System.out.println("ledger done");
    }

    // ------------------------------------------------------------- E4 search
    static void search(List<Book> books) {
        Random rng = new Random(11);
        FuzzySearch fs = new FuzzySearch(books);
        int n = 500, sub1 = 0, sub5 = 0, fz1 = 0, fz5 = 0, clean1 = 0, cleanSub1 = 0;
        double ms = 0;
        for (int t = 0; t < n; t++) {
            Book b = books.get(rng.nextInt(books.size()));
            String[] w = b.title().replaceAll("\\(.*?\\)", "").trim().split("\\s+");
            int len = Math.min(w.length, 1 + rng.nextInt(3));
            int st = rng.nextInt(w.length - len + 1);
            String query = String.join(" ", Arrays.copyOfRange(w, st, st + len));
            String typo = typo(query, rng);
            List<Book> s = FuzzySearch.substring(books, typo, 5);
            if (!s.isEmpty() && s.get(0).id() == b.id()) sub1++;
            if (s.stream().anyMatch(x -> x.id() == b.id())) sub5++;
            long t0 = System.nanoTime();
            var f = fs.search(typo, 5);
            ms += (System.nanoTime() - t0) / 1e6;
            if (!f.isEmpty() && f.get(0).book().id() == b.id()) fz1++;
            if (f.stream().anyMatch(x -> x.book().id() == b.id())) fz5++;
            var fc = fs.search(query, 1);
            if (!fc.isEmpty() && fc.get(0).book().id() == b.id()) clean1++;
            var sc = FuzzySearch.substring(books, query, 1);
            if (!sc.isEmpty() && sc.get(0).id() == b.id()) cleanSub1++;
        }
        put("search", String.format(Locale.ROOT,
                "{\"queries\":%d,\"substring_top1\":%.4f,\"substring_top5\":%.4f,\"fuzzy_top1\":%.4f,\"fuzzy_top5\":%.4f,"
                        + "\"clean_fuzzy_top1\":%.4f,\"clean_substring_top1\":%.4f,\"fuzzy_ms\":%.3f}",
                n, sub1 / (double) n, sub5 / (double) n, fz1 / (double) n, fz5 / (double) n, clean1 / (double) n,
                cleanSub1 / (double) n, ms / n));
        System.out.println("search done");
    }

    /** One random keyboard slip: substitution, deletion, insertion or transposition. */
    static String typo(String s, Random rng) {
        if (s.length() < 4) return s;
        char[] c = s.toCharArray();
        int i = 1 + rng.nextInt(c.length - 2);
        String letters = "abcdefghijklmnopqrstuvwxyz";
        return switch (rng.nextInt(4)) {
            case 0 -> s.substring(0, i) + letters.charAt(rng.nextInt(26)) + s.substring(i + 1);
            case 1 -> s.substring(0, i) + s.substring(i + 1);
            case 2 -> s.substring(0, i) + letters.charAt(rng.nextInt(26)) + s.substring(i);
            default -> { char x = c[i]; c[i] = c[i + 1]; c[i + 1] = x; yield new String(c); }
        };
    }

    // ------------------------------------------------------------- json helpers
    interface F { double f(CirculationSimulator.Result r); }
    static double avg(List<CirculationSimulator.Result> rs, F f) { return rs.stream().mapToDouble(f::f).average().orElse(0); }
    static double sd(List<CirculationSimulator.Result> rs, F f) {
        double m = avg(rs, f);
        return Math.sqrt(rs.stream().mapToDouble(r -> Math.pow(f.f(r) - m, 2)).sum() / Math.max(rs.size() - 1, 1));
    }
    static String q(String s) { return "\"" + s + "\""; }
    static void put(String k, String v) { json.append("  ").append(q(k)).append(": ").append(v).append(",\n"); }
}
