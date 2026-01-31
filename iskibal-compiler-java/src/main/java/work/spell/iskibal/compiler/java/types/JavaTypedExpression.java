package work.spell.iskibal.compiler.java.types;

import java.util.Objects;

import work.spell.iskibal.model.Expression;

/**
 * Wraps an {@link Expression} with its resolved {@link JavaType}.
 * <p>
 * This record is used internally by the Java compiler to pass type information
 * alongside AST nodes during code generation.
 */
public record JavaTypedExpression(Expression expression, JavaType type) {

	public JavaTypedExpression {
		Objects.requireNonNull(expression, "expression");
		Objects.requireNonNull(type, "type");
	}

	/**
	 * Creates a typed expression with an unknown type.
	 */
	public static JavaTypedExpression unknown(Expression expression) {
		return new JavaTypedExpression(expression, JavaType.UnknownType.INSTANCE);
	}

	/**
	 * Returns true if the type was successfully resolved.
	 */
	public boolean isResolved() {
		return !(type instanceof JavaType.UnknownType);
	}

	/**
	 * Returns true if this expression's type is a collection.
	 */
	public boolean isCollection() {
		return type.isCollection();
	}

	/**
	 * Returns true if this expression's type is a record.
	 */
	public boolean isRecord() {
		return type.isRecord();
	}
}
