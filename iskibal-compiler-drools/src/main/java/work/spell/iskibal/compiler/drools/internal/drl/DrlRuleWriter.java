package work.spell.iskibal.compiler.drools.internal.drl;

import module java.base;

import module iskibal.rule.model;

/// Writes individual DRL rule blocks (header, when, then) using an
/// [IndentedWriter] for consistent formatting.
final class DrlRuleWriter {

    private final DrlExpressionGenerator exprGen;
    private final RuleModule module;

    DrlRuleWriter(DrlExpressionGenerator exprGen, RuleModule module) {
        this.exprGen = exprGen;
        this.module = module;
    }

    void writeRule(Rule rule, IndentedWriter w) {
        switch (rule) {
            case Rule.SimpleRule sr -> writeSimpleRule(sr, w);
            case Rule.TemplateRule tr -> writeTemplateRule(tr, w);
            case Rule.DecisionTableRule dtr -> writeDecisionTableRule(dtr, w);
        }
    }

    private void writeSimpleRule(Rule.SimpleRule rule, IndentedWriter w) {
        writeRuleHeader(w, rule.id(), rule.description());
        writeWhenSection(w, rule.when(), false);
        writeThenSection(w, rule.then());

        if (!rule.elseStatements().isEmpty()) {
            w.blankLine();
            String elseDesc = rule.description() == null ? null : rule.description() + " (else)";
            writeRuleHeader(w, rule.id() + "-else", elseDesc);
            writeWhenSection(w, rule.when(), true);
            writeThenSection(w, rule.elseStatements());
        }
    }

    private void writeTemplateRule(Rule.TemplateRule rule, IndentedWriter w) {
        List<DataTable.Row> rows = rule.dataTable().rows();
        if (rows.isEmpty()) {
            w.line("// Template rule '" + rule.id() + "' has no data table rows");
            w.blankLine();
            return;
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            DataTable.Row row = rows.get(rowIndex);
            String rowDesc = rule.description() == null ? null : rule.description() + " (row " + (rowIndex + 1) + ")";
            writeRuleHeader(w, rule.id() + "-" + rowIndex, rowDesc);
            writeWhenSectionTemplate(w, rule.when(), row);
            writeThenSection(w, rule.then());
        }
    }

    private void writeDecisionTableRule(Rule.DecisionTableRule rule, IndentedWriter w) {
        if (rule.rows().isEmpty()) {
            w.line("// Decision table rule '" + rule.id() + "' has no rows");
            w.blankLine();
            return;
        }
        for (Rule.DecisionTableRule.Row row : rule.rows()) {
            writeRuleHeader(w, rule.id() + "-" + row.id(), rule.description());
            writeWhenSection(w, row.when(), false);
            writeThenSection(w, row.then());
        }
    }

    private void writeRuleHeader(IndentedWriter w, String id, String description) {
        w.line("rule \"" + escapeId(id) + "\"");
        if (description != null && !description.isBlank()) {
            w.indent().line("@Description(\"" + escapeId(description) + "\")").dedent();
        }
    }

    private void writeWhenSection(IndentedWriter w, List<Statement> when, boolean negate) {
        w.indent();
        w.line("when");
        w.indent();
        writeFactBindings(w);
        writeConditions(w, when, negate);
        w.dedent();
        w.dedent();
    }

    private void writeWhenSectionTemplate(IndentedWriter w, List<Statement> when, DataTable.Row row) {
        w.indent();
        w.line("when");
        w.indent();
        writeFactBindings(w);

        List<String> conditions = new ArrayList<>();

        for (Map.Entry<String, Expression> entry : row.values().entrySet()) {
            String varValue = exprGen.generate(entry.getValue());
            conditions.add("/* row binding: " + entry.getKey() + " = " + varValue + " */true");
        }

        for (Statement stmt : when) {
            if (stmt instanceof Statement.ExpressionStatement es) {
                conditions.add(exprGen.generate(es.expression()));
            } else if (stmt instanceof Statement.LetStatement ls) {
                conditions.add("/* TODO: let binding '" + ls.name() + "' not supported in DRL when */true");
            }
        }

        if (!conditions.isEmpty()) {
            w.line("eval(" + String.join("\n" + "    ".repeat(3) + "&& ", conditions) + ")");
        }

        w.dedent();
        w.dedent();
    }

    private void writeFactBindings(IndentedWriter w) {
        for (Fact fact : module.facts()) {
            String varName = "$" + DrlExpressionGenerator.sanitize(fact.name());
            String typeName = resolveSimpleType(fact.type());
            w.line(varName + " : " + typeName + "()");
        }
    }

    private void writeConditions(IndentedWriter w, List<Statement> when, boolean negate) {
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
            String joined = String.join("\n" + "    ".repeat(3) + "&& ", conditions);
            if (negate) {
                w.line("eval(!(" + joined + "))");
            } else {
                w.line("eval(" + joined + ")");
            }
        }
    }

    private void writeThenSection(IndentedWriter w, List<Statement> then) {
        w.indent();
        w.line("then");
        w.indent();
        for (Statement stmt : then) {
            String code = switch (stmt) {
                case Statement.ExpressionStatement es -> exprGen.generate(es.expression()) + ";";
                case Statement.LetStatement ls -> "Object " + DrlExpressionGenerator.sanitize(ls.name()) + " = "
                        + exprGen.generate(ls.expression()) + ";";
            };
            w.line(code);
        }
        w.dedent();
        w.dedent();
        w.line("end");
        w.blankLine();
    }

    /// Returns the simple (unqualified) type name when it was imported;
    /// otherwise returns the fully qualified name.
    private String resolveSimpleType(String type) {
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

    private static String escapeId(String id) {
        return id.replace("\"", "\\\"");
    }
}
