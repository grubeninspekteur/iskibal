package work.spell.iskibal.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Expressions form the actual executable parts of rules and statements.
public sealed interface Expression permits Expression.Identifier, Expression.Literal, Expression.MessageSend,
        Expression.Binary, Expression.Assignment, Expression.Navigation, Expression.Block {

    /// Reference to a named value.
    record Identifier(String name) implements Expression {
    }

    /// Literals represent constant values.
    sealed interface Literal extends Expression permits Literal.StringLiteral, Literal.NumberLiteral,
            Literal.BooleanLiteral, Literal.NullLiteral, Literal.ListLiteral, Literal.SetLiteral, Literal.MapLiteral {

        /// String literal
        record StringLiteral(String value) implements Literal {
        }

        /// Numeric literal
        record NumberLiteral(BigDecimal value) implements Literal {
        }

        /// Boolean literal
        record BooleanLiteral(boolean value) implements Literal {
        }

        /// null literal
        record NullLiteral() implements Literal {
        }

        /// List literal
        record ListLiteral(List<Expression> elements) implements Literal {
        }

        /// Set literal
        record SetLiteral(Set<Expression> elements) implements Literal {
        }

        /// Map literal
        record MapLiteral(Map<Expression, Expression> entries) implements Literal {
        }
    }

    /// Sends a message to a receiver, similar to Smalltalk style messaging.
    sealed interface MessageSend extends Expression
            permits MessageSend.UnaryMessage, MessageSend.KeywordMessage, MessageSend.DefaultMessage {
        Expression receiver();

        /// Unary message: receiver selector (e.g., machine run)
        record UnaryMessage(Expression receiver, String selector) implements MessageSend {
        }

        /// Keyword message: receiver key1: arg1 key2: arg2 (e.g., errors add: "message")
        record KeywordMessage(Expression receiver, List<KeywordPart> parts) implements MessageSend {
            public record KeywordPart(String keyword, Expression argument) {
            }
        }

        /// Default message: receiver! (explicit default message invocation)
        record DefaultMessage(Expression receiver) implements MessageSend {
        }
    }

    /// Binary operator expression like +, -, =, >= ...
    record Binary(Expression left, Operator operator, Expression right) implements Expression {
        public enum Operator {
            PLUS, MINUS, MULTIPLY, DIVIDE, EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_EQUALS, LESS_THAN, LESS_EQUALS
        }
    }

    /// Assigns the result of an expression to a target.
    record Assignment(Expression target, Expression value) implements Expression {
    }

    /// Navigates through named members of a receiver. The names are applied in
    /// order.
    record Navigation(Expression receiver, List<String> names) implements Expression {
    }

    /// Block of statements evaluated in order.
    ///
    /// @param parameters          explicit parameter names for the block
    /// @param statements          the statements in the block body
    /// @param hasImplicitParameter true if this block uses the shorthand [| expr]
    ///                            syntax with implicit 'it' parameter
    record Block(List<String> parameters, List<Statement> statements, boolean hasImplicitParameter)
            implements Expression {

        /// Convenience constructor for blocks without explicit parameters.
        public Block(List<Statement> statements) {
            this(List.of(), statements, false);
        }
    }
}
