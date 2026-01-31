package work.spell.iskibal.compiler.java.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import work.spell.iskibal.compiler.java.types.JavaType.ClassType;
import work.spell.iskibal.compiler.java.types.JavaType.UnknownType;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Expression.MessageSend;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Statement;

/**
 * Walks the AST and infers Java types for expressions.
 * <p>
 * This visitor produces {@link JavaTypedExpression} instances that pair each
 * expression with its resolved type. The type information is then used during
 * code generation to produce type-correct Java code.
 */
public final class JavaTypeInferenceVisitor {

	private final JavaTypeInferenceContext context;
	private final Map<Expression, JavaTypedExpression> typeCache;

	/**
	 * Creates a visitor with the given context.
	 */
	public JavaTypeInferenceVisitor(JavaTypeInferenceContext context) {
		this(context, new HashMap<>());
	}

	/**
	 * Creates a visitor with the given context and shared cache.
	 */
	private JavaTypeInferenceVisitor(JavaTypeInferenceContext context,
			Map<Expression, JavaTypedExpression> sharedCache) {
		this.context = context;
		this.typeCache = sharedCache;
	}

	/**
	 * Infers the type of an expression.
	 */
	public JavaTypedExpression infer(Expression expr) {
		// Check cache first
		JavaTypedExpression cached = typeCache.get(expr);
		if (cached != null) {
			return cached;
		}

		JavaTypedExpression result = switch (expr) {
			case Identifier id -> inferIdentifier(id);
			case Literal lit -> inferLiteral(lit);
			case MessageSend ms -> inferMessageSend(ms);
			case Binary bin -> inferBinary(bin);
			case Assignment assign -> inferAssignment(assign);
			case Navigation nav -> inferNavigation(nav);
			case Block block -> inferBlock(block);
		};

		typeCache.put(expr, result);
		return result;
	}

	private JavaTypedExpression inferIdentifier(Identifier id) {
		String name = id.name();

		// Handle @ prefix for globals
		if (name.startsWith("@")) {
			String globalName = name.substring(1);
			JavaType type = context.lookupGlobal(globalName);
			return new JavaTypedExpression(id, type);
		}

		JavaType type = context.lookupIdentifier(name);
		return new JavaTypedExpression(id, type);
	}

	private JavaTypedExpression inferLiteral(Literal lit) {
		JavaType type = switch (lit) {
			case Literal.StringLiteral _ -> JavaType.STRING;
			case Literal.NumberLiteral _ -> JavaType.BIG_DECIMAL;
			case Literal.BooleanLiteral _ -> JavaType.PrimitiveType.BOOLEAN;
			case Literal.NullLiteral _ -> JavaType.OBJECT;
			case Literal.ListLiteral ll -> inferListLiteralType(ll);
			case Literal.SetLiteral sl -> inferSetLiteralType(sl);
			case Literal.MapLiteral ml -> inferMapLiteralType(ml);
		};
		return new JavaTypedExpression(lit, type);
	}

	private JavaType inferListLiteralType(Literal.ListLiteral lit) {
		if (lit.elements().isEmpty()) {
			return ClassType.collection("java.util.List", JavaType.OBJECT);
		}
		// Infer element type from first element
		JavaType elementType = infer(lit.elements().getFirst()).type();
		return ClassType.collection("java.util.List", elementType);
	}

	private JavaType inferSetLiteralType(Literal.SetLiteral lit) {
		if (lit.elements().isEmpty()) {
			return ClassType.collection("java.util.Set", JavaType.OBJECT);
		}
		// Infer element type from first element
		JavaType elementType = infer(lit.elements().iterator().next()).type();
		return ClassType.collection("java.util.Set", elementType);
	}

	private JavaType inferMapLiteralType(Literal.MapLiteral lit) {
		if (lit.entries().isEmpty()) {
			return ClassType.map("java.util.Map", JavaType.OBJECT, JavaType.OBJECT);
		}
		// Infer key/value types from first entry
		var firstEntry = lit.entries().entrySet().iterator().next();
		JavaType keyType = infer(firstEntry.getKey()).type();
		JavaType valueType = infer(firstEntry.getValue()).type();
		return ClassType.map("java.util.Map", keyType, valueType);
	}

	private JavaTypedExpression inferMessageSend(MessageSend ms) {
		JavaTypedExpression receiver = infer(ms.receiver());

		JavaType resultType = switch (ms) {
			case MessageSend.UnaryMessage um -> inferUnaryMessageType(receiver, um.selector());
			case MessageSend.KeywordMessage km -> inferKeywordMessageType(receiver, km);
			case MessageSend.DefaultMessage _ -> JavaType.OBJECT; // apply() returns Object
		};

		return new JavaTypedExpression(ms, resultType);
	}

	private JavaType inferUnaryMessageType(JavaTypedExpression receiver, String selector) {
		return switch (selector) {
			// Boolean-returning collection operations
			case "exists", "notEmpty", "doesNotExist", "empty", "isEmpty" -> JavaType.PrimitiveType.BOOLEAN;

			// Numeric aggregation
			case "sum" -> JavaType.BIG_DECIMAL;

			// Size/count operations
			case "size", "count" -> JavaType.PrimitiveType.INT;

			// Default: try to resolve as a method call on the receiver type
			default -> resolveMethodReturnType(receiver.type(), selector);
		};
	}

	private JavaType inferKeywordMessageType(JavaTypedExpression receiver, MessageSend.KeywordMessage km) {
		if (km.parts().size() == 1) {
			String keyword = km.parts().getFirst().keyword();
			Expression arg = km.parts().getFirst().argument();

			return switch (keyword) {
				// Boolean-returning operations
				case "all", "contains", "and", "or" -> JavaType.PrimitiveType.BOOLEAN;

				// Collection filtering - returns same collection type
				case "where" -> receiver.type();

				// Index/key access - returns element type
				case "at" -> {
					if (receiver.type().isCollection()) {
						yield receiver.type().elementType();
					}
					if (receiver.type().isMap()) {
						yield ((ClassType) receiver.type()).valueType();
					}
					yield JavaType.OBJECT;
				}

				// each returns void (side effect)
				case "each" -> JavaType.PrimitiveType.VOID;

				// Default: try to resolve as a method call
				default -> resolveMethodReturnType(receiver.type(), keyword);
			};
		}

		// Multi-keyword message - resolve as method call
		StringBuilder methodName = new StringBuilder();
		boolean first = true;
		for (MessageSend.KeywordMessage.KeywordPart part : km.parts()) {
			if (first) {
				methodName.append(part.keyword());
				first = false;
			} else {
				methodName.append(capitalize(part.keyword()));
			}
		}
		return resolveMethodReturnType(receiver.type(), methodName.toString());
	}

	private JavaType resolveMethodReturnType(JavaType receiverType, String methodName) {
		// For now, return unknown. In a full implementation, we would:
		// 1. Load the class via reflection
		// 2. Find the method by name
		// 3. Return its return type
		// This would require method resolution in JavaTypeResolver
		return UnknownType.withHint(receiverType.qualifiedName() + "." + methodName + "()");
	}

	private JavaTypedExpression inferBinary(Binary bin) {
		JavaTypedExpression left = infer(bin.left());
		JavaTypedExpression right = infer(bin.right());

		JavaType resultType = switch (bin.operator()) {
			// Comparison operators return boolean
			case EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_EQUALS, LESS_THAN, LESS_EQUALS ->
				JavaType.PrimitiveType.BOOLEAN;

			// Arithmetic operators return BigDecimal (Iskara uses BigDecimal for all
			// numbers)
			case PLUS, MINUS, MULTIPLY, DIVIDE -> JavaType.BIG_DECIMAL;
		};

		return new JavaTypedExpression(bin, resultType);
	}

	private JavaTypedExpression inferAssignment(Assignment assign) {
		JavaTypedExpression value = infer(assign.value());
		// Assignment expression has the type of the assigned value
		return new JavaTypedExpression(assign, value.type());
	}

	private JavaTypedExpression inferNavigation(Navigation nav) {
		JavaTypedExpression receiver = infer(nav.receiver());
		JavaType currentType = receiver.type();
		boolean isCollectionNavigation = false;

		for (String name : nav.names()) {
			if (currentType.isCollection()) {
				// Navigation on collection - resolve property on element type
				// This will produce a collection of the property type (flatMap pattern)
				JavaType elementType = currentType.elementType();
				JavaType propertyType = context.resolver().resolveProperty(elementType, name);

				// Result is a collection of the property type
				if (currentType instanceof ClassType ct) {
					currentType = ClassType.collection(ct.qualifiedName(), propertyType);
				} else {
					currentType = ClassType.collection("java.util.List", propertyType);
				}
				isCollectionNavigation = true;
			} else {
				// Regular navigation
				currentType = context.resolver().resolveProperty(currentType, name);
			}
		}

		return new JavaTypedExpression(nav, currentType);
	}

	private JavaTypedExpression inferBlock(Block block) {
		// Create a child scope for the block, but share the type cache
		// so that expressions inside the block can be looked up later during code
		// generation
		JavaTypeInferenceContext blockContext = context.childScope();
		JavaTypeInferenceVisitor blockVisitor = new JavaTypeInferenceVisitor(blockContext, this.typeCache);

		JavaType lastType = JavaType.PrimitiveType.VOID;

		for (Statement stmt : block.statements()) {
			if (stmt instanceof Statement.LetStatement ls) {
				// Declare the variable with its inferred type
				JavaTypedExpression valueTyped = blockVisitor.infer(ls.expression());
				blockContext.declareLocal(ls.name(), valueTyped.type());
				lastType = valueTyped.type();
			} else if (stmt instanceof Statement.ExpressionStatement es) {
				JavaTypedExpression exprTyped = blockVisitor.infer(es.expression());
				lastType = exprTyped.type();
			}
		}

		// Block's type is the type of its last expression
		return new JavaTypedExpression(block, lastType);
	}

	/**
	 * Returns the cached type for an expression, if available.
	 */
	public JavaType getType(Expression expr) {
		JavaTypedExpression typed = typeCache.get(expr);
		return typed != null ? typed.type() : UnknownType.INSTANCE;
	}

	/**
	 * Returns the inference context.
	 */
	public JavaTypeInferenceContext context() {
		return context;
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
