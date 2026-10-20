package edu.course.shop.service;

import edu.course.shop.Services;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import edu.course.shop.payment.CashOnDeliveryPayment;
import static org.junit.jupiter.api.Assertions.*;

class CatalogAssistantTest {
    @TempDir Path temp;
    Services services; CatalogAssistant assistant; User admin;
    @BeforeEach void setup(){
        var db=new DatabaseManager(temp.resolve("chat.db"));db.initialize();var pr=new ProductRepository();var or=new OrderRepository();
        services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new AdminService(db,new AdminRepository()));
        admin=services.auth().login("admin","admin123");
        // Remove seed products from this fixture's searchable inventory.
        for(Product p:services.products().search(admin,"",null))services.products().save(admin,p.getId(),p.getName(),p.getCategory(),p.getPrice(),p.getStock(),false);
        add("MSI Titan Laptop",Category.LAPTOPS,"900",5);add("ASUS TUF Laptop",Category.LAPTOPS,"700",2);
        add("Ducati Desktop",Category.PC_SETS,"1200",5);add("GeForce RTX 5060",Category.PC_PARTS,"400",5);
        add("AMD Ryzen CPU",Category.PC_PARTS,"200",5);add("Aula Keyboard",Category.PERIPHERALS,"50",0);
        add("Logitech Mouse",Category.PERIPHERALS,"30",5);
        assistant=new CatalogAssistant(services);
    }
    void add(String name,Category c,String price,int stock){services.products().save(admin,0,name,c,new BigDecimal(price),stock,true);}
    CatalogAssistant.Plan ask(String q,CatalogAssistant.Memory memory){return assistant.prepare(new Guest(),List.of(),null,memory,q,false);}
    @Test void listsActualLaptopsAndKeepsBudgetOnFollowups(){
        var result=ask("what gaming laptops are currently listed under $1,000",CatalogAssistant.Memory.empty());
        assertEquals(2,result.products().size());assertTrue(result.products().stream().allMatch(p->p.getCategory()==Category.LAPTOPS));
        assertNotNull(result.localAnswer());
        assertTrue(result.localAnswer().contains("MSI Titan Laptop · $900.00 · In stock"));
        assertTrue(result.localAnswer().contains("ASUS TUF Laptop · $700.00 · In stock (2 left)"));
        var memory=CatalogAssistant.remember(CatalogAssistant.Memory.empty(),result,"show laptops",result.localAnswer());
        assertEquals(2,ask("Which has more RAM?",memory).products().size());
        assertEquals("ASUS TUF Laptop",ask("Which is cheaper?",memory).products().getFirst().getName());
        assertEquals(1,ask("Show me something under $800",memory).products().size());
    }
    @Test void broadLaptopListingNamesResultsAndWhereKeepsThatContext(){
        var result=ask("show the all the laptops listed in the store",CatalogAssistant.Memory.empty());
        assertEquals(2,result.products().size());
        assertTrue(result.localAnswer().contains("MSI Titan Laptop"));
        assertTrue(result.localAnswer().contains("ASUS TUF Laptop"));
        var memory=CatalogAssistant.remember(CatalogAssistant.Memory.empty(),result,"show the all the laptops listed in the store",result.localAnswer());
        var where=ask("where",memory);
        assertEquals(result.products().stream().map(Product::getId).toList(),where.products().stream().map(Product::getId).toList());
        assertTrue(where.localAnswer().contains("physical store or aisle locations"));
    }
    @Test void subtypesAndUnknownNamesDoNotReturnUnrelatedProducts(){
        var gpu=ask("show GPUs",CatalogAssistant.Memory.empty());assertEquals(1,gpu.products().size());assertTrue(gpu.products().getFirst().getName().contains("GeForce"));
        assertEquals(1,ask("show keyboards",CatalogAssistant.Memory.empty()).products().size());
        assertTrue(ask("show keyboards in stock",CatalogAssistant.Memory.empty()).products().isEmpty());
        assertTrue(ask("show Zebulon",CatalogAssistant.Memory.empty()).products().isEmpty());
    }
    @Test void explicitComparisonCanCrossCategoriesAndPageContextIsFresh(){
        assertEquals(2,ask("Compare MSI Titan with Ducati PC",CatalogAssistant.Memory.empty()).products().size());
        Product p=services.products().search(new Guest(),"MSI",null).getFirst();
        var result=assistant.prepare(new Guest(),List.of(),p,CatalogAssistant.Memory.empty(),"Explain this",false);
        assertEquals(List.of(p.getId()),result.products().stream().map(Product::getId).toList());
        services.products().save(admin,p.getId(),p.getName(),p.getCategory(),new BigDecimal("850"),1,true);
        var updated=assistant.prepare(new Guest(),List.of(),p,CatalogAssistant.Memory.empty(),"Explain this",false);
        assertEquals(new BigDecimal("850.00"),updated.products().getFirst().getPrice());
    }
    @Test void guestOrdersAreLocalAndCustomerRequiresPermission(){
        assertTrue(ask("Where is my order?",CatalogAssistant.Memory.empty()).localAnswer().contains("Sign in"));
        User customer=services.auth().login("customer","customer123");
        assertThrows(IllegalArgumentException.class,()->assistant.prepare(customer,List.of(),null,CatalogAssistant.Memory.empty(),"Where is my order?",false));
    }
    @Test void demoTrackingQuestionIsAnsweredLocallyFromOwnOrders(){
        User customer=services.auth().login("customer","customer123");
        Product product=services.products().search(new Guest(),"MSI",null).getFirst();Cart cart=new Cart(customer.getId());cart.add(product,1);
        var order=services.checkout().checkout(customer,cart,"123 Private Street",new CashOnDeliveryPayment());
        var plan=assistant.prepare(customer,List.of(),null,CatalogAssistant.Memory.empty(),"Where is my order?",true);
        assertNotNull(plan.localAnswer());assertTrue(plan.localAnswer().contains("no real shipping or live tracking"));
        assertTrue(plan.localAnswer().contains("#"+order.id()));assertTrue(plan.localAnswer().contains("payment PENDING"));
        assertTrue(plan.orderContext());
    }
    @Test void memoryIsBoundedAndOrderAnswersAreNotReplayed(){
        var memory=CatalogAssistant.Memory.empty();var plan=ask("show laptops",memory);
        for(int i=0;i<12;i++)memory=CatalogAssistant.remember(memory,plan,"question","answer");
        assertEquals(4,memory.turns().size());
        var order=ask("where is my order",memory);
        var after=CatalogAssistant.remember(memory,order,"order","PRIVATE");
        assertTrue(after.turns().stream().noneMatch(t->t.answer().contains("PRIVATE")));
    }
    @Test void confirmedBundleDoesNotPartiallyMutateCartAndRejectsPriceChange(){
        Product laptop=services.products().search(new Guest(),"MSI",null).getFirst();
        Product keyboard=services.products().search(new Guest(),"Aula",null).getFirst();
        Cart cart=new Cart(0);
        assertThrows(RuntimeException.class,()->AssistantCart.apply(new Guest(),cart,List.of(laptop,keyboard)));
        assertTrue(cart.getItems().isEmpty());
        services.products().save(admin,laptop.getId(),laptop.getName(),laptop.getCategory(),new BigDecimal("950"),5,true);
        assertThrows(RuntimeException.class,()->AssistantCart.validate(List.of(laptop),services.products()));
        assertTrue(cart.getItems().isEmpty());
    }
}
