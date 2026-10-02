package shelfwise;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import shelfwise.model.*;
import shelfwise.policy.*;

class PolicyTest {
    final LocalDate d = LocalDate.of(2026, 1, 28);
    final Member s = new Student(1, "Asha"), f = new Faculty(2, "Ravi");

    @Test void memberLimitsDiffer() { assertEquals(3, s.borrowLimit()); assertEquals(6, f.borrowLimit()); }
    @Test void fixedUsesBase() { assertEquals(14, new FixedLoanPolicy().termsFor(s, 5, d).days()); }
    @Test void adaptiveNoQueueIsStandard() {
        var t = new DemandAdaptivePolicy().termsFor(s, 0, d);
        assertEquals(14, t.days()); assertTrue(t.renewable());
    }
    @Test void adaptiveShortensWithQueue() {
        var t = new DemandAdaptivePolicy().termsFor(s, 2, d);   // 14 / (1 + 0.5*2) = 7
        assertEquals(7, t.days()); assertFalse(t.renewable());
    }
    @Test void adaptiveNeverBelowMinimum() { assertEquals(7, new DemandAdaptivePolicy().termsFor(s, 50, d).days()); }
    @Test void facultyShortenedProportionally() { assertEquals(19, new DemandAdaptivePolicy().termsFor(f, 1, d).days()); }
    @Test void dueDateCrossesMonthBoundary() {
        assertEquals(LocalDate.of(2026, 2, 11), new FixedLoanPolicy().termsFor(s, 0, d).due());
    }
    @Test void rejectsBadParameters() { assertThrows(IllegalArgumentException.class, () -> new DemandAdaptivePolicy(0, 0.5)); }
    @Test void memberNeedsName() { assertThrows(IllegalArgumentException.class, () -> new Student(3, " ")); }
    @Test void bookNeedsValidCopies() {
        assertThrows(IllegalArgumentException.class, () -> new Book(1, "9780439023481", "T", "A", 2000, 1, 1, 2));
    }
}
