package work.spell.iskibal.runtime;

/// A rule engine event emitted during evaluation.
///
/// Events are dispatched to a [RuleListener] when diagnostics mode is enabled.
/// Use pattern matching to handle specific event types:
///
/// ```java
/// RuleListener listener = event -> switch (event) {
///     case RuleEvent.RuleFired(var id, var desc) ->
///         System.out.println("Rule fired: " + id + " – " + desc);
/// };
/// ```
public sealed interface RuleEvent permits RuleEvent.RuleFired {

    /// Emitted when a rule's condition is met and the then-block is about to execute.
    ///
    /// @param ruleId
    ///            the unique identifier of the rule
    /// @param description
    ///            the human-readable description of the rule
    record RuleFired(String ruleId, String description) implements RuleEvent {
    }
}
