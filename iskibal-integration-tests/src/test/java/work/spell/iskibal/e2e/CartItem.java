package work.spell.iskibal.e2e;

import java.math.BigDecimal;

/**
 * Test fixture class representing a cart item for collection tests.
 */
public class CartItem {

	private final String name;
	private final BigDecimal price;
	private final boolean active;

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
}
