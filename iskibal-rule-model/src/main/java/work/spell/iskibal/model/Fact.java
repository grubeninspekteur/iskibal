package work.spell.iskibal.model;

import org.jspecify.annotations.Nullable;

/// A fact describes an input object that is available to all rules.
public sealed interface Fact permits Fact.Definition {

    /// name of the fact
    String name();

    /// fully qualified type of the fact
    String type();

    /// optional description to aid documentation
    @Nullable String description();

    /// Default fact declaration.
    record Definition(String name, String type, @Nullable String description) implements Fact {
    }
}
