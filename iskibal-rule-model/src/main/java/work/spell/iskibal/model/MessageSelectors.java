package work.spell.iskibal.model;

/// Constants for well-known Iskara message selectors.
///
/// These selectors have special semantics in the rule model and are
/// recognised by all compiler targets (Java, Drools, etc.).
public final class MessageSelectors {

    private MessageSelectors() {
    }

    // Unary messages
    public static final String NOT_EMPTY = "notEmpty";
    public static final String EMPTY = "empty";
    public static final String EXISTS = "exists";
    public static final String DOES_NOT_EXIST = "doesNotExist";
    public static final String SIZE = "size";
    public static final String SUM = "sum";

    // Keyword messages
    public static final String CONTAINS = "contains";
    public static final String ADD = "add";
    public static final String AT = "at";
    public static final String IF_TRUE = "ifTrue";
    public static final String IF_FALSE = "ifFalse";
    public static final String AND = "and";
    public static final String OR = "or";
    public static final String ALL = "all";
    public static final String EACH = "each";
    public static final String WHERE = "where";
    public static final String TO = "to";
}
