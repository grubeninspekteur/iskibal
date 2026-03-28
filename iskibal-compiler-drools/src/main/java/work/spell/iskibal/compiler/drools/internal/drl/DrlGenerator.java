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
///
/// ## Known limitations (TODO)
/// - Salience / priority ordering is not generated (not in the rule model)
/// - Agenda groups are not generated (not in the rule model)
/// - Drools `modify`, `retract`, `insert` are not generated
/// - Template strings (`$"..."`) are not supported
/// - Block/lambda expressions are not supported
/// - Collection aggregation (`sum`, `each`, `all`, `where`) is not supported;
///   use Drools `accumulate` instead
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
    public Map<String, String> generate(RuleModule module) {
        Set<String> factNames = module.facts().stream().map(Fact::name).collect(java.util.stream.Collectors.toSet());
        Set<String> globalNames = module.globals().stream().map(Global::name)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> outputNames = module.outputs().stream().map(Output::name)
                .collect(java.util.stream.Collectors.toSet());

        DrlExpressionGenerator exprGen = new DrlExpressionGenerator(factNames, globalNames, outputNames, OUTPUTS_VAR);

        String drl = generateDrl(module, exprGen);
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put(options.drlFilePath(), drl);

        if (!module.outputs().isEmpty()) {
            result.put(options.outputsFilePath(), generateOutputsPojo(module));
        }

        return result;
    }

    // ---- DRL source ----

    private String generateDrl(RuleModule module, DrlExpressionGenerator exprGen) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        if (options.packageName() != null && !options.packageName().isEmpty()) {
            sb.append("package ").append(options.packageName()).append(";\n\n");
        }

        // Imports
        for (Import imp : module.imports()) {
            sb.append("import ").append(imp.type()).append(";\n");
        }
        // Always import BigDecimal for numeric literals
        if (!containsImport(module, "java.math.BigDecimal")) {
            sb.append("import java.math.BigDecimal;\n");
        }
        sb.append("\n");

        // Global declarations (user globals)
        for (Global global : module.globals()) {
            sb.append("global ").append(global.type()).append(" ")
                    .append(DrlExpressionGenerator.sanitize(global.name())).append(";\n");
        }

        // Outputs global (if there are output declarations)
        if (!module.outputs().isEmpty()) {
            String outputsType = options.fullyQualifiedOutputsClassName();
            sb.append("global ").append(outputsType).append(" ").append(OUTPUTS_VAR).append(";\n");
        }

        if (!module.globals().isEmpty() || !module.outputs().isEmpty()) {
            sb.append("\n");
        }

        // Rules
        for (Rule rule : module.rules()) {
            generateRule(rule, module, exprGen, sb);
        }

        return sb.toString();
    }

    private void generateRule(Rule rule, RuleModule module, DrlExpressionGenerator exprGen, StringBuilder sb) {
        switch (rule) {
            case Rule.SimpleRule sr -> generateSimpleRule(sr, module, exprGen, sb);
            case Rule.TemplateRule tr -> generateTemplateRule(tr, module, exprGen, sb);
            case Rule.DecisionTableRule dtr -> generateDecisionTableRule(dtr, module, exprGen, sb);
        }
    }

    private void generateSimpleRule(Rule.SimpleRule rule, RuleModule module, DrlExpressionGenerator exprGen,
            StringBuilder sb) {
        appendRuleHeader(sb, rule.id(), rule.description());
        appendWhenSection(sb, rule.when(), module, exprGen);
        appendThenSection(sb, rule.then(), exprGen);

        // else block: generate a negated rule
        if (!rule.elseStatements().isEmpty()) {
            sb.append("\n");
            String elseDesc = rule.description() == null ? null : rule.description() + " (else)";
            appendRuleHeader(sb, rule.id() + "-else", elseDesc);
            appendWhenSectionNegated(sb, rule.when(), module, exprGen);
            appendThenSection(sb, rule.elseStatements(), exprGen);
        }
    }

    private void generateTemplateRule(Rule.TemplateRule rule, RuleModule module, DrlExpressionGenerator exprGen,
            StringBuilder sb) {
        // Expand template rule — one DRL rule per data table row
        List<DataTable.Row> rows = rule.dataTable().rows();
        if (rows.isEmpty()) {
            sb.append("// Template rule '").append(rule.id()).append("' has no data table rows\n\n");
            return;
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            DataTable.Row row = rows.get(rowIndex);
            String rowDesc = rule.description() == null ? null : rule.description() + " (row " + (rowIndex + 1) + ")";
            appendRuleHeader(sb, rule.id() + "-" + rowIndex, rowDesc);
            appendWhenSectionTemplate(sb, rule.when(), row, module, exprGen);
            appendThenSection(sb, rule.then(), exprGen);
        }
    }

    private void generateDecisionTableRule(Rule.DecisionTableRule rule, RuleModule module,
            DrlExpressionGenerator exprGen, StringBuilder sb) {
        // Expand decision table — one DRL rule per row
        if (rule.rows().isEmpty()) {
            sb.append("// Decision table rule '").append(rule.id()).append("' has no rows\n\n");
            return;
        }
        for (Rule.DecisionTableRule.Row row : rule.rows()) {
            appendRuleHeader(sb, rule.id() + "-" + row.id(), rule.description());
            appendWhenSection(sb, row.when(), module, exprGen);
            appendThenSection(sb, row.then(), exprGen);
        }
    }

    // ---- rule header ----

    private void appendRuleHeader(StringBuilder sb, String id, String description) {
        sb.append("rule \"").append(escapeId(id)).append("\"\n");
        if (description != null && !description.isBlank()) {
            sb.append("    @Description(\"").append(escapeId(description)).append("\")\n");
        }
    }

    // ---- when section ----

    private void appendWhenSection(StringBuilder sb, List<Statement> when, RuleModule module,
            DrlExpressionGenerator exprGen) {
        sb.append("    when\n");

        // Bind all declared facts
        for (Fact fact : module.facts()) {
            String varName = "$" + DrlExpressionGenerator.sanitize(fact.name());
            String typeName = resolveSimpleType(fact.type(), module);
            sb.append("        ").append(varName).append(" : ").append(typeName).append("()\n");
        }

        // Emit conditions
        appendConditions(sb, when, exprGen, false);
    }

    private void appendWhenSectionNegated(StringBuilder sb, List<Statement> when, RuleModule module,
            DrlExpressionGenerator exprGen) {
        sb.append("    when\n");

        // Bind all declared facts
        for (Fact fact : module.facts()) {
            String varName = "$" + DrlExpressionGenerator.sanitize(fact.name());
            String typeName = resolveSimpleType(fact.type(), module);
            sb.append("        ").append(varName).append(" : ").append(typeName).append("()\n");
        }

        // Negate conditions
        appendConditions(sb, when, exprGen, true);
    }

    private void appendWhenSectionTemplate(StringBuilder sb, List<Statement> when, DataTable.Row row,
            RuleModule module, DrlExpressionGenerator exprGen) {
        sb.append("    when\n");

        // Bind all declared facts
        for (Fact fact : module.facts()) {
            String varName = "$" + DrlExpressionGenerator.sanitize(fact.name());
            String typeName = resolveSimpleType(fact.type(), module);
            sb.append("        ").append(varName).append(" : ").append(typeName).append("()\n");
        }

        // Collect conditions from both the row variable assignments and the when block
        List<String> conditions = new ArrayList<>();

        // Row variable bindings are substituted as equality checks in conditions
        // (the row values become available as local context for when-expression generation)
        // For now emit them as let-style DRL variable declarations (not supported in when)
        // TODO: inject row values into DRL condition context
        for (Map.Entry<String, Expression> entry : row.values().entrySet()) {
            String varValue = exprGen.generate(entry.getValue());
            // Row bindings: emit as a "variable = value" eval for visibility
            // In a proper implementation this would substitute the variable references
            conditions.add("/* row binding: " + entry.getKey() + " = " + varValue + " */true");
        }

        // Collect when conditions
        for (Statement stmt : when) {
            if (stmt instanceof Statement.ExpressionStatement es) {
                conditions.add(exprGen.generate(es.expression()));
            } else if (stmt instanceof Statement.LetStatement ls) {
                conditions.add("/* TODO: let binding '" + ls.name() + "' not supported in DRL when */true");
            }
        }

        if (!conditions.isEmpty()) {
            sb.append("        eval(");
            sb.append(String.join("\n            && ", conditions));
            sb.append(")\n");
        }
    }

    private void appendConditions(StringBuilder sb, List<Statement> when, DrlExpressionGenerator exprGen,
            boolean negate) {
        if (when.isEmpty()) {
            return;
        }

        List<String> conditions = new ArrayList<>();
        for (Statement stmt : when) {
            if (stmt instanceof Statement.ExpressionStatement es) {
                conditions.add(exprGen.generate(es.expression()));
            } else if (stmt instanceof Statement.LetStatement ls) {
                conditions.add("/* TODO: let binding '" + ls.name() + "' not supported in DRL when section */true");
            }
        }

        if (!conditions.isEmpty()) {
            if (negate) {
                sb.append("        eval(!(");
                sb.append(String.join("\n            && ", conditions));
                sb.append("))\n");
            } else {
                sb.append("        eval(");
                sb.append(String.join("\n            && ", conditions));
                sb.append(")\n");
            }
        }
    }

    // ---- then section ----

    private void appendThenSection(StringBuilder sb, List<Statement> then, DrlExpressionGenerator exprGen) {
        sb.append("    then\n");
        for (Statement stmt : then) {
            String code = switch (stmt) {
                case Statement.ExpressionStatement es -> exprGen.generate(es.expression()) + ";";
                case Statement.LetStatement ls -> "Object " + DrlExpressionGenerator.sanitize(ls.name()) + " = "
                        + exprGen.generate(ls.expression()) + ";";
            };
            sb.append("        ").append(code).append("\n");
        }
        sb.append("end\n\n");
    }

    // ---- outputs POJO ----

    private String generateOutputsPojo(RuleModule module) {
        StringBuilder sb = new StringBuilder();

        if (options.packageName() != null && !options.packageName().isEmpty()) {
            sb.append("package ").append(options.packageName()).append(";\n\n");
        }

        sb.append("/// Output values produced by rule evaluation.\n");
        sb.append("///\n");
        sb.append("/// Inject an instance of this class as the `").append(OUTPUTS_VAR)
                .append("` global before executing\n");
        sb.append("/// the Drools session; field values will be populated by rule actions.\n");
        sb.append("public class ").append(options.outputsClassName()).append(" {\n\n");

        // Fields with initial values
        for (Output output : module.outputs()) {
            String fieldName = DrlExpressionGenerator.sanitize(output.name());
            String fieldType = output.type();
            if (output.initialValue() != null) {
                DrlExpressionGenerator tempGen = new DrlExpressionGenerator(Set.of(), Set.of(), Set.of(), OUTPUTS_VAR);
                String initExpr = tempGen.generate(output.initialValue());
                sb.append("    private ").append(fieldType).append(" ").append(fieldName).append(" = ").append(initExpr)
                        .append(";\n");
            } else {
                sb.append("    private ").append(fieldType).append(" ").append(fieldName).append(";\n");
            }
        }

        sb.append("\n");

        // Getters and setters
        for (Output output : module.outputs()) {
            String fieldName = DrlExpressionGenerator.sanitize(output.name());
            String capitalName = DrlExpressionGenerator.capitalize(fieldName);
            String fieldType = output.type();

            sb.append("    public ").append(fieldType).append(" get").append(capitalName).append("() {\n");
            sb.append("        return ").append(fieldName).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public void set").append(capitalName).append("(").append(fieldType).append(" ")
                    .append(fieldName).append(") {\n");
            sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ---- utilities ----

    /// Returns the simple (unqualified) type name when it was imported; otherwise
    /// returns the fully qualified name.
    private static String resolveSimpleType(String type, RuleModule module) {
        if (type == null) {
            return "Object";
        }
        int lastDot = type.lastIndexOf('.');
        if (lastDot < 0) {
            return type;
        }
        String simpleName = type.substring(lastDot + 1);
        for (Import imp : module.imports()) {
            if (imp.type().equals(type)) {
                return simpleName;
            }
        }
        return type;
    }

    private static boolean containsImport(RuleModule module, String type) {
        return module.imports().stream().anyMatch(i -> i.type().equals(type));
    }

    private static String escapeId(String id) {
        return id.replace("\"", "\\\"");
    }
}
