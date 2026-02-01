package work.spell.iskibal.model;

/**
 * Maps an alias used in rules to a fully qualified Java type name.
 */
public sealed interface Import permits Import.Definition {

    /** alias used inside rules */
    String alias();

    /** fully qualified class name */
    String type();

    /**
     * Default implementation of an {@link Import} definition.
     */
    record Definition(String alias, String type) implements Import {
    }
}
