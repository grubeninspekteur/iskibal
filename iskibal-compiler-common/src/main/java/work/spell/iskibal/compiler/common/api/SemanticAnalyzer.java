package work.spell.iskibal.compiler.common.api;

import java.util.ServiceLoader;

import work.spell.iskibal.model.RuleModule;

/**
 * Semantic analyzer interface for validating a {@link RuleModule} against
 * Iskara language rules.
 * <p>
 * Implementations are loaded via {@link ServiceLoader}. Use {@link #load()} to
 * obtain an analyzer instance.
 */
public interface SemanticAnalyzer {

	/**
	 * Analyzes the given rule module for semantic errors.
	 *
	 * @param module
	 *            the rule module to analyze
	 * @return the analysis result, either success with optional warnings or failure
	 *         with errors
	 */
	AnalysisResult analyze(RuleModule module);

	/**
	 * Loads a semantic analyzer using the ServiceLoader mechanism.
	 *
	 * @return a semantic analyzer instance
	 * @throws IllegalStateException
	 *             if no analyzer implementation is found
	 */
	static SemanticAnalyzer load() {
		return ServiceLoader.load(SemanticAnalyzer.class).findFirst().orElseThrow(() -> new IllegalStateException(
				"No SemanticAnalyzer implementation found. Ensure iskibal-compiler-common is on the module path."));
	}
}
