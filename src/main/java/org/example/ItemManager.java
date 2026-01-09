package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ItemManager {
    private static final String FILE_PATH = "items.txt";

    public static final List<String> CATEGORIES = Arrays.asList(
            "Electronics", "Fashion", "Home & Garden", "Sports",
            "Collectibles", "Motors", "Toys & Hobbies", "Business & Industrial"
    );

    public static List<Item> activeItems = new ArrayList<>();

    public static class Item {
        public String name, category, owner, imagePath, condition;
        public double startingPrice, currentBid;
        public String topBidder;
        public long endTime;
        public boolean isOpen;

        // 1. SEMAPHORE: Controls access to this specific item.
        // limit 1 = Only 1 thread can modify this item at a time.
        public final transient Semaphore bidPermit = new Semaphore(1);

        public Item(String name, double startingPrice, String category, String owner, String imagePath, String condition,
                    double currentBid, String topBidder, long endTime, boolean isOpen) {
            this.name = name; this.startingPrice = startingPrice; this.category = category;
            this.owner = owner; this.imagePath = imagePath; this.condition = condition;
            this.currentBid = currentBid; this.topBidder = topBidder; this.endTime = endTime; this.isOpen = isOpen;
        }
    }

    static { loadItemsFromDisk(); }

    private static void loadItemsFromDisk() {
        if (!Files.exists(Paths.get(FILE_PATH))) return;
        try (Stream<String> lines = Files.lines(Paths.get(FILE_PATH))) {
            activeItems = lines.map(line -> line.split(";"))
                    .filter(p -> p.length == 10)
                    .map(p -> new Item(p[0], Double.parseDouble(p[1]), p[2], p[3], p[4], p[5],
                            Double.parseDouble(p[6]), p[7], Long.parseLong(p[8]), Boolean.parseBoolean(p[9])))
                    .collect(Collectors.toList());
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void createAuction(String name, double startPrice, String category, String owner, String image, String condition, int durationMinutes) {
        long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
        Item newItem = new Item(name, startPrice, category, owner, image, condition, startPrice, "None", endTime, true);
        activeItems.add(newItem);
        saveAllItems();
    }

    // --- SAFE BIDDING WITH SEMAPHORE ---
    public static String placeBid(Item item, String bidder, double bidAmount) {
        try {
            // 2. ACQUIRE: If another user is currently bidding on this item,
            // this thread will WAIT here patiently until the other finishes.
            item.bidPermit.acquire();

            // --- CRITICAL SECTION START ---
            if (!item.isOpen) return "Auction has ended!";
            if (bidAmount <= item.currentBid) return "Bid too low! Current is $" + item.currentBid;
            if (bidder.equals(item.owner)) return "You cannot bid on your own item!";

            if (WalletManager.getBalance(bidder) < bidAmount) return "Insufficient funds!";

            // Refund logic
            if (!item.topBidder.equals("None")) {
                WalletManager.releaseFunds(item.topBidder, item.currentBid);
            }

            // Lock Funds & Update
            if (WalletManager.holdFunds(bidder, bidAmount)) {
                item.currentBid = bidAmount;
                item.topBidder = bidder;

                // Anti-Sniping
                long timeLeft = item.endTime - System.currentTimeMillis();
                if (timeLeft < 60000) item.endTime += 60000;

                saveAllItems();
                return "SUCCESS";
            } else {
                return "Wallet Transaction Failed!";
            }
            // --- CRITICAL SECTION END ---

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "System Interrupted";
        } finally {
            // 3. RELEASE: Always release the permit, even if an error occurs.
            item.bidPermit.release();
        }
    }

    // --- BACKGROUND THREAD CHECK ---
    public static boolean checkExpirations() {
        boolean changed = false;
        long now = System.currentTimeMillis();

        for (Item item : activeItems) {
            if (item.isOpen && now > item.endTime) {
                // 4. TRY ACQUIRE: If a user is bidding RIGHT NOW, skip this check for 1 second.
                // We don't want the background thread to block the UI.
                if (item.bidPermit.tryAcquire()) {
                    try {
                        if (item.isOpen) {
                            item.isOpen = false;
                            changed = true;
                            if (!item.topBidder.equals("None")) {
                                WalletManager.transferLockedFundsToSeller(item.topBidder, item.owner, item.currentBid);
                            }
                        }
                    } finally {
                        item.bidPermit.release();
                    }
                }
            }
        }
        if (changed) saveAllItems();
        return changed;
    }

    // 5. SYNCHRONIZED IO: Prevents multiple threads from writing to the file at once
    // This is "Coarse-Grained Synchronization"
    public static synchronized void saveAllItems() {
        List<String> lines = activeItems.stream()
                .map(i -> String.format("%s;%.2f;%s;%s;%s;%s;%.2f;%s;%d;%b",
                        i.name, i.startingPrice, i.category, i.owner, i.imagePath, i.condition,
                        i.currentBid, i.topBidder, i.endTime, i.isOpen))
                .collect(Collectors.toList());

        try {
            Files.write(Paths.get(FILE_PATH), lines);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Utilities
    //pushing
    public static void deleteItem(Item item) { activeItems.removeIf(i -> i == item); saveAllItems(); }
    public static List<Item> getAllItems() { return new ArrayList<>(activeItems); }
    public static List<Item> searchItems(String q) { return activeItems.stream().filter(i -> i.name.toLowerCase().contains(q.toLowerCase())).collect(Collectors.toList()); }
    public static List<Item> getItemsByCategory(String c) { return activeItems.stream().filter(i -> i.category.equalsIgnoreCase(c)).collect(Collectors.toList()); }
}