package work.spell.iskibal.compiler.common.internal;

/**
 * Represents a declared name in the symbol table.
 *
 * @param name
 *            the name of the symbol
 * @param kind
 *            the kind of symbol (fact, global, output, local, column)
 * @param type
 *            the type of the symbol (optional, may be null for columns)
 */
public record Symbol(String name, Kind kind, String type) {

    public enum Kind {
        /** Input fact - read-only */
        FACT,
        /** Global variable - accessed with @ prefix */
        GLOBAL,
        /** Output variable - writable only in then/else sections */
        OUTPUT,
        /** Local variable declared with let */
        LOCAL,
        /** Data table column - accessible within template rules */
        COLUMN,
        /** Module-level data table - read-only */
        DATATABLE
    }

    public static Symbol fact(String name, String type) {
        return new Symbol(name, Kind.FACT, type);
    }

    public static Symbol global(String name, String type) {
        return new Symbol(name, Kind.GLOBAL, type);
    }

    public static Symbol output(String name, String type) {
        return new Symbol(name, Kind.OUTPUT, type);
    }

    public static Symbol local(String name) {
        return new Symbol(name, Kind.LOCAL, null);
    }

    public static Symbol column(String name) {
        return new Symbol(name, Kind.COLUMN, null);
    }

    public static Symbol dataTable(String name) {
        return new Symbol(name, Kind.DATATABLE, null);
    }

    public boolean isWritable() {
        return kind == Kind.OUTPUT || kind == Kind.LOCAL;
    }

    public boolean requiresAtPrefix() {
        return kind == Kind.GLOBAL;
    }
}
