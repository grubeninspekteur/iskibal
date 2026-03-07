package work.spell.iskibal.mavenplugin.tests;

import java.math.BigDecimal;

/// An order fact used by the generated shipping rules.
public class Order {

    private final BigDecimal total;

    public Order(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
