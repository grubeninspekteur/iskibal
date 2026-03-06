package work.spell.iskibal.e2e;

import module java.base;

/// Test fixture representing a product in the e-commerce scenario.
public class Product {

    private final String name;
    private final String category;
    private final BigDecimal price;
    private final boolean inStock;

    public Product(String name, String category, BigDecimal price, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.inStock = inStock;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isInStock() {
        return inStock;
    }

    public boolean getInStock() {
        return inStock;
    }
}
