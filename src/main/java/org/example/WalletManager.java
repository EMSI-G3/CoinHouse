package org.example;

import java.sql.*;

public class WalletManager {
    private static final String DB_URL = "jdbc:sqlite:users.db";

    public static double getBalance(String username) {
        String sql = "SELECT balance FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public static double getLockedBalance(String username) {
        String sql = "SELECT locked_balance FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("locked_balance");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public static void deposit(String username, double amount) {
        if (amount <= 0) return;
        updateBalance(username, amount);
        UserAuth.logTransaction(username, "DEPOSIT", amount);
    }

    public static boolean holdFunds(String username, double amount) {
        if (getBalance(username) < amount) return false;
        String sql = "UPDATE users SET balance = balance - ?, locked_balance = locked_balance + ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
            UserAuth.logTransaction(username, "HOLD_BID", -amount);
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public static void releaseFunds(String username, double amount) {
        String sql = "UPDATE users SET balance = balance + ?, locked_balance = locked_balance - ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
            UserAuth.logTransaction(username, "REFUND_OUTBID", amount);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void transferLockedFundsToSeller(String buyer, String seller, double amount) {
        String sqlBuyer = "UPDATE users SET locked_balance = locked_balance - ? WHERE username = ?";
        String sqlSeller = "UPDATE users SET balance = balance + ? WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false); // ATOMIC TRANSACTION
            try (PreparedStatement p1 = conn.prepareStatement(sqlBuyer);
                 PreparedStatement p2 = conn.prepareStatement(sqlSeller)) {

                p1.setDouble(1, amount); p1.setString(2, buyer); p1.executeUpdate();
                p2.setDouble(1, amount); p2.setString(2, seller); p2.executeUpdate();

                conn.commit();
                UserAuth.logTransaction(buyer, "PAYMENT_SENT", -amount);
                UserAuth.logTransaction(seller, "PAYMENT_RECEIVED", amount);
            } catch (SQLException e) { conn.rollback(); e.printStackTrace(); }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private static void updateBalance(String username, double amount) {
        String sql = "UPDATE users SET balance = balance + ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}