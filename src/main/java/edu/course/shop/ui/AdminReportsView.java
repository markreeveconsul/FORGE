package edu.course.shop.ui;

import edu.course.shop.model.Money;
import edu.course.shop.model.User;
import edu.course.shop.persistence.AdminRepository;
import edu.course.shop.service.AdminService;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.time.Instant;
import java.time.LocalDate;
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
    private final Runnable viewAi,addProduct;
    private final Label feedback = Ui.feedback("admin-report-feedback");

    public AdminReportsView(User admin, AdminService reports, Section section, Runnable viewOrders) {
        this(admin, reports, section, viewOrders, viewOrders);
    }

    public AdminReportsView(User admin, AdminService reports, Section section, Runnable viewOrders, Runnable viewInventory) {
        this(admin,reports,section,viewOrders,viewInventory,viewInventory,viewInventory);
    }
    public AdminReportsView(User admin,AdminService reports,Section section,Runnable viewOrders,Runnable viewInventory,Runnable viewAi,Runnable addProduct){
        this.admin = admin;
        this.reports = reports;
        this.viewOrders = viewOrders;
        this.viewInventory = viewInventory;
        this.viewAi=viewAi;this.addProduct=addProduct;
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
        Button ai=Ui.button("Run AI Diagnostics",viewAi);ai.setGraphic(StoreIcon.of("sparkles",14));ai.getStyleClass().add("workspace-orange-button");ai.setId("overview-ai");
        Button add=Ui.button("Add Product",addProduct);add.getStyleClass().add("workspace-black-button");add.setId("overview-add-product");
        VBox heading=new VBox(6,Ui.label("OVERVIEW","page-title"),Ui.label("Real-time store metrics, sales analytics, and low stock warnings.","muted"));
        HBox actions=new HBox(10,ai,add);actions.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(Ui.responsiveColumns(heading,actions,850,14),feedback);
        FlowPane stats = new FlowPane(14,14);
        stats.getStyleClass().add("admin-report-stats");
        stats.setId("admin-report-stats");
        stats.widthProperty().addListener((o,a,b)->resizeStats(stats));
        getChildren().add(stats);
        CategoryAxis dates=new CategoryAxis();NumberAxis amounts=new NumberAxis();amounts.setForceZeroInRange(true);amounts.setTickLabelFormatter(new javafx.util.StringConverter<Number>(){public String toString(Number n){return "$"+(n.doubleValue()>=1000?String.format(Locale.ROOT,"%.0fk",n.doubleValue()/1000):String.format(Locale.ROOT,"%.0f",n.doubleValue()));}public Number fromString(String s){return 0;}});
        AreaChart<String,Number> chart=new AreaChart<>(dates,amounts);chart.setId("admin-revenue-chart");chart.setAnimated(false);chart.setCreateSymbols(false);chart.setLegendVisible(false);chart.setPrefHeight(260);chart.setMinHeight(240);chart.setMaxHeight(280);
        ToggleButton weekly=new ToggleButton("Weekly"),monthly=new ToggleButton("Monthly");weekly.setId("admin-chart-weekly");monthly.setId("admin-chart-monthly");ToggleGroup period=new ToggleGroup();weekly.setToggleGroup(period);monthly.setToggleGroup(period);weekly.setSelected(true);weekly.getStyleClass().add("workspace-period");monthly.getStyleClass().add("workspace-period");
        HBox range=new HBox(3,weekly,monthly);range.getStyleClass().add("workspace-range");range.setAlignment(Pos.CENTER);Region chartGap=new Region();HBox.setHgrow(chartGap,Priority.ALWAYS);
        HBox chartHeader=new HBox(12,new VBox(5,Ui.label("Revenue Performance","section-title"),Ui.label("Paid orders · simulated payments","muted")),chartGap,range);chartHeader.setAlignment(Pos.CENTER_LEFT);
        VBox chartCard=new VBox(16,chartHeader,chart);chartCard.getStyleClass().add("card");getChildren().add(chartCard);
        TableView<AdminRepository.LowStockRow> lowStock = lowStockTable();
        HBox stockHeader = new HBox(StoreIcon.of("warning",22),new VBox(5,Ui.label("Restock Attention", "section-title"),Ui.label("Products reaching the low stock threshold","muted")), new javafx.scene.layout.Region(), Ui.link("View inventory →", viewInventory));
        HBox.setHgrow(stockHeader.getChildren().get(2), Priority.ALWAYS);
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
                metric("ACTIVE PRODUCTS", Integer.toString(s.activeProducts()),"Available in store","package"),
                metric("LOW STOCK", Integer.toString(s.lowStockProducts()),"Needs restocking","warning"),
                metric("ORDERS", Integer.toString(s.orders()),"Recorded checkouts","bag"),
                metric("CUSTOMERS", Integer.toString(s.customers()),"Registered shoppers","users"),
                metric("TOTAL VALUE", Money.format(Money.amount(s.recordedOrderCents())),"All recorded orders","dollar"));
            resizeStats(stats);renderChart(chart,data.orders(),7);
            weekly.setOnAction(e->{weekly.setSelected(true);renderChart(chart,data.orders(),7);});monthly.setOnAction(e->{monthly.setSelected(true);renderChart(chart,data.orders(),30);});
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
        var action=new TableColumn<AdminRepository.LowStockRow,String>("ACTION");action.setCellValueFactory(r->new javafx.beans.property.ReadOnlyStringWrapper("Edit stock"));action.setCellFactory(c->new TableCell<>(){final Button edit=Ui.button("Manage stock",viewInventory);{edit.getStyleClass().add("workspace-black-button");}protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setGraphic(empty?null:edit);}});table.getColumns().add(action);
        table.setMinHeight(130);
        table.setPrefHeight(130);
        return table;
    }

    private VBox metric(String label,String value,String note,String icon){
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);HBox heading=new HBox(8,Ui.label(label,"eyebrow"),gap,StoreIcon.of(icon,17));heading.setAlignment(Pos.CENTER_LEFT);
        Label number=Ui.label(value,"admin-metric-value");number.setWrapText(false);number.setStyle(label.equals("TOTAL VALUE")?"-fx-font-size: 23px;":"");
        VBox tile = new VBox(12,heading,number,Ui.label(note,"workspace-metric-note"));
        if(label.equals("LOW STOCK"))tile.getStyleClass().add("workspace-warning-metric");
        tile.getStyleClass().add("admin-metric-tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMinWidth(100);
        return tile;
    }
    private void resizeStats(FlowPane stats){double width=stats.getWidth();if(width<=0)return;int columns=width>=850?5:width>=650?3:2;double tileWidth=Math.floor((width-(columns-1)*14)/columns)-1;for(var child:stats.getChildren())((Region)child).setPrefWidth(tileWidth);}
    private void renderChart(AreaChart<String,Number> chart,List<AdminRepository.OrderRow> orders,int days){
        LocalDate today=LocalDate.now();var totals=new java.util.LinkedHashMap<LocalDate,Long>();for(int i=days-1;i>=0;i--)totals.put(today.minusDays(i),0L);
        for(var order:orders){if(!"PAID".equalsIgnoreCase(order.paymentStatus()))continue;try{LocalDate date=Instant.parse(order.createdAt()).atZone(ZoneId.systemDefault()).toLocalDate();if(totals.containsKey(date))totals.merge(date,order.totalCents(),Long::sum);}catch(RuntimeException ignored){}}
        XYChart.Series<String,Number> series=new XYChart.Series<>();totals.forEach((date,cents)->series.getData().add(new XYChart.Data<>(date.format(DateTimeFormatter.ofPattern("MMM d")),cents/100.0)));
        chart.getData().setAll(List.of(series));
    }

    private void buildOrders() {
        getChildren().addAll(Ui.label("CUSTOMER ORDERS", "page-title"),
            Ui.label("Review checkouts, payment states, and order details.", "muted"), feedback);
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
        @SuppressWarnings("unchecked") TableColumn<AdminRepository.OrderRow,String> paymentColumn=(TableColumn<AdminRepository.OrderRow,String>)table.getColumns().get(3);
        AdminWorkspace.badges(paymentColumn,value->value.endsWith("Paid")?"workspace-pill-active":"workspace-pill-muted");
        table.setMinHeight(130);
        table.setPrefHeight(130);
        table.setRowFactory(t->{var row=new javafx.scene.control.TableRow<AdminRepository.OrderRow>();row.setOnMouseClicked(e->{if(!row.isEmpty()&&e.getClickCount()==2){var order=row.getItem();AdminWorkspace.inspect(this,"ORDER","#MK-"+order.id(),List.of(AdminWorkspace.detail("CUSTOMER",order.customer()),AdminWorkspace.detail("DATE",date(order.createdAt())),AdminWorkspace.detail("PAYMENT",payment(order.paymentMethod())+" · "+paymentStatus(order.paymentStatus())),AdminWorkspace.detail("TOTAL",Money.format(Money.amount(order.totalCents()))),AdminWorkspace.detail("ITEMS",Integer.toString(order.itemCount()))),null);}});return row;});
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
