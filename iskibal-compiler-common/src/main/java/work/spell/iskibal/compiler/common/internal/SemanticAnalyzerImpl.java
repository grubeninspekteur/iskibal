package work.spell.iskibal.compiler.common.internal;

import java.util.ArrayList;
import java.util.List;

import work.spell.iskibal.compiler.common.api.AnalysisResult;
import work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.compiler.common.internal.validators.AssignmentValidator;
import work.spell.iskibal.compiler.common.internal.validators.DeclarationValidator;
import work.spell.iskibal.compiler.common.internal.validators.ReferenceValidator;
import work.spell.iskibal.compiler.common.internal.validators.SectionValidator;
import work.spell.iskibal.model.RuleModule;

/**
 * Implementation of the semantic analyzer that validates a RuleModule against
 * Iskara language rules.
 */
public final class SemanticAnalyzerImpl implements SemanticAnalyzer {

	private final DeclarationValidator declarationValidator = new DeclarationValidator();
	private final ReferenceValidator referenceValidator = new ReferenceValidator();
	private final AssignmentValidator assignmentValidator = new AssignmentValidator();
	private final SectionValidator sectionValidator = new SectionValidator();

	@Override
	public AnalysisResult analyze(RuleModule module) {
		List<SemanticDiagnostic> errors = new ArrayList<>();
		List<SemanticDiagnostic> warnings = new ArrayList<>();

		// Run all validators
		collectDiagnostics(declarationValidator.validate(module), errors, warnings);
		collectDiagnostics(referenceValidator.validate(module), errors, warnings);
		collectDiagnostics(assignmentValidator.validate(module), errors, warnings);
		collectDiagnostics(sectionValidator.validate(module), errors, warnings);

		// Return result based on whether there are errors
		if (!errors.isEmpty()) {
			// Combine errors and warnings for failure result
			List<SemanticDiagnostic> allDiagnostics = new ArrayList<>(errors);
			allDiagnostics.addAll(warnings);
			return new AnalysisResult.Failure(List.copyOf(allDiagnostics));
		}

		return new AnalysisResult.Success(module, List.copyOf(warnings));
	}

	private void collectDiagnostics(List<SemanticDiagnostic> diagnostics, List<SemanticDiagnostic> errors,
			List<SemanticDiagnostic> warnings) {
		for (SemanticDiagnostic diagnostic : diagnostics) {
			switch (diagnostic.severity()) {
				case ERROR -> errors.add(diagnostic);
				case WARNING -> warnings.add(diagnostic);
			}
		}
	}
}
