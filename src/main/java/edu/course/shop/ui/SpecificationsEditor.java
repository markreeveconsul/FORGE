package edu.course.shop.ui;
import edu.course.shop.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Category templates and explicit units, retaining custom and legacy specification text. */
public final class SpecificationsEditor extends VBox {
    private final TextArea raw=new TextArea();private final VBox rows=new VBox(10),schema=new VBox(12);
    private final Map<String,TextField> fields=new LinkedHashMap<>();private Category category=Category.PC_SETS;private boolean syncing;
    public SpecificationsEditor(){
        super(12);raw.setId("product-specifications-input");raw.setWrapText(true);raw.setPrefRowCount(4);raw.setPromptText("One key: value per line. Up to 1,000 characters.");raw.setAccessibleText("Specifications as text");
        raw.textProperty().addListener((o,a,b)->{if(!syncing)render();});
        Button add=Ui.button("+ Add specification",()->addRow("",""));add.setId("add-specification");
        TitledPane custom=new TitledPane("Additional specifications",new VBox(10,rows,add));custom.setExpanded(false);custom.setAnimated(false);
        TitledPane advanced=new TitledPane("Edit as text",raw);advanced.setExpanded(false);advanced.setAnimated(false);
        getChildren().addAll(Ui.label("Enter confirmed values only. Use units such as 16 GB, 1 TB, 650 W or 300 mm. Blank fields are omitted.","muted"),schema,custom,
            Ui.label("For configurations of the same product, use the same Brand and Model, a different Variant and a unique SKU. Each inventory product keeps its own price and stock.","muted"),advanced);render();
    }
    public void setCategory(Category value){category=value==null?Category.ELECTRONICS:value;render();}
    public String getText(){return raw.getText();}public void setText(String value){raw.setText(value==null?"":value);render();}public void clear(){raw.clear();render();}
    private void render(){
        fields.clear();schema.getChildren().clear();rows.getChildren().clear();Map<String,String> values=ProductSpecifications.values(raw.getText());
        GridPane grid=new GridPane();grid.setHgap(12);grid.setVgap(12);ColumnConstraints a=new ColumnConstraints(),b=new ColumnConstraints();a.setPercentWidth(50);b.setPercentWidth(50);grid.getColumnConstraints().addAll(a,b);int index=0;
        for(String key:ProductSpecifications.fields(category)){
            TextField field=new TextField(values.getOrDefault(key,""));field.setId("spec-"+key.toLowerCase(Locale.ROOT).replace(' ','-'));field.setAccessibleText(key);field.setMinWidth(0);field.setPromptText(hint(key));fields.put(key,field);
            Label label=Ui.label(key,"field-label");label.setLabelFor(field);VBox cell=new VBox(5,label,field);cell.setMinWidth(0);grid.add(cell,index%2,index/2);index++;field.textProperty().addListener((o,old,value)->sync());
        }
        schema.getChildren().add(grid);
        for(String line:raw.getText().lines().filter(s->!s.isBlank()).toList()){
            int split=line.indexOf(':');String key=split>0?ProductSpecifications.canonical(line.substring(0,split)):"";
            if(!fields.containsKey(key))addRow(key,split>0?line.substring(split+1).trim():line);
        }
    }
    private static String hint(String key){return switch(key){case "RAM","VRAM"->"16 GB";case "Storage"->"1 TB";case "Length","Maximum GPU length"->"300 mm";case "Wattage","Recommended PSU"->"650 W";case "Refresh rate"->"144 Hz";case "SKU"->"Unique inventory code";case "Variant"->"32 GB / 1 TB / Black";default->key;};}
    private void addRow(String key,String value){
        TextField name=new TextField(key),detail=new TextField(value);name.setPromptText("Specification");detail.setPromptText("Value");name.setAccessibleText("Specification name");detail.setAccessibleText("Specification value");name.setMinWidth(0);detail.setMinWidth(0);name.setPrefWidth(130);HBox.setHgrow(detail,Priority.ALWAYS);
        HBox row=new HBox(8,name,detail);Button remove=Ui.button("×",()->{rows.getChildren().remove(row);sync();});remove.setAccessibleText("Remove specification");remove.getStyleClass().add("icon-button");row.getChildren().add(remove);rows.getChildren().add(row);name.textProperty().addListener((o,a,b)->sync());detail.textProperty().addListener((o,a,b)->sync());
    }
    private void sync(){
        StringJoiner text=new StringJoiner("\n");fields.forEach((key,field)->{if(!field.getText().isBlank())text.add(key+": "+field.getText().trim());});
        for(var node:rows.getChildren()){HBox row=(HBox)node;String key=((TextField)row.getChildren().get(0)).getText().trim(),value=((TextField)row.getChildren().get(1)).getText().trim();if(!key.isEmpty()||!value.isEmpty())text.add(key.isEmpty()?value:key+": "+value);}
        syncing=true;raw.setText(text.toString());syncing=false;
    }
}