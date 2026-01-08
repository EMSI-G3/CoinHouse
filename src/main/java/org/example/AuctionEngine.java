package org.example;

import javafx.application.Platform;

public class AuctionEngine extends Thread {

    private boolean running = true;
    private final Runnable uiRefreshCallback;

    // We pass a "callback" so the engine can tell the UI to update when an item expires
    public AuctionEngine(Runnable uiRefreshCallback) {
        this.uiRefreshCallback = uiRefreshCallback;
    }

    public void stopEngine() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("✅ Auction Engine Started");

        while (running) {
            try {
                // 1. Tick every second
                Thread.sleep(1000);

                // 2. Check for expired items (Logic)
                ItemManager.checkExpirations();

                // 3. ALWAYS Update the UI (Visuals)
                // This ensures the countdown timer (59s, 58s...) updates live
                Platform.runLater(uiRefreshCallback);

            } catch (InterruptedException e) {
                System.out.println("Auction Engine Stopped");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}