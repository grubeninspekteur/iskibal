package work.spell.iskibal.model;

import org.jspecify.annotations.Nullable;

/// Outputs represent data produced by rule execution.
public sealed interface Output permits Output.Definition {

    /// name of the output
    String name();

    /// fully qualified type
    String type();

    /// initial value assigned before rule execution, or `null` if none
    @Nullable Expression initialValue();

    /// optional description
    @Nullable String description();

    /// Default output declaration.
    record Definition(String name, String type, @Nullable Expression initialValue,
            @Nullable String description) implements Output {
    }
}
