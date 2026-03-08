package work.spell.iskibal.compiler.java.internal.codegen;

import module java.base;

import module iskibal.rule.model;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;

/// Generates Java methods for Iskara rules.
public final class RuleGenerator {

    private final StatementGenerator statementGenerator;
    private final ExpressionGenerator expressionGenerator;
    private final boolean diagnosticsEnabled;

    public RuleGenerator(StatementGenerator statementGenerator, ExpressionGenerator expressionGenerator,
            JavaCompilerOptions options) {
        this.statementGenerator = statementGenerator;
        this.expressionGenerator = expressionGenerator;
        this.diagnosticsEnabled = options.diagnosticsEnabled();
    }

    /// Generates Java method(s) for a rule, writing them into `writer`.
    public void generate(Rule rule, RuleClassWriter writer) {
        switch (rule) {
            case Rule.SimpleRule sr -> generateSimpleRule(sr, writer);
            case Rule.TemplateRule tr -> generateTemplateRule(tr, writer);
            case Rule.DecisionTableRule dtr -> generateDecisionTableRule(dtr, writer);
        }
    }

    private void generateSimpleRule(Rule.SimpleRule rule, RuleClassWriter writer) {
        String description = rule.description().isEmpty() ? rule.id() : rule.description();
        writer.ruleMethod(toMethodName(rule.id()), description, body -> {
            if (!rule.when().isEmpty()) {
                writeLetStatements(rule.when(), body);
                String condition = generateCondition(rule.when());
                if (!rule.elseStatements().isEmpty()) {
                    body.ifElseBlock(condition, then -> {
                        if (diagnosticsEnabled) {
                            then.listenerCall(rule.id(), description);
                        }
                        statementGenerator.generate(rule.then(), then);
                    }, else_ -> statementGenerator.generate(rule.elseStatements(), else_));
                } else {
                    body.ifBlock(condition, then -> {
                        if (diagnosticsEnabled) {
                            then.listenerCall(rule.id(), description);
                        }
                        statementGenerator.generate(rule.then(), then);
                    });
                }
            } else {
                if (diagnosticsEnabled) {
                    body.listenerCall(rule.id(), description);
                }
                statementGenerator.generate(rule.then(), body);
            }
        });
    }

    private void generateTemplateRule(Rule.TemplateRule rule, RuleClassWriter writer) {
        DataTable table = rule.dataTable();
        if (table == null || table.rows().isEmpty()) {
            return;
        }

        int rowIndex = 0;
        for (DataTable.Row row : table.rows()) {
            String methodName = toMethodName(rule.id()) + "_" + rowIndex;
            String description = (rule.description().isEmpty() ? rule.id() : rule.description())
                    + " (row " + (rowIndex + 1) + ")";
            String rowId = rule.id() + "_" + rowIndex;
            rowIndex++;

            writer.ruleMethod(methodName, description, body -> {
                for (Map.Entry<String, Expression> entry : row.values().entrySet()) {
                    body.varDecl(entry.getKey(), expressionGenerator.generate(entry.getValue()));
                }
                if (!rule.when().isEmpty()) {
                    writeLetStatements(rule.when(), body);
                    body.ifBlock(generateCondition(rule.when()), then -> {
                        if (diagnosticsEnabled) {
                            then.listenerCall(rowId, description);
                        }
                        statementGenerator.generate(rule.then(), then);
                    });
                } else {
                    if (diagnosticsEnabled) {
                        body.listenerCall(rowId, description);
                    }
                    statementGenerator.generate(rule.then(), body);
                }
            });
        }
    }

    private void generateDecisionTableRule(Rule.DecisionTableRule rule, RuleClassWriter writer) {
        for (Rule.DecisionTableRule.Row row : rule.rows()) {
            String description = "Decision table row: " + row.id();
            writer.ruleMethod(toMethodName(row.id()), description, body -> {
                if (!row.when().isEmpty()) {
                    writeLetStatements(row.when(), body);
                    body.ifBlock(generateCondition(row.when()), then -> {
                        if (diagnosticsEnabled) {
                            then.listenerCall(row.id(), description);
                        }
                        statementGenerator.generate(row.then(), then);
                    });
                } else {
                    if (diagnosticsEnabled) {
                        body.listenerCall(row.id(), description);
                    }
                    statementGenerator.generate(row.then(), body);
                }
            });
        }
    }

    private String generateCondition(List<Statement> statements) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Statement stmt : statements) {
            if (stmt instanceof Statement.ExpressionStatement es) {
                if (!first) {
                    sb.append(" && ");
                }
                sb.append(expressionGenerator.generate(es.expression()));
                first = false;
            }
            // LetStatements are handled separately by writeLetStatements
        }

        // No expression statements, default to true
        if (first) {
            return "true";
        }

        return sb.toString();
    }

    /// Writes local variable declarations for let statements in the when section.
    /// These must be written before the condition so they are in scope.
    private void writeLetStatements(List<Statement> statements, BodyWriter body) {
        for (Statement stmt : statements) {
            if (stmt instanceof Statement.LetStatement ls) {
                body.varDecl(ls.name(), expressionGenerator.generate(ls.expression()));
            }
        }
    }

    private static String toMethodName(String ruleId) {
        return "rule_" + ruleId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /// Returns all method names for a rule (including per-row names for template and decision table rules).
    public List<String> getMethodNames(Rule rule) {
        return switch (rule) {
            case Rule.SimpleRule sr -> List.of(toMethodName(sr.id()));
            case Rule.TemplateRule tr -> {
                DataTable table = tr.dataTable();
                if (table == null || table.rows().isEmpty()) {
                    yield List.of();
                }
                yield IntStream.range(0, table.rows().size())
                        .mapToObj(i -> toMethodName(tr.id()) + "_" + i).toList();
            }
            case Rule.DecisionTableRule dtr -> dtr.rows().stream().map(row -> toMethodName(row.id())).toList();
        };
    }
}
