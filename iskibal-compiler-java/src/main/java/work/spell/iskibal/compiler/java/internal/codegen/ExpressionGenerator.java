package work.spell.iskibal.compiler.java.internal.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.types.JavaType;
import work.spell.iskibal.compiler.java.types.JavaTypedExpression;
import work.spell.iskibal.compiler.java.types.JavaTypeInferenceVisitor;
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

/// Generates Java code for Iskara expressions.
public final class ExpressionGenerator {

    private final JavaCompilerOptions options;
    private final Set<String> globalNames;
    private final Set<String> outputNames;
    private final Map<String, String> outputTypes;
    private final JavaTypeInferenceVisitor typeVisitor;

    public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames) {
        this(options, globalNames, outputNames, Map.of(), null);
    }

    public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames,
            Map<String, String> outputTypes) {
        this(options, globalNames, outputNames, outputTypes, null);
    }

    public ExpressionGenerator(JavaCompilerOptions options, Set<String> globalNames, Set<String> outputNames,
            Map<String, String> outputTypes, JavaTypeInferenceVisitor typeVisitor) {
        this.options = options;
        this.globalNames = globalNames;
        this.outputNames = outputNames;
        this.outputTypes = outputTypes;
        this.typeVisitor = typeVisitor;
    }

    /// Returns true if type inference is enabled.
    private boolean hasTypeInfo() {
        return typeVisitor != null;
    }

    /// Gets the inferred type for an expression, or null if type inference is
    /// disabled.
    private JavaType getType(Expression expr) {
        if (typeVisitor == null) {
            return null;
        }
        JavaTypedExpression typed = typeVisitor.infer(expr);
        return typed.type();
    }

    /// Registers a let variable's type in the type context. This must be called
    /// before generating code for subsequent expressions that reference the
    /// variable.
    public void registerLetVariable(String name, Expression value) {
        if (typeVisitor != null) {
            JavaType type = getType(value);
            if (type != null) {
                typeVisitor.context().declareLocal(name, type);
            }
        }
    }

    /// Generates Java code for an expression.
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
            return "this." + JavaIdentifiers.sanitize(globalName);
        }

        // Outputs are accessed via this.
        if (outputNames.contains(name)) {
            return "this." + JavaIdentifiers.sanitize(name);
        }

        // Local variables and other identifiers
        return JavaIdentifiers.sanitize(name);
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

        // Separate ranges from regular elements
        List<Expression> ranges = new ArrayList<>();
        List<Expression> regularElements = new ArrayList<>();
        for (Expression elem : lit.elements()) {
            if (elem instanceof KeywordMessage km && km.parts().size() == 1
                    && "to".equals(km.parts().getFirst().keyword())) {
                ranges.add(elem);
            } else {
                regularElements.add(elem);
            }
        }

        // If only ranges, generate ranges directly
        if (regularElements.isEmpty()) {
            if (ranges.size() == 1) {
                return generate(ranges.getFirst());
            }
            // Multiple ranges: union them
            return "unionSets(" + ranges.stream().map(this::generate).collect(Collectors.joining(", ")) + ")";
        }

        // If only regular elements, use Set.of
        if (ranges.isEmpty()) {
            String elements = regularElements.stream().map(this::generate).collect(Collectors.joining(", "));
            return "java.util.Set.of(" + elements + ")";
        }

        // Mixed: union ranges with Set.of elements
        String rangesStr = ranges.stream().map(this::generate).collect(Collectors.joining(", "));
        String elementsStr = regularElements.stream().map(this::generate).collect(Collectors.joining(", "));
        return "unionSets(" + rangesStr + ", java.util.Set.of(" + elementsStr + "))";
    }

    private String generateMapLiteral(Literal.MapLiteral lit) {
        if (lit.entries().isEmpty()) {
            return "java.util.Map.of()";
        }
        // Use Map.ofEntries to support any number of entries (Map.of only supports up
        // to 10)
        String entries = lit.entries().entrySet().stream()
                .map(e -> "java.util.Map.entry(" + generate(e.getKey()) + ", " + generate(e.getValue()) + ")")
                .collect(Collectors.joining(", "));
        return "java.util.Map.ofEntries(" + entries + ")";
    }

    private String generateMessageSend(MessageSend ms) {
        String receiver = generate(ms.receiver());

        return switch (ms) {
            case UnaryMessage um -> generateUnaryMessage(receiver, um.selector(), ms.receiver());
            case KeywordMessage km -> generateKeywordMessage(receiver, km, ms.receiver());
            case DefaultMessage dm -> {
                // Default message: receiver! -> invoke the functional interface method
                // Detect type to call correct method
                JavaType receiverType = getType(dm.receiver());
                if (receiverType != null) {
                    String typeName = receiverType.qualifiedName();
                    if (typeName != null) {
                        if (typeName.startsWith("java.util.function.Supplier")) {
                            yield receiver + ".get()";
                        } else if (typeName.equals("java.lang.Runnable")) {
                            yield receiver + ".run()";
                        } else if (typeName.startsWith("java.util.concurrent.Callable")) {
                            yield receiver + ".call()";
                        }
                    }
                }
                // Default to .get() for Supplier-like interfaces
                yield receiver + ".get()";
            }
        };
    }

    private String generateUnaryMessage(String receiver, String selector, Expression receiverExpr) {
        // Check if receiver is a collection when type info is available
        JavaType receiverType = getType(receiverExpr);
        boolean isCollection = receiverType != null && receiverType.isCollection();

        // Handle special collection unary messages
        return switch (selector) {
            case "exists", "notEmpty" -> {
                if (hasTypeInfo() && !isCollection) {
                    // Not a collection - treat as regular method call
                    yield receiver + "." + selector + "()";
                }
                yield "!" + receiver + ".isEmpty()";
            }
            case "doesNotExist", "empty" -> {
                if (hasTypeInfo() && !isCollection) {
                    yield receiver + "." + selector + "()";
                }
                yield receiver + ".isEmpty()";
            }
            case "sum" -> {
                if (hasTypeInfo() && !isCollection) {
                    yield receiver + ".sum()";
                }
                yield receiver + ".stream().reduce(java.math.BigDecimal.ZERO, (a, b) -> a.add(toBigDecimal(b)))";
            }
            default -> receiver + "." + selector + "()";
        };
    }

    private String generateKeywordMessage(String receiver, KeywordMessage km, Expression receiverExpr) {
        if (km.parts().size() == 1) {
            KeywordMessage.KeywordPart part = km.parts().getFirst();
            String keyword = part.keyword();
            String arg = generate(part.argument());

            // Check if receiver is a collection when type info is available
            JavaType receiverType = getType(receiverExpr);
            boolean isCollection = receiverType != null && receiverType.isCollection();
            boolean isMap = receiverType != null && receiverType.isMap();

            // Handle special keyword messages based on type
            return switch (keyword) {
                case "all" -> {
                    if (hasTypeInfo() && !isCollection) {
                        // Not a collection - treat as regular method call
                        yield receiver + ".all(" + arg + ")";
                    }
                    yield receiver + ".stream().allMatch(" + arg + ")";
                }
                case "each" -> {
                    if (hasTypeInfo() && !isCollection) {
                        yield receiver + ".each(" + arg + ")";
                    }
                    yield receiver + ".forEach(" + arg + ")";
                }
                case "where" -> {
                    if (hasTypeInfo() && !isCollection) {
                        yield receiver + ".where(" + arg + ")";
                    }
                    yield receiver + ".stream().filter(" + arg + ").toList()";
                }
                case "at" -> {
                    if (hasTypeInfo()) {
                        if (isCollection) {
                            yield receiver + ".get(" + arg + ".intValue())";
                        } else if (isMap) {
                            yield receiver + ".get(" + arg + ")";
                        }
                        // Not a collection/map - use runtime helper for backward compatibility
                    }
                    yield generateAtAccess(receiver, arg);
                }
                case "contains" -> {
                    if (hasTypeInfo()) {
                        JavaType rcvType = getType(receiverExpr);
                        if (rcvType != null && rcvType.isMap()) {
                            yield receiver + ".containsKey(" + arg + ")";
                        }
                    }
                    yield receiver + ".contains(" + arg + ")";
                }
                case "and" -> receiver + " && " + arg;
                case "or" -> receiver + " || " + arg;
                case "to" -> "range(" + receiver + ", " + arg + ")";
                case "ifTrue" -> {
                    // ifTrue: block -> if (condition) { block body }
                    if (part.argument() instanceof Block block) {
                        yield "{ if (" + receiver + ") { " + generateBlockBody(block) + " } }";
                    }
                    yield "{ if (" + receiver + ") ((Runnable)" + arg + ").run(); }";
                }
                case "ifFalse" -> {
                    // ifFalse: block -> if (!condition) { block body }
                    if (part.argument() instanceof Block block) {
                        yield "{ if (!(" + receiver + ")) { " + generateBlockBody(block) + " } }";
                    }
                    yield "{ if (!(" + receiver + ")) ((Runnable)" + arg + ").run(); }";
                }
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
            case PLUS -> {
                // Check if this is string concatenation (template expressions)
                if (isStringExpression(bin.left()) || isStringExpression(bin.right())) {
                    yield left + " + " + right;
                }
                yield "addNumeric(" + left + ", " + right + ")";
            }
            case MINUS -> "subtractNumeric(" + left + ", " + right + ")";
            case MULTIPLY -> "multiplyNumeric(" + left + ", " + right + ")";
            case DIVIDE -> "divideNumeric(" + left + ", " + right + ")";
            case GREATER_THAN -> "compareNumeric(" + left + ", " + right + ") > 0";
            case GREATER_EQUALS -> "compareNumeric(" + left + ", " + right + ") >= 0";
            case LESS_THAN -> "compareNumeric(" + left + ", " + right + ") < 0";
            case LESS_EQUALS -> "compareNumeric(" + left + ", " + right + ") <= 0";
        };
    }

    /// Checks if an expression evaluates to a String type. This is used to
    /// determine if a PLUS operation should use string concatenation instead of
    /// numeric addition.
    private boolean isStringExpression(Expression expr) {
        return switch (expr) {
            case Literal.StringLiteral _ -> true;
            case Binary bin when bin.operator() == Binary.Operator.PLUS ->
                isStringExpression(bin.left()) || isStringExpression(bin.right());
            default -> {
                // Check via type inference if available
                if (hasTypeInfo()) {
                    JavaType type = getType(expr);
                    yield type != null && type.isString();
                }
                yield false;
            }
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
                    yield "this." + JavaIdentifiers.sanitize(name.substring(1));
                }
                if (outputNames.contains(name)) {
                    yield "this." + JavaIdentifiers.sanitize(name);
                }
                yield JavaIdentifiers.sanitize(name);
            }
            case Navigation nav -> generateNavigation(nav);
            default -> generate(target);
        };
    }

    private String generateNavigation(Navigation nav) {
        String receiver = generate(nav.receiver());

        // Get type information if available
        JavaType receiverType = getType(nav.receiver());

        if (hasTypeInfo() && receiverType != null && receiverType.isCollection()) {
            // Collection navigation - use flatMap pattern
            return generateCollectionNavigation(nav, receiver, receiverType);
        }

        if (options.generateNullChecks() && nav.names().size() > 1) {
            // Generate null-safe navigation using Optional for chains
            return generateNullSafeNavigation(nav, receiver, receiverType);
        } else {
            // Simple navigation without null checks
            return generateSimpleNavigation(nav, receiver, receiverType);
        }
    }

    private String generateCollectionNavigation(Navigation nav, String receiver, JavaType receiverType) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiver).append(".stream()");

        JavaType currentElementType = receiverType.elementType();

        for (int i = 0; i < nav.names().size(); i++) {
            String name = nav.names().get(i);
            String accessor = generatePropertyAccessor(name, currentElementType);

            // Check if the property is itself a collection (nested flatMap)
            JavaType propertyType = null;
            if (hasTypeInfo() && typeVisitor != null) {
                propertyType = typeVisitor.context().resolver().resolveProperty(currentElementType, name);
            }

            if (propertyType != null && propertyType.isCollection()) {
                // Nested collection - use flatMap
                sb.append(".flatMap(v -> v.").append(accessor).append(".stream())");
                currentElementType = propertyType.elementType();
            } else {
                // Regular property - use map
                sb.append(".map(v -> v.").append(accessor).append(")");
                if (propertyType != null) {
                    currentElementType = propertyType;
                }
            }
        }

        sb.append(".toList()");
        return sb.toString();
    }

    private String generateNullSafeNavigation(Navigation nav, String receiver, JavaType receiverType) {
        JavaType currentType = receiverType;

        // Check if any intermediate property returns a collection
        for (int i = 0; i < nav.names().size(); i++) {
            String name = nav.names().get(i);

            if (hasTypeInfo() && currentType != null && typeVisitor != null) {
                currentType = typeVisitor.context().resolver().resolveProperty(currentType, name);

                // If we hit a collection, switch to collection navigation for the rest
                if (currentType != null && currentType.isCollection() && i < nav.names().size() - 1) {
                    // Build the path up to and including the collection property
                    StringBuilder pathToCollection = new StringBuilder();
                    pathToCollection.append(receiver);
                    JavaType pathType = receiverType;
                    for (int j = 0; j <= i; j++) {
                        String propName = nav.names().get(j);
                        String accessor = generatePropertyAccessor(propName, pathType);
                        pathToCollection.append(".").append(accessor);
                        if (hasTypeInfo() && pathType != null && typeVisitor != null) {
                            pathType = typeVisitor.context().resolver().resolveProperty(pathType, propName);
                        }
                    }

                    // Generate collection navigation for remaining properties
                    List<String> remainingNames = nav.names().subList(i + 1, nav.names().size());
                    return generateMidChainCollectionNavigation(pathToCollection.toString(), currentType,
                            remainingNames);
                }
            }
        }

        // No collection in chain - use standard null-safe navigation
        StringBuilder sb = new StringBuilder();
        sb.append("java.util.Optional.ofNullable(").append(receiver).append(")");

        currentType = receiverType;

        // Add .map() for each intermediate step (all but the last)
        for (int i = 0; i < nav.names().size() - 1; i++) {
            String name = nav.names().get(i);
            String accessor = generatePropertyAccessor(name, currentType);
            sb.append(".map(v -> v.").append(accessor).append(")");

            // Update current type for next iteration
            if (hasTypeInfo() && currentType != null && typeVisitor != null) {
                currentType = typeVisitor.context().resolver().resolveProperty(currentType, name);
            }
        }

        // Add final .map() and .orElse(null) for the last property
        String lastName = nav.names().getLast();
        String lastAccessor = generatePropertyAccessor(lastName, currentType);
        sb.append(".map(v -> v.").append(lastAccessor).append(")");
        sb.append(".orElse(null)");

        return sb.toString();
    }

    private String generateSimpleNavigation(Navigation nav, String receiver, JavaType receiverType) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiver);

        JavaType currentType = receiverType;
        boolean inCollectionMode = false;

        for (int i = 0; i < nav.names().size(); i++) {
            String name = nav.names().get(i);

            // Check if we're now navigating on a collection (mid-chain collection)
            if (hasTypeInfo() && currentType != null && currentType.isCollection() && !inCollectionMode) {
                // Switch to collection navigation for remaining properties
                List<String> remainingNames = nav.names().subList(i, nav.names().size());
                return generateMidChainCollectionNavigation(sb.toString(), currentType, remainingNames);
            }

            String accessor = generatePropertyAccessor(name, currentType);
            sb.append(".").append(accessor);

            // Update current type for next iteration
            if (hasTypeInfo() && currentType != null && typeVisitor != null) {
                currentType = typeVisitor.context().resolver().resolveProperty(currentType, name);
            }
        }
        return sb.toString();
    }

    /// Generates code for navigating through a collection that appears mid-chain.
    /// For example: cart.items.name where items is a List
    private String generateMidChainCollectionNavigation(String collectionExpr, JavaType collectionType,
            List<String> propertyNames) {
        StringBuilder sb = new StringBuilder();
        sb.append(collectionExpr).append(".stream()");

        JavaType currentElementType = collectionType.elementType();

        for (int i = 0; i < propertyNames.size(); i++) {
            String name = propertyNames.get(i);
            String accessor = generatePropertyAccessor(name, currentElementType);

            // Check if this property returns a collection (nested flatMap)
            JavaType propertyType = null;
            if (hasTypeInfo() && typeVisitor != null) {
                propertyType = typeVisitor.context().resolver().resolveProperty(currentElementType, name);
            }

            if (propertyType != null && propertyType.isCollection()) {
                // Nested collection - use flatMap
                sb.append(".flatMap(v -> v.").append(accessor).append(".stream())");
                currentElementType = propertyType.elementType();
            } else {
                // Regular property - use map
                sb.append(".map(v -> v.").append(accessor).append(")");
                if (propertyType != null) {
                    currentElementType = propertyType;
                }
            }
        }

        sb.append(".toList()");
        return sb.toString();
    }

    /// Generates the property accessor call for a given property name. Uses
    /// record-style accessor (name()) for records, bean-style (getName()) for
    /// others.
    private String generatePropertyAccessor(String name, JavaType ownerType) {
        if (hasTypeInfo() && ownerType != null && ownerType.isRecord()) {
            // Record accessor: name()
            return name + "()";
        }
        // Bean accessor: getName()
        return "get" + capitalize(name) + "()";
    }

    private String generateBlock(Block block) {
        // Pre-infer the block to populate the type cache for all expressions
        // This ensures that let statement types are available during code generation
        if (hasTypeInfo()) {
            typeVisitor.infer(block);
        }

        // Use explicit parameters from the Block record
        List<String> params = new ArrayList<>(block.parameters());
        List<Statement> bodyStatements = block.statements();

        // Build lambda parameter list
        String paramList = params.isEmpty()
                ? "()"
                : params.size() == 1 ? params.getFirst() : "(" + String.join(", ", params) + ")";

        // For simple single-expression blocks, generate inline
        if (bodyStatements.size() == 1 && bodyStatements.getFirst() instanceof Statement.ExpressionStatement es) {
            Expression bodyExpr = es.expression();
            // For implicit parameter blocks, rewrite the expression AST to include 'it'
            if (block.hasImplicitParameter()) {
                bodyExpr = rewriteImplicitItExpression(bodyExpr);
            }
            String body = generate(bodyExpr);
            return paramList + " -> " + body;
        }

        // Multi-statement blocks generate a Supplier or Runnable
        StringBuilder sb = new StringBuilder();
        sb.append(paramList).append(" -> {\n");
        for (Statement stmt : bodyStatements) {
            sb.append("    ");
            if (stmt instanceof Statement.ExpressionStatement es) {
                Expression exprToGenerate = block.hasImplicitParameter()
                        ? rewriteImplicitItExpression(es.expression())
                        : es.expression();
                String exprCode = generate(exprToGenerate);
                if (stmt == bodyStatements.getLast()) {
                    sb.append("return ").append(exprCode).append(";\n");
                } else {
                    sb.append(exprCode).append(";\n");
                }
            } else if (stmt instanceof Statement.LetStatement ls) {
                sb.append("var ").append(ls.name()).append(" = ").append(generate(ls.expression())).append(";\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /// Rewrites an expression to include the implicit 'it' parameter.
    /// Transforms Identifier("x") to Navigation(Identifier("it"), ["x"])
    /// so that normal code generation produces proper property accessors.
    private Expression rewriteImplicitItExpression(Expression expr) {
        return switch (expr) {
            case Identifier id -> new Navigation(new Identifier("it"), List.of(id.name()));
            case Binary bin -> new Binary(
                rewriteImplicitItExpression(bin.left()),
                bin.operator(),
                bin.right()
            );
            case UnaryMessage um -> new UnaryMessage(
                rewriteImplicitItExpression(um.receiver()),
                um.selector()
            );
            case Navigation nav -> new Navigation(
                rewriteImplicitItExpression(nav.receiver()),
                nav.names()
            );
            default -> expr;
        };
    }

    /// Generates the body of a block as statements (without lambda wrapper).
    /// Used for inlining blocks in control flow constructs like ifTrue:/ifFalse:.
    private String generateBlockBody(Block block) {
        StringBuilder sb = new StringBuilder();
        for (Statement stmt : block.statements()) {
            if (stmt instanceof Statement.ExpressionStatement es) {
                sb.append(generate(es.expression())).append("; ");
            } else if (stmt instanceof Statement.LetStatement ls) {
                sb.append("var ").append(ls.name()).append(" = ").append(generate(ls.expression())).append("; ");
            }
        }
        return sb.toString().trim();
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
