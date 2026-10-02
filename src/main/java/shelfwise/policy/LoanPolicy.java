package shelfwise.policy;

import java.time.LocalDate;
import shelfwise.model.LoanTerms;
import shelfwise.model.Member;

/** Decides how long a member may keep a book. */
public interface LoanPolicy {
    LoanTerms termsFor(Member member, int holdsWaiting, LocalDate issued);
    String name();
}
