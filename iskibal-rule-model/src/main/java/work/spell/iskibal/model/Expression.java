package work.spell.iskibal.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expressions form the actual executable parts of rules and statements.
 */
public sealed interface Expression permits
        Expression.Identifier,
        Expression.Literal,
        Expression.MessageSend,
        Expression.Binary,
        Expression.Block {

    /** Reference to a named value. */
    record Identifier(String name) implements Expression { }

    /**
     * Literals represent constant values.
     */
    sealed interface Literal extends Expression permits
            Literal.StringLiteral,
            Literal.NumberLiteral,
            Literal.BooleanLiteral,
            Literal.NullLiteral,
            Literal.ListLiteral,
            Literal.SetLiteral,
            Literal.MapLiteral {

        /** String literal */
        record StringLiteral(String value) implements Literal { }

        /** Numeric literal */
        record NumberLiteral(BigDecimal value) implements Literal { }

        /** Boolean literal */
        record BooleanLiteral(boolean value) implements Literal { }

        /** null literal */
        record NullLiteral() implements Literal { }

        /** List literal */
        record ListLiteral(List<Expression> elements) implements Literal { }

        /** Set literal */
        record SetLiteral(Set<Expression> elements) implements Literal { }

        /** Map literal */
        record MapLiteral(Map<Expression, Expression> entries) implements Literal { }
    }

    /**
     * Sends a message to a receiver, similar to Smalltalk style messaging.
     */
    record MessageSend(Expression receiver, List<MessagePart> parts) implements Expression {
        /** A single part of a message consisting of a keyword and an argument. */
        public record MessagePart(String name, Expression argument) { }
    }

    /**
     * Binary operator expression like +, -, =, >= ...
     */
    record Binary(Expression left, Operator operator, Expression right) implements Expression {
        public enum Operator {
            PLUS, MINUS, MULTIPLY, DIVIDE,
            EQUALS, NOT_EQUALS,
            GREATER_THAN, GREATER_EQUALS,
            LESS_THAN, LESS_EQUALS
        }
    }

    /**
     * Block of statements evaluated in order.
     */
    record Block(List<Statement> statements) implements Expression { }
}

