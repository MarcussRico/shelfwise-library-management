package shelfwise.sim;

import java.time.LocalDate;
import java.util.*;
import shelfwise.model.Book;
import shelfwise.model.LoanTerms;
import shelfwise.model.Member;
import shelfwise.model.Student;
import shelfwise.model.Faculty;
import shelfwise.policy.LoanPolicy;

/**
 * Discrete-event (daily) simulation of a year of circulation. Members request titles in
 * proportion to their real Goodreads popularity; unavailable titles go to a FIFO hold queue.
 * Each borrower needs a random reading time; they return when finished or at the due date
 * (sometimes late), and renew once if nobody is waiting. Only the loan policy differs between runs.
 */
public class CirculationSimulator {
    public record Config(int days, int warmup, double requestsPerDay, int students, int faculty,
                         double medianReadDays, double readSigma, double lateProb, double meanLateDays, long seed) {
        public static Config standard(long seed) {
            return new Config(365, 30, 45, 360, 40, 10, 0.6, 0.25, 3, seed);
        }
    }

    public record Result(String policy, long seed, int requests, int issuedAtOnce, int queued, double meanWait,
                         double p90Wait, int servedFromQueue, double meanLoanDays, double contestedShare,
                         double unfinishedShare, double uncontestedMeanDays, long fineRupees, double lateShare) {}

    private static final class Copy { int busyUntil = -1; }
    private record Hold(int member, int placed) {}
    private static final class Loan {
        int member, book, issued, due, readDays; boolean renewable, renewed, contested;
    }

    private final Config cfg;
    private final List<Book> books;
    private final double[] cumulative;

    public CirculationSimulator(Config cfg, List<Book> books) {
        this.cfg = cfg;
        this.books = books;
        cumulative = new double[books.size()];
        double run = 0;
        for (int i = 0; i < books.size(); i++) { run += books.get(i).popularity(); cumulative[i] = run; }
    }

    public Result run(LoanPolicy policy) {
        Random rng = new Random(cfg.seed());
        fineTally = 0;
        lateTally = 0;
        LocalDate day0 = LocalDate.of(2026, 1, 1);
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < cfg.students(); i++) members.add(new Student(i, "S" + i));
        for (int i = 0; i < cfg.faculty(); i++) members.add(new Faculty(cfg.students() + i, "F" + i));

        int nb = books.size();
        int[] free = new int[nb];
        for (int i = 0; i < nb; i++) free[i] = books.get(i).copies();
        List<ArrayDeque<Hold>> queues = new ArrayList<>();
        for (int i = 0; i < nb; i++) queues.add(new ArrayDeque<>());
        int[] onLoan = new int[members.size()];
        Set<Long> active = new HashSet<>();             // member*1e6+book: loan or hold in progress
        Map<Integer, List<Loan>> returnsByDay = new HashMap<>();

        int requests = 0, issuedAtOnce = 0, queued = 0, served = 0, unfinished = 0, late = 0, loansCounted = 0, contested = 0;
        long fines = 0;
        List<Integer> waits = new ArrayList<>();
        double loanDaysSum = 0, uncontestedDaysSum = 0;
        int uncontestedN = 0;

        for (int day = 0; day < cfg.days() + cfg.warmup(); day++) {
            boolean measure = day >= cfg.warmup();
            // 1. returns due today
            for (Loan l : returnsByDay.getOrDefault(day, List.of())) {
                onLoan[l.member]--;
                active.remove(key(l.member, l.book));
                ArrayDeque<Hold> q = queues.get(l.book);
                if (q.isEmpty()) { free[l.book]++; continue; }
                Hold h = q.poll();
                if (measure) { waits.add(day - h.placed()); served++; }
                Loan nl = startLoan(policy, members.get(h.member()), h.member(), l.book, day, q.size(), rng, day0);
                if (measure) { loansCounted++; loanDaysSum += nl.due - nl.issued; if (nl.contested) contested++;
                    else { uncontestedDaysSum += nl.due - nl.issued; uncontestedN++; } }
                schedule(nl, day, returnsByDay, rng, measure, queues);
                if (measure) { if (nl.readDays > nl.due - nl.issued && !nl.renewed) unfinished++; }
            }
            // 2. new requests
            int n = poisson(rng, cfg.requestsPerDay());
            for (int r = 0; r < n; r++) {
                int m = rng.nextInt(members.size());
                int b = pick(rng);
                Member mem = members.get(m);
                if (onLoan[m] >= mem.borrowLimit() || active.contains(key(m, b))) continue;
                if (measure) requests++;
                active.add(key(m, b));
                if (free[b] > 0 && queues.get(b).isEmpty()) {
                    free[b]--;
                    onLoan[m]++;
                    Loan l = startLoan(policy, mem, m, b, day, 0, rng, day0);
                    if (measure) { issuedAtOnce++; loansCounted++; loanDaysSum += l.due - l.issued;
                        uncontestedDaysSum += l.due - l.issued; uncontestedN++; }
                    schedule(l, day, returnsByDay, rng, measure, queues);
                    if (measure && l.readDays > l.due - l.issued && !l.renewed) unfinished++;
                } else {
                    queues.get(b).add(new Hold(m, day));
                    onLoan[m]++;              // a hold counts against the borrowing limit while it waits
                    if (measure) queued++;
                }
            }
        }
        // fines and lateness are tallied in schedule(); read back from the counters
        fines = fineTally; late = lateTally;
        Collections.sort(waits);
        double mean = waits.stream().mapToInt(Integer::intValue).average().orElse(0);
        double p90 = waits.isEmpty() ? 0 : waits.get((int) Math.floor(0.9 * (waits.size() - 1)));
        return new Result(policy.name(), cfg.seed(), requests, issuedAtOnce, queued, mean, p90, served,
                loanDaysSum / Math.max(loansCounted, 1), (double) contested / Math.max(loansCounted, 1),
                (double) unfinished / Math.max(loansCounted, 1), uncontestedDaysSum / Math.max(uncontestedN, 1),
                fines, (double) late / Math.max(loansCounted, 1));
    }

    private long fineTally;
    private int lateTally;

    private Loan startLoan(LoanPolicy policy, Member mem, int m, int b, int day, int waiting, Random rng, LocalDate day0) {
        LocalDate d = day0.plusDays(day);
        LoanTerms t = policy.termsFor(mem, waiting, d);
        Loan l = new Loan();
        l.member = m; l.book = b; l.issued = day; l.due = day + t.days();
        l.renewable = t.renewable(); l.contested = waiting > 0;
        l.readDays = Math.max(1, (int) Math.round(Math.exp(Math.log(cfg.medianReadDays()) + cfg.readSigma() * rng.nextGaussian())));
        return l;
    }

    private void schedule(Loan l, int day, Map<Integer, List<Loan>> byDay, Random rng, boolean measure,
                          List<ArrayDeque<Hold>> queues) {
        int len = l.due - l.issued;
        int ret;
        if (l.readDays <= len) {
            ret = l.issued + l.readDays;
        } else if (l.renewable && queues.get(l.book).isEmpty()) {
            l.renewed = true;                                  // renew once: same length again
            ret = l.issued + Math.min(l.readDays, 2 * len);
            l.due = l.issued + 2 * len;
        } else if (rng.nextDouble() < cfg.lateProb()) {
            int extra = 1 + (int) Math.floor(-Math.log(1 - rng.nextDouble()) * cfg.meanLateDays());
            ret = l.due + extra;
            if (measure) { lateTally++; fineTally += 2L * extra; }
        } else {
            ret = l.due;
        }
        byDay.computeIfAbsent(Math.max(ret, day + 1), k -> new ArrayList<>()).add(l);
    }

    private int pick(Random rng) {
        double x = rng.nextDouble() * cumulative[cumulative.length - 1];
        int i = Arrays.binarySearch(cumulative, x);
        return i >= 0 ? i : -i - 1;
    }

    private static int poisson(Random rng, double lambda) {
        double l = Math.exp(-lambda), p = 1;
        int k = 0;
        do { k++; p *= rng.nextDouble(); } while (p > l);
        return k - 1;
    }

    private static long key(int m, int b) { return m * 1_000_000L + b; }
}
