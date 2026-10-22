package edu.course.shop.ui;
import edu.course.shop.model.*;
import edu.course.shop.exception.ValidationException;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Category templates and explicit units, retaining custom and legacy specification text. */
public final class SpecificationsEditor extends VBox {
    private final TextArea raw=new TextArea();private final VBox rows=new VBox(10),schema=new VBox(12);
    private final Map<String,TextField> fields=new LinkedHashMap<>();
    private final Map<String,ComboBox<String>> units=new LinkedHashMap<>();
    private final Map<String,Label> errors=new LinkedHashMap<>();
    private Category category=Category.PC_SETS;private boolean syncing;
    public SpecificationsEditor(){
        super(12);raw.setId("product-specifications-input");raw.setWrapText(true);raw.setPrefRowCount(4);raw.setPromptText("One key: value per line. Up to 1,000 characters.");raw.setAccessibleText("Specifications as text");
        raw.textProperty().addListener((o,a,b)->{if(!syncing)render();});
        Button add=Ui.button("+ Add specification",()->addRow("",""));add.setId("add-specification");
        TitledPane custom=new TitledPane("Additional specifications",new VBox(10,rows,add));custom.setExpanded(false);custom.setAnimated(false);
        TitledPane advanced=new TitledPane("Edit as text",raw);advanced.setExpanded(false);advanced.setAnimated(false);
        getChildren().addAll(Ui.label("Enter confirmed values only. Choose the capacity unit beside RAM, VRAM and Storage. Blank fields are omitted. Other formats can still be saved.","muted"),schema,custom,
            Ui.label("For configurations of the same product, use the same Brand and Model, a different Variant and a unique SKU. Each inventory product keeps its own price and stock.","muted"),advanced);render();
    }
    public void setCategory(Category value){category=value==null?Category.ELECTRONICS:value;render();}
    public String getText(){return raw.getText();}public void setText(String value){raw.setText(value==null?"":value);render();}public void clear(){raw.clear();render();}
    public String validatedText(){
        clearErrors();try{String text=ProductSpecifications.listingText(getText());showWarnings();return text;}
        catch(ValidationException error){
            String message=error.getMessage();String key=message.contains(":")?message.substring(0,message.indexOf(':')):"";
            if(fields.containsKey(key)){Ui.flag(fields.get(key),true);errors.get(key).setText(message.substring(message.indexOf(':')+1).trim());Ui.focus(fields.get(key));}
            throw error;
        }
    }
    public void clearErrors(){fields.values().forEach(field->Ui.flag(field,false));errors.values().forEach(label->{label.setText("");label.getStyleClass().remove("field-warning");});showWarnings();}
    private void showWarnings(){
        errors.values().forEach(label->{if(label.getStyleClass().remove("field-warning"))label.setText("");});
        for(var warning:ProductSpecifications.formatWarnings(getText()))if(errors.containsKey(warning.key())){
            Label label=errors.get(warning.key());if(!label.getStyleClass().contains("field-warning"))label.getStyleClass().add("field-warning");label.setText(warning.message());
        }
    }
    private static List<String> unitsFor(String key){return Set.of("RAM","VRAM","Storage").contains(key)?List.of("GB","TB","MB"):List.of();}
    private void render(){
        fields.clear();units.clear();errors.clear();schema.getChildren().clear();rows.getChildren().clear();Map<String,String> values=ProductSpecifications.values(raw.getText());
        GridPane grid=new GridPane();grid.setHgap(12);grid.setVgap(12);ColumnConstraints a=new ColumnConstraints(),b=new ColumnConstraints();a.setPercentWidth(50);b.setPercentWidth(50);grid.getColumnConstraints().addAll(a,b);int index=0;
        for(String key:ProductSpecifications.fields(category)){
            String stored=values.getOrDefault(key,"");TextField field=new TextField(stored);String id=key.toLowerCase(Locale.ROOT).replace(' ','-');field.setId("spec-"+id);field.setAccessibleText(key);field.setMinWidth(0);field.setPromptText(hint(key));fields.put(key,field);
            Label label=Ui.label(key,"field-label");label.setLabelFor(field);Label error=Ui.feedback("spec-"+id+"-error");error.getStyleClass().add("field-error");errors.put(key,error);
            javafx.scene.Node input=field;List<String> choices=unitsFor(key);
            if(!choices.isEmpty()){
                ComboBox<String> unit=new ComboBox<>();unit.getItems().addAll(choices);unit.setValue("GB");unit.setId("spec-"+id+"-unit");unit.setAccessibleText(key+" unit");unit.getStyleClass().add("specification-unit");unit.setMinWidth(90);unit.setPrefWidth(90);unit.setMaxWidth(90);
                unit.setButtonCell(new ListCell<>(){@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?"":value);}});units.put(key,unit);
                var match=java.util.regex.Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(GB|TB|MB)").matcher(stored);
                if(match.matches()){field.setText(match.group(1));unit.setValue(match.group(2).toUpperCase(Locale.ROOT));}
                field.setPromptText(key.equals("Storage")?"512":"16");HBox.setHgrow(field,Priority.ALWAYS);input=new HBox(6,field,unit);
                unit.valueProperty().addListener((o,old,value)->{
                    // A pasted capacity remains accepted; choosing another unit updates that explicit value.
                    if(field.getText().matches("(?i)[0-9]+(?:\\.[0-9]+)?\\s*(GB|TB|MB)"))field.setText(field.getText().replaceFirst("(?i)\\s*(GB|TB|MB)$", "").trim());
                    clearErrors();sync();
                });
            }
            VBox cell=new VBox(5,label,input,error);cell.setMinWidth(0);grid.add(cell,index%2,index/2);index++;field.textProperty().addListener((o,old,value)->{clearErrors();sync();});
        }
        schema.getChildren().add(grid);
        for(String line:raw.getText().lines().filter(s->!s.isBlank()).toList()){
            int split=line.indexOf(':');String key=split>0?ProductSpecifications.canonical(line.substring(0,split)):"";
            if(!fields.containsKey(key))addRow(key,split>0?line.substring(split+1).trim():line);
        }showWarnings();
    }
    private static String hint(String key){return switch(key){case "RAM","VRAM"->"16 GB";case "Storage"->"1 TB";case "Length","Maximum GPU length"->"300 mm";case "Wattage","Recommended PSU"->"650 W";case "Refresh rate"->"144 Hz";case "SKU"->"Unique inventory code";case "Variant"->"32 GB / 1 TB / Black";default->key;};}
    private void addRow(String key,String value){
        TextField name=new TextField(key),detail=new TextField(value);name.setPromptText("Specification");detail.setPromptText("Value");name.setAccessibleText("Specification name");detail.setAccessibleText("Specification value");name.setMinWidth(0);detail.setMinWidth(0);name.setPrefWidth(130);HBox.setHgrow(detail,Priority.ALWAYS);
        HBox row=new HBox(8,name,detail);Button remove=Ui.button("×",()->{rows.getChildren().remove(row);sync();});remove.setAccessibleText("Remove specification");remove.getStyleClass().add("icon-button");row.getChildren().add(remove);rows.getChildren().add(row);name.textProperty().addListener((o,a,b)->sync());detail.textProperty().addListener((o,a,b)->sync());
    }
    private void sync(){
        StringJoiner text=new StringJoiner("\n");fields.forEach((key,field)->{String value=field.getText().trim();if(!value.isBlank()){if(units.containsKey(key)&&value.matches("[0-9]+(?:\\.[0-9]+)?"))value+=" "+units.get(key).getValue();text.add(key+": "+value);}});
        for(var node:rows.getChildren()){HBox row=(HBox)node;String key=((TextField)row.getChildren().get(0)).getText().trim(),value=((TextField)row.getChildren().get(1)).getText().trim();if(!key.isEmpty()||!value.isEmpty())text.add(key.isEmpty()?value:key+": "+value);}
        syncing=true;raw.setText(text.toString());syncing=false;showWarnings();
    }
}