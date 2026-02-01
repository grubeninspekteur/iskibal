package work.spell.iskibal.compiler.java.internal.codegen;

import java.util.List;
import java.util.Map;

import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.Statement;

/**
 * Generates Java methods for Iskara rules.
 */
public final class RuleGenerator {

    private final StatementGenerator statementGenerator;
    private final ExpressionGenerator expressionGenerator;

    public RuleGenerator(StatementGenerator statementGenerator, ExpressionGenerator expressionGenerator) {
        this.statementGenerator = statementGenerator;
        this.expressionGenerator = expressionGenerator;
    }

    /**
     * Generates Java method(s) for a rule.
     */
    public String generate(Rule rule) {
        return switch (rule) {
            case Rule.SimpleRule sr -> generateSimpleRule(sr);
            case Rule.TemplateRule tr -> generateTemplateRule(tr);
            case Rule.DecisionTableRule dtr -> generateDecisionTableRule(dtr);
        };
    }

    private String generateSimpleRule(Rule.SimpleRule rule) {
        StringBuilder sb = new StringBuilder();
        String methodName = toMethodName(rule.id());

        sb.append("\t/**\n");
        sb.append("\t * ").append(rule.description().isEmpty() ? rule.id() : rule.description()).append("\n");
        sb.append("\t */\n");
        sb.append("\tprivate void ").append(methodName).append("() {\n");

        // Generate when section as condition
        if (!rule.when().isEmpty()) {
            // First, generate let statements as local variable declarations
            sb.append(generateLetStatements(rule.when(), "\t\t"));

            sb.append("\t\tif (");
            sb.append(generateCondition(rule.when()));
            sb.append(") {\n");

            // Generate then section
            sb.append(statementGenerator.generateStatements(rule.then(), "\t\t\t"));

            // Generate else section if present
            if (!rule.elseStatements().isEmpty()) {
                sb.append("\t\t} else {\n");
                sb.append(statementGenerator.generateStatements(rule.elseStatements(), "\t\t\t"));
            }

            sb.append("\t\t}\n");
        } else {
            // No condition, just execute then section
            sb.append(statementGenerator.generateStatements(rule.then(), "\t\t"));
        }

        sb.append("\t}\n");
        return sb.toString();
    }

    private String generateTemplateRule(Rule.TemplateRule rule) {
        StringBuilder sb = new StringBuilder();
        DataTable table = rule.dataTable();

        if (table == null || table.rows().isEmpty()) {
            return "";
        }

        // Generate one method per row
        int rowIndex = 0;
        for (DataTable.Row row : table.rows()) {
            String methodName = toMethodName(rule.id()) + "_" + rowIndex;
            rowIndex++;

            sb.append("\t/**\n");
            sb.append("\t * ").append(rule.description().isEmpty() ? rule.id() : rule.description());
            sb.append(" (row ").append(rowIndex).append(")\n");
            sb.append("\t */\n");
            sb.append("\tprivate void ").append(methodName).append("() {\n");

            // Declare column values as local variables
            for (Map.Entry<String, Expression> entry : row.values().entrySet()) {
                sb.append("\t\tvar ").append(entry.getKey()).append(" = ");
                sb.append(expressionGenerator.generate(entry.getValue())).append(";\n");
            }

            // Generate when section as condition
            if (!rule.when().isEmpty()) {
                // First, generate let statements as local variable declarations
                sb.append(generateLetStatements(rule.when(), "\t\t"));

                sb.append("\t\tif (");
                sb.append(generateCondition(rule.when()));
                sb.append(") {\n");
                sb.append(statementGenerator.generateStatements(rule.then(), "\t\t\t"));
                sb.append("\t\t}\n");
            } else {
                sb.append(statementGenerator.generateStatements(rule.then(), "\t\t"));
            }

            sb.append("\t}\n\n");
        }

        return sb.toString();
    }

    private String generateDecisionTableRule(Rule.DecisionTableRule rule) {
        StringBuilder sb = new StringBuilder();

        for (Rule.DecisionTableRule.Row row : rule.rows()) {
            String methodName = toMethodName(row.id());

            sb.append("\t/**\n");
            sb.append("\t * Decision table row: ").append(row.id()).append("\n");
            sb.append("\t */\n");
            sb.append("\tprivate void ").append(methodName).append("() {\n");

            if (!row.when().isEmpty()) {
                // First, generate let statements as local variable declarations
                sb.append(generateLetStatements(row.when(), "\t\t"));

                sb.append("\t\tif (");
                sb.append(generateCondition(row.when()));
                sb.append(") {\n");
                sb.append(statementGenerator.generateStatements(row.then(), "\t\t\t"));
                sb.append("\t\t}\n");
            } else {
                sb.append(statementGenerator.generateStatements(row.then(), "\t\t"));
            }

            sb.append("\t}\n\n");
        }

        return sb.toString();
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
            // LetStatements are handled separately by generateLetStatements
        }

        if (first) {
            // No expression statements, default to true
            return "true";
        }

        return sb.toString();
    }

    /**
     * Generates local variable declarations for let statements in the when section.
     * These need to be generated before the condition so they are in scope.
     */
    private String generateLetStatements(List<Statement> statements, String indent) {
        StringBuilder sb = new StringBuilder();

        for (Statement stmt : statements) {
            if (stmt instanceof Statement.LetStatement ls) {
                sb.append(indent).append("var ").append(ls.name()).append(" = ");
                sb.append(expressionGenerator.generate(ls.expression())).append(";\n");
            }
        }

        return sb.toString();
    }

    private static String toMethodName(String ruleId) {
        // Convert rule ID to valid Java method name
        return "rule_" + ruleId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Returns the method name for a rule (for use in evaluate method).
     */
    public String getMethodName(Rule rule) {
        return switch (rule) {
            case Rule.SimpleRule sr -> toMethodName(sr.id());
            case Rule.TemplateRule tr -> toMethodName(tr.id());
            case Rule.DecisionTableRule dtr -> toMethodName(dtr.id());
        };
    }

    /**
     * Returns all method names for a rule (including template row methods).
     */
    public List<String> getMethodNames(Rule rule) {
        return switch (rule) {
            case Rule.SimpleRule sr -> List.of(toMethodName(sr.id()));
            case Rule.TemplateRule tr -> {
                DataTable table = tr.dataTable();
                if (table == null || table.rows().isEmpty()) {
                    yield List.of();
                }
                yield java.util.stream.IntStream.range(0, table.rows().size())
                        .mapToObj(i -> toMethodName(tr.id()) + "_" + i).toList();
            }
            case Rule.DecisionTableRule dtr -> dtr.rows().stream().map(row -> toMethodName(row.id())).toList();
        };
    }
}
