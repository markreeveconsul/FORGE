package edu.course.shop.ui;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native cached playback. Artwork: robot-heads, Fayaz Ahmed (MIT); see /licenses. FX thread only. */
public final class RobotAvatar extends Region {
    public enum State { IDLE, THINKING, SEARCHING, LISTENING, SPEAKING, WORKING, HAPPY, ERROR, SLEEPING }
    private static final int FRAME_W=162,FRAME_H=234,COLUMNS=8,FRAMES=64,BOX=160,PAD_X=1,PAD_Y=53;
    private static final Map<State,Image> ATLASES=new EnumMap<>(State.class),STILLS=new EnumMap<>(State.class);
    private final ImageView picture=new ImageView(),previous=new ImageView();
    private final ObjectProperty<State> state=new SimpleObjectProperty<>(State.IDLE);
    private final BooleanProperty animated=new SimpleBooleanProperty(true);
    private final Timeline playback=new Timeline(),blend=new Timeline();
    private final PauseTransition reaction=new PauseTransition(),typing=new PauseTransition(Duration.seconds(2.5)),rest=new PauseTransition();
    private final double size;
    private int frame;
    private boolean attentive;
    private Duration sleepAfter=Duration.ZERO;
    private final List<Node> ancestors=new ArrayList<>();
    private final Set<State> watchedImages=EnumSet.noneOf(State.class);
    private final InvalidationListener lifecycle=o->syncPlayback();
    private final InvalidationListener visibility=o->{observeVisibility();syncPlayback();};
    private final ChangeListener<Window> windowListener=(o,a,b)->watchWindow(b);
    private final ChangeListener<Number> imageReady=(o,a,b)->paint();
    private Window window;

    public RobotAvatar(double size){
        if(size<16||size>160)throw new IllegalArgumentException("Avatar size must be between 16 and 160.");
        this.size=size;getStyleClass().add("forge-ai-robot");setMinSize(size,size);setPrefSize(size,size);setMaxSize(size,size);setMouseTransparent(true);setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        for(ImageView layer:List.of(previous,picture)){layer.setManaged(false);layer.setMouseTransparent(true);layer.setSmooth(true);layer.setFitWidth(FRAME_W*size/BOX);layer.setFitHeight(FRAME_H*size/BOX);}
        getChildren().addAll(previous,picture);previous.setOpacity(0);
        state.addListener((o,a,b)->changeExpression(b));
        animated.addListener(lifecycle);visibleProperty().addListener(lifecycle);parentProperty().addListener(visibility);
        Motion.reducedProperty().addListener(new WeakInvalidationListener(lifecycle));
        sceneProperty().addListener((o,a,b)->{if(a!=null)a.windowProperty().removeListener(windowListener);if(b!=null)b.windowProperty().addListener(windowListener);observeVisibility();watchWindow(b==null?null:b.getWindow());});
        reaction.setOnFinished(e->{if(getState()==State.SPEAKING)reactFor(State.HAPPY,Duration.millis(900));else if(getState()==State.HAPPY||getState()==State.ERROR)returnToRest();});
        typing.setOnFinished(e->setAttentive(false));
        rest.setOnFinished(e->{if(present()&&getState()==State.IDLE)setState(State.SLEEPING);});
        setAccessibleText("FORGE AI, idle");configure();paint();
    }
    public State getState(){return state.get();}
    public void setState(State value){state.set(Objects.requireNonNull(value));}
    public ReadOnlyObjectProperty<State> stateProperty(){return state;}
    public void setAnimated(boolean value){animated.set(value);}
    public void react(State value){reactFor(value,Duration.seconds(2.5));}
    /** A text response is appearing; this is an expression, not audio playback. */
    public void replyPresented(){reactFor(State.SPEAKING,Duration.millis(1350));}
    private void reactFor(State value,Duration duration){setState(value);reaction.setDuration(duration);if(present())reaction.playFromStart();}
    public void returnToRest(){reaction.stop();setState(attentive?State.LISTENING:State.IDLE);armRest();}
    public void wake(){if(getState()==State.SLEEPING)returnToRest();armRest();}
    public void setAttentive(boolean value){attentive=value;if(quiet())returnToRest();}
    public void setSleepAfter(Duration delay){
        if(delay==null||delay.isUnknown()||delay.isIndefinite()||delay.lessThan(Duration.ZERO))throw new IllegalArgumentException("Use a finite inactivity delay.");
        sleepAfter=delay;armRest();
    }
    /** Typing briefly animates attention. It never turns on a microphone or interrupts a task. */
    public void watchInput(TextInputControl input,BooleanSupplier busy){
        Runnable attention=()->{if(busy.getAsBoolean())return;wake();setAttentive(true);typing.playFromStart();};
        input.focusedProperty().addListener((o,a,focused)->{if(focused)attention.run();else{typing.stop();setAttentive(false);}});
        input.textProperty().addListener((o,a,b)->{if(b.isBlank()){typing.stop();setAttentive(false);}else attention.run();});
    }
    private boolean quiet(){return getState()==State.IDLE||getState()==State.LISTENING||getState()==State.SLEEPING;}
    private void changeExpression(State next){
        reaction.stop();rest.stop();frame=0;
        if(!quiet()){typing.stop();attentive=false;}
        setAccessibleText("FORGE AI, "+switch(next){case LISTENING->"ready for your message";case SPEAKING->"showing a text reply";case SLEEPING->"resting";default->next.name().toLowerCase(Locale.ROOT);});
        blend.stop();boolean dissolve=canAnimate()&&picture.getImage()!=null;
        if(dissolve){previous.setImage(picture.getImage());previous.setViewport(picture.getViewport());previous.setOpacity(1);picture.setOpacity(0);}
        else clearBlend();
        configure();syncPlayback();
        if(dissolve){blend.getKeyFrames().setAll(new KeyFrame(Duration.millis(180),new KeyValue(picture.opacityProperty(),1,Interpolator.EASE_BOTH),new KeyValue(previous.opacityProperty(),0,Interpolator.EASE_BOTH)));blend.setOnFinished(e->previous.setImage(null));blend.playFromStart();}
    }
    private void clearBlend(){blend.stop();previous.setImage(null);previous.setOpacity(0);picture.setOpacity(1);}
    private static Image still(State state){return STILLS.computeIfAbsent(state,s->new Image(Objects.requireNonNull(RobotAvatar.class.getResourceAsStream("/images/forge-ai/"+s.name().toLowerCase(Locale.ROOT)+"-still.png"))));}
    private Image atlas(State value){
        Image image=ATLASES.computeIfAbsent(value,s->new Image(Objects.requireNonNull(RobotAvatar.class.getResource("/images/forge-ai/"+s.name().toLowerCase(Locale.ROOT)+".png")).toExternalForm(),true));
        if(watchedImages.add(value))image.progressProperty().addListener(new WeakChangeListener<>(imageReady));return image;
    }
    private void configure(){
        playback.stop();int fps=getState()==State.SLEEPING?12:getState()==State.IDLE?16:30;
        playback.getKeyFrames().setAll(new KeyFrame(Duration.millis(1000d/fps),e->{frame=(frame+1)%FRAMES;paint();}));playback.setCycleCount(Animation.INDEFINITE);
    }
    private void paint(){
        boolean moving=canAnimate();Image image=moving?atlas(getState()):still(getState());
        if(!moving||image.isError()||image.getProgress()<1){picture.setImage(still(getState()));picture.setViewport(new Rectangle2D(0,0,FRAME_W,FRAME_H));}
        else{picture.setImage(image);picture.setViewport(new Rectangle2D((frame%COLUMNS)*FRAME_W,(frame/COLUMNS)*FRAME_H,FRAME_W,FRAME_H));}
    }
    @Override protected void layoutChildren(){double scale=size/BOX;for(ImageView layer:List.of(previous,picture)){layer.setFitWidth(FRAME_W*scale);layer.setFitHeight(FRAME_H*scale);layer.relocate(-PAD_X*scale,-PAD_Y*scale);}}
    private boolean present(){
        if(!animated.get()||getScene()==null||window==null||!window.isShowing()||(window instanceof Stage stage&&stage.isIconified()))return false;
        for(Node node=this;node!=null;node=node.getParent())if(!node.isVisible())return false;return true;
    }
    private boolean canAnimate(){return present()&&!Motion.isReduced();}
    private void armRest(){rest.stop();if(present()&&getState()==State.IDLE&&sleepAfter.greaterThan(Duration.ZERO)){rest.setDuration(sleepAfter);rest.playFromStart();}}
    private void syncPlayback(){
        if(canAnimate()){atlas(getState());if(playback.getStatus()!=Animation.Status.RUNNING)playback.play();}
        else{playback.stop();frame=0;clearBlend();}
        if(!present()){reaction.stop();typing.stop();rest.stop();}else armRest();paint();
    }
    private void observeVisibility(){
        for(Node node:ancestors)node.visibleProperty().removeListener(lifecycle);ancestors.clear();
        if(getScene()!=null)for(Node node=getParent();node!=null;node=node.getParent()){ancestors.add(node);node.visibleProperty().addListener(lifecycle);}
    }
    private void watchWindow(Window next){
        if(window!=null){window.showingProperty().removeListener(lifecycle);if(window instanceof Stage stage)stage.iconifiedProperty().removeListener(lifecycle);}
        window=next;if(next!=null){next.showingProperty().addListener(lifecycle);if(next instanceof Stage stage)stage.iconifiedProperty().addListener(lifecycle);}syncPlayback();
    }
    boolean isAnimating(){return playback.getStatus()==Animation.Status.RUNNING;}
    int frameIndex(){return frame;}
    Image displayedImage(){return picture.getImage();}
    static long cachedAtlasBytes(){return ATLASES.values().stream().mapToLong(image->(long)(image.getWidth()*image.getHeight()*4)).sum();}
}