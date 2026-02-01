package work.spell.iskibal.compiler.common.internal.validators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Expression.MessageSend;
import work.spell.iskibal.model.Expression.MessageSend.DefaultMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage;
import work.spell.iskibal.model.Expression.MessageSend.UnaryMessage;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;

/**
 * Validates assignment expressions - ensures facts are not assigned to and
 * outputs are only assigned in then/else sections.
 */
public final class AssignmentValidator {

    private final List<SemanticDiagnostic> diagnostics = new ArrayList<>();

    /**
     * Validates assignment rules in the module.
     *
     * @param module
     *            the rule module to validate
     * @return list of diagnostics (empty if no errors)
     */
    public List<SemanticDiagnostic> validate(RuleModule module) {
        diagnostics.clear();

        Set<String> factNames = new HashSet<>();
        for (Fact fact : module.facts()) {
            factNames.add(fact.name());
        }

        Set<String> outputNames = new HashSet<>();
        for (Output output : module.outputs()) {
            outputNames.add(output.name());
        }

        for (Rule rule : module.rules()) {
            validateRule(rule, factNames, outputNames);
        }

        return List.copyOf(diagnostics);
    }

    private void validateRule(Rule rule, Set<String> factNames, Set<String> outputNames) {
        switch (rule) {
            case Rule.SimpleRule sr -> validateSimpleRule(sr, factNames, outputNames);
            case Rule.TemplateRule tr -> validateTemplateRule(tr, factNames, outputNames);
            case Rule.DecisionTableRule dtr -> validateDecisionTableRule(dtr, factNames, outputNames);
        }
    }

    private void validateSimpleRule(Rule.SimpleRule rule, Set<String> factNames, Set<String> outputNames) {
        // When section - outputs cannot be assigned here
        for (Statement stmt : rule.when()) {
            validateStatement(stmt, factNames, outputNames, false);
        }

        // Then section - outputs can be assigned
        for (Statement stmt : rule.then()) {
            validateStatement(stmt, factNames, outputNames, true);
        }

        // Else section - outputs can be assigned
        for (Statement stmt : rule.elseStatements()) {
            validateStatement(stmt, factNames, outputNames, true);
        }
    }

    private void validateTemplateRule(Rule.TemplateRule rule, Set<String> factNames, Set<String> outputNames) {
        // Add column names as protected (cannot assign to them)
        Set<String> protectedNames = new HashSet<>(factNames);
        DataTable table = rule.dataTable();
        if (table != null && !table.rows().isEmpty()) {
            protectedNames.addAll(table.rows().getFirst().values().keySet());
        }

        // When section - outputs cannot be assigned
        for (Statement stmt : rule.when()) {
            validateStatement(stmt, protectedNames, outputNames, false);
        }

        // Then section - outputs can be assigned
        for (Statement stmt : rule.then()) {
            validateStatement(stmt, protectedNames, outputNames, true);
        }
    }

    private void validateDecisionTableRule(Rule.DecisionTableRule rule, Set<String> factNames,
            Set<String> outputNames) {
        for (Rule.DecisionTableRule.Row row : rule.rows()) {
            // When section - outputs cannot be assigned
            for (Statement stmt : row.when()) {
                validateStatement(stmt, factNames, outputNames, false);
            }

            // Then section - outputs can be assigned
            for (Statement stmt : row.then()) {
                validateStatement(stmt, factNames, outputNames, true);
            }
        }
    }

    private void validateStatement(Statement stmt, Set<String> factNames, Set<String> outputNames,
            boolean inActionSection) {
        switch (stmt) {
            case Statement.ExpressionStatement es ->
                validateExpression(es.expression(), factNames, outputNames, inActionSection);
            case Statement.LetStatement ls ->
                validateExpression(ls.expression(), factNames, outputNames, inActionSection);
        }
    }

    private void validateExpression(Expression expr, Set<String> factNames, Set<String> outputNames,
            boolean inActionSection) {
        switch (expr) {
            case Assignment assign -> {
                validateAssignmentTarget(assign.target(), factNames, outputNames, inActionSection);
                validateExpression(assign.value(), factNames, outputNames, inActionSection);
            }
            case MessageSend ms -> {
                validateExpression(ms.receiver(), factNames, outputNames, inActionSection);
                switch (ms) {
                    case UnaryMessage _ -> {
                        // No arguments to validate
                    }
                    case KeywordMessage km -> {
                        for (KeywordMessage.KeywordPart part : km.parts()) {
                            validateExpression(part.argument(), factNames, outputNames, inActionSection);
                        }
                    }
                    case DefaultMessage _ -> {
                        // No arguments to validate
                    }
                }
            }
            case Binary bin -> {
                validateExpression(bin.left(), factNames, outputNames, inActionSection);
                validateExpression(bin.right(), factNames, outputNames, inActionSection);
            }
            case Navigation nav -> validateExpression(nav.receiver(), factNames, outputNames, inActionSection);
            case Block block -> {
                for (Statement stmt : block.statements()) {
                    validateStatement(stmt, factNames, outputNames, inActionSection);
                }
            }
            case Literal lit -> validateLiteral(lit, factNames, outputNames, inActionSection);
            case Identifier _ -> {
                // No validation needed for reading identifiers
            }
        }
    }

    private void validateLiteral(Literal lit, Set<String> factNames, Set<String> outputNames, boolean inActionSection) {
        switch (lit) {
            case Literal.ListLiteral ll -> {
                for (Expression elem : ll.elements()) {
                    validateExpression(elem, factNames, outputNames, inActionSection);
                }
            }
            case Literal.SetLiteral sl -> {
                for (Expression elem : sl.elements()) {
                    validateExpression(elem, factNames, outputNames, inActionSection);
                }
            }
            case Literal.MapLiteral ml -> {
                for (Map.Entry<Expression, Expression> entry : ml.entries().entrySet()) {
                    validateExpression(entry.getKey(), factNames, outputNames, inActionSection);
                    validateExpression(entry.getValue(), factNames, outputNames, inActionSection);
                }
            }
            default -> {
                // Primitive literals need no validation
            }
        }
    }

    private void validateAssignmentTarget(Expression target, Set<String> factNames, Set<String> outputNames,
            boolean inActionSection) {
        switch (target) {
            case Identifier id -> {
                String name = id.name();
                if (factNames.contains(name)) {
                    diagnostics.add(SemanticDiagnostic.error("Cannot assign to fact", name));
                }
                if (outputNames.contains(name) && !inActionSection) {
                    diagnostics.add(SemanticDiagnostic.error("Output can only be assigned in then/else section", name));
                }
            }
            case Navigation nav -> {
                // Navigation assignments (e.g., customer.name := "VIP") are only allowed
                // in action sections (then/else). In when sections, only local variable
                // assignments are allowed.
                if (!inActionSection) {
                    diagnostics.add(SemanticDiagnostic.error(
                            "Navigation assignment not allowed in when section; only local variables can be assigned",
                            nav.toString()));
                }
            }
            default -> {
                // Other assignment targets (like message sends) are handled by validation
            }
        }
    }
}
