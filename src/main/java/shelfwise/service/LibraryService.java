package shelfwise.service;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import shelfwise.model.Book;
import shelfwise.model.LoanTerms;
import shelfwise.model.Member;
import shelfwise.policy.LoanPolicy;
import shelfwise.store.BookDao;
import shelfwise.store.Database;
import shelfwise.store.LedgerStore;
import shelfwise.store.MemberDao;

/**
 * The library's rules in one place: borrowing limits, the hold queue, loan length (delegated to a
 * {@link LoanPolicy}), fines, and the audit ledger. Every operation is one database transaction.
 */
public class LibraryService {
    public static final int FINE_PER_DAY = 2; // rupees

    public record IssueResult(boolean issued, int loanId, LoanTerms terms, int queuePosition, String message) {}
    public record ReturnResult(int fine, long daysLate, String nextMember, int nextMemberId, String message) {}
    public record OpenLoan(int loanId, Book book, Member member, LocalDate issued, LocalDate due, String note) {}
    public record HotTitle(Book book, int holds) {}

    private final Database db;
    private final BookDao books;
    private final MemberDao members;
    private final LedgerStore ledger;
    private LoanPolicy policy;

    public LibraryService(Database db, LoanPolicy policy) {
        this.db = db;
        this.books = new BookDao(db);
        this.members = new MemberDao(db);
        this.ledger = new LedgerStore(db);
        this.policy = policy;
    }

    public LoanPolicy policy() { return policy; }
    public void setPolicy(LoanPolicy p) { this.policy = p; }
    public BookDao books() { return books; }
    public MemberDao members() { return members; }
    public LedgerStore ledger() { return ledger; }

    public IssueResult issue(int memberId, int bookId, LocalDate today) throws LibraryException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                Member m = members.find(memberId).orElseThrow(() -> new LibraryException.NotFound("Member #" + memberId));
                Book b = books.find(bookId).orElseThrow(() -> new LibraryException.NotFound("Book #" + bookId));
                if (count(c, "SELECT COUNT(*) FROM loans WHERE member_id=? AND book_id=? AND returned IS NULL", memberId, bookId) > 0)
                    throw new LibraryException.AlreadyHeld();
                if (count(c, "SELECT COUNT(*) FROM loans WHERE member_id=? AND returned IS NULL", memberId) >= m.borrowLimit())
                    throw new LibraryException.LimitReached(m.getName(), m.borrowLimit());

                List<Integer> queue = holdQueue(c, bookId);
                boolean atHead = !queue.isEmpty() && queue.get(0) == memberId;
                boolean othersFirst = !queue.isEmpty() && !atHead;
                if (othersFirst || !books.takeCopy(c, bookId)) {
                    if (queue.contains(memberId)) throw new LibraryException.AlreadyHeld();
                    exec(c, "INSERT INTO holds(book_id,member_id,placed) VALUES(?,?,?)", bookId, memberId, today.toString());
                    int pos = queue.size() + 1;
                    ledger.append(c, today.toString(), "HOLD", "book=" + bookId + ";member=" + memberId + ";position=" + pos);
                    c.commit();
                    return new IssueResult(false, -1, null, pos,
                            "No copy free. " + m.getName() + " is number " + pos + " in the queue for \"" + b.title() + "\".");
                }
                if (atHead) exec(c, "DELETE FROM holds WHERE book_id=? AND member_id=?", bookId, memberId);
                int waiting = (int) count(c, "SELECT COUNT(*) FROM holds WHERE book_id=?", bookId);
                LoanTerms t = policy.termsFor(m, waiting, today);
                int loanId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO loans(book_id,member_id,issued,due,note) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, bookId); ps.setInt(2, memberId); ps.setString(3, today.toString());
                    ps.setString(4, t.due().toString()); ps.setString(5, t.reason());
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); loanId = k.getInt(1); }
                }
                ledger.append(c, today.toString(), "ISSUE", "loan=" + loanId + ";book=" + bookId + ";member=" + memberId
                        + ";due=" + t.due() + ";days=" + t.days() + ";waiting=" + waiting);
                c.commit();
                return new IssueResult(true, loanId, t, 0, "Issued \"" + b.title() + "\" to " + m.getName() + ", due " + t.due() + ".");
            } catch (LibraryException | SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new LibraryException("Database error: " + e.getMessage());
        }
    }

    public ReturnResult returnBook(int loanId, LocalDate today) throws LibraryException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int bookId, memberId;
                LocalDate due;
                try (PreparedStatement ps = c.prepareStatement("SELECT book_id, member_id, due FROM loans WHERE id=? AND returned IS NULL")) {
                    ps.setInt(1, loanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new LibraryException.NotOnLoan();
                        bookId = rs.getInt(1); memberId = rs.getInt(2); due = LocalDate.parse(rs.getString(3));
                    }
                }
                if (today.isBefore(LocalDate.parse(issuedOf(c, loanId))))
                    throw new LibraryException.InvalidInput("Return date is before the issue date.");
                long late = Math.max(0, ChronoUnit.DAYS.between(due, today));
                int fine = (int) late * FINE_PER_DAY;
                exec(c, "UPDATE loans SET returned=?, fine=? WHERE id=?", today.toString(), fine, loanId);
                books.putBack(c, bookId);
                ledger.append(c, today.toString(), "RETURN", "loan=" + loanId + ";book=" + bookId + ";member=" + memberId
                        + ";late=" + late + ";fine=" + fine);
                List<Integer> queue = holdQueue(c, bookId);
                c.commit();
                String next = queue.isEmpty() ? null : members.find(queue.get(0)).map(Member::getName).orElse(null);
                String msg = (fine > 0 ? "Returned " + late + " day(s) late: fine Rs " + fine + ". " : "Returned on time. ")
                        + (next != null ? "Hold this copy for " + next + "." : "Back on the shelf.");
                return new ReturnResult(fine, late, next, queue.isEmpty() ? 0 : queue.get(0), msg);
            } catch (LibraryException | SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new LibraryException("Database error: " + e.getMessage());
        }
    }

    public List<OpenLoan> openLoans() throws SQLException {
        List<OpenLoan> out = new ArrayList<>();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                 SELECT l.id AS lid, l.issued, l.due, l.note, b.id AS bid, b.isbn13, b.title, b.authors, b.year,
                        b.popularity, b.copies, b.available, m.id AS mid, m.name AS mname, m.type AS mtype
                 FROM loans l JOIN books b ON b.id=l.book_id JOIN members m ON m.id=l.member_id
                 WHERE l.returned IS NULL ORDER BY l.due, l.id""")) {
            while (rs.next()) {
                Book b = new Book(rs.getInt("bid"), rs.getString("isbn13"), rs.getString("title"), rs.getString("authors"),
                        rs.getInt("year"), rs.getLong("popularity"), rs.getInt("copies"), rs.getInt("available"));
                out.add(new OpenLoan(rs.getInt("lid"), b, Member.of(rs.getInt("mid"), rs.getString("mname"), rs.getString("mtype")),
                        LocalDate.parse(rs.getString("issued")), LocalDate.parse(rs.getString("due")), rs.getString("note")));
            }
        }
        return out;
    }

    public List<HotTitle> hotTitles(int limit) throws SQLException {
        List<HotTitle> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                 SELECT b.*, COUNT(h.id) AS n FROM holds h JOIN books b ON b.id=h.book_id
                 GROUP BY b.id ORDER BY n DESC, b.popularity DESC LIMIT ?""")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new HotTitle(new Book(rs.getInt("id"), rs.getString("isbn13"), rs.getString("title"),
                        rs.getString("authors"), rs.getInt("year"), rs.getLong("popularity"), rs.getInt("copies"),
                        rs.getInt("available")), rs.getInt("n")));
            }
        }
        return out;
    }

    public int holdsWaiting(int bookId) throws SQLException {
        try (Connection c = db.connect()) { return (int) count(c, "SELECT COUNT(*) FROM holds WHERE book_id=?", bookId); }
    }

    public long scalar(String sql) throws SQLException {
        try (Connection c = db.connect()) { return count(c, sql); }
    }

    // ------------------------------------------------------------------ helpers
    private List<Integer> holdQueue(Connection c, int bookId) throws SQLException {
        List<Integer> q = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT member_id FROM holds WHERE book_id=? ORDER BY placed, id")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) q.add(rs.getInt(1)); }
        }
        return q;
    }

    private String issuedOf(Connection c, int loanId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT issued FROM loans WHERE id=?")) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private static long count(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }
}
