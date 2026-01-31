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
 * @param typeClassLoader
 *            the class loader to use for resolving types (null to disable type
 *            inference)
 */
public record JavaCompilerOptions(String packageName, String className, boolean generateNullChecks,
		ClassLoader typeClassLoader) {

	/**
	 * Creates default options with no package and "GeneratedRules" class name.
	 */
	public static JavaCompilerOptions defaults() {
		return new JavaCompilerOptions("", "GeneratedRules", true, null);
	}

	/**
	 * Creates options with the specified package and class name, with null checks
	 * enabled.
	 */
	public static JavaCompilerOptions of(String packageName, String className) {
		return new JavaCompilerOptions(packageName, className, true, null);
	}

	/**
	 * Creates options with the specified package, class name, and null check
	 * setting.
	 */
	public static JavaCompilerOptions of(String packageName, String className, boolean generateNullChecks) {
		return new JavaCompilerOptions(packageName, className, generateNullChecks, null);
	}

	/**
	 * Creates options with type inference enabled using the specified class loader.
	 */
	public static JavaCompilerOptions withTypeInference(String packageName, String className,
			ClassLoader typeClassLoader) {
		return new JavaCompilerOptions(packageName, className, true, typeClassLoader);
	}

	/**
	 * Returns true if type inference is enabled.
	 */
	public boolean typeInferenceEnabled() {
		return typeClassLoader != null;
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
