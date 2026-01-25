package work.spell.iskibal.compiler.java.internal.codegen;

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
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Statement;

/**
 * Generates Java code for Iskara expressions.
 */
public final class ExpressionGenerator {

	private final JavaCompilerOptions options;
	private final Set<String> globalNames;
	private final Set<String> outputNames;

	public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames) {
		this.options = options;
		this.globalNames = globalNames;
		this.outputNames = outputNames;
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
		String entries = lit.entries().entrySet().stream()
				.map(e -> generate(e.getKey()) + ", " + generate(e.getValue())).collect(Collectors.joining(", "));
		return "java.util.Map.of(" + entries + ")";
	}

	private String generateMessageSend(MessageSend ms) {
		String receiver = generate(ms.receiver());

		// Build method name from keyword parts (Smalltalk style)
		if (ms.parts().size() == 1) {
			// Single keyword message: receiver keyword: arg -> receiver.keyword(arg)
			MessageSend.MessagePart part = ms.parts().getFirst();
			String methodName = part.name();
			String arg = generate(part.argument());
			return receiver + "." + methodName + "(" + arg + ")";
		}

		// Multi-keyword message: receiver k1: a1 k2: a2 -> receiver.k1K2(a1, a2)
		StringBuilder methodName = new StringBuilder();
		StringBuilder args = new StringBuilder();
		boolean first = true;
		for (MessageSend.MessagePart part : ms.parts()) {
			if (first) {
				methodName.append(part.name());
				first = false;
			} else {
				methodName.append(capitalize(part.name()));
				args.append(", ");
			}
			args.append(generate(part.argument()));
		}
		return receiver + "." + methodName + "(" + args + ")";
	}

	private String generateBinary(Binary bin) {
		String left = generate(bin.left());
		String right = generate(bin.right());

		return switch (bin.operator()) {
			case EQUALS -> "java.util.Objects.equals(" + left + ", " + right + ")";
			case NOT_EQUALS -> "!java.util.Objects.equals(" + left + ", " + right + ")";
			case PLUS -> left + ".add(" + right + ")";
			case MINUS -> left + ".subtract(" + right + ")";
			case MULTIPLY -> left + ".multiply(" + right + ")";
			case DIVIDE -> left + ".divide(" + right + ")";
			case GREATER_THAN -> left + ".compareTo(" + right + ") > 0";
			case GREATER_EQUALS -> left + ".compareTo(" + right + ") >= 0";
			case LESS_THAN -> left + ".compareTo(" + right + ") < 0";
			case LESS_EQUALS -> left + ".compareTo(" + right + ") <= 0";
		};
	}

	private String generateAssignment(Assignment assign) {
		String target = generateAssignmentTarget(assign.target());
		String value = generate(assign.value());
		return target + " = " + value;
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
		// Blocks are translated to lambdas or inline code
		// For simple single-expression blocks, generate inline
		if (block.statements().size() == 1
				&& block.statements().getFirst() instanceof Statement.ExpressionStatement es) {
			return "() -> " + generate(es.expression());
		}

		// Multi-statement blocks generate a Supplier or Runnable
		StringBuilder sb = new StringBuilder();
		sb.append("() -> {\n");
		for (Statement stmt : block.statements()) {
			sb.append("    ");
			if (stmt instanceof Statement.ExpressionStatement es) {
				if (stmt == block.statements().getLast()) {
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
