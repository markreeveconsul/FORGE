package edu.course.shop.service;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import edu.course.shop.exception.*;
import java.sql.*;
import java.time.Instant;
import java.util.List;

/** Local fulfillment workflow. Payment and delivery states are independent. */
public final class OrderService {
    private final DatabaseManager database;private final OrderRepository orders;
    public OrderService(DatabaseManager database,OrderRepository orders){this.database=database;this.orders=orders;}
    private void authorize(Connection c,User user,Order order,boolean adminOnly)throws SQLException{
        if(user==null||(!user.canManageProducts()&&!(user instanceof Customer)))throw new ValidationException("Sign in to view orders.");
        String required=user.canManageProducts()?"ADMIN":"CUSTOMER";
        if(adminOnly&&!required.equals("ADMIN"))throw new ValidationException("Administrator access is required.");
        try(var p=c.prepareStatement("SELECT 1 FROM users WHERE id=? AND role=?")){p.setLong(1,user.getId());p.setString(2,required);try(var r=p.executeQuery()){if(!r.next())throw new ValidationException("Account access is no longer valid.");}}
        if(order!=null&&!user.canManageProducts()&&order.customerId()!=user.getId())throw new ValidationException("This order belongs to another customer.");
    }
    public List<Order> history(User customer){
        if(!(customer instanceof Customer))throw new ValidationException("Customer access is required.");
        try(Connection c=database.open()){authorize(c,customer,null,false);return orders.forCustomer(c,customer.getId());}catch(SQLException e){throw new StorageException(e);}
    }
    public Order get(User actor,long id){try(Connection c=database.open()){Order order=orders.find(c,id);authorize(c,actor,order,false);return order;}catch(SQLException e){throw new StorageException(e);}}
    public Order update(User admin,long id,FulfillmentStatus expected,FulfillmentStatus next,String carrier,String tracking){return change(admin,id,expected,next,carrier,tracking,false);}
    public Order cancel(User customer,long id,FulfillmentStatus expected){
        if(!(customer instanceof Customer))throw new ValidationException("Customer access is required.");
        return change(customer,id,expected,FulfillmentStatus.CANCELLED,"","",true);
    }
    private Order change(User actor,long id,FulfillmentStatus expected,FulfillmentStatus next,String carrier,String tracking,boolean customerCancel){
        if(next==null||expected==null)throw new ValidationException("Choose a delivery status.");
        String courier=carrier==null?"":carrier.trim(),number=tracking==null?"":tracking.trim();
        if(courier.length()>80||number.length()>100||courier.matches("(?s).*\\p{Cntrl}.*")||number.matches("(?s).*\\p{Cntrl}.*"))throw new ValidationException("Enter carrier and tracking details within 80 and 100 characters, without control characters.");
        try(Connection c=database.open()){
            c.setAutoCommit(false);try{
                Order before=orders.find(c,id);authorize(c,actor,before,!customerCancel);
                if(before.fulfillmentStatus()!=expected)throw new ValidationException("This order changed. Refresh it before updating.");
                if(customerCancel&&(expected!=FulfillmentStatus.CONFIRMED||!before.paymentStatus().equals("PENDING")))throw new ValidationException("Only unprocessed, unpaid orders can be cancelled here. Contact the store for other orders.");
                boolean trackingEdit=expected==FulfillmentStatus.SHIPPED&&next==expected&&(!courier.equals(before.carrier())||!number.equals(before.trackingNumber()));
                if(!expected.next().contains(next)&&!trackingEdit)throw new ValidationException("This delivery status change is not allowed.");
                if(next==FulfillmentStatus.SHIPPED&&(courier.isBlank()||number.isBlank()))throw new ValidationException("Enter a carrier and tracking number before marking shipped.");
                if(next!=FulfillmentStatus.SHIPPED){courier=before.carrier();number=before.trackingNumber();}
                String payment=next==FulfillmentStatus.CANCELLED?(before.paymentStatus().equals("PAID")?"REFUNDED":"CANCELLED"):before.paymentStatus();
                try(var p=c.prepareStatement("UPDATE orders SET fulfillment_status=?,carrier=?,tracking_number=?,payment_status=? WHERE id=? AND fulfillment_status=?")){
                    p.setString(1,next.name());p.setString(2,courier);p.setString(3,number);p.setString(4,payment);p.setLong(5,id);p.setString(6,expected.name());if(p.executeUpdate()!=1)throw new ValidationException("This order changed. Refresh before saving.");
                }
                if(next==FulfillmentStatus.CANCELLED)for(OrderItem line:before.items()){
                    try(var p=c.prepareStatement("UPDATE products SET stock=stock+? WHERE id=? AND stock+?<=100000")){
                        p.setInt(1,line.quantity());p.setLong(2,line.productId());p.setInt(3,line.quantity());if(p.executeUpdate()!=1)throw new ValidationException("Could not return the cancelled items to inventory; no changes were saved.");
                    }
                }
                String note=trackingEdit?"Tracking details updated":next==FulfillmentStatus.SHIPPED?"Handed to "+courier+" · "+number:next==FulfillmentStatus.CANCELLED?"Cancelled; inventory restored"+(before.paymentStatus().equals("PAID")?"; simulated refund recorded":""):"Order "+next.toString().toLowerCase(java.util.Locale.ROOT);
                orders.event(c,id,next,Instant.now().toString(),note,actor.getId());Order result=orders.find(c,id);c.commit();return result;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }catch(SQLException e){throw new StorageException(e);}
    }
}