package work.spell.iskibal.e2e;

import java.math.BigDecimal;

/**
 * Test fixture class representing a customer for rule tests.
 */
public class Customer {

	private final String name;
	private final BigDecimal age;

	public Customer(String name, int age) {
		this.name = name;
		this.age = BigDecimal.valueOf(age);
	}

	public String getName() {
		return name;
	}

	public BigDecimal getAge() {
		return age;
	}
}
