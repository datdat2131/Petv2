package vn.soulpets.storage;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/** Lưu hồ sơ người chơi vào SQLite hoặc MySQL. Không phụ thuộc Bukkit (dễ test). Mọi thao tác đồng bộ hóa trên một kết nối. */
public final class SqlStorage implements AutoCloseable {
    public record TopRow(String uuid, String name, long score) {}
    private final String url, user, pass; private final boolean mysql; private final Logger log;
    private Connection conn;

    public SqlStorage(String url, String user, String pass, boolean mysql, Logger log) { this.url = url; this.user = user; this.pass = pass; this.mysql = mysql; this.log = log; }

    private synchronized Connection c() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(2)) {
            try { if (conn != null) conn.close(); } catch (SQLException ignored) { }
            conn = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, pass);
            if (!mysql) try (Statement st = conn.createStatement()) { st.execute("PRAGMA journal_mode=WAL"); st.execute("PRAGMA synchronous=FULL"); }
        }
        return conn;
    }
    public synchronized void init() throws SQLException {
        try (Statement st = c().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS astrax_profiles (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32), score BIGINT DEFAULT 0, data MEDIUMTEXT NOT NULL, updated BIGINT)");
        }
    }
    /** @return dữ liệu thô hoặc null nếu chưa có hồ sơ. Ném SQLException nếu lỗi (KHÔNG coi lỗi là "chưa có" để tránh ghi đè mất dữ liệu). */
    public synchronized String load(UUID id) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT data FROM astrax_profiles WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }
    public synchronized void save(UUID id, String name, long score, String data) throws SQLException {
        String sql = mysql
                ? "INSERT INTO astrax_profiles(uuid,name,score,data,updated) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),score=VALUES(score),data=VALUES(data),updated=VALUES(updated)"
                : "INSERT INTO astrax_profiles(uuid,name,score,data,updated) VALUES(?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,score=excluded.score,data=excluded.data,updated=excluded.updated";
        Connection cn = c();
        boolean auto = cn.getAutoCommit();
        try {
            cn.setAutoCommit(false);
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, id.toString()); ps.setString(2, name); ps.setLong(3, score); ps.setString(4, data); ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
            cn.commit();
        } catch (SQLException e) { try { cn.rollback(); } catch (SQLException ignored) { } throw e; }
        finally { try { cn.setAutoCommit(auto); } catch (SQLException ignored) { } }
    }
    public synchronized List<TopRow> top(int limit) throws SQLException {
        List<TopRow> l = new ArrayList<>();
        try (PreparedStatement ps = c().prepareStatement("SELECT uuid,name,score FROM astrax_profiles ORDER BY score DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) l.add(new TopRow(rs.getString(1), rs.getString(2), rs.getLong(3))); }
        }
        return l;
    }
    @Override public synchronized void close() { try { if (conn != null) conn.close(); } catch (SQLException e) { log.warning("Đóng DB: " + e.getMessage()); } }
}
