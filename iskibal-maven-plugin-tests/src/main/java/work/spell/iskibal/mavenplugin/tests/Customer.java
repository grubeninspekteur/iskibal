package work.spell.iskibal.mavenplugin.tests;

/// A customer fact used by the generated discount rules.
public class Customer {

    private final String name;
    private final int loyaltyPoints;

    public Customer(String name, int loyaltyPoints) {
        this.name = name;
        this.loyaltyPoints = loyaltyPoints;
    }

    public String getName() {
        return name;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }
}
