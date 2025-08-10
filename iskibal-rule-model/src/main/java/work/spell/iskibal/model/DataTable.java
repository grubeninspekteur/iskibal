package work.spell.iskibal.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a table of data that can be referenced from rules.
 */
public sealed interface DataTable permits DataTable.Default {

    /** identifier used for referencing the table */
    String id();

    /** rows of the table */
    List<Row> rows();

    /**
     * A single row inside a data table. Each column name maps to an expression
     * value which can be of any supported type.
     */
    record Row(Map<String, Expression> values) { }

    /**
     * Default implementation of a {@link DataTable}.
     */
    record Default(String id, List<Row> rows) implements DataTable { }
}

