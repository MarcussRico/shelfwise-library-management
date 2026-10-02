package shelfwise.model;

import java.time.LocalDate;

public record Loan(int id, int bookId, int memberId, LocalDate issued, LocalDate due,
                   LocalDate returned, String policyNote) {
    public boolean isOpen() { return returned == null; }
}
