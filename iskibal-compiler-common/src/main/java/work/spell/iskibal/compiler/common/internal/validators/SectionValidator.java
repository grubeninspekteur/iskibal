package work.spell.iskibal.compiler.common.internal.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;

/**
 * Validates rule section constraints - ensures rules have required sections.
 */
public final class SectionValidator {

	private static final Set<Binary.Operator> COMPARISON_OPERATORS = Set.of(Binary.Operator.EQUALS,
			Binary.Operator.NOT_EQUALS, Binary.Operator.GREATER_THAN, Binary.Operator.GREATER_EQUALS,
			Binary.Operator.LESS_THAN, Binary.Operator.LESS_EQUALS);

	private final List<SemanticDiagnostic> diagnostics = new ArrayList<>();

	/**
	 * Validates section constraints in the module.
	 *
	 * @param module
	 *            the rule module to validate
	 * @return list of diagnostics (empty if no errors)
	 */
	public List<SemanticDiagnostic> validate(RuleModule module) {
		diagnostics.clear();

		for (Rule rule : module.rules()) {
			validateRule(rule);
		}

		return List.copyOf(diagnostics);
	}

	private void validateRule(Rule rule) {
		switch (rule) {
			case Rule.SimpleRule sr -> validateSimpleRule(sr);
			case Rule.TemplateRule tr -> validateTemplateRule(tr);
			case Rule.DecisionTableRule dtr -> validateDecisionTableRule(dtr);
		}
	}

	private void validateSimpleRule(Rule.SimpleRule rule) {
		// A simple rule must have at least a when or then section
		if (rule.when().isEmpty() && rule.then().isEmpty()) {
			diagnostics.add(SemanticDiagnostic.warning("Rule has no when or then section", rule.id()));
		}

		// Validate disconnected boolean expressions in when section
		validateWhenSection(rule.when(), rule.id());
	}

	private void validateTemplateRule(Rule.TemplateRule rule) {
		// Template rule must have a data table
		if (rule.dataTable() == null) {
			diagnostics.add(SemanticDiagnostic.error("Template rule must have a data table", rule.id()));
		} else if (rule.dataTable().rows().isEmpty()) {
			diagnostics.add(SemanticDiagnostic.warning("Template rule has empty data table", rule.id()));
		}

		// Template rule must have at least when or then
		if (rule.when().isEmpty() && rule.then().isEmpty()) {
			diagnostics.add(SemanticDiagnostic.warning("Template rule has no when or then section", rule.id()));
		}

		// Validate disconnected boolean expressions in when section
		validateWhenSection(rule.when(), rule.id());
	}

	private void validateDecisionTableRule(Rule.DecisionTableRule rule) {
		// Decision table must have at least one row
		if (rule.rows().isEmpty()) {
			diagnostics.add(SemanticDiagnostic.warning("Decision table rule has no rows", rule.id()));
		}

		// Each row must have at least a when or then section
		for (Rule.DecisionTableRule.Row row : rule.rows()) {
			if (row.when().isEmpty() && row.then().isEmpty()) {
				diagnostics.add(SemanticDiagnostic.warning("Decision table row has no when or then section", row.id()));
			}
			// Note: We do NOT validate disconnected boolean expressions in decision table
			// rows
			// because multiple WHEN columns are by design combined with AND
		}
	}

	/**
	 * Validates that boolean expressions in when sections are not disconnected.
	 * According to the Iskara spec, boolean expressions that aren't the last
	 * statement, aren't in a comma expression, and aren't local variable
	 * assignments should be rejected.
	 */
	private void validateWhenSection(List<Statement> statements, String ruleId) {
		if (statements.size() <= 1) {
			return; // Single statement or empty - nothing to validate
		}

		// Check all statements except the last one
		for (int i = 0; i < statements.size() - 1; i++) {
			Statement stmt = statements.get(i);
			if (stmt instanceof Statement.ExpressionStatement exprStmt) {
				if (isBooleanExpression(exprStmt.expression())) {
					diagnostics.add(SemanticDiagnostic.error(
							"Disconnected boolean expression in when section; use comma (,) to combine conditions",
							ruleId));
				}
			}
			// LetStatements are allowed to have boolean expressions
		}
	}

	/**
	 * Checks if an expression is likely to evaluate to a boolean. This includes
	 * comparison operators and boolean literals.
	 */
	private boolean isBooleanExpression(Expression expr) {
		return switch (expr) {
			case Binary bin -> COMPARISON_OPERATORS.contains(bin.operator());
			case Expression.Literal.BooleanLiteral _ -> true;
			default -> false;
		};
	}
}
