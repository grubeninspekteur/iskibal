package work.spell.iskibal.compiler.java.internal.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Expression.MessageSend;
import work.spell.iskibal.model.Expression.MessageSend.DefaultMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage;
import work.spell.iskibal.model.Expression.MessageSend.UnaryMessage;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Statement;

/**
 * Generates Java code for Iskara expressions.
 */
public final class ExpressionGenerator {

	private final JavaCompilerOptions options;
	private final Set<String> globalNames;
	private final Set<String> outputNames;
	private final Map<String, String> outputTypes;

	public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames) {
		this(options, globalNames, outputNames, Map.of());
	}

	public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames,
			Map<String, String> outputTypes) {
		this.options = options;
		this.globalNames = globalNames;
		this.outputNames = outputNames;
		this.outputTypes = outputTypes;
	}

	/**
	 * Generates Java code for an expression.
	 */
	public String generate(Expression expr) {
		return switch (expr) {
			case Identifier id -> generateIdentifier(id);
			case Literal lit -> generateLiteral(lit);
			case MessageSend ms -> generateMessageSend(ms);
			case Binary bin -> generateBinary(bin);
			case Assignment assign -> generateAssignment(assign);
			case Navigation nav -> generateNavigation(nav);
			case Block block -> generateBlock(block);
		};
	}

	private String generateIdentifier(Identifier id) {
		String name = id.name();

		// Handle @ prefix for globals
		if (name.startsWith("@")) {
			String globalName = name.substring(1);
			return "this." + globalName;
		}

		// Outputs are accessed via this.
		if (outputNames.contains(name)) {
			return "this." + name;
		}

		return name;
	}

	private String generateLiteral(Literal lit) {
		return switch (lit) {
			case Literal.StringLiteral sl -> "\"" + escapeString(sl.value()) + "\"";
			case Literal.NumberLiteral nl -> "new java.math.BigDecimal(\"" + nl.value().toPlainString() + "\")";
			case Literal.BooleanLiteral bl -> String.valueOf(bl.value());
			case Literal.NullLiteral _ -> "null";
			case Literal.ListLiteral ll -> generateListLiteral(ll);
			case Literal.SetLiteral sl -> generateSetLiteral(sl);
			case Literal.MapLiteral ml -> generateMapLiteral(ml);
		};
	}

	private String generateListLiteral(Literal.ListLiteral lit) {
		if (lit.elements().isEmpty()) {
			return "java.util.List.of()";
		}
		String elements = lit.elements().stream().map(this::generate).collect(Collectors.joining(", "));
		return "java.util.List.of(" + elements + ")";
	}

	private String generateSetLiteral(Literal.SetLiteral lit) {
		if (lit.elements().isEmpty()) {
			return "java.util.Set.of()";
		}
		String elements = lit.elements().stream().map(this::generate).collect(Collectors.joining(", "));
		return "java.util.Set.of(" + elements + ")";
	}

	private String generateMapLiteral(Literal.MapLiteral lit) {
		if (lit.entries().isEmpty()) {
			return "java.util.Map.of()";
		}
		// Use Map.ofEntries to support any number of entries (Map.of only supports up to 10)
		String entries = lit.entries().entrySet().stream()
				.map(e -> "java.util.Map.entry(" + generate(e.getKey()) + ", " + generate(e.getValue()) + ")")
				.collect(Collectors.joining(", "));
		return "java.util.Map.ofEntries(" + entries + ")";
	}

	private String generateMessageSend(MessageSend ms) {
		String receiver = generate(ms.receiver());

		return switch (ms) {
			case UnaryMessage um -> generateUnaryMessage(receiver, um.selector());
			case KeywordMessage km -> generateKeywordMessage(receiver, km);
			case DefaultMessage _ -> {
				// Default message: receiver! -> receiver.apply() or similar
				yield receiver + ".apply()";
			}
		};
	}

	private String generateUnaryMessage(String receiver, String selector) {
		// Handle special collection unary messages
		return switch (selector) {
			case "exists", "notEmpty" -> "!" + receiver + ".isEmpty()";
			case "doesNotExist", "empty" -> receiver + ".isEmpty()";
			case "sum" -> receiver + ".stream().reduce(java.math.BigDecimal.ZERO, (a, b) -> a.add(toBigDecimal(b)))";
			default -> receiver + "." + selector + "()";
		};
	}

	private String generateKeywordMessage(String receiver, KeywordMessage km) {
		if (km.parts().size() == 1) {
			KeywordMessage.KeywordPart part = km.parts().getFirst();
			String keyword = part.keyword();
			String arg = generate(part.argument());

			// Handle special collection keyword messages
			return switch (keyword) {
				case "all" -> receiver + ".stream().allMatch(" + arg + ")";
				case "each" -> receiver + ".forEach(" + arg + ")";
				case "where" -> receiver + ".stream().filter(" + arg + ").toList()";
				case "at" -> generateAtAccess(receiver, arg);
				case "contains" -> receiver + ".contains(" + arg + ")";
				case "and" -> receiver + " && " + arg;
				case "or" -> receiver + " || " + arg;
				default -> receiver + "." + keyword + "(" + arg + ")";
			};
		}
		// Multi-keyword message: receiver k1: a1 k2: a2 -> receiver.k1K2(a1, a2)
		StringBuilder methodName = new StringBuilder();
		StringBuilder args = new StringBuilder();
		boolean first = true;
		for (KeywordMessage.KeywordPart part : km.parts()) {
			if (first) {
				methodName.append(part.keyword());
				first = false;
			} else {
				methodName.append(capitalize(part.keyword()));
				args.append(", ");
			}
			args.append(generate(part.argument()));
		}
		return receiver + "." + methodName + "(" + args + ")";
	}

	private String generateAtAccess(String receiver, String indexOrKey) {
		// The at: message is used for both list index access and map key access.
		// Use runtime helper that handles both cases.
		return "at(" + receiver + ", " + indexOrKey + ")";
	}

	private String generateBinary(Binary bin) {
		String left = generate(bin.left());
		String right = generate(bin.right());

		// Use helper methods for type-safe numeric operations
		// This handles both BigDecimal and primitive int/long values
		return switch (bin.operator()) {
			case EQUALS -> "equalsNumericAware(" + left + ", " + right + ")";
			case NOT_EQUALS -> "!equalsNumericAware(" + left + ", " + right + ")";
			case PLUS -> "addNumeric(" + left + ", " + right + ")";
			case MINUS -> "subtractNumeric(" + left + ", " + right + ")";
			case MULTIPLY -> "multiplyNumeric(" + left + ", " + right + ")";
			case DIVIDE -> "divideNumeric(" + left + ", " + right + ")";
			case GREATER_THAN -> "compareNumeric(" + left + ", " + right + ") > 0";
			case GREATER_EQUALS -> "compareNumeric(" + left + ", " + right + ") >= 0";
			case LESS_THAN -> "compareNumeric(" + left + ", " + right + ") < 0";
			case LESS_EQUALS -> "compareNumeric(" + left + ", " + right + ") <= 0";
		};
	}

	private String generateAssignment(Assignment assign) {
		// Handle navigation assignment specially - generates setter call
		if (assign.target() instanceof Navigation nav) {
			return generateNavigationAssignment(nav, assign.value());
		}

		String target = generateAssignmentTarget(assign.target());
		String value = generate(assign.value());

		// Apply type coercion for output assignments
		if (assign.target() instanceof Identifier id) {
			String outputName = id.name();
			if (outputNames.contains(outputName)) {
				String targetType = outputTypes.get(outputName);
				value = applyTypeCoercion(value, targetType);
			}
		}

		return target + " = " + value;
	}

	private String applyTypeCoercion(String value, String targetType) {
		if (targetType == null) {
			return value;
		}
		return switch (targetType) {
			case "int", "Integer", "java.lang.Integer" -> "toInt(" + value + ")";
			case "long", "Long", "java.lang.Long" -> "toLong(" + value + ")";
			case "float", "Float", "java.lang.Float" -> "toFloat(" + value + ")";
			case "double", "Double", "java.lang.Double" -> "toDouble(" + value + ")";
			case "BigInteger", "java.math.BigInteger" -> "toBigInteger(" + value + ")";
			case "BigDecimal", "java.math.BigDecimal" -> "toBigDecimal(" + value + ")";
			default -> value;
		};
	}

	private String generateNavigationAssignment(Navigation nav, Expression value) {
		String receiver = generate(nav.receiver());
		String valueCode = generate(value);

		// Build the navigation path, using getters for all but the last property
		StringBuilder sb = new StringBuilder();
		sb.append(receiver);
		for (int i = 0; i < nav.names().size() - 1; i++) {
			String name = nav.names().get(i);
			sb.append(".get").append(capitalize(name)).append("()");
		}

		// Use setter for the last property
		String lastName = nav.names().getLast();
		sb.append(".set").append(capitalize(lastName)).append("(").append(valueCode).append(")");

		return sb.toString();
	}

	private String generateAssignmentTarget(Expression target) {
		return switch (target) {
			case Identifier id -> {
				String name = id.name();
				if (name.startsWith("@")) {
					yield "this." + name.substring(1);
				}
				if (outputNames.contains(name)) {
					yield "this." + name;
				}
				yield name;
			}
			case Navigation nav -> generateNavigation(nav);
			default -> generate(target);
		};
	}

	private String generateNavigation(Navigation nav) {
		String receiver = generate(nav.receiver());

		if (options.generateNullChecks() && nav.names().size() > 1) {
			// Generate null-safe navigation using Optional for chains
			StringBuilder sb = new StringBuilder();
			sb.append("java.util.Optional.ofNullable(").append(receiver).append(")");

			// Add .map() for each intermediate step (all but the last)
			for (int i = 0; i < nav.names().size() - 1; i++) {
				String name = nav.names().get(i);
				sb.append(".map(v -> v.get").append(capitalize(name)).append("())");
			}

			// Add final .map() and .orElse(null) for the last property
			String lastName = nav.names().getLast();
			sb.append(".map(v -> v.get").append(capitalize(lastName)).append("())");
			sb.append(".orElse(null)");

			return sb.toString();
		} else {
			// Simple navigation without null checks
			StringBuilder sb = new StringBuilder();
			sb.append(receiver);
			for (String name : nav.names()) {
				sb.append(".get").append(capitalize(name)).append("()");
			}
			return sb.toString();
		}
	}

	private String generateBlock(Block block) {
		// Extract block parameters (represented as LetStatement with
		// Identifier("param"))
		List<String> params = new ArrayList<>();
		List<Statement> bodyStatements = new ArrayList<>();

		for (Statement stmt : block.statements()) {
			if (stmt instanceof Statement.LetStatement ls && ls.expression() instanceof Identifier id
					&& "param".equals(id.name())) {
				// This is a block parameter placeholder
				params.add(ls.name());
			} else {
				bodyStatements.add(stmt);
			}
		}

		// Build lambda parameter list
		String paramList = params.isEmpty()
				? "()"
				: params.size() == 1 ? params.getFirst() : "(" + String.join(", ", params) + ")";

		// For simple single-expression blocks, generate inline
		if (bodyStatements.size() == 1 && bodyStatements.getFirst() instanceof Statement.ExpressionStatement es) {
			return paramList + " -> " + generate(es.expression());
		}

		// Multi-statement blocks generate a Supplier or Runnable
		StringBuilder sb = new StringBuilder();
		sb.append(paramList).append(" -> {\n");
		for (Statement stmt : bodyStatements) {
			sb.append("    ");
			if (stmt instanceof Statement.ExpressionStatement es) {
				if (stmt == bodyStatements.getLast()) {
					sb.append("return ").append(generate(es.expression())).append(";\n");
				} else {
					sb.append(generate(es.expression())).append(";\n");
				}
			} else if (stmt instanceof Statement.LetStatement ls) {
				sb.append("var ").append(ls.name()).append(" = ").append(generate(ls.expression())).append(";\n");
			}
		}
		sb.append("}");
		return sb.toString();
	}

	private static String escapeString(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
				"\\t");
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
