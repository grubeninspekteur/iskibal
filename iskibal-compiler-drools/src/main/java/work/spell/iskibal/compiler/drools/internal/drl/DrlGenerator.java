package work.spell.iskibal.compiler.drools.internal.drl;

import module java.base;

import module iskibal.rule.model;
import work.spell.iskibal.compiler.drools.api.DroolsCompilerOptions;

/// Generates Drools Rule Language (DRL) source from a [RuleModule].
///
/// Each rule in the module is translated to a DRL `rule` block. Facts are bound
/// as pattern variables (`$factName : FactType()`). When-conditions are wrapped
/// in `eval(...)` predicates. Then-actions are emitted as straight Java
/// statements.
///
/// Output values are collected in a generated POJO (`<Name>Outputs`) which is
/// injected into the DRL session as a global named `__outputs`.
public final class DrlGenerator {

    private static final String OUTPUTS_VAR = "__outputs";

    private final DroolsCompilerOptions options;

    public DrlGenerator(DroolsCompilerOptions options) {
        this.options = options;
    }

    /// Generates both the DRL source and the companion outputs POJO source.
    ///
    /// @param module
    ///            the rule module to compile
    /// @return map of relative file path → source content
    public Map<Path, String> generate(RuleModule module) {
        Set<String> factNames = module.facts().stream().map(Fact::name).collect(java.util.stream.Collectors.toSet());
        Set<String> globalNames = module.globals().stream().map(Global::name)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> outputNames = module.outputs().stream().map(Output::name)
                .collect(java.util.stream.Collectors.toSet());

        DrlExpressionGenerator exprGen = new DrlExpressionGenerator(factNames, globalNames, outputNames, OUTPUTS_VAR);

        String drl = generateDrl(module, exprGen);
        Map<Path, String> result = new LinkedHashMap<>();
        result.put(Path.of(options.drlFilePath()), drl);

        if (!module.outputs().isEmpty()) {
            result.put(Path.of(options.outputsFilePath()), generateOutputsPojo(module));
        }

        result.put(Path.of(options.adapterFilePath()), generateAdapter(module));

        return result;
    }

    private String generateDrl(RuleModule module, DrlExpressionGenerator exprGen) {
        IndentedWriter w = new IndentedWriter();

        // Package declaration
        if (options.packageName() != null && !options.packageName().isEmpty()) {
            w.line("package " + options.packageName() + ";");
            w.blankLine();
        }

        // Imports
        for (Import imp : module.imports()) {
            w.line("import " + imp.type() + ";");
        }
        if (!containsImport(module, "java.math.BigDecimal")) {
            w.line("import java.math.BigDecimal;");
        }
        w.blankLine();

        // Global declarations
        for (Global global : module.globals()) {
            w.line("global " + global.type() + " " + DrlExpressionGenerator.sanitize(global.name()) + ";");
        }

        // Outputs global
        if (!module.outputs().isEmpty()) {
            String outputsType = options.fullyQualifiedOutputsClassName();
            w.line("global " + outputsType + " " + OUTPUTS_VAR + ";");
        }

        if (!module.globals().isEmpty() || !module.outputs().isEmpty()) {
            w.blankLine();
        }

        // Rules
        DrlRuleWriter ruleWriter = new DrlRuleWriter(exprGen, module);
        for (Rule rule : module.rules()) {
            ruleWriter.writeRule(rule, w);
        }

        return w.toString();
    }

    private String generateOutputsPojo(RuleModule module) {
        IndentedWriter w = new IndentedWriter();

        if (options.packageName() != null && !options.packageName().isEmpty()) {
            w.line("package " + options.packageName() + ";");
            w.blankLine();
        }

        w.line("/// Output values produced by rule evaluation.");
        w.line("///");
        w.line("/// Inject an instance of this class as the `" + OUTPUTS_VAR + "` global before executing");
        w.line("/// the Drools session; field values will be populated by rule actions.");
        w.line("public class " + options.outputsClassName() + " {");
        w.blankLine();

        w.indent();

        // Fields with initial values
        for (Output output : module.outputs()) {
            String fieldName = DrlExpressionGenerator.sanitize(output.name());
            String fieldType = output.type();
            if (output.initialValue() != null) {
                DrlExpressionGenerator tempGen = new DrlExpressionGenerator(Set.of(), Set.of(), Set.of(), OUTPUTS_VAR);
                String initExpr = tempGen.generate(output.initialValue());
                w.line("private " + fieldType + " " + fieldName + " = " + initExpr + ";");
            } else {
                w.line("private " + fieldType + " " + fieldName + ";");
            }
        }

        w.blankLine();

        // Getters and setters
        for (Output output : module.outputs()) {
            String fieldName = DrlExpressionGenerator.sanitize(output.name());
            String capitalName = DrlExpressionGenerator.capitalize(fieldName);
            String fieldType = output.type();

            w.line("public " + fieldType + " get" + capitalName + "() {");
            w.indent().line("return " + fieldName + ";").dedent();
            w.line("}");
            w.blankLine();

            w.line("public void set" + capitalName + "(" + fieldType + " " + fieldName + ") {");
            w.indent().line("this." + fieldName + " = " + fieldName + ";").dedent();
            w.line("}");
            w.blankLine();
        }

        w.dedent();
        w.line("}");
        return w.toString();
    }

    private String generateAdapter(RuleModule module) {
        IndentedWriter w = new IndentedWriter();

        if (options.packageName() != null && !options.packageName().isEmpty()) {
            w.line("package " + options.packageName() + ";");
            w.blankLine();
        }

        w.line("import org.kie.api.KieBase;");
        w.line("import org.kie.api.runtime.KieSession;");
        w.blankLine();

        String adapterName = options.adapterClassName();
        String outputsName = options.outputsClassName();
        boolean hasOutputs = !module.outputs().isEmpty();

        w.line("/// Adapter that sets up a Drools session with the declared facts,");
        w.line("/// globals, and outputs, then fires all rules.");
        w.line("///");
        w.line("/// Usage:");
        w.line("///");
        w.line("/// ```java");
        w.line("/// var adapter = new " + adapterName + "(kieBase);");
        for (Fact fact : module.facts()) {
            String name = DrlExpressionGenerator.sanitize(fact.name());
            w.line("/// adapter.set" + DrlExpressionGenerator.capitalize(name) + "(" + name + ");");
        }
        w.line("/// adapter.evaluate();");
        if (hasOutputs) {
            w.line("/// " + outputsName + " outputs = adapter.getOutputs();");
        }
        w.line("/// ```");
        w.line("public class " + adapterName + " {");
        w.blankLine();
        w.indent();

        w.line("private final KieBase kieBase;");

        // Fact fields
        for (Fact fact : module.facts()) {
            String name = DrlExpressionGenerator.sanitize(fact.name());
            String type = fact.type();
            w.line("private " + type + " " + name + ";");
        }

        // Global fields
        for (Global global : module.globals()) {
            String name = DrlExpressionGenerator.sanitize(global.name());
            String type = global.type();
            w.line("private " + type + " " + name + ";");
        }

        // Outputs field
        if (hasOutputs) {
            w.line("private " + outputsName + " outputs;");
        }

        w.blankLine();

        // Constructor
        w.line("public " + adapterName + "(KieBase kieBase) {");
        w.indent();
        w.line("this.kieBase = kieBase;");
        w.dedent();
        w.line("}");
        w.blankLine();

        // Fact setters
        for (Fact fact : module.facts()) {
            String name = DrlExpressionGenerator.sanitize(fact.name());
            String capitalName = DrlExpressionGenerator.capitalize(name);
            String type = fact.type();

            w.line("public " + adapterName + " set" + capitalName + "(" + type + " " + name + ") {");
            w.indent();
            w.line("this." + name + " = " + name + ";");
            w.line("return this;");
            w.dedent();
            w.line("}");
            w.blankLine();
        }

        // Global setters
        for (Global global : module.globals()) {
            String name = DrlExpressionGenerator.sanitize(global.name());
            String capitalName = DrlExpressionGenerator.capitalize(name);
            String type = global.type();

            w.line("public " + adapterName + " set" + capitalName + "(" + type + " " + name + ") {");
            w.indent();
            w.line("this." + name + " = " + name + ";");
            w.line("return this;");
            w.dedent();
            w.line("}");
            w.blankLine();
        }

        // Outputs getter
        if (hasOutputs) {
            w.line("/// Returns the outputs after rule evaluation.");
            w.line("public " + outputsName + " getOutputs() {");
            w.indent();
            w.line("return outputs;");
            w.dedent();
            w.line("}");
            w.blankLine();
        }

        // evaluate() method
        w.line("/// Creates a new Drools session, inserts all facts and globals,");
        w.line("/// fires all rules, and disposes of the session.");
        if (hasOutputs) {
            w.line("///");
            w.line("/// @return the outputs populated by rule actions");
            w.line("public " + outputsName + " evaluate() {");
        } else {
            w.line("public void evaluate() {");
        }
        w.indent();
        w.line("KieSession session = kieBase.newKieSession();");
        w.line("try {");
        w.indent();

        // Insert facts
        for (Fact fact : module.facts()) {
            String name = DrlExpressionGenerator.sanitize(fact.name());
            w.line("if (" + name + " != null) {");
            w.indent().line("session.insert(" + name + ");").dedent();
            w.line("}");
        }

        // Set globals
        for (Global global : module.globals()) {
            String name = DrlExpressionGenerator.sanitize(global.name());
            w.line("if (" + name + " != null) {");
            w.indent().line("session.setGlobal(\"" + name + "\", " + name + ");").dedent();
            w.line("}");
        }

        // Set outputs global
        if (hasOutputs) {
            w.line("this.outputs = new " + outputsName + "();");
            w.line("session.setGlobal(\"" + OUTPUTS_VAR + "\", outputs);");
        }

        w.line("session.fireAllRules();");

        w.dedent();
        w.line("} finally {");
        w.indent().line("session.dispose();").dedent();
        w.line("}");

        if (hasOutputs) {
            w.line("return outputs;");
        }

        w.dedent();
        w.line("}");

        w.dedent();
        w.line("}");
        return w.toString();
    }

    private static boolean containsImport(RuleModule module, String type) {
        return module.imports().stream().anyMatch(i -> i.type().equals(type));
    }
}
