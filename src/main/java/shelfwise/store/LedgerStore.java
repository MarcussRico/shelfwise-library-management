package shelfwise.store;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import shelfwise.ledger.HashChain;
import shelfwise.ledger.LedgerEntry;

/** Append-only audit ledger kept in the same database as the data it audits. */
public class LedgerStore {
    private final Database db;

    public LedgerStore(Database db) { this.db = db; }

    /** Appends inside the caller's transaction, so the event and the change it records commit together. */
    public LedgerEntry append(Connection c, String ts, String type, String payload) throws SQLException {
        long seq = 1;
        String prev = HashChain.GENESIS;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT seq, hash FROM ledger ORDER BY seq DESC LIMIT 1")) {
            if (rs.next()) { seq = rs.getLong(1) + 1; prev = rs.getString(2); }
        }
        String hash = HashChain.hash(seq, ts, type, payload, prev);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ledger(seq,ts,type,payload,prev_hash,hash) VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, seq); ps.setString(2, ts); ps.setString(3, type);
            ps.setString(4, payload); ps.setString(5, prev); ps.setString(6, hash);
            ps.executeUpdate();
        }
        return new LedgerEntry(seq, ts, type, payload, prev, hash);
    }

    public List<LedgerEntry> all() throws SQLException {
        List<LedgerEntry> out = new ArrayList<>();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM ledger ORDER BY seq")) {
            while (rs.next()) out.add(new LedgerEntry(rs.getLong("seq"), rs.getString("ts"), rs.getString("type"),
                    rs.getString("payload"), rs.getString("prev_hash"), rs.getString("hash")));
        }
        return out;
    }

    /** -1 when intact, else the 0-based index of the first entry that no longer verifies. */
    public int verify() throws SQLException { return HashChain.firstBroken(all()); }
}
