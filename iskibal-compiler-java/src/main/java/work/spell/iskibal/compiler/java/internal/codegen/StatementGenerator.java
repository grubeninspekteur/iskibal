package work.spell.iskibal.compiler.java.internal.codegen;

import java.util.List;

import work.spell.iskibal.model.Statement;

/// Generates Java code for Iskara statements.
public final class StatementGenerator {

    private final ExpressionGenerator expressionGenerator;

    public StatementGenerator(ExpressionGenerator expressionGenerator) {
        this.expressionGenerator = expressionGenerator;
    }

    /// Generates Java code for a single statement.
    public String generate(Statement stmt, String indent) {
        return switch (stmt) {
            case Statement.ExpressionStatement es -> indent + expressionGenerator.generate(es.expression()) + ";";
            case Statement.LetStatement ls -> {
                // Register the variable type before generating code
                // so that subsequent expressions can look it up
                expressionGenerator.registerLetVariable(ls.name(), ls.expression());
                yield indent + "var " + ls.name() + " = " + expressionGenerator.generate(ls.expression()) + ";";
            }
        };
    }

    /// Generates Java code for a list of statements.
    public String generateStatements(List<Statement> statements, String indent) {
        StringBuilder sb = new StringBuilder();
        for (Statement stmt : statements) {
            sb.append(generate(stmt, indent)).append("\n");
        }
        return sb.toString();
    }
}
