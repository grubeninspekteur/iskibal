package work.spell.iskibal.e2e;

/**
 * Test fixture class representing a passport for navigation tests.
 */
public class Passport {

    private final boolean valid;
    private final String countryCode;

    public Passport(boolean valid, String countryCode) {
        this.valid = valid;
        this.countryCode = countryCode;
    }

    public boolean isValid() {
        return valid;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
