package edu.course.shop.ui;

import edu.course.shop.model.Money;
import edu.course.shop.model.User;
import edu.course.shop.persistence.AdminRepository;
import edu.course.shop.service.AdminService;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Read-only overview, order and customer reports for the administrator workspace. */
public final class AdminReportsView extends VBox {
    public enum Section { OVERVIEW, ORDERS, CUSTOMERS }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        .withZone(ZoneId.systemDefault());
    private final User admin;
    private final AdminService reports;
    private final Runnable viewOrders;
    private final Runnable viewInventory;
    private final Label feedback = Ui.feedback("admin-report-feedback");

    public AdminReportsView(User admin, AdminService reports, Section section, Runnable viewOrders) {
        this(admin, reports, section, viewOrders, viewOrders);
    }

    public AdminReportsView(User admin, AdminService reports, Section section, Runnable viewOrders, Runnable viewInventory) {
        this.admin = admin;
        this.reports = reports;
        this.viewOrders = viewOrders;
        this.viewInventory = viewInventory;
        setId("admin-report-" + section.name().toLowerCase(Locale.ROOT));
        getStyleClass().add("page");
        setSpacing(16);
        switch (section) {
            case OVERVIEW -> buildOverview();
            case ORDERS -> buildOrders();
            case CUSTOMERS -> buildCustomers();
        }
    }

    private void buildOverview() {
        getChildren().addAll(Ui.label("OVERVIEW", "page-title"),
            Ui.label("A clear view of the store, inventory and recent activity.", "muted"), feedback);
        HBox stats = new HBox(14);
        stats.getStyleClass().add("admin-report-stats");
        stats.setId("admin-report-stats");
        getChildren().add(stats);
        TableView<AdminRepository.LowStockRow> lowStock = lowStockTable();
        HBox stockHeader = new HBox(Ui.label("Restock attention", "section-title"), new javafx.scene.layout.Region(), Ui.link("Manage inventory →", viewInventory));
        HBox.setHgrow(stockHeader.getChildren().get(1), Priority.ALWAYS);
        stockHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox stockCard = new VBox(12, stockHeader, lowStock);
        stockCard.getStyleClass().add("card");
        getChildren().add(stockCard);
        TableView<AdminRepository.OrderRow> recent = orderTable("admin-recent-orders");
        recent.setPrefHeight(130);
        Label title = Ui.label("Recent orders", "section-title");
        var allOrders = Ui.link("View all orders →", viewOrders);
        HBox header = new HBox(title, new javafx.scene.layout.Region(), allOrders);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox card = new VBox(14, header, recent);
        card.getStyleClass().add("card");
        getChildren().add(card);
        Ui.async(this, "Loading store overview…", () -> new OverviewData(reports.summary(admin), reports.orders(admin), reports.lowStock(admin)), data -> {
            var s = data.summary();
            stats.getChildren().setAll(
                metric("ACTIVE PRODUCTS", Integer.toString(s.activeProducts())),
                metric("LOW STOCK", Integer.toString(s.lowStockProducts())),
                metric("ORDERS", Integer.toString(s.orders())),
                metric("CUSTOMERS", Integer.toString(s.customers())),
                metric("ORDER VALUE", Money.format(Money.amount(s.recordedOrderCents()))));
            List<AdminRepository.OrderRow> latest = data.orders().stream().limit(6).toList();
            recent.setItems(FXCollections.observableArrayList(latest));
            fitTable(recent, latest.size());
            recent.setPlaceholder(Ui.label("No orders yet. New purchases will appear here.", "muted"));
            lowStock.setItems(FXCollections.observableArrayList(data.lowStock()));
            fitTable(lowStock, data.lowStock().size());
            lowStock.setPlaceholder(Ui.label("All active products have more than five units in stock.", "muted"));
        }, error -> Ui.failure(feedback, error));
    }

    private TableView<AdminRepository.LowStockRow> lowStockTable() {
        TableView<AdminRepository.LowStockRow> table = Ui.table();
        table.setId("admin-low-stock-table");
        table.getStyleClass().add("admin-report-table");
        table.setPlaceholder(Ui.label("Loading inventory alerts…", "muted"));
        table.getColumns().setAll(List.of(
            Ui.column("PRODUCT", AdminRepository.LowStockRow::name),
            Ui.column("CATEGORY", row -> row.category().replace('_', ' ')),
            Ui.column("UNITS LEFT", row -> Integer.toString(row.stock()))));
        table.setMinHeight(130);
        table.setPrefHeight(130);
        return table;
    }

    private VBox metric(String label, String value) {
        VBox tile = new VBox(7, Ui.label(label, "eyebrow"), Ui.label(value, "admin-metric-value"));
        tile.getStyleClass().add("admin-metric-tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMinWidth(100);
        return tile;
    }

    private void buildOrders() {
        getChildren().addAll(Ui.label("ORDERS", "page-title"),
            Ui.label("Review customer purchases and the payment state recorded at checkout.", "muted"), feedback);
        TableView<AdminRepository.OrderRow> table = orderTable("admin-orders-table");
        VBox card = new VBox(12, table);
        card.getStyleClass().add("card");
        getChildren().add(card);
        Ui.async(this, "Loading orders…", () -> reports.orders(admin), rows -> {
            table.setItems(FXCollections.observableArrayList(rows));
            fitTable(table, rows.size());
            table.setPlaceholder(Ui.label("No orders have been placed yet.", "muted"));
        }, error -> Ui.failure(feedback, error));
    }

    private TableView<AdminRepository.OrderRow> orderTable(String id) {
        TableView<AdminRepository.OrderRow> table = Ui.table();
        table.setId(id);
        table.getStyleClass().add("admin-report-table");
        table.setPlaceholder(Ui.label("Loading orders…", "muted"));
        table.getColumns().setAll(List.of(
            Ui.column("ORDER / ITEMS", row -> "#MK-" + row.id() + " · " + row.itemCount() + (row.itemCount() == 1 ? " item" : " items")),
            Ui.column("CUSTOMER", AdminRepository.OrderRow::customer),
            Ui.column("DATE", row -> date(row.createdAt())),
            Ui.column("PAYMENT", row -> payment(row.paymentMethod()) + " · " + paymentStatus(row.paymentStatus())),
            Ui.column("TOTAL", row -> Money.format(Money.amount(row.totalCents())))));
        table.setMinHeight(130);
        table.setPrefHeight(130);
        return table;
    }

    private void buildCustomers() {
        getChildren().addAll(Ui.label("CUSTOMERS", "page-title"),
            Ui.label("Customer accounts and their recorded purchase totals.", "muted"), feedback);
        TextField search = new TextField();
        search.setId("admin-customer-search");
        search.setPromptText("Search by name or username");
        search.setAccessibleText("Search customers by name or username");
        VBox.setVgrow(search, Priority.NEVER);
        TableView<AdminRepository.CustomerRow> table = Ui.table();
        table.setId("admin-customers-table");
        table.getStyleClass().add("admin-report-table");
        table.getColumns().setAll(List.of(
            Ui.column("CUSTOMER", AdminRepository.CustomerRow::displayName),
            Ui.column("USERNAME", row -> "@" + row.username()),
            Ui.column("ORDERS", row -> Integer.toString(row.orderCount())),
            Ui.column("ORDER VALUE", row -> Money.format(Money.amount(row.orderCents())))));
        VBox card = new VBox(14, search, table);
        card.getStyleClass().add("card");
        getChildren().add(card);
        Ui.async(this, "Loading customers…", () -> reports.customers(admin), rows -> {
            table.setItems(FXCollections.observableArrayList(rows));
            fitTable(table, rows.size());
            search.textProperty().addListener((obs, oldValue, newValue) -> {
                String term = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
                List<AdminRepository.CustomerRow> filtered = rows.stream().filter(row ->
                    row.username().toLowerCase(Locale.ROOT).contains(term)
                        || row.displayName().toLowerCase(Locale.ROOT).contains(term)).toList();
                table.setItems(FXCollections.observableArrayList(filtered));
                fitTable(table, filtered.size());
            });
            table.setPlaceholder(Ui.label("No customer accounts found.", "muted"));
        }, error -> Ui.failure(feedback, error));
    }

    private static String date(String value) {
        try { return DATE.format(Instant.parse(value)); }
        catch (RuntimeException ignored) { return value; }
    }

    private static String payment(String value) {
        if (value == null || value.isBlank()) return "—";
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("wallet")) return "Wallet";
        if (normalized.contains("cash") || normalized.contains("cod")) return "COD";
        return value.replace('_', ' ');
    }

    private static String paymentStatus(String value) {
        if ("PAID".equalsIgnoreCase(value)) return "Paid";
        if ("COD".equalsIgnoreCase(value) || "PENDING".equalsIgnoreCase(value)) return "Due";
        return value == null ? "Unknown" : value;
    }

    private static void fitTable(TableView<?> table, int rows) {
        double height = 44 + 48.0 * Math.max(1, rows);
        table.setPrefHeight(Math.min(420, height));
        table.setMinHeight(Math.min(130, height));
        table.setMaxHeight(Math.min(420, height));
    }

    private record OverviewData(AdminRepository.Summary summary, List<AdminRepository.OrderRow> orders,
                                List<AdminRepository.LowStockRow> lowStock) { }
}
