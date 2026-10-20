package edu.course.shop.ui;

import edu.course.shop.model.ProductReview;
import edu.course.shop.model.User;
import edu.course.shop.service.AssistantPreferences;
import edu.course.shop.service.GeminiAssistant;
import edu.course.shop.service.ProductReviewService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Verified-purchase review collection with an optional, consent-based Gemini summary. */
public final class ProductReviewsView extends VBox {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
    private final long productId;
    private final String productName;
    private final User user;
    private final ProductReviewService reviews;
    private final GeminiAssistant gemini;
    private final AssistantPreferences preferences = new AssistantPreferences();
    private final Label average = Ui.label("Loading reviews…", "muted");
    private final VBox entries = new VBox(10);
    private final Label feedback = Ui.feedback("product-review-feedback");
    private final Label summary = Ui.label("", "review-summary");
    private final Button summarize = Ui.button("Summarize reviews with AI", this::summarize);
    private final ComboBox<Integer> rating = new ComboBox<>(FXCollections.observableArrayList(5, 4, 3, 2, 1));
    private final TextArea text = new TextArea();

    public ProductReviewsView(long productId, String productName, User user, ProductReviewService reviews, GeminiAssistant gemini) {
        this.productId = productId; this.productName = productName; this.user = user; this.reviews = reviews; this.gemini = gemini;
        setId("product-reviews"); setSpacing(14); getStyleClass().add("product-reviews");
        Label title = Ui.label("Customer reviews", "section-title");
        HBox heading = new HBox(12, title, average); heading.setAlignment(Pos.CENTER_LEFT);
        RegionSpacer spacer = new RegionSpacer(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, summarize); actions.setAlignment(Pos.CENTER_RIGHT);
        summarize.setId("summarize-reviews");
        summary.setId("review-ai-summary"); summary.setWrapText(true); summary.setVisible(false); summary.managedProperty().bind(summary.visibleProperty());
        VBox reviewList = new VBox(10, entries); reviewList.setId("product-review-list");
        VBox card = new VBox(14, heading, summary, actions, reviewList);
        card.getStyleClass().add("card");
        getChildren().add(card);
        buildForm();
        load();
    }

    private void buildForm() {
        VBox form = new VBox(10); form.getStyleClass().add("card");
        Label heading = Ui.label("Share your experience", "section-title");
        if (user.getId() <= 0 || !user.canShop() || user.canManageProducts()) {
            form.getChildren().addAll(heading, Ui.label("Sign in and purchase this product to leave a verified review.", "muted"));
        } else {
            rating.setId("review-rating"); rating.getSelectionModel().select(Integer.valueOf(5)); rating.setMaxWidth(190);
            text.setId("review-text"); text.setPromptText("What worked well? What should another shopper know?"); text.setWrapText(true); text.setPrefRowCount(3); text.setMaxWidth(Double.MAX_VALUE);
            Button submit = Ui.primary("Post review", this::submit); submit.setId("submit-review");
            form.getChildren().addAll(heading, Ui.label("Only customers who bought this item can review it. One review per account; posting again updates yours.", "muted"), rating, text, submit, feedback);
        }
        getChildren().add(form);
    }

    private void load() {
        Ui.readOnly(this, () -> new ReviewData(reviews.list(productId), reviews.canReview(user, productId)), data -> {
            List<ProductReview> rows = data.rows();
            average.setText(rows.isEmpty() ? "No reviews yet" : String.format(java.util.Locale.ROOT, "%.1f ★ · %d %s", rows.stream().mapToInt(ProductReview::rating).average().orElse(0), rows.size(), rows.size() == 1 ? "review" : "reviews"));
            entries.getChildren().clear();
            for (ProductReview item : rows.stream().limit(12).toList()) entries.getChildren().add(reviewCard(item));
            if (rows.isEmpty()) entries.getChildren().add(Ui.label("Be the first to share a verified review after purchasing this product.", "muted"));
            summarize.setDisable(rows.isEmpty());
            if (user.getId() > 0 && user.canShop() && !user.canManageProducts()) {
                javafx.scene.Node form = getChildren().get(1);
                if (!data.canReview()) form.setDisable(true);
                if (!data.canReview()) feedback.setText("A completed purchase is required before posting a review.");
            }
        }, error -> Ui.failure(feedback, error));
    }

    private VBox reviewCard(ProductReview review) {
        Label by = Ui.label(review.author() + " · " + date(review.createdAt()), "muted");
        Label stars = Ui.label("★".repeat(review.rating()) + "☆".repeat(5 - review.rating()), "review-stars");
        Label body = Ui.label(review.text(), "product-description"); body.setWrapText(true);
        HBox top = new HBox(12, Ui.label(review.author(), "field-label"), new javafx.scene.layout.Region(), stars);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS); top.setAlignment(Pos.CENTER_LEFT);
        VBox item = new VBox(5, top, by, body); item.getStyleClass().add("review-entry"); return item;
    }

    private void submit() {
        Ui.async(this, "Saving your review…", () -> { reviews.submit(user, productId, rating.getValue(), text.getText()); return true; }, done -> { text.clear(); Ui.success(feedback, "Your verified review is saved."); load(); }, error -> Ui.failure(feedback, error));
    }

    private void summarize() {
        if (!preferences.hasReviewSummaryConsent(user.getId()) && !confirmSharing()) return;
        Ui.async(this, "Summarizing customer reviews…", () -> gemini.summarizeReviews(productName, reviews.list(productId)), answer -> { summary.setText(answer); summary.setVisible(true); }, error -> Ui.failure(feedback, error));
    }

    private boolean confirmSharing() {
        CheckBox agree = new CheckBox("I agree to send the product name, ratings, and review text to Google Gemini. No reviewer names or account details are included.");
        agree.setWrapText(true); agree.setId("review-ai-consent");
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, "Review text may contain personal details. Remove sensitive information before sharing. Free-tier content may help improve Google's products.", ButtonType.OK, ButtonType.CANCEL);
        dialog.initOwner(getScene().getWindow()); dialog.setTitle("Review summary privacy"); dialog.setHeaderText("Share public review text with Gemini?"); dialog.getDialogPane().setExpandableContent(agree);
        dialog.getDialogPane().setExpanded(true);
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK && agree.isSelected() && saveConsent();
    }

    private boolean saveConsent() {
        try { preferences.rememberReviewSummaryConsent(user.getId()); return true; }
        catch (RuntimeException e) { Ui.failure(feedback, e); return false; }
    }

    private static String date(String value) {
        try { return DATE.format(Instant.parse(value)); } catch (RuntimeException ignored) { return value; }
    }

    private record ReviewData(List<ProductReview> rows, boolean canReview) { }
    private static final class RegionSpacer extends javafx.scene.layout.Region { }
}
