package work.spell.iskibal.model;

/**
 * Outputs represent data produced by rule execution.
 */
public sealed interface Output permits Output.Definition {

    /** name of the output */
    String name();

    /** fully qualified type */
    String type();

    /** initial value assigned before rule execution */
    Expression initialValue();

    /** optional description */
    String description();

    /**
     * Default output declaration.
     */
    record Definition(String name, String type, Expression initialValue, String description)
            implements Output { }
}

