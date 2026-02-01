package work.spell.iskibal.e2e;

import java.math.BigDecimal;

/**
 * Test fixture class representing an order for rule tests.
 */
public class Order {

    private final BigDecimal total;
    private final Customer customer;

    public Order(BigDecimal total) {
        this(total, null);
    }

    public Order(BigDecimal total, Customer customer) {
        this.total = total;
        this.customer = customer;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Customer getCustomer() {
        return customer;
    }
}
