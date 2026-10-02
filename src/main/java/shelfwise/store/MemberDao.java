package shelfwise.store;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import shelfwise.model.Member;

public class MemberDao {
    private final Database db;

    public MemberDao(Database db) { this.db = db; }

    public void insert(Connection c, Member m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO members(id,name,type) VALUES(?,?,?)")) {
            ps.setInt(1, m.getId()); ps.setString(2, m.getName()); ps.setString(3, m.type());
            ps.executeUpdate();
        }
    }

    public Optional<Member> find(int id) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT * FROM members WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Member.of(rs.getInt("id"), rs.getString("name"), rs.getString("type")))
                        : Optional.empty();
            }
        }
    }

    public List<Member> all() throws SQLException {
        List<Member> out = new ArrayList<>();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM members ORDER BY id")) {
            while (rs.next()) out.add(Member.of(rs.getInt("id"), rs.getString("name"), rs.getString("type")));
        }
        return out;
    }
}
