package work.spell.iskibal.e2e;

/**
 * Test fixture class representing a customer with int age for type conversion
 * tests. This class tests what happens when comparing int fields with
 * BigDecimal literals.
 */
public class CustomerWithIntAge {

	private final String name;
	private final int age;

	public CustomerWithIntAge(String name, int age) {
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
