package work.spell.iskibal.testing;

import java.util.List;
import java.util.Optional;

/**
 * Result of building and compiling a rule test.
 */
public sealed interface RuleTestResult permits RuleTestResult.Success, RuleTestResult.ParseFailure,
		RuleTestResult.AnalysisFailure, RuleTestResult.CodegenFailure, RuleTestResult.CompilationFailure {

	/**
	 * Successful result with compiled and instantiated rules.
	 */
	record Success(CompiledRules compiledRules, String generatedSource) implements RuleTestResult {
		@Override
		public boolean isSuccess() {
			return true;
		}

		@Override
		public Optional<CompiledRules> rules() {
			return Optional.of(compiledRules);
		}

		@Override
		public List<String> getErrors() {
			return List.of();
		}

		@Override
		public String getStage() {
			return "success";
		}
	}

	/**
	 * Failed to parse the Iskara source.
	 */
	record ParseFailure(List<String> errors) implements RuleTestResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<CompiledRules> rules() {
			return Optional.empty();
		}

		@Override
		public List<String> getErrors() {
			return errors;
		}

		@Override
		public String getStage() {
			return "parse";
		}
	}

	/**
	 * Failed semantic analysis.
	 */
	record AnalysisFailure(List<String> errors) implements RuleTestResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<CompiledRules> rules() {
			return Optional.empty();
		}

		@Override
		public List<String> getErrors() {
			return errors;
		}

		@Override
		public String getStage() {
			return "analysis";
		}
	}

	/**
	 * Failed Java code generation.
	 */
	record CodegenFailure(List<String> errors) implements RuleTestResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<CompiledRules> rules() {
			return Optional.empty();
		}

		@Override
		public List<String> getErrors() {
			return errors;
		}

		@Override
		public String getStage() {
			return "codegen";
		}
	}

	/**
	 * Failed Java compilation of generated code.
	 */
	record CompilationFailure(List<String> errors, String generatedSource) implements RuleTestResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<CompiledRules> rules() {
			return Optional.empty();
		}

		@Override
		public List<String> getErrors() {
			return errors;
		}

		@Override
		public String getStage() {
			return "compilation";
		}
	}

	/**
	 * Returns true if all stages succeeded.
	 */
	boolean isSuccess();

	/**
	 * Returns the compiled rules if successful.
	 */
	Optional<CompiledRules> rules();

	/**
	 * Returns error messages from the failed stage.
	 */
	List<String> getErrors();

	/**
	 * Returns the name of the stage that failed (or "success" if all succeeded).
	 */
	String getStage();
}
