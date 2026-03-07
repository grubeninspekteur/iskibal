package work.spell.iskibal.model;

import org.jspecify.annotations.Nullable;

/// Globals provide additional state shared across rule executions.
public sealed interface Global permits Global.Definition {

    /// name of the global
    String name();

    /// fully qualified type
    String type();

    /// optional description
    @Nullable String description();

    /// Default global declaration.
    record Definition(String name, String type, @Nullable String description) implements Global {
    }
}
