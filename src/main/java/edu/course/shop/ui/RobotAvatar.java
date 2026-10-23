package edu.course.shop.ui;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.*;

/** Native, shared sprite playback. Artwork: robot-heads, Fayaz Ahmed (MIT); see /licenses. */
public final class RobotAvatar extends Region {
    public enum State { IDLE, THINKING, SEARCHING, WORKING, HAPPY, ERROR }
    private static final int FRAME_W=162,FRAME_H=234,COLUMNS=8,FRAMES=64,BOX=160,PAD_X=1,PAD_Y=53;
    private static final Map<State,Image> ATLASES=new EnumMap<>(State.class),STILLS=new EnumMap<>(State.class);
    private final ImageView picture=new ImageView();
    private final ObjectProperty<State> state=new SimpleObjectProperty<>(State.IDLE);
    private final BooleanProperty animated=new SimpleBooleanProperty(true);
    private final Timeline playback=new Timeline();
    private final PauseTransition reaction=new PauseTransition(Duration.seconds(2.5));
    private final double size;
    private int frame;
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
        picture.setManaged(false);picture.setMouseTransparent(true);picture.setSmooth(true);picture.setFitWidth(FRAME_W*size/BOX);picture.setFitHeight(FRAME_H*size/BOX);getChildren().add(picture);
        state.addListener((o,a,b)->{reaction.stop();frame=0;setAccessibleText("FORGE AI, "+b.name().toLowerCase(Locale.ROOT));configure();syncPlayback();});
        animated.addListener(lifecycle);visibleProperty().addListener(lifecycle);parentProperty().addListener(visibility);
        Motion.reducedProperty().addListener(new WeakInvalidationListener(lifecycle));
        sceneProperty().addListener((o,a,b)->{if(a!=null)a.windowProperty().removeListener(windowListener);if(b!=null)b.windowProperty().addListener(windowListener);observeVisibility();watchWindow(b==null?null:b.getWindow());});
        reaction.setOnFinished(e->{if(getState()==State.HAPPY||getState()==State.ERROR)setState(State.IDLE);});
        setAccessibleText("FORGE AI, idle");configure();paint();
    }
    public State getState(){return state.get();}
    public void setState(State value){state.set(Objects.requireNonNull(value));}
    public ReadOnlyObjectProperty<State> stateProperty(){return state;}
    public void setAnimated(boolean value){animated.set(value);}
    public void react(State value){setState(value);reaction.playFromStart();}
    private static Image still(State state){return STILLS.computeIfAbsent(state,s->new Image(Objects.requireNonNull(RobotAvatar.class.getResourceAsStream("/images/forge-ai/"+s.name().toLowerCase(Locale.ROOT)+"-still.png"))));}
    private Image atlas(State value){
        Image image=ATLASES.computeIfAbsent(value,s->new Image(Objects.requireNonNull(RobotAvatar.class.getResource("/images/forge-ai/"+s.name().toLowerCase(Locale.ROOT)+".png")).toExternalForm(),true));
        if(watchedImages.add(value))image.progressProperty().addListener(new WeakChangeListener<>(imageReady));return image;
    }
    private void configure(){
        playback.stop();int fps=getState()==State.IDLE?16:30;playback.getKeyFrames().setAll(new KeyFrame(Duration.millis(1000d/fps),e->{frame=(frame+1)%FRAMES;paint();}));playback.setCycleCount(Animation.INDEFINITE);
    }
    private void paint(){
        boolean moving=canAnimate();Image image=moving?atlas(getState()):still(getState());
        if(!moving||image.isError()||image.getProgress()<1){picture.setImage(still(getState()));picture.setViewport(new Rectangle2D(0,0,FRAME_W,FRAME_H));}
        else{picture.setImage(image);picture.setViewport(new Rectangle2D((frame%COLUMNS)*FRAME_W,(frame/COLUMNS)*FRAME_H,FRAME_W,FRAME_H));}

    }
    @Override protected void layoutChildren(){double scale=size/BOX;picture.setFitWidth(FRAME_W*scale);picture.setFitHeight(FRAME_H*scale);picture.relocate(-PAD_X*scale,-PAD_Y*scale);}
    private boolean canAnimate(){
        if(!animated.get()||Motion.isReduced()||getScene()==null||window==null||!window.isShowing()||(window instanceof Stage stage&&stage.isIconified()))return false;
        for(Node node=this;node!=null;node=node.getParent())if(!node.isVisible())return false;return true;
    }
    private void syncPlayback(){
        if(canAnimate()){atlas(getState());if(playback.getStatus()!=Animation.Status.RUNNING)playback.play();}
        else{playback.stop();frame=0;reaction.stop();}paint();
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