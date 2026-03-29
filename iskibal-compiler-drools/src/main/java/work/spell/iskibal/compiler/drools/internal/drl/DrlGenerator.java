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

    private static boolean containsImport(RuleModule module, String type) {
        return module.imports().stream().anyMatch(i -> i.type().equals(type));
    }
}
