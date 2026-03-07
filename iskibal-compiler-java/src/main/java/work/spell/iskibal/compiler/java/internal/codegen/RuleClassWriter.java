package work.spell.iskibal.compiler.java.internal.codegen;

import module java.base;

/// Builds a Java source file for a rule evaluator class.
///
/// Methods are domain-specific: [ruleMethod] always generates `private void`,
/// [evaluateMethod] always generates `public void evaluate()`, etc.
/// Call [build] to get the final source string.
public final class RuleClassWriter {

    private final StringBuilder sb = new StringBuilder();

    /// Writes a `package` declaration followed by a blank line.
    public RuleClassWriter packageDecl(String pkg) {
        sb.append("package ").append(pkg).append(";\n\n");
        return this;
    }

    /// Writes an `import` line. The argument is everything after `import `.
    public RuleClassWriter importLine(String imp) {
        sb.append("import ").append(imp).append(";\n");
        return this;
    }

    /// Writes a blank line.
    public RuleClassWriter blankLine() {
        sb.append("\n");
        return this;
    }

    /// Opens `public class name {`.
    public RuleClassWriter beginClass(String name) {
        sb.append("public class ").append(name).append(" {\n\n");
        return this;
    }

    /// Closes the class declaration.
    public RuleClassWriter endClass() {
        sb.append("}\n");
        return this;
    }

    /// Writes a `private final type name;` field.
    public RuleClassWriter finalField(String type, String name) {
        sb.append("\tprivate final ").append(type).append(" ").append(name).append(";\n");
        return this;
    }

    /// Writes a `private type name;` mutable field.
    public RuleClassWriter mutableField(String type, String name) {
        sb.append("\tprivate ").append(type).append(" ").append(name).append(";\n");
        return this;
    }

    /// Writes a `private final type name = value;` field.
    ///
    /// The value may span multiple lines (e.g. for complex collection initializers).
    public RuleClassWriter finalFieldWithValue(String type, String name, String value) {
        sb.append("\tprivate final ").append(type).append(" ").append(name).append(" = ").append(value).append(";\n");
        return this;
    }

    /// Writes the public constructor.
    public RuleClassWriter constructor(String className, List<String> params, Consumer<BodyWriter> body) {
        sb.append("\tpublic ").append(className).append("(").append(String.join(", ", params)).append(") {\n");
        body.accept(new BodyWriter(sb, "\t\t"));
        sb.append("\t}\n\n");
        return this;
    }

    /// Writes a `private void` rule method with a Javadoc comment.
    public RuleClassWriter ruleMethod(String name, String description, Consumer<BodyWriter> body) {
        sb.append("\t/**\n\t * ").append(description).append("\n\t */\n");
        sb.append("\tprivate void ").append(name).append("() {\n");
        body.accept(new BodyWriter(sb, "\t\t"));
        sb.append("\t}\n\n");
        return this;
    }

    /// Writes `public void evaluate()` with a Javadoc comment.
    public RuleClassWriter evaluateMethod(Consumer<BodyWriter> body) {
        sb.append("\t/**\n\t * Evaluates all rules in order.\n\t */\n");
        sb.append("\tpublic void evaluate() {\n");
        body.accept(new BodyWriter(sb, "\t\t"));
        sb.append("\t}\n\n");
        return this;
    }

    /// Writes a `public type getCapitalizedName()` getter returning `this.fieldName`.
    public RuleClassWriter getter(String type, String fieldName, String capitalizedName) {
        sb.append("\tpublic ").append(type).append(" get").append(capitalizedName).append("() {\n");
        sb.append("\t\treturn this.").append(fieldName).append(";\n");
        sb.append("\t}\n\n");
        return this;
    }

    /// Returns the generated Java source code.
    public String build() {
        return sb.toString();
    }
}
