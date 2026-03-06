package work.spell.iskibal.e2e;

/// Test fixture representing a customer profile in the e-commerce scenario.
public class CustomerProfile {

    private final String name;
    private final int age;
    private final int loyaltyPoints;
    private final boolean vip;

    public CustomerProfile(String name, int age, int loyaltyPoints, boolean vip) {
        this.name = name;
        this.age = age;
        this.loyaltyPoints = loyaltyPoints;
        this.vip = vip;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public boolean isVip() {
        return vip;
    }

    public boolean getVip() {
        return vip;
    }
}
