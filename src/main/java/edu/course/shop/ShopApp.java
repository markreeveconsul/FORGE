package edu.course.shop;
import edu.course.shop.persistence.*;
import edu.course.shop.service.*;
import edu.course.shop.ui.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.nio.file.Path;

public final class ShopApp extends Application {
    @Override public void start(Stage stage) {
        DatabaseManager database = new DatabaseManager(Path.of(System.getProperty("shop.db", "data/shop.db")));
        ProductRepository products = new ProductRepository();
        OrderRepository orders = new OrderRepository();
        Services services = new Services(new AuthService(database, new UserRepository()),
            new ProductService(database, products), new CheckoutService(database, products, orders),
            new OrderService(database, orders), new AdminService(database, new AdminRepository()));
        stage.setTitle("FORGE / Java OOP Store");
        // Visual bounds exclude the taskbar and use JavaFX's display-scaled coordinates.
        var desktop = javafx.stage.Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.min(1280, desktop.getWidth() * .90);
        double windowHeight = Math.min(860, desktop.getHeight() * .90);
        stage.setMinWidth(Math.min(960, windowWidth));
        stage.setMinHeight(Math.min(680, windowHeight));
        stage.setResizable(true);
        stage.setFullScreen(false);
        stage.setMaximized(false);
        var loading = new javafx.scene.layout.StackPane(new javafx.scene.control.Label("Preparing your store..."));
        Scene scene = new Scene(loading);
        scene.getStylesheets().add(getClass().getResource("/shop.css").toExternalForm());
        stage.setScene(scene);
        // Size the decorated window, rather than adding window chrome to a fixed scene size.
        stage.setWidth(windowWidth);
        stage.setHeight(windowHeight);
        stage.setX(desktop.getMinX() + (desktop.getWidth() - windowWidth) / 2);
        stage.setY(desktop.getMinY() + (desktop.getHeight() - windowHeight) / 2);
        stage.show();
        Ui.async(loading, () -> { database.initialize(); return true; },
            ready -> new ShopWindow(stage, services).showStorefront());
    }
    public static void main(String[] args) { launch(args); }
}
