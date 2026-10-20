package edu.course.shop.ui;
import javafx.scene.control.*;
import javafx.scene.layout.*;
/** Structured editor with an optional plain-text view; both edit the same validated catalog content. */
public final class SpecificationsEditor extends VBox {
    private final TextArea raw=new TextArea();
    private final VBox rows=new VBox(10);
    private boolean syncing;
    public SpecificationsEditor() {
        super(10);raw.setId("product-specifications-input");raw.setWrapText(true);raw.setPrefRowCount(3);
        raw.setPromptText("One key: value per line. Up to 1,000 characters.");raw.setAccessibleText("Specifications as text");
        raw.textProperty().addListener((o,a,b)->{if(!syncing)render();});
        Button add=Ui.button("+ Add specification",()->{addRow("","");});add.setId("add-specification");
        TitledPane advanced=new TitledPane("Edit as text",raw);advanced.setExpanded(false);advanced.setAnimated(false);
        Label guidance=Ui.label("Use manufacturer-confirmed values. Helpful keys: CPU and motherboard—Socket; RAM and motherboard—Memory type; GPU—Length and Recommended PSU; case—Maximum GPU length; PSU—Wattage. Missing facts stay unverified by FORGE.","muted");
        guidance.setWrapText(true);
        getChildren().addAll(rows,add,guidance,advanced);render();
    }
    public String getText(){return raw.getText();}
    public void setText(String value){raw.setText(value);}
    public void clear(){raw.clear();}
    private void render() {
        rows.getChildren().clear();
        for(String line:raw.getText().lines().filter(s->!s.isBlank()).toList()) {
            int colon=line.indexOf(':');addRow(colon>0?line.substring(0,colon).trim():"",colon>0?line.substring(colon+1).trim():line);
        }
    }
    private void addRow(String key,String value) {
        TextField name=new TextField(key),detail=new TextField(value);name.setPromptText("Specification");detail.setPromptText("Value");name.setAccessibleText("Specification name");detail.setAccessibleText("Specification value");name.setMinWidth(0);detail.setMinWidth(0);name.setPrefWidth(150);HBox.setHgrow(detail,Priority.ALWAYS);
        HBox row=new HBox(10,name,detail);Button remove=Ui.button("×",()->{rows.getChildren().remove(row);sync();});remove.setAccessibleText("Remove specification");remove.getStyleClass().add("icon-button");row.getChildren().add(remove);rows.getChildren().add(row);
        name.textProperty().addListener((o,a,b)->sync());detail.textProperty().addListener((o,a,b)->sync());
    }
    private void sync() {
        StringBuilder text=new StringBuilder();for(var node:rows.getChildren()) {
            HBox row=(HBox)node;String key=((TextField)row.getChildren().get(0)).getText().trim(),value=((TextField)row.getChildren().get(1)).getText().trim();
            if(!key.isEmpty()||!value.isEmpty()){if(!text.isEmpty())text.append('\n');if(!key.isEmpty())text.append(key).append(": ");text.append(value);}
        }syncing=true;raw.setText(text.toString());syncing=false;
    }
}
