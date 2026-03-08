package work.spell.iskibal.runtime;

/// Listener for rule execution events, allowing rule evaluation to be observed.
///
/// Implement this interface to receive notifications during rule evaluation.
/// Register a listener by passing it to the generated rule class constructor
/// when diagnostics mode is enabled.
///
/// Example usage:
///
/// ```java
/// RuleListener listener = event -> switch (event) {
///     case RuleEvent.RuleFired(var id, var desc) ->
///         System.out.println("Rule fired: " + id + " – " + desc);
/// };
/// var rules = new GeneratedRules(fact1, fact2, listener);
/// rules.evaluate();
/// ```
@FunctionalInterface
public interface RuleListener {

    /// Called for each event emitted during rule evaluation.
    ///
    /// @param event
    ///            the rule event
    void onEvent(RuleEvent event);

    /// Creates a [RuleListener] that drops all events.
    static RuleListener noOp() {
        return _ -> {};
    }
}
