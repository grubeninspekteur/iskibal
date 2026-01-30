package work.spell.iskibal.e2e;

/**
 * Test fixture class representing a customer for rule tests. Uses int for age
 * to test type coercion between int and BigDecimal.
 */
public class Customer {

	private final String name;
	private final int age;

	public Customer(String name, int age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public int getAge() {
		return age;
	}
}
