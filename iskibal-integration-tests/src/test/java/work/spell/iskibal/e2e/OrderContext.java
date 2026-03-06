package work.spell.iskibal.e2e;

import module java.base;

/// Test fixture representing an order context in the e-commerce scenario.
public class OrderContext {

    private final CustomerProfile customer;
    private final List<OrderLine> lines;
    private final String couponCode;

    public OrderContext(CustomerProfile customer, List<OrderLine> lines, String couponCode) {
        this.customer = customer;
        this.lines = lines;
        this.couponCode = couponCode;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public String getCouponCode() {
        return couponCode;
    }
}
