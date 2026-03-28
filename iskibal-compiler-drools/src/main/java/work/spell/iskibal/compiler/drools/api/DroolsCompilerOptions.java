package work.spell.iskibal.compiler.drools.api;

/// Options for Drools DRL code generation.
///
/// @param packageName
///            the package name written in the DRL `package` declaration
/// @param ruleName
///            base name used to derive the `.drl` filename and output holder class
///            name (e.g. `"pricing_rules"` → `pricing_rules.drl` +
///            `PricingRulesOutputs.java`)
public record DroolsCompilerOptions(String packageName, String ruleName) {

    /// Creates default options with no package and `"generated_rules"` base name.
    public static DroolsCompilerOptions defaults() {
        return new DroolsCompilerOptions("", "generated_rules");
    }

    /// Creates options with the specified package and rule name.
    public static DroolsCompilerOptions of(String packageName, String ruleName) {
        return new DroolsCompilerOptions(packageName, ruleName);
    }

    /// Returns the file path for the generated DRL file (relative, using `/`
    /// separators).
    public String drlFilePath() {
        String fileName = ruleName + ".drl";
        if (packageName == null || packageName.isEmpty()) {
            return fileName;
        }
        return packageName.replace('.', '/') + "/" + fileName;
    }

    /// Returns the simple class name for the generated outputs holder POJO.
    public String outputsClassName() {
        // Convert snake_case/kebab-case to PascalCase and append "Outputs"
        String[] parts = ruleName.split("[_\\-\\.]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.append("Outputs").toString();
    }

    /// Returns the fully qualified outputs holder class name.
    public String fullyQualifiedOutputsClassName() {
        if (packageName == null || packageName.isEmpty()) {
            return outputsClassName();
        }
        return packageName + "." + outputsClassName();
    }

    /// Returns the file path for the generated outputs holder Java file (relative).
    public String outputsFilePath() {
        String fileName = outputsClassName() + ".java";
        if (packageName == null || packageName.isEmpty()) {
            return fileName;
        }
        return packageName.replace('.', '/') + "/" + fileName;
    }
}
