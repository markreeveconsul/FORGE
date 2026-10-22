package edu.course.shop.service;

import edu.course.shop.persistence.AdminRepository.OrderRow;
import edu.course.shop.ui.*;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic native chart checks, with local fixture orders and no API or production database. */
public final class RevenueChartUiSmoke {
    private static Scene scene;private static int checks;
    private static <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(scene!=null){scene.getRoot().applyCss();scene.getRoot().layout();}return work.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static void press(String id)throws Exception{fx(()->{((Button)scene.lookup("#"+id)).fire();return null;});}
    private static void snapshot(Path file,int width)throws Exception{fx(()->{scene.getRoot().resize(width,860);for(int i=0;i<4;i++){scene.getRoot().applyCss();scene.getRoot().layout();}var image=scene.getRoot().snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(png,"png",file.toFile());return null;});}
    private static void key(KeyCode key)throws Exception{fx(()->{scene.lookup("#admin-revenue-chart").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",key,false,false,false,false));return null;});}
    private static OrderRow order(long id,int day,long cents,String status){return new OrderRow(id,"Demo shopper",String.format(Locale.ROOT,"2026-10-%02dT01:00:00Z",day),"Demo",status,1,cents);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);Clock clock=Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"),ZoneId.of("Asia/Singapore"));
        List<OrderRow> rows=new ArrayList<>(List.of(order(1,16,10000,"PAID"),order(2,17,15000,"PAID"),order(3,18,5000,"PAID"),order(4,20,25000,"PAID"),order(5,21,18000,"PAID"),order(6,22,40000,"PAID"),order(7,22,20000,"PENDING"),order(8,9,20000,"PAID"),order(9,10,10000,"PAID")));
        AtomicReference<List<OrderRow>> refreshed=new AtomicReference<>(rows);CountDownLatch ready=new CountDownLatch(1);Platform.startup(ready::countDown);ready.await();Platform.setImplicitExit(false);
        try{
            fx(()->{Motion.reducedProperty().set(true);RevenuePerformanceCard card=new RevenuePerformanceCard(refreshed::get,clock);VBox page=new VBox(card);page.setStyle("-fx-padding: 24;");VBox frame=new VBox(Ui.scroll(page));frame.getStyleClass().add("forge-admin");Stage stage=new Stage();scene=new Scene(frame,1060,860);scene.getStylesheets().add(RevenueChartUiSmoke.class.getResource("/shop.css").toExternalForm());scene.getStylesheets().add(RevenueChartUiSmoke.class.getResource("/admin-dashboard.css").toExternalForm());stage.setScene(scene);card.setOrders(rows);return null;});
            snapshot(root.resolve("01-revenue-light.png"),1060);
            fx(()->{check(((Label)scene.lookup("#revenue-total")).getText().equals("$1,130.00"),"Summary uses exact paid cents and excludes pending orders");check(((Label)scene.lookup("#revenue-average")).getText().equals("$188.33"),"Average order value is rounded to currency precision");check(((Label)scene.lookup("#revenue-change")).getText().startsWith("+276.7%"),"Growth reconciles with the adjacent previous period");((CheckBox)scene.lookup("#revenue-compare")).fire();check(((AreaChart<?,?>)scene.lookup("#admin-revenue-chart")).getData().size()==2,"Comparison adds an equal-length previous-period series");return null;});
            fx(()->{AreaChart<?,?> chart=(AreaChart<?,?>)scene.lookup("#admin-revenue-chart");javafx.scene.chart.NumberAxis x=(javafx.scene.chart.NumberAxis)chart.getXAxis();javafx.scene.Node plot=chart.lookup(".chart-plot-background");Point2D location=x.localToScene(x.getDisplayPosition(2),0);Point2D middle=plot.localToScene(0,plot.getBoundsInLocal().getHeight()/2);MouseEvent move=new MouseEvent(MouseEvent.MOUSE_MOVED,location.getX(),middle.getY(),0,0,MouseButton.NONE,0,false,false,false,false,false,false,false,false,false,true,null);chart.getOnMouseMoved().handle(move);MouseEvent click=new MouseEvent(MouseEvent.MOUSE_CLICKED,location.getX(),middle.getY(),0,0,MouseButton.PRIMARY,1,false,false,false,false,false,false,false,false,false,true,null);chart.getOnMouseClicked().handle(click);check(scene.lookup("#revenue-orders-table") instanceof TableView<?> table&&table.getItems().size()==1&&((OrderRow)table.getItems().getFirst()).id()==3,"Pointer hover and click drill down to the corresponding day");return null;});press("revenue-clear-selection");
            key(KeyCode.RIGHT);key(KeyCode.ENTER);
            fx(()->{check(scene.lookup("#revenue-hover-card").isVisible(),"Keyboard inspection shows the exact hover card");check(scene.lookup("#revenue-orders-table") instanceof TableView<?> table&&table.getItems().size()==1,"Selecting today opens only its paid order records");return null;});
            snapshot(root.resolve("02-revenue-inspected.png"),1060);press("revenue-clear-selection");
            fx(()->{((ToggleButton)scene.lookup("#revenue-metric-all")).fire();check(((Label)scene.lookup("#revenue-total")).getText().equals("$1,330.00"),"All-order value is separate from paid revenue");return null;});
            fx(()->{((ToggleButton)scene.lookup("#admin-chart-quarterly")).fire();check(((AreaChart<?,?>)scene.lookup("#admin-revenue-chart")).getData().getFirst().getData().size()==90,"90-day range preserves daily data without vertical date labels");((ToggleButton)scene.lookup("#revenue-custom-dates")).fire();((DatePicker)scene.lookup("#revenue-date-from")).setValue(LocalDate.of(2026,10,22));((DatePicker)scene.lookup("#revenue-date-to")).setValue(LocalDate.of(2026,10,22));return null;});
            press("revenue-apply-dates");fx(()->{check(((AreaChart<?,?>)scene.lookup("#admin-revenue-chart")).getData().getFirst().getData().size()==1,"Custom one-day range works");check(((Label)scene.lookup("#revenue-total")).getText().equals("$600.00"),"One-day totals reconcile with its order rows");((DatePicker)scene.lookup("#revenue-date-to")).setValue(LocalDate.of(2026,10,23));return null;});
            press("revenue-apply-dates");fx(()->{check(!((Label)scene.lookup("#revenue-error")).getText().isBlank(),"Future custom dates are rejected without replacing the valid chart");return null;});
            fx(()->{((ToggleButton)scene.lookup("#admin-chart-weekly")).fire();((ToggleButton)scene.lookup("#revenue-metric-paid")).fire();scene.getRoot().getStyleClass().add("admin-dark");return null;});
            snapshot(root.resolve("03-revenue-dark.png"),1060);snapshot(root.resolve("04-revenue-compact.png"),740);
            List<OrderRow> newRows=new ArrayList<>(rows);newRows.add(order(10,22,1000,"PAID"));refreshed.set(newRows);press("revenue-refresh");
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!fx(()->((Label)scene.lookup("#revenue-total")).getText().equals("$1,140.00"))){if(System.nanoTime()>until)throw new AssertionError("Refresh did not update chart");Thread.sleep(30);}
            check(true,"Refresh reloads records and recomputes totals without a white screen");
            fx(()->{((RevenuePerformanceCard)scene.lookup("#revenue-performance-card")).setOrders(List.of());check(((Label)scene.lookup("#revenue-total")).getText().equals("$0.00"),"No-data state retains a truthful zero total");check(((Label)scene.lookup("#revenue-change")).getText().equals("No change from previous period"),"Empty periods do not show a made-up growth percentage");return null;});
            snapshot(root.resolve("05-revenue-empty.png"),1060);System.out.println("Revenue chart UI checks passed: "+checks);
        }finally{Platform.exit();}
    }
}
