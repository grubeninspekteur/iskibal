package work.spell.iskibal.e2e;

/// Mutable test fixture for testing navigation assignment (setters). Unlike
/// Customer, this class has setters to allow property modification.
public class MutableCustomer {

    private String name;
    private String category;

    public MutableCustomer(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
