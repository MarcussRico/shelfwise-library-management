package shelfwise.policy;

import java.time.LocalDate;
import shelfwise.model.LoanTerms;
import shelfwise.model.Member;

/**
 * Shortens the loan when other members are already waiting for the same title:
 * days = max(minDays, round(base / (1 + k * holds))). With nobody waiting the loan is
 * exactly the standard one, so readers of uncontested books are never affected.
 */
public class DemandAdaptivePolicy implements LoanPolicy {
    private final int minDays;
    private final double k;

    public DemandAdaptivePolicy() { this(7, 0.5); }

    public DemandAdaptivePolicy(int minDays, double k) {
        if (minDays < 1 || k < 0) throw new IllegalArgumentException("Bad policy parameters");
        this.minDays = minDays;
        this.k = k;
    }

    @Override
    public LoanTerms termsFor(Member member, int holdsWaiting, LocalDate issued) {
        int base = member.baseLoanDays();
        if (holdsWaiting <= 0) {
            return new LoanTerms(issued.plusDays(base), base, true,
                    "Nobody is waiting, so the standard " + base + "-day loan applies.");
        }
        int days = Math.max(minDays, (int) Math.round(base / (1 + k * holdsWaiting)));
        String who = holdsWaiting == 1 ? "1 member is" : holdsWaiting + " members are";
        return new LoanTerms(issued.plusDays(days), days, false,
                who + " waiting for this title, so the loan is " + days + " days (standard " + base
                        + ") and cannot be renewed.");
    }

    public int minDays() { return minDays; }
    public double k() { return k; }
    @Override public String name() { return "Demand-adaptive"; }
}
