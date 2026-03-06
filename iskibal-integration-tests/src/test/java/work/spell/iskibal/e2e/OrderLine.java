package work.spell.iskibal.e2e;

/// Test fixture representing an order line in the e-commerce scenario.
public class OrderLine {

    private final Product product;
    private final int quantity;

    public OrderLine(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}
