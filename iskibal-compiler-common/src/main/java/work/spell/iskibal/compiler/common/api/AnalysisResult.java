package work.spell.iskibal.compiler.common.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import work.spell.iskibal.model.RuleModule;

/**
 * Result of semantic analysis, either success with optional warnings, or
 * failure with a list of errors.
 */
public sealed interface AnalysisResult permits AnalysisResult.Success, AnalysisResult.Failure {

	/**
	 * Successful analysis result containing the validated module and any warnings.
	 */
	record Success(RuleModule module, List<SemanticDiagnostic> warnings) implements AnalysisResult {
		public Success(RuleModule module) {
			this(module, List.of());
		}

		@Override
		public boolean isSuccess() {
			return true;
		}

		@Override
		public Optional<RuleModule> getModule() {
			return Optional.of(module);
		}

		@Override
		public List<SemanticDiagnostic> getDiagnostics() {
			return warnings;
		}

		@Override
		public List<SemanticDiagnostic> getErrors() {
			return List.of();
		}
	}

	/**
	 * Failed analysis result containing the list of errors.
	 */
	record Failure(List<SemanticDiagnostic> errors) implements AnalysisResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<RuleModule> getModule() {
			return Optional.empty();
		}

		@Override
		public List<SemanticDiagnostic> getDiagnostics() {
			return errors;
		}

		@Override
		public List<SemanticDiagnostic> getErrors() {
			return errors;
		}
	}

	/**
	 * Returns true if the analysis was successful.
	 */
	boolean isSuccess();

	/**
	 * Returns the validated module if successful, empty otherwise.
	 */
	Optional<RuleModule> getModule();

	/**
	 * Returns all diagnostics (warnings for success, errors for failure).
	 */
	List<SemanticDiagnostic> getDiagnostics();

	/**
	 * Returns errors only (empty for success).
	 */
	List<SemanticDiagnostic> getErrors();

	/**
	 * Maps the module if successful, preserving warnings.
	 */
	default <U> AnalysisResult map(Function<RuleModule, RuleModule> mapper) {
		return switch (this) {
			case Success(var module, var warnings) -> new Success(mapper.apply(module), warnings);
			case Failure f -> f;
		};
	}
}
