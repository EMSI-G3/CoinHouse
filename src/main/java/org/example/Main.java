package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class Main {
    // This string tells the driver to create a file named 'users.db' in your project folder
    private static final String DB_URL = "jdbc:sqlite:users.db";

    public static void main(String[] args) {
        // 1. Initialize the database and create the table if it doesn't exist
        initializeDatabase();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- WELCOME ---");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("Enter new username: ");
                    String regUser = scanner.nextLine();
                    System.out.print("Enter new password: ");
                    String regPass = scanner.nextLine();
                    register(regUser, regPass);
                    break;
                case "2":
                    System.out.print("Username: ");
                    String logUser = scanner.nextLine();
                    System.out.print("Password: ");
                    String logPass = scanner.nextLine();
                    login(logUser, logPass);
                    break;
                case "3":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // --- DATABASE METHODS ---

    private static void initializeDatabase() {
        // SQL to create a table. UNIQUE ensures no duplicate usernames.
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL UNIQUE, " +
                "password TEXT NOT NULL)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("Database connected and checked.");

        } catch (SQLException e) {
            System.out.println("Error connecting to database: " + e.getMessage());
        }
    }

    private static void register(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();

            System.out.println("✅ User registered successfully!");

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("❌ Error: That username is already taken.");
            } else {
                System.out.println("❌ Error registering: " + e.getMessage());
            }
        }
    }

    private static void login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                System.out.println("✅ Login Successful! Welcome, " + username);
            } else {
                System.out.println("❌ Invalid username or password.");
            }

        } catch (SQLException e) {
            System.out.println("❌ Database error: " + e.getMessage());
        }
    }
}