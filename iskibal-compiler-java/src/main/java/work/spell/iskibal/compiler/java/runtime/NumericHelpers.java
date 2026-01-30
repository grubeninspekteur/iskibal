package work.spell.iskibal.compiler.java.runtime;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Runtime helper methods for type-safe numeric operations in generated rule
 * code.
 * <p>
 * These methods handle automatic type coercion between different numeric types
 * (int, long, double, BigDecimal) that may be returned from fact properties.
 */
public final class NumericHelpers {

	private NumericHelpers() {
		// Utility class
	}

	/**
	 * Converts any numeric value to BigDecimal for consistent arithmetic.
	 *
	 * @param value
	 *            the value to convert
	 * @return the value as BigDecimal
	 * @throws IllegalArgumentException
	 *             if the value cannot be converted
	 */
	public static BigDecimal toBigDecimal(Object value) {
		if (value instanceof BigDecimal bd)
			return bd;
		if (value instanceof Integer i)
			return BigDecimal.valueOf(i);
		if (value instanceof Long l)
			return BigDecimal.valueOf(l);
		if (value instanceof Double d)
			return BigDecimal.valueOf(d);
		if (value instanceof Number n)
			return BigDecimal.valueOf(n.doubleValue());
		throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
	}

	/**
	 * Checks if a value is numeric.
	 *
	 * @param value
	 *            the value to check
	 * @return true if the value is a Number
	 */
	public static boolean isNumeric(Object value) {
		return value instanceof Number;
	}

	/**
	 * Performs numeric-aware equality comparison.
	 * <p>
	 * If both values are numeric, they are compared as BigDecimal values. Otherwise,
	 * standard equals comparison is used.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return true if the values are equal
	 */
	public static boolean equalsNumericAware(Object left, Object right) {
		if (isNumeric(left) && isNumeric(right)) {
			return compareNumeric(left, right) == 0;
		}
		return Objects.equals(left, right);
	}

	/**
	 * Compares two numeric values.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return negative if left &lt; right, zero if equal, positive if left &gt;
	 *         right
	 */
	public static int compareNumeric(Object left, Object right) {
		return toBigDecimal(left).compareTo(toBigDecimal(right));
	}

	/**
	 * Adds two numeric values.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return the sum as BigDecimal
	 */
	public static BigDecimal addNumeric(Object left, Object right) {
		return toBigDecimal(left).add(toBigDecimal(right));
	}

	/**
	 * Subtracts two numeric values.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return the difference as BigDecimal
	 */
	public static BigDecimal subtractNumeric(Object left, Object right) {
		return toBigDecimal(left).subtract(toBigDecimal(right));
	}

	/**
	 * Multiplies two numeric values.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return the product as BigDecimal
	 */
	public static BigDecimal multiplyNumeric(Object left, Object right) {
		return toBigDecimal(left).multiply(toBigDecimal(right));
	}

	/**
	 * Divides two numeric values.
	 *
	 * @param left
	 *            the left operand
	 * @param right
	 *            the right operand
	 * @return the quotient as BigDecimal
	 */
	public static BigDecimal divideNumeric(Object left, Object right) {
		return toBigDecimal(left).divide(toBigDecimal(right));
	}
}
