package work.spell.iskibal.e2e;

/**
 * Test fixture record for testing record accessor patterns. The getter methods
 * are added because the Iskara compiler generates getName() style accessors,
 * but Java records use name() style. These delegation methods provide
 * compatibility.
 */
public record PersonRecord(String name, int age) {

	public String getName() {
		return name;
	}

	public int getAge() {
		return age;
	}
}
