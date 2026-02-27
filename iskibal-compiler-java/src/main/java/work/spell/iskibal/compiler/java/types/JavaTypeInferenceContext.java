package work.spell.iskibal.compiler.java.types;

import module java.base;

import module iskibal.rule.model;

/// Tracks type information during type inference.
///
/// This context manages:
/// - Fact types - input objects available to rules
/// - Global types - shared state accessible via @name syntax
/// - Output types - rule outputs
/// - Local variable types - variables declared with let
public final class JavaTypeInferenceContext {

    private final JavaTypeResolver resolver;
    private final Map<String, JavaType> factTypes = new HashMap<>();
    private final Map<String, JavaType> globalTypes = new HashMap<>();
    private final Map<String, JavaType> outputTypes = new HashMap<>();
    private final Map<String, JavaType> localTypes = new HashMap<>();
    private final Map<String, JavaType> dataTableTypes = new HashMap<>();

    /// Creates a context with the given resolver.
    public JavaTypeInferenceContext(JavaTypeResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /// Creates a context and initializes it from a RuleModule.
    public static JavaTypeInferenceContext fromModule(RuleModule module, JavaTypeResolver resolver) {
        JavaTypeInferenceContext context = new JavaTypeInferenceContext(resolver);
        context.initializeFromModule(module);
        return context;
    }

    /// Initializes the context with type information from a RuleModule.
    public void initializeFromModule(RuleModule module) {
        // Register fact types
        for (Fact fact : module.facts()) {
            JavaType type = resolver.resolve(fact.type());
            factTypes.put(fact.name(), type);
        }

        // Register global types
        for (Global global : module.globals()) {
            JavaType type = resolver.resolve(global.type());
            globalTypes.put(global.name(), type);
        }

        // Register output types
        for (Output output : module.outputs()) {
            JavaType type = resolver.resolve(output.type());
            outputTypes.put(output.name(), type);
        }

        // Register data table types
        for (DataTable table : module.dataTables()) {
            if (table.rows().isEmpty()) {
                continue;
            }
            int columnCount = table.rows().getFirst().values().size();
            JavaType type;
            if (columnCount == 2) {
                // Two-column table: dictionary (Map<Object, Object>)
                type = JavaType.ClassType.map("java.util.Map", JavaType.OBJECT, JavaType.OBJECT);
            } else {
                // Multi-column table: list of dictionaries (List<Map<String, Object>>)
                JavaType rowMapType = JavaType.ClassType.map("java.util.Map", JavaType.STRING, JavaType.OBJECT);
                type = JavaType.ClassType.collection("java.util.List", rowMapType);
            }
            dataTableTypes.put(table.id(), type);
        }
    }

    /// Returns the type resolver.
    public JavaTypeResolver resolver() {
        return resolver;
    }

    /// Looks up the type of an identifier.
    ///
    /// This checks facts, outputs, and local variables in order.
    ///
    /// @param name
    ///            the identifier name
    /// @return the type, or UnknownType if not found
    public JavaType lookupIdentifier(String name) {
        // Check facts first
        JavaType type = factTypes.get(name);
        if (type != null) {
            return type;
        }

        // Check outputs
        type = outputTypes.get(name);
        if (type != null) {
            return type;
        }

        // Check local variables
        type = localTypes.get(name);
        if (type != null) {
            return type;
        }

        // Check data tables
        type = dataTableTypes.get(name);
        if (type != null) {
            return type;
        }

        return JavaType.UnknownType.withHint("identifier: " + name);
    }

    /// Looks up the type of a global (prefixed with @ in source).
    ///
    /// @param name
    ///            the global name (without @ prefix)
    /// @return the type, or UnknownType if not found
    public JavaType lookupGlobal(String name) {
        JavaType type = globalTypes.get(name);
        if (type != null) {
            return type;
        }
        return JavaType.UnknownType.withHint("global: @" + name);
    }

    /// Declares a local variable with the given type.
    public void declareLocal(String name, JavaType type) {
        localTypes.put(name, type);
    }

    /// Returns true if the given name is a known fact.
    public boolean isFact(String name) {
        return factTypes.containsKey(name);
    }

    /// Returns true if the given name is a known global.
    public boolean isGlobal(String name) {
        return globalTypes.containsKey(name);
    }

    /// Returns true if the given name is a known output.
    public boolean isOutput(String name) {
        return outputTypes.containsKey(name);
    }

    /// Returns the type of the given output.
    public JavaType getOutputType(String name) {
        return outputTypes.getOrDefault(name, JavaType.UnknownType.INSTANCE);
    }

    /// Creates a child context for a nested scope (e.g., block).
    ///
    /// The child context shares fact, global, and output types but has its own local
    /// variables.
    public JavaTypeInferenceContext childScope() {
        JavaTypeInferenceContext child = new JavaTypeInferenceContext(resolver);
        child.factTypes.putAll(this.factTypes);
        child.globalTypes.putAll(this.globalTypes);
        child.outputTypes.putAll(this.outputTypes);
        child.localTypes.putAll(this.localTypes);
        child.dataTableTypes.putAll(this.dataTableTypes);
        return child;
    }
}
