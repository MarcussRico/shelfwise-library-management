package shelfwise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shelfwise.model.*;
import shelfwise.policy.DemandAdaptivePolicy;
import shelfwise.service.*;
import shelfwise.store.*;

class ServiceTest {
    LibraryService svc;
    final LocalDate d = LocalDate.of(2026, 10, 1);

    @BeforeEach void setUp() throws Exception {
        var path = Files.createTempFile("svc", ".db");
        Files.delete(path);
        Database db = new Database(path.toString());
        try (Connection c = db.connect()) {
            BookDao bd = new BookDao(db);
            bd.insert(c, new Book(1, "9780439023481", "The Hunger Games", "Suzanne Collins", 2008, 100, 1, 1));
            bd.insert(c, new Book(2, "9780000000002", "B", "X", 2000, 1, 2, 2));
            bd.insert(c, new Book(3, "9780000000019", "C", "X", 2000, 1, 1, 1));
            bd.insert(c, new Book(4, "9780000000026", "D", "X", 2000, 1, 1, 1));
            MemberDao md = new MemberDao(db);
            for (int i = 1; i <= 4; i++) md.insert(c, new Student(i, "M" + i));
        }
        svc = new LibraryService(db, new DemandAdaptivePolicy());
    }

    @Test void issueTakesCopy() throws Exception {
        var r = svc.issue(1, 1, d);
        assertTrue(r.issued());
        assertEquals(0, svc.books().find(1).orElseThrow().available());
    }
    @Test void secondMemberGoesToQueue() throws Exception {
        svc.issue(1, 1, d);
        var r = svc.issue(2, 1, d);
        assertFalse(r.issued()); assertEquals(1, r.queuePosition());
    }
    @Test void sameMemberCannotTakeTwice() throws Exception {
        svc.issue(1, 2, d);
        assertThrows(LibraryException.AlreadyHeld.class, () -> svc.issue(1, 2, d));
    }
    @Test void borrowLimitEnforced() throws Exception {
        svc.issue(1, 1, d); svc.issue(1, 2, d); svc.issue(1, 3, d);
        assertThrows(LibraryException.LimitReached.class, () -> svc.issue(1, 4, d));
    }
    @Test void queueHeadGetsShortenedLoan() throws Exception {
        int loan = svc.issue(1, 1, d).loanId();
        svc.issue(2, 1, d); svc.issue(3, 1, d);
        var ret = svc.returnBook(loan, d.plusDays(5));
        assertEquals(2, ret.nextMemberId());
        var r = svc.issue(2, 1, d.plusDays(5));       // one still waiting: 14 / 1.5 = 9 days
        assertTrue(r.issued()); assertEquals(9, r.terms().days()); assertFalse(r.terms().renewable());
    }
    @Test void othersCannotJumpTheQueue() throws Exception {
        int loan = svc.issue(1, 1, d).loanId();
        svc.issue(2, 1, d);
        svc.returnBook(loan, d.plusDays(3));
        assertFalse(svc.issue(3, 1, d.plusDays(3)).issued());
    }
    @Test void lateReturnFinedAcrossMonth() throws Exception {
        int loan = svc.issue(1, 2, LocalDate.of(2026, 1, 20)).loanId();   // due 3 Feb
        var r = svc.returnBook(loan, LocalDate.of(2026, 2, 6));
        assertEquals(3, r.daysLate()); assertEquals(6, r.fine());
    }
    @Test void onTimeReturnNoFine() throws Exception {
        int loan = svc.issue(1, 2, d).loanId();
        assertEquals(0, svc.returnBook(loan, d.plusDays(14)).fine());
    }
    @Test void cannotReturnTwice() throws Exception {
        int loan = svc.issue(1, 2, d).loanId();
        svc.returnBook(loan, d.plusDays(1));
        assertThrows(LibraryException.NotOnLoan.class, () -> svc.returnBook(loan, d.plusDays(2)));
    }
    @Test void returnBeforeIssueRejected() throws Exception {
        int loan = svc.issue(1, 2, d).loanId();
        assertThrows(LibraryException.InvalidInput.class, () -> svc.returnBook(loan, d.minusDays(1)));
    }
    @Test void unknownMemberRejected() { assertThrows(LibraryException.NotFound.class, () -> svc.issue(99, 1, d)); }
    @Test void failedIssueLeavesNoTrace() throws Exception {
        assertThrows(LibraryException.class, () -> svc.issue(99, 1, d));
        assertEquals(1, svc.books().find(1).orElseThrow().available());
        assertEquals(0, svc.ledger().all().size());
    }
    @Test void everyOperationIsLedgered() throws Exception {
        int loan = svc.issue(1, 1, d).loanId();
        svc.issue(2, 1, d);
        svc.returnBook(loan, d.plusDays(2));
        assertEquals(3, svc.ledger().all().size());
        assertEquals(-1, svc.ledger().verify());
    }
}
