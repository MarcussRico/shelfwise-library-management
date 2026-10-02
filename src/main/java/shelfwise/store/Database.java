package shelfwise.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens the SQLite database and creates the schema. */
public final class Database {
    private final String url;

    public Database(String path) {
        this.url = "jdbc:sqlite:" + path;
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA journal_mode=WAL");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS books(
                  id INTEGER PRIMARY KEY, isbn13 TEXT UNIQUE NOT NULL, title TEXT NOT NULL,
                  authors TEXT NOT NULL, year INTEGER, popularity INTEGER NOT NULL,
                  copies INTEGER NOT NULL CHECK(copies >= 1),
                  available INTEGER NOT NULL CHECK(available >= 0 AND available <= copies))""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS members(
                  id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL)""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS loans(
                  id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL REFERENCES books(id),
                  member_id INTEGER NOT NULL REFERENCES members(id), issued TEXT NOT NULL,
                  due TEXT NOT NULL, returned TEXT, fine INTEGER NOT NULL DEFAULT 0, note TEXT)""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS holds(
                  id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL REFERENCES books(id),
                  member_id INTEGER NOT NULL REFERENCES members(id), placed TEXT NOT NULL,
                  UNIQUE(book_id, member_id))""");
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ledger(
                  seq INTEGER PRIMARY KEY, ts TEXT NOT NULL, type TEXT NOT NULL,
                  payload TEXT NOT NULL, prev_hash TEXT NOT NULL, hash TEXT NOT NULL)""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_loans_open ON loans(member_id, returned)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_holds_book ON holds(book_id, placed)");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database " + path, e);
        }
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }
}
