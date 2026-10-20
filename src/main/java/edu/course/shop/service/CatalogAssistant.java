package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.Services;
import edu.course.shop.model.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Local retrieval and trusted facts. No model output can select records or mutate the cart. */
public final class CatalogAssistant {
    public record Turn(String question, String answer) { }
    public record Memory(List<Long> ids, Category category, BigDecimal ceiling, List<Turn> turns) {
        public Memory { ids = List.copyOf(ids); turns = List.copyOf(turns); }
        public static Memory empty() { return new Memory(List.of(), null, null, List.of()); }
    }
    public record Plan(List<Product> products, JsonObject facts, String localAnswer, String context,
                       Category category, BigDecimal ceiling, boolean orderContext) {
        public Plan { products = List.copyOf(products); }
    }
    private final Services services;
    public CatalogAssistant(Services services) { this.services = services; }
    public static boolean asksOrders(String question) {
        return words(question).matches(".*\\b(order|orders|delivery|tracking|shipment|refund|receipt)\\b.*");
    }
    private static String words(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$., ]", " ").replaceAll("\\s+", " ").trim(); }
    public Plan prepare(User user, List<CartItem> cart, Product page, Memory memory, String question, boolean allowOrders) {
        if (question == null || question.isBlank() || question.length() > 1000) throw new IllegalArgumentException("Ask a question of 1–1,000 characters.");
        String q = words(question);
        JsonObject facts = new JsonObject();
        JsonArray history = new JsonArray();
        for (Turn turn : memory.turns()) {
            JsonObject t = new JsonObject(); t.addProperty("question", turn.question()); t.addProperty("answer", turn.answer()); history.add(t);
        }
        facts.add("conversationForReferenceOnly", history);
        if (asksOrders(q)) {
            if (!(user instanceof Customer)) return new Plan(List.of(), facts, "Sign in as a customer to see your own orders. This demo does not provide real delivery or tracking.", "Order help", null, null, true);
            if (!allowOrders) throw new IllegalArgumentException("Please approve sharing your order summary first.");
            List<Order> orders = services.orders().history(user);
            Matcher id = Pattern.compile("(?:order|receipt)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(question);
            if (id.find()) {
                String number = id.group(1);
                orders = orders.stream().filter(o -> Long.toString(o.id()).equals(number)).toList();
            }
            boolean asksTracking = q.matches(".*\\b(where|track|tracking|delivery|deliver|shipment|shipped|shipping|arrive|status)\\b.*");
            boolean asksRefund = q.matches(".*\\b(refund|return|exchange)\\b.*");
            boolean asksReceipt = q.matches(".*\\b(receipt|invoice)\\b.*");
            String answer;
            if (orders.isEmpty()) answer = "I couldn't find a matching order in your account. Open My orders to check the order list.";
            else {
                String records = orders.stream().limit(5).map(o -> "#" + o.id() + " · " + o.createdAt() + " · "
                    + Money.format(o.total()) + " · " + o.items().stream().mapToInt(OrderItem::quantity).sum()
                    + " item(s) · payment " + o.paymentStatus()).reduce((a,b) -> a + "\n" + b).orElse("");
                String purpose = asksTracking
                    ? "FORGE is a course demo, so it has no real shipping or live tracking. Payment status is simulated and does not show delivery."
                    : asksRefund
                        ? "FORGE cannot start or verify a return or refund. Here are your local order records; payment status is simulated."
                        : asksReceipt
                            ? "Open My orders to view or print the saved receipt. Here are your recent order records; payment status is simulated."
                            : "Here are your recent order records. Payment status is simulated.";
                answer = purpose + "\n" + records + "\nOpen My orders for full details.";
            }
            // Order records are rendered locally after explicit access approval; they never enter Gemini facts.
            return new Plan(List.of(), facts, answer, "Your order summaries · up to 5", null, null, true);
        }
        if (q.matches(".*\\b(cart|basket|checkout)\\b.*")) {
            if (cart.isEmpty()) return new Plan(List.of(), facts, "Your cart is empty. Tell me what you're looking for and your budget.", "Your cart · empty", null, null, false);
            facts.add("cart", AssistantContext.cart(cart, services.products()));
            return new Plan(List.of(), facts, null, "Your cart · " + cart.size() + " products", null, null, false);
        }
        List<Product> catalog = services.products().search(new Guest(), "", null);
        Category category = category(q);
        if ((q.contains("compare") || q.contains(" vs ")) && !terms(q).isEmpty()) category = null;
        BigDecimal budget = budget(q);
        boolean locationFollowUp = !memory.ids().isEmpty()
            && q.matches("^(where|where is it|where can i (find|buy) it|where can i find them|where can i buy them|where are they)$");
        boolean followUp = locationFollowUp || q.matches(".*\\b(it|this|these|those|them|which|something|cheaper|cheapest|more ram|alternatives|another|more|compare them)\\b.*");
        boolean alternatives = q.contains("alternative") || q.contains("show more") || q.contains("another");
        if (!memory.ids().isEmpty() && q.matches("^(which|what about|does it|is it|will it).*")) category = memory.category();
        boolean freshCategory = category != null && category != memory.category();
        if (category == null && followUp) category = memory.category();
        if (budget == null && followUp && !freshCategory) budget = memory.ceiling();
        if (alternatives && q.contains("cheap") && !memory.ids().isEmpty()) {
            BigDecimal previousLowest = catalog.stream().filter(p -> memory.ids().contains(p.getId())).map(Product::getPrice).min(BigDecimal::compareTo).orElse(null);
            if (previousLowest != null) budget = previousLowest.subtract(new BigDecimal("0.01")).max(BigDecimal.ZERO);
        }
        final Category selectedCategory = category;
        final BigDecimal maxPrice = budget;
        List<Product> eligible = catalog.stream().filter(p -> selectedCategory == null || p.getCategory() == selectedCategory)
            .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
            .filter(p -> !q.contains("in stock") || p.getStock() > 0).toList();
        if (!followUp) {
            String subtype = q.matches(".*\\b(gpu|gpus|graphics)\\b.*") ? "gpu|geforce|radeon|rtx|graphics"
                : q.matches(".*\\b(keyboard|keyboards)\\b.*") ? "keyboard"
                : q.matches(".*\\b(mouse|mice)\\b.*") ? "mouse|mice"
                : q.matches(".*\\b(headset|headphones)\\b.*") ? "headset|headphones" : null;
            if (subtype != null) eligible = eligible.stream().filter(p -> words(p.getName() + " " + p.getDescription()).matches(".*\\b(" + subtype + ")\\b.*")).toList();
        }
        // Name tokens distinguish explicit products from broad shopping requests.
        Set<String> terms = terms(q);
        Map<Long,Integer> scores = new HashMap<>();
        for (Product p : eligible) {
            Set<String> name = terms(words(p.getName()));
            int score = (int) terms.stream().filter(name::contains).count();
            if (!name.isEmpty() && q.contains(words(p.getName()))) score += 10;
            scores.put(p.getId(), score);
        }
        boolean named = scores.values().stream().anyMatch(score -> score > 0);
        List<Product> selected;
        if (named) selected = eligible.stream().filter(p -> scores.get(p.getId()) > 0)
            .sorted(Comparator.<Product>comparingInt(p -> scores.get(p.getId())).reversed()).toList();
        else if (page != null && q.matches(".*\\b(this|it|specs|specifications|compatible|fit)\\b.*") && !alternatives && (selectedCategory == null || selectedCategory == page.getCategory()))
            selected = eligible.stream().filter(p -> p.getId() == page.getId()).toList();
        else if (followUp && !alternatives && !freshCategory && !memory.ids().isEmpty())
            selected = eligible.stream().filter(p -> memory.ids().contains(p.getId())).toList();
        else selected = eligible;
        if (alternatives) selected = selected.stream().filter(p -> !memory.ids().contains(p.getId())).toList();
        // Unknown named queries shouldn't return an unrelated complete catalog as a match.
        if (!named && !terms.isEmpty() && selectedCategory == null && !followUp && page == null && maxPrice == null)
            selected = List.of();
        if (q.contains("cheap") || maxPrice != null) selected = selected.stream().sorted(Comparator.comparing(Product::getPrice)).toList();
        List<Product> allSelected = List.copyOf(selected);
        int count = allSelected.size();
        List<Product> visible = allSelected.stream().limit(6).toList();
        JsonArray products = new JsonArray(); allSelected.stream().limit(20).forEach(p -> products.add(AssistantContext.product(p)));
        facts.add("matchingCatalogRecords", products); facts.addProperty("totalMatches", count);
        facts.addProperty("shownMatches", Math.min(count, 20));
        facts.addProperty("queryInterpretation", (category == null ? "All categories" : category.label()) + (budget == null ? "" : " · up to " + Money.format(budget)));
        facts.addProperty("buildWarning", "These are search results, not a verified compatible or complete PC build.");
        boolean listing = q.matches(".*\\b(list|listed|show|find|available|have|looking|browse)\\b.*")
            && !q.matches(".*\\b(compare|why|explain|better|best|fit|compatible|performance)\\b.*");
        String local;
        boolean asksCompatibility = q.matches(".*\\b(compatible|compatibility|fit together|work together|fit in|clearance)\\b.*");
        if (asksCompatibility && !named && memory.ids().isEmpty() && page == null) {
            local = "Which parts should I check? Name at least two listed components, or ask me to review the parts in your cart.";
            allSelected = List.of();
        } else if (asksCompatibility && allSelected.size() >= 2) {
            local = CompatibilityAdvisor.report(allSelected);
        } else if (asksCompatibility && allSelected.size() < 2) {
            local = "I need at least two listed products to check compatibility. Ask me to compare or check specific parts, such as a CPU and motherboard.";
        } else if (locationFollowUp && !visible.isEmpty()) {
            local = "The product" + (visible.size() == 1 ? " is" : "s are")
                + " listed here in FORGE under " + (selectedCategory == null ? "the catalog" : selectedCategory.label())
                + ". Open the product card below to view details. This demo doesn't have physical store or aisle locations.";
        } else if (count == 0) {
            local = "I couldn't find a match in the current catalog. Try a product name or category such as laptops, GPUs or keyboards, or increase your budget.";
        } else if (listing) {
            String rows = visible.stream().map(p -> p.getName() + " · " + Money.format(p.getPrice()) + " · "
                + (p.getStock() == 0 ? "Out of stock" : "In stock" + (p.getStock() <= 3 ? " (" + p.getStock() + " left)" : "")))
                .reduce((a, b) -> a + "\n" + b).orElse("");
            local = "I found " + count + " matching product" + (count == 1 ? "" : "s") + ":\n" + rows
                + (count > 6 ? "\nShowing the first 6; use Load more below to see the rest." : "")
                + (category == Category.LAPTOPS && q.contains("gaming") ? "\nCheck the listed GPU and specifications before choosing one for gaming." : "");
        } else {
            local = null;
        }
        if (q.contains("compare") && !named && memory.ids().isEmpty() && page == null) {
            local = "Which products would you like to compare? Tell me their names, or first ask me to show a category such as gaming laptops.";
            selected = List.of();
        }
        return new Plan(allSelected, facts, local, "Catalog · " + count + (count == 1 ? " match" : " matches"), category, budget, false);
    }
    public static Memory remember(Memory old, Plan plan, String question, String answer) {
        List<Turn> turns = new ArrayList<>(old.turns());
        // Private order answers are not replayed as general chat history.
        if (!plan.orderContext()) turns.add(new Turn(question, answer.substring(0, Math.min(1800, answer.length()))));
        while (turns.size() > 4) turns.removeFirst();
        return new Memory(plan.products().stream().map(Product::getId).toList(), plan.category(), plan.ceiling(), turns);
    }
    static BigDecimal budget(String q) {
        Matcher m = Pattern.compile("(?:under|below|less than|up to|maximum|max|budget(?: of)?|within)\\s*\\$?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(k)?\\b").matcher(q);
        if (!m.find()) return null;
        BigDecimal value = new BigDecimal(m.group(1).replace(",", ""));
        return m.group(2) == null ? value : value.multiply(new BigDecimal("1000"));
    }
    private static Category category(String q) {
        if (q.matches(".*\\b(laptop|laptops|notebook|notebooks)\\b.*")) return Category.LAPTOPS;
        if (q.matches(".*\\b(phone|phones|tablet|tablets)\\b.*")) return Category.PHONES_TABLETS;
        if (q.matches(".*\\b(gpu|gpus|graphics|cpu|processor|ram|motherboard|psu|ssd)\\b.*")) return Category.PC_PARTS;
        if (q.matches(".*\\b(keyboard|keyboards|mouse|mice|headset|headphones|monitor)\\b.*")) return Category.PERIPHERALS;
        if (q.matches(".*\\b(pc|desktop|desktops)\\b.*")) return Category.PC_SETS;
        return null;
    }
    private static Set<String> terms(String q) {
        Set<String> ignored = Set.of("i","a","an","the","me","my","we","for","to","of","and","or","in","on","is","are","do","does","you","your","have","what","which","with","without","can","could","would","please","want","need","find","show","list","listed","currently","available","looking","browse","under","below","less","than","up","max","maximum","budget","within","gaming","school","work","play","build","setup","product","products","laptop","laptops","notebook","notebooks","pc","desktop","desktops","phone","phones","tablet","tablets","gpu","gpus","graphics","card","cards","cpu","ram","processor","motherboard","psu","ssd","keyboard","keyboards","mouse","mice","headset","headphones","monitor","stock","cheaper","cheapest","compare","two","these","those","them","this","it","more","better","best","specs","specifications","explain","fit","compatible","alternatives","alternative","another","good","be","will","how","much","all","store","where","there","buy");
        Set<String> result = new LinkedHashSet<>();
        for (String token : q.split("[^a-z0-9]+")) if (token.length() > 1 && !token.matches("\\d+") && !ignored.contains(token)) result.add(token);
        return result;
    }
}
