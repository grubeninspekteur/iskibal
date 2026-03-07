package work.spell.iskibal.compiler.java.internal.codegen;

import module java.base;

/// Writes Java method body statements with automatic indentation tracking.
///
/// Instances are created by [RuleClassWriter] and passed to body consumers.
/// Each nested block (if, else) gets a deeper indentation level.
final class BodyWriter {

    private final StringBuilder sb;
    private final String indent;

    BodyWriter(StringBuilder sb, String indent) {
        this.sb = sb;
        this.indent = indent;
    }

    /// Writes `expr;`.
    BodyWriter statement(String expr) {
        sb.append(indent).append(expr).append(";\n");
        return this;
    }

    /// Writes `var name = value;`.
    BodyWriter varDecl(String name, String value) {
        sb.append(indent).append("var ").append(name).append(" = ").append(value).append(";\n");
        return this;
    }

    /// Writes `target = value;`.
    BodyWriter assign(String target, String value) {
        sb.append(indent).append(target).append(" = ").append(value).append(";\n");
        return this;
    }

    /// Writes `name();`.
    BodyWriter callMethod(String name) {
        sb.append(indent).append(name).append("();\n");
        return this;
    }

    /// Writes `if (condition) { ... }`.
    BodyWriter ifBlock(String condition, Consumer<BodyWriter> then) {
        sb.append(indent).append("if (").append(condition).append(") {\n");
        then.accept(nested());
        sb.append(indent).append("}\n");
        return this;
    }

    /// Writes `if (condition) { ... } else { ... }`.
    BodyWriter ifElseBlock(String condition, Consumer<BodyWriter> then, Consumer<BodyWriter> else_) {
        sb.append(indent).append("if (").append(condition).append(") {\n");
        then.accept(nested());
        sb.append(indent).append("} else {\n");
        else_.accept(nested());
        sb.append(indent).append("}\n");
        return this;
    }

    private BodyWriter nested() {
        return new BodyWriter(sb, indent + "\t");
    }
}
