package work.spell.iskibal.compiler.common.internal.validators;

import java.util.ArrayList;
import java.util.List;

import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;

/**
 * Validates rule section constraints - ensures rules have required sections.
 */
public final class SectionValidator {

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
		}
	}
}
