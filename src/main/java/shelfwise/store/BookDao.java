package shelfwise.store;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import shelfwise.model.Book;

/** JDBC access to the books table. Every query is a PreparedStatement. */
public class BookDao {
    private final Database db;

    public BookDao(Database db) { this.db = db; }

    public void insert(Connection c, Book b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO books(id,isbn13,title,authors,year,popularity,copies,available) VALUES(?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, b.id()); ps.setString(2, b.isbn13()); ps.setString(3, b.title());
            ps.setString(4, b.authors()); ps.setInt(5, b.year()); ps.setLong(6, b.popularity());
            ps.setInt(7, b.copies()); ps.setInt(8, b.available());
            ps.executeUpdate();
        }
    }

    public Optional<Book> find(int id) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM books WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        }
    }

    public List<Book> all() throws SQLException {
        List<Book> out = new ArrayList<>();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM books ORDER BY popularity DESC")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    /**
     * Takes one copy off the shelf only if one is there. The check and the decrement are a
     * single SQL statement, so two librarians can never issue the last copy twice.
     */
    public boolean takeCopy(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET available = available - 1 WHERE id=? AND available > 0")) {
            ps.setInt(1, bookId);
            return ps.executeUpdate() == 1;
        }
    }

    public void putBack(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET available = available + 1 WHERE id=? AND available < copies")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        }
    }

    static Book map(ResultSet rs) throws SQLException {
        return new Book(rs.getInt("id"), rs.getString("isbn13"), rs.getString("title"), rs.getString("authors"),
                rs.getInt("year"), rs.getLong("popularity"), rs.getInt("copies"), rs.getInt("available"));
    }
}
