package work.spell.iskibal.compiler.java.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of Java compilation, containing generated source files.
 */
public sealed interface CompilationResult permits CompilationResult.Success, CompilationResult.Failure {

	/**
	 * Successful compilation result containing the generated source files.
	 */
	record Success(Map<String, String> sourceFiles) implements CompilationResult {
		@Override
		public boolean isSuccess() {
			return true;
		}

		@Override
		public Optional<Map<String, String>> getSourceFiles() {
			return Optional.of(sourceFiles);
		}

		@Override
		public List<String> getErrors() {
			return List.of();
		}

		/**
		 * Returns the source code for the main generated file.
		 */
		public String getMainSource() {
			if (sourceFiles.isEmpty()) {
				return "";
			}
			return sourceFiles.values().iterator().next();
		}
	}

	/**
	 * Failed compilation result containing error messages.
	 */
	record Failure(List<String> errors) implements CompilationResult {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public Optional<Map<String, String>> getSourceFiles() {
			return Optional.empty();
		}

		@Override
		public List<String> getErrors() {
			return errors;
		}
	}

	/**
	 * Returns true if the compilation was successful.
	 */
	boolean isSuccess();

	/**
	 * Returns the generated source files if successful, empty otherwise. Map keys
	 * are file paths (e.g., "com/example/GeneratedRules.java"), values are source
	 * code.
	 */
	Optional<Map<String, String>> getSourceFiles();

	/**
	 * Returns error messages if failed, empty list otherwise.
	 */
	List<String> getErrors();
}
