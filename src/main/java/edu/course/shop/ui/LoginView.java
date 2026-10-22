package edu.course.shop.ui;
import edu.course.shop.service.AuthService;
import edu.course.shop.model.User;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public final class LoginView extends StackPane {
    public LoginView(AuthService auth,Consumer<User> signedIn) {
        getStyleClass().add("login-background");getStylesheets().add(LoginView.class.getResource("/login.css").toExternalForm());
        BooleanProperty signingIn=new SimpleBooleanProperty();
        TextField username=new TextField();username.setPromptText("Your username");username.setId("username");username.disableProperty().bind(signingIn);
        PasswordField password=new PasswordField();password.setPromptText("Your password");password.setId("password");password.disableProperty().bind(signingIn);
        TextField visiblePassword=new TextField();visiblePassword.setPromptText("Your password");visiblePassword.setId("password-visible");visiblePassword.disableProperty().bind(signingIn);visiblePassword.setAccessibleText("Password, visible");
        visiblePassword.textProperty().bindBidirectional(password.textProperty());visiblePassword.setVisible(false);visiblePassword.setManaged(false);
        Button reveal=Ui.button("",()->{});reveal.setGraphic(StoreIcon.of("eye",18));reveal.setId("show-password");reveal.getStyleClass().add("password-toggle");reveal.setAccessibleText("Show password");reveal.setTooltip(new Tooltip("Show password"));reveal.disableProperty().bind(signingIn);
        StackPane passwordInputs=new StackPane(password,visiblePassword);passwordInputs.setMinWidth(0);HBox.setHgrow(passwordInputs,Priority.ALWAYS);
        HBox passwordRow=new HBox(0,passwordInputs,reveal);passwordRow.setAlignment(Pos.CENTER_LEFT);passwordRow.getStyleClass().add("login-password-shell");
        javafx.beans.InvalidationListener passwordFocus=o->passwordRow.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("input-focused"),password.isFocused()||visiblePassword.isFocused());password.focusedProperty().addListener(passwordFocus);visiblePassword.focusedProperty().addListener(passwordFocus);
        reveal.setOnAction(e->{
            boolean show=!visiblePassword.isVisible();int caret=show?password.getCaretPosition():visiblePassword.getCaretPosition();
            password.setVisible(!show);password.setManaged(!show);visiblePassword.setVisible(show);visiblePassword.setManaged(show);
            reveal.setGraphic(StoreIcon.of(show?"eye-off":"eye",18));reveal.setAccessibleText(show?"Hide password":"Show password");reveal.getTooltip().setText(show?"Hide password":"Show password");
            TextField active=show?visiblePassword:password;active.requestFocus();active.positionCaret(caret);
        });
        Label usernameError=Ui.feedback("username-error"),passwordError=Ui.feedback("password-error"),feedback=Ui.feedback("login-feedback"),status=Ui.feedback("operation-status");
        username.textProperty().addListener((o,a,b)->{usernameError.setText("");Ui.flag(username,false);});
        password.textProperty().addListener((o,a,b)->{passwordError.setText("");Ui.flag(password,false);Ui.flag(visiblePassword,false);});
        Button login=Ui.primary("Sign in",()->{
            if(signingIn.get())return;String name=username.getText(),secret=password.getText();usernameError.setText("");passwordError.setText("");feedback.setText("");
            if(name.isBlank()){usernameError.setText("Enter your username.");Ui.flag(username,true);username.requestFocus();return;}
            if(secret.isBlank()){passwordError.setText("Enter your password.");Ui.flag(password,true);Ui.flag(visiblePassword,true);(visiblePassword.isVisible()?visiblePassword:password).requestFocus();return;}
            signingIn.set(true);Ui.async(this,"Signing you in…",()->auth.login(name,secret),user->{signingIn.set(false);password.clear();signedIn.accept(user);},error->{signingIn.set(false);Ui.failure(feedback,error);});
        });
        login.setId("login");login.setDefaultButton(true);login.setMaxWidth(Double.MAX_VALUE);login.getStyleClass().addAll("cta","purchase-action");login.disableProperty().bind(signingIn);
        Button fillCustomer=Ui.link("Use customer login",()->{username.setText("customer");password.setText("customer123");});fillCustomer.setId("login-demo-customer");
        Button fillAdmin=Ui.link("Use admin login",()->{username.setText("admin");password.setText("admin123");});fillAdmin.setId("login-demo-admin");
        HBox shortcuts=new HBox(10,fillCustomer,fillAdmin);shortcuts.setAlignment(Pos.CENTER_LEFT);
        VBox demo=new VBox(8,Ui.label("Customer: customer / customer123\nAdmin: admin / admin123","muted"),shortcuts);demo.getStyleClass().add("login-demo-content");
        TitledPane demoPanel=new TitledPane("Demo accounts",demo);demoPanel.setExpanded(false);demoPanel.animatedProperty().bind(Motion.reducedProperty().not());demoPanel.setId("demo-accounts");demoPanel.disableProperty().bind(signingIn);
        Button forgot=Ui.link("Forgot password?",()->{demoPanel.setExpanded(true);feedback.getStyleClass().remove("error-feedback");feedback.setText("For account access help, contact the store administrator. Demo accounts are shown below.");});forgot.setId("login-forgot-password");forgot.getStyleClass().add("login-forgot");forgot.disableProperty().bind(signingIn);
        Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);HBox recovery=new HBox(gap,forgot);recovery.setAlignment(Pos.CENTER_RIGHT);
        Label passwordLabel=Ui.label("Password","field-label");passwordLabel.setLabelFor(password);password.setAccessibleText("Password");passwordError.getStyleClass().add("field-error");
        Label brand=Ui.label("FORGE","login-wordmark");brand.setId("login-brand");brand.setAccessibleText("FORGE");brand.setWrapText(false);
        VBox heading=new VBox(8,brand,Ui.label("ACCOUNT ACCESS","login-access-label"),Ui.label("Welcome back.","page-title"),Ui.label("Sign in to continue shopping.","muted"));heading.getStyleClass().add("login-heading");
        VBox credentials=new VBox(12,Ui.field("Username",username,usernameError),new VBox(6,passwordLabel,passwordRow,passwordError),recovery);credentials.getStyleClass().add("login-credentials");
        VBox form=new VBox(14,heading,credentials,feedback,status,login,demoPanel);form.getStyleClass().add("login-card");form.setMinWidth(0);form.setMaxWidth(Double.MAX_VALUE);form.setMaxHeight(Region.USE_PREF_SIZE);
        LoginGlassPanel glass=new LoginGlassPanel(form);glass.setPrefWidth(500);glass.setMinHeight(Region.USE_PREF_SIZE);
        StackPane host=new StackPane(glass);host.setMinHeight(Region.USE_PREF_SIZE);host.setPadding(new Insets(30,0,54,0));
        ScrollPane formScroll=Ui.scroll(host);formScroll.setId("login-form-scroll");formScroll.setFitToHeight(true);
        StackPane formPane=new StackPane(formScroll);formPane.getStyleClass().add("login-form-pane");formPane.setMinWidth(0);glass.maxWidthProperty().bind(javafx.beans.binding.Bindings.min(520,formPane.widthProperty().multiply(.82)));
        EditorialHeader intro=EditorialHeader.forLogin();intro.setMinHeight(0);intro.setPrefHeight(0);intro.setMaxHeight(Double.MAX_VALUE);intro.setPrefWidth(0);formPane.setPrefWidth(0);
        HBox.setHgrow(intro,Priority.ALWAYS);HBox.setHgrow(formPane,Priority.ALWAYS);HBox layout=new HBox(0,intro,formPane);layout.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);getChildren().add(layout);
        widthProperty().addListener((o,a,b)->{getStyleClass().remove("login-compact");if(b.doubleValue()<1150)getStyleClass().add("login-compact");});
        Interactions.install(this);
    }
}