package work.spell.iskibal.compiler.java.internal.codegen;

import module java.base;

import module iskibal.rule.model;

/// Generates Java code for Iskara statements.
public final class StatementGenerator {

    private final ExpressionGenerator expressionGenerator;

    public StatementGenerator(ExpressionGenerator expressionGenerator) {
        this.expressionGenerator = expressionGenerator;
    }

    /// Writes Java code for a single statement into `body`.
    public void generate(Statement stmt, BodyWriter body) {
        switch (stmt) {
            case Statement.ExpressionStatement es -> body.statement(expressionGenerator.generate(es.expression()));
            case Statement.LetStatement ls -> {
                // Register the variable type before generating code
                // so that subsequent expressions can look it up
                expressionGenerator.registerLetVariable(ls.name(), ls.expression());
                body.varDecl(ls.name(), expressionGenerator.generate(ls.expression()));
            }
        }
    }

    /// Writes Java code for a list of statements into `body`.
    public void generate(List<Statement> statements, BodyWriter body) {
        for (Statement stmt : statements) {
            generate(stmt, body);
        }
    }
}
