package work.spell.iskibal.model;

import module java.base;

import org.jspecify.annotations.Nullable;
import work.spell.iskibal.model.Expression.Block;

/// A single rule which can either be a simple rule, a template rule or a
/// decision table rule.
public sealed interface Rule permits Rule.SimpleRule, Rule.TemplateRule, Rule.DecisionTableRule {

    /// identifier of the rule
    String id();

    /// human readable description, or `null` if none was provided
    @Nullable String description();

    /// A standard rule with when/then/else sections.
    record SimpleRule(String id, @Nullable String description, List<Statement> when, List<Statement> then,
            List<Statement> elseStatements) implements Rule {
    }

    /// A template rule is backed by a data table and produces one rule per row.
    record TemplateRule(String id, @Nullable String description, DataTable dataTable, List<Statement> when,
            List<Statement> then) implements Rule {
    }

    /// A decision table rule which may expand to multiple [SimpleRule]s.
    record DecisionTableRule(String id, @Nullable String description, List<Row> rows,
            Map<String, Block> aliases) implements Rule {
        /// A single rule entry generated from a decision table.
        public record Row(String id, List<Statement> when, List<Statement> then) {
        }
    }
}
