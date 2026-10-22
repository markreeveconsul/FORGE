package edu.course.shop.ui;
import java.util.*;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.geometry.Pos;
/** Scene-local modal with background and keyboard focus restoration. */
final class ShopOverlay {
 static Runnable show(Node owner,Region panel,boolean glass,boolean drawer,Runnable dismissed){
  var scene=owner.getScene();Parent original=scene.getRoot();boolean wrapped=!(original instanceof StackPane);StackPane root=wrapped?new StackPane():(StackPane)original;if(wrapped){scene.setRoot(root);root.getChildren().add(original);}Node focus=scene.getFocusOwner();
  List<Runnable> restore=new ArrayList<>();
  for(Node n:List.copyOf(root.getChildren())){var effect=n.getEffect();boolean transparent=n.isMouseTransparent();restore.add(()->{n.setEffect(effect);n.setMouseTransparent(transparent);});if(glass)n.setEffect(new GaussianBlur(13));n.setMouseTransparent(true);}
  Region scrim=new Region();scrim.setStyle("-fx-background-color: rgba(0,0,0,"+(glass?"0.36":"0.24")+");");
  StackPane overlay=new StackPane(scrim,panel);overlay.setId("shop-modal-overlay");StackPane.setAlignment(panel,drawer?Pos.CENTER_RIGHT:Pos.CENTER);
  javafx.beans.value.ChangeListener<Node> focusGuard=(o,a,b)->{if(b!=null){Node parent=b;while(parent!=null&&parent!=panel)parent=parent.getParent();if(parent==null){List<Node> nodes=new ArrayList<>();collect(panel,nodes);if(!nodes.isEmpty())nodes.getFirst().requestFocus();}}};scene.focusOwnerProperty().addListener(focusGuard); boolean[] closed={false};Runnable close=()->{if(closed[0])return;closed[0]=true;scene.focusOwnerProperty().removeListener(focusGuard);root.getChildren().remove(overlay);restore.forEach(Runnable::run);if(wrapped){root.getChildren().remove(original);scene.setRoot(original);}dismissed.run();if(focus!=null)focus.requestFocus();};
  scrim.setOnMouseClicked(e->close.run());
  overlay.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ESCAPE){close.run();e.consume();}if(e.getCode()==KeyCode.TAB){List<Node> nodes=new ArrayList<>();collect(panel,nodes);if(!nodes.isEmpty()){int i=nodes.indexOf(owner.getScene().getFocusOwner());nodes.get(Math.floorMod(i+(e.isShiftDown()?-1:1),nodes.size())).requestFocus();}e.consume();}});
  root.getChildren().add(overlay);Motion.panel(panel);javafx.application.Platform.runLater(()->{List<Node> nodes=new ArrayList<>();collect(panel,nodes);if(!nodes.isEmpty())nodes.getFirst().requestFocus();});return close;
 }
 private static void collect(Node n,List<Node> nodes){if(!n.isVisible()||n.isDisabled())return;if(n.isFocusTraversable())nodes.add(n);if(n instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())collect(child,nodes);}
}
