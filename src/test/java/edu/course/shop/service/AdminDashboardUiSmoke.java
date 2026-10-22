package edu.course.shop.service;

import edu.course.shop.Services;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.nio.file.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Isolated end-to-end checks for the supplied dashboard design and real local actions. */
public final class AdminDashboardUiSmoke {
    private static Scene scene;private static int checks;
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>until)throw new AssertionError("Dashboard did not reach expected state");Thread.sleep(30);}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void snapshot(Path path,int width)throws Exception{fx(()->{scene.getRoot().resize(width,940);for(int i=0;i<5;i++){scene.getRoot().applyCss();scene.getRoot().layout();}var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",path.toFile());return null;});}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);DatabaseManager db=new DatabaseManager(root.resolve("dashboard-"+System.nanoTime()+".db"));db.initialize();var pr=new ProductRepository();var or=new OrderRepository();
        Services services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        User admin=services.auth().login("admin","admin123"),customer=services.auth().login("customer","customer123");
        try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        services.products().save(admin,0,"NVIDIA GEFORCE RTX 5060 Ti",Category.PC_PARTS,new BigDecimal("999.99"),3,true);
        services.products().save(admin,0,"Studio wireless headset",Category.PERIPHERALS,new BigDecimal("79.90"),14,true);
        services.products().save(admin,0,"MSI Titan 18 HX Dragon Edition",Category.LAPTOPS,new BigDecimal("4789.99"),8,true);
        Product gpu=services.products().search(admin,"NVIDIA",null).getFirst();
        try(var c=db.open();var p=c.prepareStatement("INSERT INTO orders(customer_id,created_at,address,payment_method,payment_status,total_cents) VALUES(?,?,'Demo address','Demo',?,?)")){
            for(int i=0;i<2;i++){p.setLong(1,customer.getId());p.setString(2,Instant.now().toString());p.setString(3,i==0?"PAID":"PENDING");p.setLong(4,i==0?15000:99900);p.executeUpdate();}
        }
        CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);Stage stage=new Stage();scene=new Scene(new StackPane(),1440,940);scene.getStylesheets().add(AdminDashboardUiSmoke.class.getResource("/shop.css").toExternalForm());stage.setScene(scene);new ShopWindow(stage,services).showLogin();scene.getRoot().applyCss();scene.getRoot().layout();((TextField)scene.lookup("#username")).setText("admin");((PasswordField)scene.lookup("#password")).setText("admin123");((Button)scene.lookup("#login")).fire();return null;});
            waitFor(()->scene.lookup("#admin-revenue-chart") instanceof AreaChart<?,?> chart&&!chart.getData().isEmpty());
            fx(()->{check(scene.lookup("#admin-report-overview")!=null,"Admin signs in to Overview");check(Color.WHITE.equals(((Label)scene.lookup("#admin-forge-logo")).getTextFill()),"Every FORGE logo letter is white");check(scene.lookup("#admin-nav-overview").getStyleClass().contains("admin-nav-active"),"Overview is selected in the charcoal sidebar");AreaChart<?,?> chart=(AreaChart<?,?>)scene.lookup("#admin-revenue-chart");check(chart.getData().getFirst().getData().size()==7,"Weekly chart has seven real daily buckets");double total=chart.getData().getFirst().getData().stream().mapToDouble(d->((Number)d.getYValue()).doubleValue()).sum();check(total==150,"Revenue chart excludes unpaid orders");return null;});
            snapshot(root.resolve("01-overview-light.png"),1440);
            fx(()->{var cards=((javafx.scene.layout.FlowPane)scene.lookup("#admin-report-stats")).getChildren();double y=cards.getFirst().getBoundsInParent().getMinY();check(cards.size()==5&&cards.stream().allMatch(n->Math.abs(n.getBoundsInParent().getMinY()-y)<1),"All five metric cards share one row on desktop");return null;});
            fx(()->{((ToggleButton)scene.lookup("#admin-chart-monthly")).fire();check(((AreaChart<?,?>)scene.lookup("#admin-revenue-chart")).getData().getFirst().getData().size()==30,"Monthly control changes the real chart range");return null;});
            press("admin-theme-toggle");snapshot(root.resolve("02-overview-dark.png"),1440);
            fx(()->{check(scene.lookup("#admin-workspace").getStyleClass().contains("admin-dark"),"Dark Obsidian switches the whole admin workspace");return null;});
            snapshot(root.resolve("03-overview-compact.png"),960);press("admin-theme-toggle");
            press("nav-inventory");waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> table&&table.getItems().size()==3);
            snapshot(root.resolve("04-inventory.png"),1440);
            fx(()->{TextField stock=scene.getRoot().lookupAll(".workspace-inline-field").stream().filter(n->n instanceof TextField f&&"Stock quantity".equals(f.getAccessibleText())&&"3".equals(f.getText())).map(n->(TextField)n).findFirst().orElseThrow();stock.setText("7");stock.fireEvent(new javafx.event.ActionEvent());return null;});
            waitFor(()->services.products().get(gpu.getId()).getStock()==7);
            check(services.products().get(gpu.getId()).getPrice().equals(new BigDecimal("999.99")),"Inline stock edit saves without changing price");
            waitFor(()->scene.lookup("#admin-table") instanceof TableView<?> table&&table.getItems().stream().anyMatch(p->p instanceof Product item&&item.getId()==gpu.getId()&&item.getStock()==7));
            fx(()->{CheckBox checkbox=scene.getRoot().lookupAll(".check-box").stream().filter(n->n instanceof CheckBox c&&c.getAccessibleText()!=null&&c.getAccessibleText().startsWith("Select NVIDIA")).map(n->(CheckBox)n).findFirst().orElseThrow();checkbox.fire();check(scene.lookup("#admin-bulk-actions").isVisible(),"Selecting inventory reveals the bulk toolbar");return null;});
            snapshot(root.resolve("05-inventory-bulk.png"),1440);
            press("new-product");waitFor(()->scene.lookup("#admin-product-modal")!=null);
            fx(()->{check(scene.lookup("#product-name")!=null,"Add Product opens the existing editor in the styled modal");return null;});snapshot(root.resolve("09-add-product.png"),1440);
            fx(()->{scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&"Close product editor".equals(b.getAccessibleText())).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});
            fx(()->{check(scene.lookup("#admin-product-modal")==null&&scene.lookup("#admin-table").isVisible(),"Closing the editor restores inventory without saving");return null;});
            fx(()->{TableRow<?> row=scene.getRoot().lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> r&&r.getItem() instanceof Product p&&p.getId()==gpu.getId()).map(n->(TableRow<?>)n).findFirst().orElseThrow();row.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,0,0,0,0,javafx.scene.input.MouseButton.PRIMARY,2,false,false,false,false,false,false,false,false,false,true,null));check(scene.lookup("#admin-modal")!=null,"Double-clicking inventory opens the product inspection drawer");return null;});
            snapshot(root.resolve("10-product-drawer.png"),1440);
            fx(()->{scene.lookup("#admin-modal").lookupAll(".button").stream().filter(n->n instanceof Button b&&"Close".equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});
            press("admin-command-menu");fx(()->{((TextField)scene.lookup("#admin-command-search")).setText("Customer Orders");return null;});
            fx(()->{Button command=scene.getRoot().lookupAll(".workspace-command-item").stream().filter(n->n instanceof Button).map(n->(Button)n).findFirst().orElseThrow();command.fire();return null;});
            waitFor(()->scene.lookup("#admin-orders-table") instanceof TableView<?> t&&t.getItems().size()==2);
            fx(()->{check(scene.lookup("#admin-modal")==null,"Command palette navigates and closes");return null;});snapshot(root.resolve("06-orders.png"),1440);
            press("admin-nav-customers");waitFor(()->scene.lookup("#admin-customers-table") instanceof TableView<?> t&&!t.getItems().isEmpty());snapshot(root.resolve("07-customers.png"),1440);
            press("admin-nav-ai-manager");waitFor(()->scene.lookup("#ai-manager-chip-fix")!=null);snapshot(root.resolve("08-ai-manager.png"),1440);
            fx(()->{check(scene.lookup("#ai-manager-activity")!=null,"AI Manager includes the styled activity terminal");check(scene.lookup("#admin-page")==null&&scene.lookup("#admin-report-customers")==null,"Only one management module is visible");((AiManagerView)scene.lookup("#ai-manager-page")).dispose();return null;});
            System.out.println("Admin dashboard checks passed: "+checks);
        }finally{Platform.exit();}
    }
}
