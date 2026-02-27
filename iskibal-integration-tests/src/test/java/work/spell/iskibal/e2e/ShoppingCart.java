package work.spell.iskibal.e2e;

import java.util.List;

/// Test fixture class representing a shopping cart for collection tests.
public class ShoppingCart {

    private final List<CartItem> items;

    public ShoppingCart(List<CartItem> items) {
        this.items = items;
    }

    public List<CartItem> getItems() {
        return items;
    }
}
