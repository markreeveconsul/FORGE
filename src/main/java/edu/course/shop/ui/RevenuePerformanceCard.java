package edu.course.shop.ui;

import edu.course.shop.model.Money;
import edu.course.shop.persistence.AdminRepository.OrderRow;
import edu.course.shop.service.RevenueAnalytics;
import edu.course.shop.service.RevenueAnalytics.*;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/** Interactive chart with exact source values, keyboard inspection and a local order drilldown. */
public final class RevenuePerformanceCard extends VBox {
    private static final DateTimeFormatter SHORT=DateTimeFormatter.ofPattern("MMM d",Locale.US);
    private static final DateTimeFormatter FULL=DateTimeFormatter.ofPattern("EEE, MMM d, yyyy",Locale.US);
    private final Clock clock;
    private final Supplier<List<OrderRow>> loader;
    private List<OrderRow> rows=List.of();
    private RevenueAnalytics.Report report;
    private LocalDate start,end;
    private Metric metric=Metric.PAID_REVENUE;
    private int hovered=-1,pinned=-1;
    private final NumberAxis dates=new NumberAxis(),amounts=new NumberAxis();
    private final AreaChart<Number,Number> chart=new AreaChart<>(dates,amounts);
    private final RevenueDraw drawing=new RevenueDraw(chart);
    private final Label total=Ui.label("—","revenue-total"),change=Ui.label("","revenue-change"),orderCount=Ui.label("—","revenue-stat-value"),average=Ui.label("—","revenue-stat-value");
    private final Label periodText=Ui.label("","revenue-period-text"),note=Ui.label("Loading order values…","revenue-note"),updated=Ui.label("","revenue-updated"),error=Ui.feedback("revenue-error");
    private final Label totalLabel=Ui.label("PAID REVENUE","eyebrow"),countLabel=Ui.label("PAID ORDERS","eyebrow");
    private final CheckBox compare=new CheckBox("Compare previous period");
    private final DatePicker from=new DatePicker(),to=new DatePicker();
    private final ToggleGroup presetGroup=new ToggleGroup();
    private final Button refresh,forward;
    private final VBox details=new VBox(12);
    private final Pane overlay=new Pane();
    private final Line guide=new Line();
    private final Circle dot=new Circle(4.5);
    private final Label hoverDate=Ui.label("","revenue-hover-date"),hoverAmount=Ui.label("","revenue-hover-amount"),hoverOrders=Ui.label("","revenue-hover-orders"),hoverPrevious=Ui.label("","revenue-hover-orders");
    private final VBox hoverCard=new VBox(5,hoverDate,hoverAmount,hoverOrders,hoverPrevious);
    private Task<List<OrderRow>> pending;

    public RevenuePerformanceCard(Supplier<List<OrderRow>> loader){this(loader,Clock.systemDefaultZone());}
    public RevenuePerformanceCard(Supplier<List<OrderRow>> loader,Clock clock){
        this.loader=Objects.requireNonNull(loader);this.clock=Objects.requireNonNull(clock);
        end=LocalDate.now(clock);start=end.minusDays(6);setId("revenue-performance-card");getStyleClass().addAll("card","revenue-card");setSpacing(12);setMinWidth(0);
        refresh=Ui.button("↻ Refresh",this::reload);refresh.setId("revenue-refresh");refresh.getStyleClass().add("revenue-quiet-button");
        VBox heading=new VBox(5,Ui.label("Revenue Performance","section-title"),Ui.label("Understand your sales, one day at a time.","muted"));
        HBox header=new HBox(12,heading,spacer(),refresh);header.setAlignment(Pos.CENTER_LEFT);
        ToggleButton paid=new ToggleButton("Paid revenue"),all=new ToggleButton("All order value");paid.setId("revenue-metric-paid");all.setId("revenue-metric-all");
        ToggleGroup metrics=new ToggleGroup();paid.setToggleGroup(metrics);all.setToggleGroup(metrics);paid.setSelected(true);
        paid.getStyleClass().add("workspace-period");all.getStyleClass().add("workspace-period");
        paid.setOnAction(e->{paid.setSelected(true);metric=Metric.PAID_REVENUE;render();});all.setOnAction(e->{all.setSelected(true);metric=Metric.ORDER_VALUE;render();});
        HBox metricSwitch=new HBox(3,paid,all);metricSwitch.getStyleClass().add("workspace-range");metricSwitch.setAlignment(Pos.CENTER);
        FlowPane controls=new FlowPane(12,10);controls.setAlignment(Pos.CENTER_LEFT);controls.getChildren().add(metricSwitch);
        HBox ranges=new HBox(3);ranges.getStyleClass().add("workspace-range");
        for(int days:new int[]{7,30,90}){
            ToggleButton button=new ToggleButton(days+" days");button.setId(days==7?"admin-chart-weekly":days==30?"admin-chart-monthly":"admin-chart-quarterly");button.getStyleClass().add("workspace-period");button.setToggleGroup(presetGroup);button.setUserData(days);button.setSelected(days==7);
            button.setOnAction(e->{button.setSelected(true);end=LocalDate.now(clock);start=end.minusDays(days-1);syncDates();render();});ranges.getChildren().add(button);
        }
        compare.setId("revenue-compare");compare.getStyleClass().add("revenue-compare");compare.setOnAction(e->render());controls.getChildren().addAll(ranges,compare);
        from.setId("revenue-date-from");to.setId("revenue-date-to");from.setAccessibleText("Revenue start date");to.setAccessibleText("Revenue end date");from.setPrefWidth(144);to.setPrefWidth(144);
        for(DatePicker picker:List.of(from,to))picker.setDayCellFactory(p->new DateCell(){public void updateItem(LocalDate date,boolean empty){super.updateItem(date,empty);setDisable(empty||date==null||date.isAfter(LocalDate.now(clock)));}});
        Button apply=Ui.button("Apply dates",this::applyDates);apply.setId("revenue-apply-dates");apply.getStyleClass().add("revenue-quiet-button");
        Button back=Ui.button("‹",()->shift(-1));back.setId("revenue-previous-range");back.setAccessibleText("Previous date range");
        forward=Ui.button("›",()->shift(1));forward.setId("revenue-next-range");forward.setAccessibleText("Next date range");
        Button today=Ui.button("Latest",()->{long days=ChronoUnit.DAYS.between(start,end);end=LocalDate.now(clock);start=end.minusDays(days);syncDates();render();});today.setId("revenue-latest");
        FlowPane dateControls=new FlowPane(8,8,back,forward,from,Ui.label("to","muted"),to,apply,today);dateControls.setAlignment(Pos.CENTER_LEFT);dateControls.setVisible(false);dateControls.managedProperty().bind(dateControls.visibleProperty());ToggleButton custom=new ToggleButton("Custom dates");custom.setId("revenue-custom-dates");custom.getStyleClass().add("workspace-period");custom.setOnAction(e->dateControls.setVisible(custom.isSelected()));controls.getChildren().add(custom);
        HBox stats=new HBox(30,new VBox(7,totalLabel,total,change),spacer(),new VBox(7,countLabel,orderCount),new VBox(7,Ui.label("AVERAGE ORDER","eyebrow"),average));stats.setAlignment(Pos.TOP_LEFT);stats.getStyleClass().add("revenue-stats");
        total.setId("revenue-total");change.setId("revenue-change");orderCount.setId("revenue-order-count");average.setId("revenue-average");periodText.setId("revenue-range-label");
        configureChart();
        overlay.setMinSize(0,0);overlay.setPrefSize(0,0);guide.setManaged(false);dot.setManaged(false);overlay.setMouseTransparent(true);overlay.setPickOnBounds(false);guide.getStyleClass().add("revenue-crosshair");dot.getStyleClass().add("revenue-hover-dot");hoverCard.getStyleClass().add("revenue-hover-card");hoverCard.setManaged(false);hoverCard.setId("revenue-hover-card");
        overlay.getChildren().addAll(guide,dot,hoverCard);hideHover();StackPane surface=new StackPane(chart,overlay);surface.setMinWidth(0);
        chart.setOnMouseMoved(e->{if(!insidePlot(e.getSceneX(),e.getSceneY())){if(pinned<0)hideHover();return;}Number value=dates.getValueForDisplay(dates.sceneToLocal(e.getSceneX(),e.getSceneY()).getX());if(value!=null)inspect((int)Math.round(value.doubleValue()));});
        chart.setOnMouseExited(e->{if(pinned<0)hideHover();else inspect(pinned);});
        chart.setOnMouseClicked(e->{if(hovered>=0&&insidePlot(e.getSceneX(),e.getSceneY())){pinned=hovered;showDetails();chart.requestFocus();}});
        chart.setOnKeyPressed(e->{if(report==null)return;if(e.getCode()==KeyCode.LEFT||e.getCode()==KeyCode.RIGHT){int next=hovered<0?report.current().days().size()-1:hovered+(e.getCode()==KeyCode.RIGHT?1:-1);inspect(next);e.consume();}else if(e.getCode()==KeyCode.ENTER&&hovered>=0){pinned=hovered;showDetails();e.consume();}else if(e.getCode()==KeyCode.ESCAPE){clearSelection();e.consume();}});
        details.setId("revenue-day-details");details.setVisible(false);details.managedProperty().bind(details.visibleProperty());
        getChildren().addAll(header,controls,dateControls,error,stats,periodText,surface,note,updated,details);syncDates();
        sceneProperty().addListener((o,a,b)->{if(b==null&&pending!=null)pending.cancel(true);});
    }
    private static Region spacer(){Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);return gap;}
    private void configureChart(){
        chart.setId("admin-revenue-chart");chart.setAnimated(false);chart.setLegendVisible(false);chart.setCreateSymbols(true);chart.setHorizontalGridLinesVisible(true);chart.setVerticalGridLinesVisible(false);chart.setAlternativeRowFillVisible(false);chart.setMinHeight(230);chart.setPrefHeight(260);chart.setMaxHeight(280);chart.setFocusTraversable(true);
        chart.setAccessibleText("Daily revenue chart. Use Left and Right to inspect dates, Enter to show orders, and Escape to clear selection.");
        dates.setAutoRanging(false);dates.setMinorTickVisible(false);dates.setTickLabelRotation(0);amounts.setForceZeroInRange(true);amounts.setMinorTickVisible(false);
        dates.setTickLabelFormatter(new StringConverter<>(){public String toString(Number n){return start.plusDays(Math.round(n.doubleValue())).format(SHORT);}public Number fromString(String s){return 0;}});
        amounts.setTickLabelFormatter(new StringConverter<>(){public String toString(Number n){double value=n.doubleValue();return "$"+(value>=1_000_000?new DecimalFormat("0.##").format(value/1_000_000)+"m":value>=1000?new DecimalFormat("0.##").format(value/1000)+"k":new DecimalFormat("#,##0.##").format(value));}public Number fromString(String s){return 0;}});
    }
    public void setOrders(List<OrderRow> orders){rows=List.copyOf(orders);if(presetGroup.getSelectedToggle()!=null){int days=(Integer)presetGroup.getSelectedToggle().getUserData();end=LocalDate.now(clock);start=end.minusDays(days-1);syncDates();}render();updated.setText("Updated "+LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm:ss"))+" · "+clock.getZone());}
    private void syncDates(){from.setValue(start);to.setValue(end);forward.setDisable(!end.isBefore(LocalDate.now(clock)));}
    private void applyDates(){
        try{from.commitValue();to.commitValue();LocalDate a=from.getValue(),b=to.getValue();RevenueAnalytics.calculate(rows,a,b,clock);start=a;end=b;presetGroup.selectToggle(null);error.setText("");syncDates();render();}
        catch(RuntimeException failure){error.setText("Choose a valid date range of 1–366 days, ending today or earlier.");}
    }
    private void shift(int direction){long days=ChronoUnit.DAYS.between(start,end)+1;LocalDate newEnd=end.plusDays(days*direction);if(newEnd.isAfter(LocalDate.now(clock)))newEnd=LocalDate.now(clock);end=newEnd;start=end.minusDays(days-1);presetGroup.selectToggle(null);syncDates();render();}
    private void render(){
        error.setText("");
        try{
            report=RevenueAnalytics.calculate(rows,start,end,clock);clearSelection();long cents=report.current().cents(metric),before=report.previous().cents(metric);total.setText(money(cents));totalLabel.setText(metric==Metric.PAID_REVENUE?"PAID REVENUE":"ALL ORDER VALUE");countLabel.setText(metric==Metric.PAID_REVENUE?"PAID ORDERS":"ALL ORDERS");orderCount.setText(""+report.current().count(metric));average.setText(currency(report.current().average(metric)));
            change.setText(report.changePercent(metric).map(p->(p.signum()>0?"+":"")+p.toPlainString()+"% vs previous period").orElse(cents==0?"No change from previous period":"New activity · previous period was $0.00"));
            change.getStyleClass().removeAll("revenue-up","revenue-down");change.getStyleClass().add(cents>=before?"revenue-up":"revenue-down");
            change.setTooltip(new Tooltip(report.previous().start()+" – "+report.previous().end()+": "+money(before)+". Calendar periods are compared; today may be incomplete."));
            int days=report.current().days().size();dates.setLowerBound(days==1?-.5:0);dates.setUpperBound(days==1?.5:days-1);dates.setTickUnit(Math.max(1,Math.ceil((days-1)/6.0)));
            XYChart.Series<Number,Number> current=new XYChart.Series<>();current.setName(metric.label());XYChart.Series<Number,Number> previous=new XYChart.Series<>();previous.setName("Previous period");
            for(int i=0;i<days;i++){Day day=report.current().days().get(i);current.getData().add(new XYChart.Data<>(i,day.cents(metric)/100.0));Day old=report.previous().days().get(i);previous.getData().add(new XYChart.Data<>(i,old.cents(metric)/100.0));}
            chart.getData().setAll(compare.isSelected()?List.of(current,previous):List.of(current));drawing.request();
            periodText.setText(start.format(FULL)+"  –  "+end.format(FULL)+(end.equals(report.today())?"  ·  Today is in progress":"")+(compare.isSelected()?"  |  Dashed: previous "+days+" days":""));
            int excluded=report.current().count(Metric.ORDER_VALUE)-report.current().count(Metric.PAID_REVENUE);
            String basis=metric==Metric.PAID_REVENUE?"Paid orders only · "+excluded+" unpaid orders excluded":"All payment statuses included · order value is not collected revenue";
            note.setText((report.current().count(metric)==0?"No "+(metric==Metric.PAID_REVENUE?"paid orders":"orders")+" in this period. ":"")+basis+". Grouped by order date in "+report.zone()+"; no payment-date history is recorded. USD · simulated payments. Hover to inspect; click a date to see its orders."+(report.invalidRecords()>0?" "+report.invalidRecords()+" invalid records omitted.":"")+(report.futureRecords()>0?" "+report.futureRecords()+" future-dated records excluded.":""));
        }catch(ArithmeticException failure){error.setText("These totals exceed the supported currency range. Choose a smaller date range.");}
    }
    private boolean insidePlot(double sceneX,double sceneY){Node plot=chart.lookup(".chart-plot-background");return plot!=null&&plot.getBoundsInLocal().contains(plot.sceneToLocal(sceneX,sceneY));}
    private void inspect(int index){
        drawing.complete();
        if(report==null)return;hovered=Math.max(0,Math.min(index,report.current().days().size()-1));Day day=report.current().days().get(hovered);Node plot=chart.lookup(".chart-plot-background");if(plot==null||chart.getScene()==null)return;
        hoverDate.setText(day.date().format(FULL));hoverAmount.setText(money(day.cents(metric)));hoverOrders.setText(day.count(metric)+" "+(metric==Metric.PAID_REVENUE?"paid":"recorded")+" orders");
        Day previous=report.previous().days().get(hovered);hoverPrevious.setText(compare.isSelected()?previous.date().format(SHORT)+": "+money(previous.cents(metric)):"Click to inspect orders");
        var bounds=overlay.sceneToLocal(plot.localToScene(plot.getBoundsInLocal()));double x=overlay.sceneToLocal(dates.localToScene(dates.getDisplayPosition(hovered),0)).getX();double y=overlay.sceneToLocal(amounts.localToScene(0,amounts.getDisplayPosition(day.cents(metric)/100.0))).getY();
        guide.setStartX(x);guide.setEndX(x);guide.setStartY(bounds.getMinY());guide.setEndY(bounds.getMaxY());dot.setCenterX(x);dot.setCenterY(y);guide.setVisible(true);dot.setVisible(true);hoverCard.setVisible(true);hoverCard.applyCss();hoverCard.autosize();
        hoverCard.relocate(Math.max(bounds.getMinX(),Math.min(x+14,bounds.getMaxX()-hoverCard.getWidth())),Math.max(bounds.getMinY()+4,Math.min(y-hoverCard.getHeight()-12,bounds.getMaxY()-hoverCard.getHeight())));
    }
    private void hideHover(){guide.setVisible(false);dot.setVisible(false);hoverCard.setVisible(false);}
    private void clearSelection(){pinned=-1;hovered=-1;hideHover();details.setVisible(false);details.getChildren().clear();}
    private void showDetails(){
        if(pinned<0)return;Day day=report.current().days().get(pinned);Label title=Ui.label(day.date().format(FULL)+" · "+money(day.cents(metric)),"section-title");Button close=Ui.button("Clear selection",this::clearSelection);close.setId("revenue-clear-selection");HBox heading=new HBox(12,title,spacer(),close);heading.setAlignment(Pos.CENTER_LEFT);
        TableView<OrderRow> table=Ui.table();table.setId("revenue-orders-table");table.getColumns().setAll(List.of(Ui.column("ORDER",o->"#MK-"+o.id()),Ui.column("CUSTOMER",OrderRow::customer),Ui.column("PAYMENT",OrderRow::paymentStatus),Ui.column("TOTAL",o->money(o.totalCents()))));table.setItems(FXCollections.observableArrayList(day.matching(metric)));table.setPlaceholder(Ui.label("No matching orders on this day.","muted"));table.setPrefHeight(Math.min(260,58+Math.max(1,table.getItems().size())*48));details.getChildren().setAll(heading,table);details.setVisible(true);inspect(pinned);
    }
    private void reload(){
        if(pending!=null&&pending.isRunning())return;refresh.setDisable(true);refresh.setText("Refreshing…");
        Task<List<OrderRow>> task=new Task<>(){protected List<OrderRow> call(){return loader.get();}};pending=task;
        task.setOnSucceeded(e->{pending=null;refresh.setDisable(false);refresh.setText("↻ Refresh");setOrders(task.getValue());});
        task.setOnFailed(e->{pending=null;refresh.setDisable(false);refresh.setText("↻ Refresh");error.setText("Orders could not be refreshed. Showing the last loaded values; try again.");});
        Thread thread=new Thread(task,"forge-revenue-refresh");thread.setDaemon(true);thread.start();
    }
    private static String money(long cents){return currency(Money.amount(cents));}
    private static String currency(BigDecimal amount){return "$"+new DecimalFormat("#,##0.00",java.text.DecimalFormatSymbols.getInstance(Locale.US)).format(amount);}
}
