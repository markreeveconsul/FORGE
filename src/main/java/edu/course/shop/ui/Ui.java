package edu.course.shop.ui;

import edu.course.shop.exception.StorageException;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.Category;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public final class Ui {
    static {
        // Bundled under the SIL Open Font License; no network or installed font required.
        try (var font = Ui.class.getResourceAsStream("/fonts/ArchivoBlack-Regular.ttf")) {
            if (font != null) javafx.scene.text.Font.loadFont(font, 14);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
    private Ui() {
    }

    public static void main(String[] args) {
    }

    public static Label label(String text, String style) {
        Label label = new Label(text);
        // An empty or null style is allowed (JavaFX throws if an empty class is added).
        if (style != null && !style.isBlank()) {
            label.getStyleClass().add(style);
        }
        label.setWrapText(true);
        return label;
    }

    public static String category(Category category) {
        return category.label();
    }

    public static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> attempt(action));
        Motion.press(button);
        return button;
    }

    public static Button primary(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("primary");
        return button;
    }

    /** Quiet text-only button for secondary actions such as "View product". */
    public static Button link(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("link-button");
        return button;
    }

    /** Clips any node (e.g. a product photo) to rounded corners; arc is the corner diameter in pixels. */
    public static void round(Node node, double arc) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        node.layoutBoundsProperty().addListener((o, a, b) -> {
            clip.setWidth(b.getWidth());
            clip.setHeight(b.getHeight());
        });
        node.setClip(clip);
    }

    /** Reveal validation feedback inside the nearest scrolling form without discarding the draft. */
    public static void focus(Node target) {
        target.requestFocus();javafx.application.Platform.runLater(()->{
            if(target.getScene()==null)return;
            for(Node ancestor=target.getParent();ancestor!=null;ancestor=ancestor.getParent())if(ancestor instanceof ScrollPane scroll){
                scroll.applyCss();scroll.layout();Node content=scroll.getContent();
                double range=content.getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
                if(range>0){double y=target.localToScene(target.getBoundsInLocal()).getMinY()-content.localToScene(content.getBoundsInLocal()).getMinY();scroll.setVvalue(Math.max(0,Math.min(1,(y-24)/range)));}break;
            }
        });
    }
    /** Shows or clears the red "invalid" border on a form control. */
    public static void flag(Control control, boolean invalid) {
        control.getStyleClass().remove("invalid");
        if (invalid) {
            control.getStyleClass().add("invalid");
        }
    }

    /** One line with text on the left and a value pinned to the right (receipts, totals). */
    public static HBox row(String left, String right, String leftStyle, String rightStyle) {
        Label name = label(left, leftStyle);
        Label value = label(right, rightStyle);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        value.setWrapText(false);
        HBox row = new HBox(12, name, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public static VBox page(String title, String subtitle, Node... content) {
        VBox page = new VBox(18, label(title, "page-title"), label(subtitle, "muted"));
        page.getStyleClass().add("page");
        page.getChildren().addAll(content);
        return page;
    }

    public static Label feedback(String id) {
        Label label = label("", "feedback");
        label.setId(id);
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.managedProperty());
        return label;
    }

    public static VBox field(String title, Control input, Label error) {
        Label label = label(title, "field-label");
        label.setLabelFor(input);
        input.setAccessibleText(title);
        error.getStyleClass().add("field-error");
        return new VBox(6, label, input, error);
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(0);
        scroll.setMinWidth(0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("clean-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    /** Reparent only at a breakpoint, preserving keyboard focus in both layouts. */
    public static VBox responsiveColumns(javafx.scene.layout.Region first,javafx.scene.layout.Region second,double breakpoint,double gap) {
        VBox root=new VBox(gap);root.setMinWidth(0);HBox wide=new HBox(gap);wide.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(first,Priority.ALWAYS);HBox.setHgrow(second,Priority.ALWAYS);
        wide.getChildren().addAll(first,second);root.getChildren().add(wide);boolean[] stacked={false};
        root.widthProperty().addListener((o,a,b)->{
            boolean compact=b.doubleValue()<breakpoint;if(stacked[0]==compact)return;stacked[0]=compact;
            Node focus=root.getScene()==null?null:root.getScene().getFocusOwner();boolean inside=false;
            for(Node node=focus;node!=null;node=node.getParent())if(node==root){inside=true;break;}
            wide.getChildren().clear();root.getChildren().clear();
            first.setMaxWidth(Double.MAX_VALUE);second.setMaxWidth(Double.MAX_VALUE);
            if(compact)root.getChildren().addAll(first,second);else{wide.getChildren().addAll(first,second);root.getChildren().add(wide);}
            if(inside&&focus!=null)focus.requestFocus();
        });return root;
    }

    public static VBox skeleton() {
        VBox placeholder=new VBox(16);placeholder.setId("loading-placeholder");placeholder.setAccessibleText("Loading products");
        placeholder.getChildren().addAll(skeletonProductGrid(3),label("Finding your next favorite…","muted"));return placeholder;
    }

    public static TilePane skeletonProductGrid(int count) {
        TilePane cards=new TilePane(20,24);cards.setId("loading-placeholder");cards.setAccessibleText("Loading product cards");cards.setPrefColumns(3);cards.setPrefTileWidth(240);cards.setMinWidth(0);
        for(int i=0;i<count;i++)cards.getChildren().add(skeletonProductCard());
        cards.widthProperty().addListener((o,a,b)->cards.setPrefColumns(Math.max(1,Math.min(5,(int)((b.doubleValue()+20)/260)))));
        Motion.loading(cards);return cards;
    }

    public static VBox skeletonRows(int count) {
        VBox rows=new VBox(12);rows.setId("loading-placeholder");rows.setAccessibleText("Loading records");
        for(int i=0;i<count;i++) {
            javafx.scene.layout.Region heading=skeletonBlock(220,18),detail=skeletonBlock(300,12),line=skeletonBlock(120,12);
            VBox row=new VBox(12,heading,detail,line);row.setMinHeight(105);row.setMaxWidth(Double.MAX_VALUE);row.getStyleClass().add("skeleton-row");rows.getChildren().add(row);
        }
        Motion.loading(rows);return rows;
    }

    public static VBox skeletonProductDetail() {
        VBox content=new VBox(24);content.setId("loading-placeholder");content.setAccessibleText("Loading product details");content.getStyleClass().add("page");
        javafx.scene.layout.Region heading=skeletonBlock(430,34);
        javafx.scene.layout.Region image=skeletonBlock(520,390);image.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.Region line1=skeletonBlock(360,22),line2=skeletonBlock(260,18),line3=skeletonBlock(300,18),action=skeletonBlock(200,48);
        VBox detail=new VBox(18,line1,line2,line3,action);detail.setMinWidth(0);
        content.getChildren().addAll(heading,responsiveColumns(image,detail,950,28));Motion.loading(content);return content;
    }

    private static VBox skeletonProductCard() {
        VBox card=new VBox(12);card.setMinWidth(0);card.setMaxWidth(Double.MAX_VALUE);card.getStyleClass().add("skeleton-card");
        javafx.scene.layout.Region photo=skeletonBlock(220,190),title=skeletonBlock(180,16),subtitle=skeletonBlock(130,12),price=skeletonBlock(110,20),action=skeletonBlock(150,38);
        photo.setMaxWidth(Double.MAX_VALUE);card.getChildren().addAll(photo,title,subtitle,price,action);return card;
    }

    private static javafx.scene.layout.Region skeletonBlock(double width,double height) {
        javafx.scene.layout.Region block=new javafx.scene.layout.Region();block.setPrefSize(width,height);block.setMinHeight(height);block.getStyleClass().add("skeleton-block");return block;
    }

    /** Stack purchase columns in compact windows, sharing one outer scroll surface. */
    public static VBox purchaseColumns(javafx.scene.layout.Region form,javafx.scene.layout.Region summary) {
        VBox content=new VBox(24);content.setMinWidth(0);
        HBox columns=new HBox(24);columns.setAlignment(Pos.TOP_LEFT);HBox.setHgrow(form,Priority.ALWAYS);
        boolean[] stacked={false};columns.getChildren().addAll(form,summary);content.getChildren().add(columns);
        content.widthProperty().addListener((o,a,b)->{
            boolean compact=b.doubleValue()<920;if(stacked[0]==compact)return;stacked[0]=compact;
            Node focus=content.getScene()==null?null:content.getScene().getFocusOwner();
            boolean restore=false;
            for(Node node=focus;node!=null;node=node.getParent())if(node==form||node==summary){restore=true;break;}
            columns.getChildren().clear();content.getChildren().clear();
            summary.setMaxWidth(compact?Double.MAX_VALUE:330);
            if(compact)content.getChildren().addAll(form,summary);
            else{columns.getChildren().addAll(form,summary);content.getChildren().add(columns);}
            if(restore&&focus!=null)focus.requestFocus();
        });
        summary.setMaxWidth(330);stickySummary(content,form,summary,stacked);return content;
    }

    /** Keep a short summary visible beside a long form; compact layouts use normal scrolling. */
    private static void stickySummary(VBox content,javafx.scene.layout.Region form,javafx.scene.layout.Region summary,boolean[] stacked) {
        ScrollPane[] owner={null};javafx.beans.InvalidationListener[] update={null};
        content.sceneProperty().addListener((o,a,b)->{
            if(owner[0]!=null&&update[0]!=null){owner[0].vvalueProperty().removeListener(update[0]);owner[0].viewportBoundsProperty().removeListener(update[0]);content.layoutBoundsProperty().removeListener(update[0]);summary.layoutBoundsProperty().removeListener(update[0]);owner[0]=null;}
            summary.setTranslateY(0);if(b==null)return;
            javafx.application.Platform.runLater(()->{
                if(content.getScene()==null)return;
                Node parent=content.getParent();while(parent!=null&&!(parent instanceof ScrollPane))parent=parent.getParent();
                if(!(parent instanceof ScrollPane scroll))return;owner[0]=scroll;
                update[0]=change->{
                    if(stacked[0]||summary.getHeight()>scroll.getViewportBounds().getHeight()-16){summary.setTranslateY(0);return;}
                    double offset=Math.max(0,scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight())*scroll.getVvalue();
                    double start=content.localToScene(0,0).getY()-scroll.getContent().localToScene(0,0).getY();
                    summary.setTranslateY(Math.max(0,Math.min(offset-start,form.getHeight()-summary.getHeight())));
                };
                scroll.vvalueProperty().addListener(update[0]);scroll.viewportBoundsProperty().addListener(update[0]);content.layoutBoundsProperty().addListener(update[0]);summary.layoutBoundsProperty().addListener(update[0]);update[0].invalidated(null);
            });
        });
    }

    public static VBox empty(String title, String detail, String action, Runnable callback) {
        VBox box = new VBox(14, StoreIcon.of("bag"), label(title, "section-title"), label(detail, "muted"), primary(action, callback));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        return box;
    }

    public static <T> TableColumn<T, String> column(String name, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    public static <T> TableView<T> table() {
        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(label("No matching products.", "muted"));
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    public static int integer(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a whole number within the allowed range.");
        }
    }

    public static <T> T selected(TableView<T> table) {
        T value = table.getSelectionModel().getSelectedItem();
        if (value == null) {
            throw new ValidationException("Select a row first.");
        }
        return value;
    }

    public static void attempt(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            error(e);
        }
    }

    public static String message(Throwable error) {
        if (error instanceof ValidationException || error instanceof StorageException) {
            return error.getMessage();
        }
        error.printStackTrace();
        return "Something went wrong. Please try again.";
    }

    public static void failure(Label label, Throwable error) {
        label.getStyleClass().removeAll("error-feedback");
        label.getStyleClass().add("error-feedback");
        label.setText(message(error));
    }

    public static void success(Label label, String text) {
        label.getStyleClass().removeAll("error-feedback");
        label.setText(text);Motion.reveal(label);
    }

    public static void error(Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message(e), ButtonType.OK);
        alert.setHeaderText("Unable to complete the action");
        alert.showAndWait();
    }

    public static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText("FORGE");
        alert.showAndWait();
    }

    public static <T> void async(Node owner, Callable<T> work, Consumer<T> success) {
        async(owner, "Loading…", work, success, Ui::error);
    }

    /** Read-only secondary content must not interrupt typing or purchase controls. */
    public static <T> void readOnly(Node owner,Callable<T> work,Consumer<T> success,Consumer<Throwable> failure) {
        Task<T> task=new Task<>() {protected T call() throws Exception{return work.call();}};
        task.setOnSucceeded(e->{if(owner.getScene()!=null)attempt(()->success.accept(task.getValue()));});
        task.setOnFailed(e->{if(owner.getScene()!=null)failure.accept(task.getException());});
        Thread thread=new Thread(task,"shop-read");thread.setDaemon(true);thread.start();
    }

    /** Tasks preserve the FX thread for rendering, and block duplicate mutations. */
    public static <T> void async(Node owner, String busyText, Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Node root=owner.getScene()==null?owner:owner.getScene().getRoot();
        Node lock=owner instanceof javafx.scene.control.Control?owner:null;
        Label status = root.lookup("#operation-status") instanceof Label l ? l : null;
        if (status != null) {
            status.setText(busyText);
        }
        boolean wasDisabled=lock!=null&&lock.isDisabled();
        if(lock!=null)lock.setDisable(true);
        Runnable unlock = () -> {
            if(lock!=null)lock.setDisable(wasDisabled);
            if (status != null) {
                status.setText("");
            }
        };
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            unlock.run();
            attempt(() -> success.accept(task.getValue()));
        });
        task.setOnFailed(e -> {
            unlock.run();
            failure.accept(task.getException());
        });
        Thread thread = new Thread(task, "shop-operation");
        thread.setDaemon(true);
        thread.start();
    }
}
