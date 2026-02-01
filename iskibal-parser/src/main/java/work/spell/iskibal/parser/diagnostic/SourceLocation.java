package work.spell.iskibal.parser.diagnostic;

/**
 * Represents a location in source code.
 *
 * @param sourceName
 *            the name of the source file or description
 * @param line
 *            the line number (1-based)
 * @param column
 *            the column number (1-based)
 * @param length
 *            the length of the highlighted region
 */
public record SourceLocation(String sourceName, int line, int column, int length) {
    public static SourceLocation at(String sourceName, int line, int column) {
        return new SourceLocation(sourceName, line, column, 1);
    }

    public static SourceLocation at(String sourceName, int line, int column, int length) {
        return new SourceLocation(sourceName, line, column, length);
    }

    @Override
    public String toString() {
        return "%s:%d:%d".formatted(sourceName, line, column);
    }
}
