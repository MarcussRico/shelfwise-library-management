package shelfwise.policy;

import java.time.LocalDate;
import shelfwise.model.LoanTerms;
import shelfwise.model.Member;

/** The conventional rule: the same loan length for every book, renewable once. */
public class FixedLoanPolicy implements LoanPolicy {
    @Override
    public LoanTerms termsFor(Member member, int holdsWaiting, LocalDate issued) {
        int days = member.baseLoanDays();
        return new LoanTerms(issued.plusDays(days), days, true,
                "Standard " + days + "-day loan for a " + member.type().toLowerCase() + ".");
    }

    @Override public String name() { return "Fixed"; }
}
