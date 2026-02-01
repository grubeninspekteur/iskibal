package work.spell.iskibal.e2e;

import java.math.BigDecimal;

/**
 * Test fixture class representing a cart item for collection tests.
 */
public class CartItem {

    private String name;
    private BigDecimal price;
    private boolean active;

    public CartItem(String name, BigDecimal price, boolean active) {
        this.name = name;
        this.price = price;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    /** Alias for isActive() to support JavaBeans-style navigation */
    public boolean getActive() {
        return active;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
