package work.spell.iskibal.model;

/// Statements form the building blocks inside rule sections.
public sealed interface Statement permits Statement.ExpressionStatement, Statement.LetStatement {

    /// A statement consisting solely of an expression.
    record ExpressionStatement(Expression expression) implements Statement {
    }

    /// Declares a new local variable.
    record LetStatement(String name, Expression expression) implements Statement {
    }
}
