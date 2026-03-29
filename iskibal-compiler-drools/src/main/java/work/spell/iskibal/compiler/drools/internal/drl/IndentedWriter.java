package work.spell.iskibal.compiler.drools.internal.drl;

/// A [StringBuilder] facade that tracks the current indentation level.
///
/// Each call to [indent] increases the level by one unit (4 spaces);
/// [dedent] decreases it. [line] appends a line with the current
/// indentation prefix.
final class IndentedWriter {

    private static final String UNIT = "    ";

    private final StringBuilder sb = new StringBuilder();
    private int level;

    IndentedWriter() {
        this(0);
    }

    IndentedWriter(int initialLevel) {
        this.level = initialLevel;
    }

    /// Increases indentation by one level.
    IndentedWriter indent() {
        level++;
        return this;
    }

    /// Decreases indentation by one level.
    IndentedWriter dedent() {
        if (level > 0) {
            level--;
        }
        return this;
    }

    /// Appends a line with the current indentation, followed by a newline.
    IndentedWriter line(String text) {
        sb.append(UNIT.repeat(level)).append(text).append("\n");
        return this;
    }

    /// Appends raw text without indentation or trailing newline.
    IndentedWriter raw(String text) {
        sb.append(text);
        return this;
    }

    /// Appends an empty line.
    IndentedWriter blankLine() {
        sb.append("\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
