package org.example;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;

public class UserAuth {
    private static final String DB_URL = "jdbc:sqlite:users.db";

    public static void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // 1. USERS TABLE (Now with Wallet Columns)
            String sqlUser = "CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY, " +
                    "password_hash TEXT NOT NULL, " +
                    "rating REAL DEFAULT 0, " +
                    "review_count INTEGER DEFAULT 0, " +
                    "balance REAL DEFAULT 0.0, " +
                    "locked_balance REAL DEFAULT 0.0)";
            stmt.execute(sqlUser);

            // 2. LEDGER (The Audit Log - Immutable History)
            String sqlLedger = "CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "type TEXT NOT NULL, " + // DEPOSIT, WITHDRAW, HOLD, REFUND, PAYMENT
                    "amount REAL NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
            stmt.execute(sqlLedger);

        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static boolean register(String username, String password) {
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        // Give new users $1000 sign-up bonus for testing
        String sql = "INSERT INTO users(username, password_hash, rating, review_count, balance) VALUES(?, ?, ?, ?, 1000.0)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashed);
            // Random rating logic
            pstmt.setDouble(3, 3.5 + (Math.random() * 1.5));
            pstmt.setInt(4, (int) (Math.random() * 50));
            pstmt.executeUpdate();

            // Log the sign-up bonus
            logTransaction(username, "SIGNUP_BONUS", 1000.0);
            return true;
        } catch (SQLException e) { return false; }
    }

    public static boolean login(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return BCrypt.checkpw(password, rs.getString("password_hash"));
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public static String getSellerRating(String username) {
        String sql = "SELECT rating, review_count FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return String.format("⭐ %.1f (%d)", rs.getDouble("rating"), rs.getInt("review_count"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "New Seller";
    }

    // --- INTERNAL HELPER: AUDIT LOGGING ---
    // Only accessible by other classes in this package
    static void logTransaction(String username, String type, double amount) {
        String sql = "INSERT INTO transactions(username, type, amount) VALUES(?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, type);
            pstmt.setDouble(3, amount);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}