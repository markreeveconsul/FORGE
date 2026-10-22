package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.exception.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Revalidates an explicitly approved set; model text never enters this mutation path. */
public final class AssistantCart {
    public record Edit(long productId,String productName,Integer quantity) {
        public boolean removes(){return quantity==null;}
    }
    private AssistantCart() { }
    public static Optional<Edit> prepareEdit(String question,List<CartItem> items) {
        String q=question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
        Matcher remove=Pattern.compile("^(?:please )?(?:remove|delete) (.+?)(?: from)? (?:my )?(?:cart|basket)$").matcher(q);
        if(remove.matches())return find(remove.group(1),items).map(item->new Edit(item.getProduct().getId(),item.getProduct().getName(),null));
        Matcher quantity=Pattern.compile("^(?:please )?(?:set|change|update) (?:the )?quantity (?:of )?(.+?) (?:to|at) (\\d+)$").matcher(q);
        if(quantity.matches()) {
            int count;try{count=Integer.parseInt(quantity.group(2));}catch(NumberFormatException e){return Optional.empty();}
            if(count<1)return Optional.empty();
            return find(quantity.group(1),items).map(item->new Edit(item.getProduct().getId(),item.getProduct().getName(),count));
        }
        return Optional.empty();
    }
    private static Optional<CartItem> find(String phrase,List<CartItem> items) {
        String wanted=phrase.replaceAll("\\b(the|my|item|product)\\b"," ").replaceAll("\\s+"," ").trim();
        List<CartItem> matches=items.stream().filter(item->{
            String name=item.getProduct().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
            return name.contains(wanted)||wanted.contains(name);
        }).toList();
        return matches.size()==1?Optional.of(matches.getFirst()):Optional.empty();
    }
    public static void applyEdit(User user,Cart cart,Edit edit,ProductService products) {
        CartService.checkOwner(user,cart);
        if(edit.removes()){cart.remove(edit.productId());return;}
        Product latest=products.get(edit.productId());
        if(latest==null||!latest.isActive()||latest.getCategory().isRetired())throw new ValidationException("That product is no longer available.");
        cart.setQuantity(latest,edit.quantity());
    }
    public static List<Product> validate(List<Product> approved, ProductService products) {
        if (approved.isEmpty() || approved.size() > 6) throw new ValidationException("Choose between one and six products.");
        return approved.stream().map(p -> {
            Product current = products.get(p.getId());
            if (current == null || !current.isActive() || current.getCategory().isRetired() || current.getStock() < 1)
                throw new ValidationException(p.getName() + " is no longer available. Ask for updated options.");
            if (current.getPrice().compareTo(p.getPrice()) != 0)
                throw new ValidationException(p.getName() + " changed price. Ask for updated options before adding it.");
            return current;
        }).toList();
    }
    public static void apply(User user, Cart cart, List<Product> validated) {
        CartService.checkOwner(user, cart);
        Cart replacement = new Cart(cart.getCustomerId());
        for (CartItem item : cart.getItems()) replacement.add(item.getProduct(), item.getQuantity());
        for (Product p : validated) replacement.add(p, 1);
        cart.replaceWith(replacement);
    }
}
