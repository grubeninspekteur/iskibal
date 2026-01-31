package work.spell.iskibal.e2e;

import java.math.BigDecimal;

/**
 * Test fixture class representing a calculator for messaging tests.
 */
public class Calculator {

	private final BigDecimal value;

	public Calculator(BigDecimal value) {
		this.value = value;
	}

	public BigDecimal getValue() {
		return value;
	}

	/**
	 * Unary message - negates the value.
	 */
	public BigDecimal negate() {
		return value.negate();
	}

	/**
	 * Single keyword message - adds x to the value.
	 */
	public BigDecimal add(BigDecimal x) {
		return value.add(x);
	}

	/**
	 * Single keyword message - multiplies value by x.
	 */
	public BigDecimal multiply(BigDecimal x) {
		return value.multiply(x);
	}

	/**
	 * Multi-keyword message - scales value by a then adds b. Method name
	 * deliberately chosen to be unique and not confusable with unary calls.
	 */
	public BigDecimal scaleByThenAdd(BigDecimal a, BigDecimal b) {
		return value.multiply(a).add(b);
	}
}
