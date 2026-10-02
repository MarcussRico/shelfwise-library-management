package shelfwise.model;

import java.time.LocalDate;

/** The outcome of a loan policy: due date, whether renewal is allowed, and the reason in words. */
public record LoanTerms(LocalDate due, int days, boolean renewable, String reason) {}
