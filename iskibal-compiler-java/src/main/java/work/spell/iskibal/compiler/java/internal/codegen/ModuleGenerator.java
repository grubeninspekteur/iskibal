package work.spell.iskibal.compiler.java.internal.codegen;

import module java.base;

import module iskibal.rule.model;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.types.JavaTypeInferenceContext;
import work.spell.iskibal.compiler.java.types.JavaTypeInferenceVisitor;
import work.spell.iskibal.compiler.java.types.JavaTypeResolver;

/// Generates a complete Java class for a RuleModule.
public final class ModuleGenerator {

    private final JavaCompilerOptions options;

    public ModuleGenerator(JavaCompilerOptions options) {
        this.options = options;
    }

    /// Generates Java source code for the rule module.
    public String generate(RuleModule module) {
        RuleClassWriter writer = new RuleClassWriter();

        if (options.packageName() != null && !options.packageName().isEmpty()) {
            writer.packageDecl(options.packageName());
        }

        writer.importLine("java.math.BigDecimal")
                .importLine("java.util.Objects")
                .importLine("static work.spell.iskibal.runtime.NumericHelpers.*")
                .importLine("static work.spell.iskibal.runtime.CollectionHelpers.*");
        if (options.diagnosticsEnabled()) {
            writer.importLine("work.spell.iskibal.runtime.RuleEvent");
            writer.importLine("work.spell.iskibal.runtime.RuleListener");
        }
        for (Import imp : module.imports()) {
            writer.importLine(imp.type());
        }
        writer.blankLine();

        writer.beginClass(options.className());

        generateFields(writer, module);
        generateConstructor(writer, module);

        Set<String> globalNames = new HashSet<>();
        for (Global g : module.globals()) {
            globalNames.add(g.name());
        }
        Set<String> outputNames = new HashSet<>();
        Map<String, String> outputTypes = new HashMap<>();
        for (Output o : module.outputs()) {
            outputNames.add(o.name());
            outputTypes.put(o.name(), o.type());
        }

        JavaTypeInferenceVisitor typeVisitor = null;
        if (options.typeInferenceEnabled()) {
            JavaTypeResolver resolver = new JavaTypeResolver(options.typeClassLoader());
            JavaTypeInferenceContext context = JavaTypeInferenceContext.fromModule(module, resolver);
            typeVisitor = new JavaTypeInferenceVisitor(context);
        }

        ExpressionGenerator exprGen = new ExpressionGenerator(options, globalNames, outputNames, outputTypes,
                typeVisitor);
        StatementGenerator stmtGen = new StatementGenerator(exprGen);
        RuleGenerator ruleGen = new RuleGenerator(stmtGen, exprGen, options);

        for (Rule rule : module.rules()) {
            ruleGen.generate(rule, writer);
        }

        generateEvaluateMethod(writer, module, ruleGen);
        generateOutputGetters(writer, module);

        writer.endClass();
        return writer.build();
    }

    private void generateFields(RuleClassWriter writer, RuleModule module) {
        for (Fact fact : module.facts()) {
            writer.finalField(fact.type(), JavaIdentifiers.sanitize(fact.name()));
        }
        for (Global global : module.globals()) {
            writer.finalField(global.type(), JavaIdentifiers.sanitize(global.name()));
        }
        for (Output output : module.outputs()) {
            writer.mutableField(output.type(), JavaIdentifiers.sanitize(output.name()));
        }
        if (options.diagnosticsEnabled()) {
            writer.finalField("RuleListener", "__ruleListener");
        }
        if (!module.dataTables().isEmpty()) {
            ExpressionGenerator litGen = new ExpressionGenerator(options, Set.of(), Set.of());
            for (DataTable table : module.dataTables()) {
                generateDataTableField(writer, table, litGen);
            }
        }
        if (!module.facts().isEmpty() || !module.globals().isEmpty() || !module.outputs().isEmpty()
                || !module.dataTables().isEmpty() || options.diagnosticsEnabled()) {
            writer.blankLine();
        }
    }

    private void generateDataTableField(RuleClassWriter writer, DataTable table, ExpressionGenerator exprGen) {
        if (table.rows().isEmpty()) {
            return;
        }

        List<String> columns = new ArrayList<>(table.rows().getFirst().values().keySet());

        if (columns.size() == 2) {
            String keyCol = columns.get(0);
            String valueCol = columns.get(1);
            StringBuilder value = new StringBuilder("java.util.Map.ofEntries(\n");
            for (int i = 0; i < table.rows().size(); i++) {
                DataTable.Row row = table.rows().get(i);
                value.append("\t\t\tjava.util.Map.entry(")
                        .append(exprGen.generate(row.values().get(keyCol))).append(", ")
                        .append(exprGen.generate(row.values().get(valueCol))).append(")");
                if (i < table.rows().size() - 1) {
                    value.append(",");
                }
                value.append("\n");
            }
            value.append("\t\t)");
            writer.finalFieldWithValue("java.util.Map<Object, Object>", table.id(), value.toString());
        } else {
            StringBuilder value = new StringBuilder("java.util.List.of(\n");
            for (int i = 0; i < table.rows().size(); i++) {
                DataTable.Row row = table.rows().get(i);
                value.append("\t\t\tjava.util.Map.ofEntries(\n");
                List<Map.Entry<String, Expression>> entries = new ArrayList<>(row.values().entrySet());
                for (int j = 0; j < entries.size(); j++) {
                    Map.Entry<String, Expression> entry = entries.get(j);
                    value.append("\t\t\t\tjava.util.Map.entry(\"").append(entry.getKey()).append("\", ")
                            .append(exprGen.generate(entry.getValue())).append(")");
                    if (j < entries.size() - 1) {
                        value.append(",");
                    }
                    value.append("\n");
                }
                value.append("\t\t\t)");
                if (i < table.rows().size() - 1) {
                    value.append(",");
                }
                value.append("\n");
            }
            value.append("\t\t)");
            writer.finalFieldWithValue("java.util.List<java.util.Map<String, Object>>", table.id(),
                    value.toString());
        }
    }

    private void generateConstructor(RuleClassWriter writer, RuleModule module) {
        Set<String> globalNames = new HashSet<>();
        for (Global g : module.globals()) {
            globalNames.add(g.name());
        }
        Set<String> outputNames = new HashSet<>();
        for (Output o : module.outputs()) {
            outputNames.add(o.name());
        }

        ExpressionGenerator exprGen = new ExpressionGenerator(options, globalNames, outputNames);

        List<String> params = new ArrayList<>();
        for (Fact fact : module.facts()) {
            params.add(fact.type() + " " + JavaIdentifiers.sanitize(fact.name()));
        }
        for (Global global : module.globals()) {
            params.add(global.type() + " " + JavaIdentifiers.sanitize(global.name()));
        }
        if (options.diagnosticsEnabled()) {
            params.add("RuleListener __ruleListener");
        }

        writer.constructor(options.className(), params, body -> {
            for (Fact fact : module.facts()) {
                String s = JavaIdentifiers.sanitize(fact.name());
                body.assign("this." + s, s);
            }
            for (Global global : module.globals()) {
                String s = JavaIdentifiers.sanitize(global.name());
                body.assign("this." + s, s);
            }
            if (options.diagnosticsEnabled()) {
                body.assign("this.__ruleListener", "__ruleListener");
            }
            for (Output output : module.outputs()) {
                if (output.initialValue() != null) {
                    body.assign("this." + JavaIdentifiers.sanitize(output.name()),
                            exprGen.generate(output.initialValue()));
                }
            }
        });
    }

    private void generateEvaluateMethod(RuleClassWriter writer, RuleModule module, RuleGenerator ruleGen) {
        writer.evaluateMethod(body -> {
            for (Rule rule : module.rules()) {
                for (String methodName : ruleGen.getMethodNames(rule)) {
                    body.callMethod(methodName);
                }
            }
        });
    }

    private void generateOutputGetters(RuleClassWriter writer, RuleModule module) {
        for (Output output : module.outputs()) {
            String sanitized = JavaIdentifiers.sanitize(output.name());
            writer.getter(output.type(), sanitized, JavaIdentifiers.capitalize(sanitized));
        }
    }
}
