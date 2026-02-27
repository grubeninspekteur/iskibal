package work.spell.iskibal.e2e;

/// Test fixture class representing a passenger for navigation tests.
public class Passenger {

    private final String name;
    private final Passport passport;

    public Passenger(String name, Passport passport) {
        this.name = name;
        this.passport = passport;
    }

    public String getName() {
        return name;
    }

    public Passport getPassport() {
        return passport;
    }
}
