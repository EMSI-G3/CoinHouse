package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AuctionApp extends Application {

    private Stage primaryStage;
    private VBox itemListContainer;
    private VBox sidebar;
    private Label resCount;
    private Label lblBalance;
    private File selectedImageFile;
    private AuctionEngine backgroundThread;

    // --- FIX 1: Store the search query so the thread can see it ---
    private String currentSearchQuery = "";

    private final Map<String, ItemRow> rowCache = new HashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        UserAuth.initDB();
        showLoginScreen();
        stage.setTitle("eBay Clone - Real Time Auction");
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (backgroundThread != null) backgroundThread.stopEngine();
        super.stop();
    }

    // --- SCREEN 1: LOGIN ---
    private void showLoginScreen() {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");

        Label lblTitle = new Label("Sign in to your account");
        lblTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        TextField txtUser = new TextField(); txtUser.setPromptText("Username"); txtUser.setMaxWidth(300);
        PasswordField txtPass = new PasswordField(); txtPass.setPromptText("Password"); txtPass.setMaxWidth(300);
        Label lblStatus = new Label();

        Button btnLogin = new Button("Sign In");
        btnLogin.setStyle("-fx-background-color: #3665f3; -fx-text-fill: white; -fx-padding: 10 120; -fx-background-radius: 20;");
        btnLogin.setOnAction(e -> {
            if (UserAuth.login(txtUser.getText(), txtPass.getText())) showMainScreen(txtUser.getText());
            else lblStatus.setText("❌ Invalid Credentials");
        });

        Button btnRegister = new Button("Create account");
        btnRegister.setStyle("-fx-background-color: transparent; -fx-text-fill: #3665f3;");
        btnRegister.setOnAction(e -> {
            if (UserAuth.register(txtUser.getText(), txtPass.getText())) lblStatus.setText("✅ Account created! (+ $1000 Bonus)");
            else lblStatus.setText("❌ Username taken.");
        });

        layout.getChildren().addAll(lblTitle, txtUser, txtPass, btnLogin, btnRegister, lblStatus);
        primaryStage.setScene(new Scene(layout, 500, 500));
    }

    // --- SCREEN 2: MAIN DASHBOARD ---
    private void showMainScreen(String username) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // HEADER
        HBox header = new HBox(15);
        header.setPadding(new Insets(15, 30, 15, 30));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-border-color: #e5e5e5; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("ebay");
        logo.setStyle("-fx-font-size: 32px; -fx-font-weight: 900; -fx-text-fill: #e53238; font-family: 'Arial Black';");

        HBox searchBox = new HBox(); HBox.setHgrow(searchBox, Priority.ALWAYS);
        TextField txtSearch = new TextField(); txtSearch.setPromptText("Search..."); txtSearch.setPrefWidth(500);

        Button btnSearch = new Button("Search");
        btnSearch.setStyle("-fx-background-color: #3665f3; -fx-text-fill: white;");

        // --- FIX 2: Update the variable when searching ---
        btnSearch.setOnAction(e -> {
            currentSearchQuery = txtSearch.getText();
            refreshList(ItemManager.searchItems(currentSearchQuery), username);
        });

        searchBox.getChildren().addAll(txtSearch, btnSearch);

        lblBalance = new Label();
        lblBalance.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 14px;");
        updateBalanceLabel(username);

        Button btnWallet = new Button("Wallet"); btnWallet.setOnAction(e -> showWalletPopup(username));
        Button btnSell = new Button("Sell"); btnSell.setOnAction(e -> showSellScreen(username));

        Label lblUser = new Label("Hi, " + username);

        Button btnLogout = new Button("Sign out");
        btnLogout.setStyle("-fx-background-color: transparent; -fx-text-fill: #3665f3; -fx-underline: true;");
        btnLogout.setOnAction(e -> {
            if (backgroundThread != null) backgroundThread.stopEngine();
            showLoginScreen();
        });

        header.getChildren().addAll(logo, searchBox, lblBalance, btnWallet, btnSell, lblUser, btnLogout);
        root.setTop(header);

        // SIDEBAR
        sidebar = new VBox(10); sidebar.setPadding(new Insets(20)); sidebar.setPrefWidth(220); root.setLeft(sidebar);

        // CENTER LIST
        VBox centerLayout = new VBox(15); centerLayout.setPadding(new Insets(20));
        HBox filters = new HBox(10);
        resCount = new Label("Loading results..."); resCount.setStyle("-fx-font-weight: bold;");

        Button btnReset = new Button("Refresh / Show All");
        btnReset.setOnAction(e -> {
            currentSearchQuery = ""; // Clear search
            txtSearch.clear();
            refreshList(ItemManager.getAllItems(), username);
        });

        filters.getChildren().addAll(resCount, btnReset);

        itemListContainer = new VBox(0);
        ScrollPane scroll = new ScrollPane(itemListContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-border-color: transparent;");

        centerLayout.getChildren().addAll(filters, scroll);
        root.setCenter(centerLayout);

        // --- FIX 3: Background thread uses the saved query ---
        if (backgroundThread == null || !backgroundThread.isAlive()) {
            backgroundThread = new AuctionEngine(() -> {
                // Always refresh based on what the user is currently searching for
                refreshList(ItemManager.searchItems(currentSearchQuery), username);
            });
            backgroundThread.start();
        }

        refreshList(ItemManager.getAllItems(), username);
        primaryStage.setScene(new Scene(root, 1200, 800));
    }

    // --- SCREEN 3: SELL ITEM ---
    private void showSellScreen(String username) {
        VBox root = new VBox(20); root.setAlignment(Pos.CENTER); root.setPadding(new Insets(40));
        Label title = new Label("Create your listing"); title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        VBox form = new VBox(15); form.setMaxWidth(600); form.setPadding(new Insets(30)); form.setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);");

        TextField txtName = new TextField(); txtName.setPromptText("Title");
        TextField txtPrice = new TextField(); txtPrice.setPromptText("Starting Bid ($)");
        ComboBox<String> cmbCategory = new ComboBox<>(); cmbCategory.getItems().addAll(ItemManager.CATEGORIES); cmbCategory.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Integer> cmbDuration = new ComboBox<>(); cmbDuration.getItems().addAll(1, 5, 10, 60); cmbDuration.getSelectionModel().select(0); cmbDuration.setMaxWidth(Double.MAX_VALUE);

        Button btnImage = new Button("Upload Photo"); Label lblImgStatus = new Label("No file");
        btnImage.setOnAction(e -> { FileChooser fc = new FileChooser(); selectedImageFile = fc.showOpenDialog(primaryStage); if(selectedImageFile!=null) lblImgStatus.setText("OK"); });

        Button btnSubmit = new Button("List Item"); btnSubmit.setStyle("-fx-background-color: #3665f3; -fx-text-fill: white;");
        btnSubmit.setOnAction(e -> {
            if (!txtName.getText().isEmpty() && !txtPrice.getText().isEmpty() && selectedImageFile != null && cmbCategory.getValue() != null) {
                ItemManager.createAuction(txtName.getText(), Double.parseDouble(txtPrice.getText()), cmbCategory.getValue(), username, selectedImageFile.toURI().toString(), "New", cmbDuration.getValue());
                showMainScreen(username);
            }
        });
        Button btnCancel = new Button("Cancel"); btnCancel.setOnAction(e -> showMainScreen(username));
        form.getChildren().addAll(new Label("Title"), txtName, new Label("Price"), txtPrice, new Label("Category"), cmbCategory, new Label("Duration (Mins)"), cmbDuration, btnImage, lblImgStatus, btnSubmit, btnCancel);
        root.getChildren().addAll(title, form);
        primaryStage.setScene(new Scene(root, 1200, 800));
    }

    // --- SMART REFRESH LOGIC ---
    private void refreshList(List<ItemManager.Item> items, String currentUser) {
        updateBalanceLabel(currentUser);
        resCount.setText(items.size() + " results");

        // 1. Refresh Sidebar
        sidebar.getChildren().clear(); sidebar.getChildren().add(new Label("Categories"));
        sidebar.getChildren().addAll(ItemManager.CATEGORIES.stream().map(cat -> {
            Hyperlink link = new Hyperlink(cat);
            link.setOnAction(e -> {
                // Clicking sidebar clears search and filters by category
                currentSearchQuery = "";
                refreshList(ItemManager.getItemsByCategory(cat), currentUser);
            });
            return link;
        }).collect(Collectors.toList()));

        // 2. Refresh Items (SMART UPDATE)
        Map<String, ItemManager.Item> currentItemMap = new HashMap<>();
        for(ItemManager.Item item : items) {
            String key = item.name + item.owner;
            currentItemMap.put(key, item);
        }

        for (ItemManager.Item item : items) {
            String key = item.name + item.owner;
            if (rowCache.containsKey(key)) {
                rowCache.get(key).update(item, currentUser);
            } else {
                ItemRow newRow = new ItemRow(item, currentUser);
                rowCache.put(key, newRow);
                itemListContainer.getChildren().add(newRow);
            }
        }

        rowCache.keySet().removeIf(key -> {
            if (!currentItemMap.containsKey(key)) {
                itemListContainer.getChildren().remove(rowCache.get(key));
                return true;
            }
            return false;
        });
    }

    // --- ITEM ROW ---
    private class ItemRow extends HBox {
        private final Label lblTime, lblStatus, lblBid, lblTop;
        private final VBox bidBox;

        public ItemRow(ItemManager.Item item, String currentUser) {
            super(20);
            this.setPadding(new Insets(20, 0, 20, 0));
            this.setStyle("-fx-border-color: #eee; -fx-border-width: 0 0 1 0;");

            ImageView imageView = new ImageView();
            try { imageView.setImage(new Image(item.imagePath)); } catch (Exception e) {}
            imageView.setFitWidth(160); imageView.setFitHeight(160); imageView.setPreserveRatio(true);

            VBox details = new VBox(5); HBox.setHgrow(details, Priority.ALWAYS);
            Label title = new Label(item.name); title.setStyle("-fx-font-size: 18px; -fx-text-fill: #333;");
            lblTime = new Label();
            lblStatus = new Label();
            Label sellerLbl = new Label("Seller: " + item.owner); sellerLbl.setStyle("-fx-text-fill: #777;");
            details.getChildren().addAll(title, lblTime, lblStatus, sellerLbl);

            bidBox = new VBox(8); bidBox.setAlignment(Pos.TOP_RIGHT); bidBox.setMinWidth(200);
            lblBid = new Label(); lblBid.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
            lblTop = new Label(); lblTop.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

            if (!item.owner.equals(currentUser)) {
                HBox action = new HBox(5); action.setAlignment(Pos.CENTER_RIGHT);
                TextField txtBid = new TextField(); txtBid.setPromptText("Bid"); txtBid.setPrefWidth(80);
                Button btnPlace = new Button("Bid");
                btnPlace.setStyle("-fx-background-color: #3665f3; -fx-text-fill: white;");

                btnPlace.setOnAction(e -> {
                    try {
                        String res = ItemManager.placeBid(item, currentUser, Double.parseDouble(txtBid.getText()));
                        if(!res.equals("SUCCESS")) showAlert(res);
                        else txtBid.clear();
                    } catch(Exception ex) { showAlert("Invalid"); }
                });
                action.getChildren().addAll(txtBid, btnPlace);
                bidBox.getChildren().addAll(lblBid, lblTop, action);
            } else {
                Button btnDel = new Button("Delete");
                btnDel.setOnAction(e -> { ItemManager.deleteItem(item); refreshList(ItemManager.getAllItems(), currentUser); });
                bidBox.getChildren().addAll(lblBid, lblTop, new Label("(Your Item)"), btnDel);
            }

            this.getChildren().addAll(imageView, details, bidBox);
            update(item, currentUser);
        }

        public void update(ItemManager.Item item, String currentUser) {
            long timeLeft = item.endTime - System.currentTimeMillis();
            String timeStr = (timeLeft > 0) ? String.format("%d min, %d sec left", TimeUnit.MILLISECONDS.toMinutes(timeLeft), TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60) : "ENDED";

            lblTime.setText(timeStr);
            lblTime.setStyle(timeLeft > 0 ? "-fx-text-fill: #e53238; -fx-font-weight: bold;" : "-fx-text-fill: gray;");

            lblStatus.setText(item.isOpen ? "ACTIVE BIDDING" : "SOLD / EXPIRED");
            lblStatus.setStyle("-fx-background-color: " + (item.isOpen ? "#e5ffe5" : "#eee") + "; -fx-padding: 3; -fx-font-size: 10px;");

            lblBid.setText("Current Bid: $" + item.currentBid);
            lblTop.setText("High Bidder: " + (item.topBidder.equals("None") ? "-" : item.topBidder));

            bidBox.setDisable(!item.isOpen && !item.owner.equals(currentUser));
        }
    }

    private void showWalletPopup(String username) {
        Stage popup = new Stage(); VBox box = new VBox(15); box.setPadding(new Insets(20)); box.setAlignment(Pos.CENTER);
        Label current = new Label("Bal: $" + WalletManager.getBalance(username));
        TextField txt = new TextField(); Button btn = new Button("Deposit");
        btn.setOnAction(e -> { WalletManager.deposit(username, Double.parseDouble(txt.getText())); popup.close(); updateBalanceLabel(username); });
        box.getChildren().addAll(new Label("Wallet"), current, txt, btn); popup.setScene(new Scene(box, 300, 200)); popup.show();
    }
    private void updateBalanceLabel(String u) { lblBalance.setText(String.format("$%.2f", WalletManager.getBalance(u))); }
    private void showAlert(String m) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(m); a.show(); }
}