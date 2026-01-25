package work.spell.iskibal.compiler.java.api;

/**
 * Options for Java code generation.
 *
 * @param packageName
 *            the package name for generated classes
 * @param className
 *            the name of the generated class
 * @param generateNullChecks
 *            whether to generate null safety checks for navigation expressions
 */
public record JavaCompilerOptions(String packageName, String className, boolean generateNullChecks) {

	/**
	 * Creates default options with no package and "GeneratedRules" class name.
	 */
	public static JavaCompilerOptions defaults() {
		return new JavaCompilerOptions("", "GeneratedRules", true);
	}

	/**
	 * Creates options with the specified package and class name, with null checks
	 * enabled.
	 */
	public static JavaCompilerOptions of(String packageName, String className) {
		return new JavaCompilerOptions(packageName, className, true);
	}

	/**
	 * Returns the fully qualified class name.
	 */
	public String fullyQualifiedClassName() {
		if (packageName == null || packageName.isEmpty()) {
			return className;
		}
		return packageName + "." + className;
	}

	/**
	 * Returns the file path for the generated class.
	 */
	public String filePath() {
		String path = className + ".java";
		if (packageName != null && !packageName.isEmpty()) {
			path = packageName.replace('.', '/') + "/" + path;
		}
		return path;
	}
}
