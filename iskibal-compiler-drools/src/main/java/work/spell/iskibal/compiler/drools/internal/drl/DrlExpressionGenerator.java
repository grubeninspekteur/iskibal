package work.spell.iskibal.compiler.drools.internal.drl;

import module java.base;

import module iskibal.rule.model;
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
import work.spell.iskibal.model.Expression.Raw;

/// Translates Iskara [Expression]s to Java/DRL source code fragments used inside
/// DRL rule bodies.
///
/// The generator supports the common subset of Iskara expressions that map
/// naturally to DRL. Unsupported constructs are emitted as `/* TODO: ... */`
/// comments so that generated DRL files are always syntactically intact.
final class DrlExpressionGenerator {

    private final Set<String> factNames;
    private final Set<String> globalNames;
    private final Set<String> outputNames;
    private final String outputsVar;

    DrlExpressionGenerator(Set<String> factNames, Set<String> globalNames, Set<String> outputNames,
            String outputsVar) {
        this.factNames = factNames;
        this.globalNames = globalNames;
        this.outputNames = outputNames;
        this.outputsVar = outputsVar;
    }

    /// Generates a Java expression string from an Iskara expression.
    String generate(Expression expr) {
        return switch (expr) {
            case Identifier id -> generateIdentifier(id);
            case Literal lit -> generateLiteral(lit);
            case MessageSend ms -> generateMessageSend(ms);
            case Binary bin -> generateBinary(bin);
            case Assignment assign -> generateAssignment(assign);
            case Navigation nav -> generateNavigation(nav);
            case Block block -> "/* TODO: block expressions are not supported in DRL */";
            case Raw raw -> raw.text();
        };
    }

    private String generateIdentifier(Identifier id) {
        String name = id.name();

        if (name.startsWith("@")) {
            // Global variable — strip @ prefix; globals are referenced by name in DRL
            return sanitize(name.substring(1));
        }

        if (outputNames.contains(name)) {
            // Output access (read side — rare in when, but keep consistent)
            return outputsVar + ".get" + capitalize(sanitize(name)) + "()";
        }

        if (factNames.contains(name)) {
            // Fact variables are bound with $prefix in DRL
            return "$" + sanitize(name);
        }

        // Local variable or unknown identifier
        return sanitize(name);
    }

    private String generateLiteral(Literal lit) {
        return switch (lit) {
            case Literal.StringLiteral s -> "\"" + escapeString(s.value()) + "\"";
            case Literal.NumberLiteral n -> {
                String plain = n.value().toPlainString();
                // DRL then blocks are Java; use BigDecimal for decimal numbers
                if (plain.contains(".")) {
                    yield "new java.math.BigDecimal(\"" + plain + "\")";
                }
                yield "new java.math.BigDecimal(" + plain + ")";
            }
            case Literal.BooleanLiteral b -> String.valueOf(b.value());
            case Literal.NullLiteral n -> "null";
            case Literal.ListLiteral l ->
                "/* TODO: list literals are not supported in DRL */ null";
            case Literal.SetLiteral s ->
                "/* TODO: set literals are not supported in DRL */ null";
            case Literal.MapLiteral m ->
                "/* TODO: map literals are not supported in DRL */ null";
        };
    }

    private String generateMessageSend(MessageSend ms) {
        return switch (ms) {
            case UnaryMessage u -> generateUnaryMessage(u);
            case KeywordMessage k -> generateKeywordMessage(k);
            case DefaultMessage d ->
                "/* TODO: default message is not supported in DRL */ " + generate(d.receiver());
        };
    }

    private String generateUnaryMessage(UnaryMessage u) {
        String recv = generate(u.receiver());
        return switch (u.selector()) {
            case "notEmpty" -> "(" + recv + " != null && !" + recv + ".isEmpty())";
            case "empty" -> "(" + recv + " == null || " + recv + ".isEmpty())";
            case "exists" -> "(" + recv + " != null)";
            case "size" -> recv + ".size()";
            case "sum" ->
                "/* TODO: sum aggregation is not supported in DRL — use accumulate */ 0";
            default ->
                recv + "." + u.selector() + "()";
        };
    }

    private String generateKeywordMessage(KeywordMessage k) {
        String recv = generate(k.receiver());
        // Build compound keyword selector and argument list
        String selector = k.parts().stream().map(p -> p.keyword()).collect(java.util.stream.Collectors.joining("_"));
        List<String> args = k.parts().stream().map(p -> generate(p.argument())).toList();

        return switch (selector) {
            case "contains" -> recv + ".contains(" + args.getFirst() + ")";
            case "add" -> recv + ".add(" + args.getFirst() + ")";
            case "at" -> recv + ".get(" + args.getFirst() + ")";
            case "ifTrue" ->
                "/* TODO: ifTrue: is not supported in DRL */";
            case "ifFalse" ->
                "/* TODO: ifFalse: is not supported in DRL */";
            case "and" ->
                "(" + recv + " && " + args.getFirst() + ")";
            case "or" ->
                "(" + recv + " || " + args.getFirst() + ")";
            default ->
                "/* TODO: keyword message '" + selector + "' is not supported in DRL */";
        };
    }

    private String generateBinary(Binary bin) {
        String left = generate(bin.left());
        String right = generate(bin.right());
        String op = switch (bin.operator()) {
            case PLUS -> "+";
            case MINUS -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case EQUALS -> "==";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_EQUALS -> ">=";
            case LESS_THAN -> "<";
            case LESS_EQUALS -> "<=";
        };

        // Numeric operations on BigDecimal require method calls
        // TODO: without type inference we cannot distinguish numeric from other types;
        // emit plain operators and rely on the Drools compiler to catch mismatches
        return left + " " + op + " " + right;
    }

    private String generateAssignment(Assignment assign) {
        String value = generate(assign.value());

        // Output assignment: identifier that matches a declared output name
        if (assign.target() instanceof Identifier id && outputNames.contains(id.name())) {
            return outputsVar + ".set" + capitalize(sanitize(id.name())) + "(" + value + ")";
        }

        // Navigation assignment (fact property mutation)
        if (assign.target() instanceof Navigation nav) {
            String base = generateNavigation(nav, /* setterMode= */ true, value);
            return base;
        }

        // Fallback: plain Java assignment
        return generate(assign.target()) + " = " + value;
    }

    private String generateNavigation(Navigation nav) {
        return generateNavigation(nav, false, null);
    }

    /// Generates navigation code.
    ///
    /// In setter mode the final name segment is converted to a setter call with
    /// [value] as argument. In getter mode it is converted to a getter call.
    private String generateNavigation(Navigation nav, boolean setterMode, String value) {
        String base = generate(nav.receiver());

        // When the root is a fact variable, add the $ sigil
        if (nav.receiver() instanceof Identifier id && factNames.contains(id.name())) {
            base = "$" + sanitize(id.name());
        }

        List<String> names = nav.names();

        if (setterMode) {
            // All but last name are getters; last is the setter
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < names.size() - 1; i++) {
                sb.append(".get").append(capitalize(sanitize(names.get(i)))).append("()");
            }
            sb.append(".set").append(capitalize(sanitize(names.getLast()))).append("(").append(value).append(")");
            return sb.toString();
        }

        // Getter chain
        StringBuilder sb = new StringBuilder(base);
        for (String name : names) {
            sb.append(".get").append(capitalize(sanitize(name))).append("()");
        }
        return sb.toString();
    }

    // ---- helpers ----

    static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == ' ') {
                nextUpper = true;
            } else if (!Character.isJavaIdentifierPart(c)) {
                // skip invalid chars
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.isEmpty() ? "_" : sb.toString();
    }

    static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
