package work.spell.iskibal.parser.visitor;

import work.spell.iskibal.model.*;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.parser.IskaraParser;
import work.spell.iskibal.parser.IskaraParserBaseVisitor;
import work.spell.iskibal.parser.diagnostic.IskaraDiagnosticListener;

import java.util.*;

/**
 * Visitor that builds a complete RuleModule from the parse tree.
 */
public class RuleModuleVisitor extends IskaraParserBaseVisitor<RuleModule> {

    private final ExpressionVisitor expressionVisitor;
    private final StatementVisitor statementVisitor;
    private final TableVisitor tableVisitor;
    private final IskaraDiagnosticListener diagnostics;

    private final List<Import> imports = new ArrayList<>();
    private final List<Fact> facts = new ArrayList<>();
    private final List<Global> globals = new ArrayList<>();
    private final List<Output> outputs = new ArrayList<>();
    private final List<DataTable> dataTables = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();

    public RuleModuleVisitor(ExpressionVisitor expressionVisitor, IskaraDiagnosticListener diagnostics) {
        this.expressionVisitor = expressionVisitor;
        this.statementVisitor = expressionVisitor.getStatementVisitor();
        this.tableVisitor = new TableVisitor(expressionVisitor, diagnostics);
        this.diagnostics = diagnostics;
    }

    @Override
    public RuleModule visitRuleModule(IskaraParser.RuleModuleContext ctx) {
        // Process all preamble sections
        for (var preamble : ctx.preamble()) {
            processPreamble(preamble);
        }

        // Process all rule definitions
        for (var ruleDef : ctx.ruleDefinition()) {
            Rule rule = processRuleDefinition(ruleDef);
            if (rule != null) {
                rules.add(rule);
            }
        }

        return new RuleModule.Default(List.copyOf(imports), List.copyOf(facts), List.copyOf(globals),
                List.copyOf(outputs), List.copyOf(dataTables), List.copyOf(rules));
    }

    // =========================================================================
    // Preamble sections
    // =========================================================================

    private void processPreamble(IskaraParser.PreambleContext ctx) {
        if (ctx.importSection() != null) {
            processImportSection(ctx.importSection());
        } else if (ctx.factSection() != null) {
            processFactSection(ctx.factSection());
        } else if (ctx.globalSection() != null) {
            processGlobalSection(ctx.globalSection());
        } else if (ctx.outputSection() != null) {
            processOutputSection(ctx.outputSection());
        } else if (ctx.dataTableDef() != null) {
            processDataTableDef(ctx.dataTableDef());
        }
    }

    private void processImportSection(IskaraParser.ImportSectionContext ctx) {
        for (var importDecl : ctx.importDecl()) {
            String alias = expressionVisitor.extractIdentifier(importDecl.identifier());
            String type = importDecl.qualifiedName().getText();
            imports.add(new Import.Definition(alias, type));
        }
    }

    private void processFactSection(IskaraParser.FactSectionContext ctx) {
        for (var factDecl : ctx.factDecl()) {
            String name = expressionVisitor.extractIdentifier(factDecl.identifier());
            String type = processTypeRef(factDecl.typeRef());
            String description = factDecl.STRING() != null ? unquoteString(factDecl.STRING().getText()) : null;
            facts.add(new Fact.Definition(name, type, description));
        }
    }

    private void processGlobalSection(IskaraParser.GlobalSectionContext ctx) {
        for (var globalDecl : ctx.globalDecl()) {
            String name = expressionVisitor.extractIdentifier(globalDecl.identifier());
            String type = processTypeRef(globalDecl.typeRef());
            String description = globalDecl.STRING() != null ? unquoteString(globalDecl.STRING().getText()) : null;
            globals.add(new Global.Definition(name, type, description));
        }
    }

    private void processOutputSection(IskaraParser.OutputSectionContext ctx) {
        for (var outputDecl : ctx.outputDecl()) {
            String name = expressionVisitor.extractIdentifier(outputDecl.identifier());
            String type = processTypeRef(outputDecl.typeRef());
            Expression initialValue = outputDecl.expression() != null
                    ? expressionVisitor.visit(outputDecl.expression())
                    : null;
            String description = outputDecl.STRING() != null ? unquoteString(outputDecl.STRING().getText()) : null;
            outputs.add(new Output.Definition(name, type, initialValue, description));
        }
    }

    private void processDataTableDef(IskaraParser.DataTableDefContext ctx) {
        String id = expressionVisitor.extractIdentifier(ctx.identifier());
        DataTable table = tableVisitor.parseDataTable(id, ctx.tableContent());
        dataTables.add(table);
    }

    private String processTypeRef(IskaraParser.TypeRefContext ctx) {
        return switch (ctx) {
            case IskaraParser.ListTypeContext ltc -> ltc.qualifiedName().getText() + "[]";
            case IskaraParser.SetTypeContext stc -> stc.qualifiedName().getText() + "{}";
            case IskaraParser.MapTypeContext mtc ->
                "[" + processTypeRef(mtc.typeRef(0)) + ":" + processTypeRef(mtc.typeRef(1)) + "]";
            case IskaraParser.SimpleTypeContext stc -> stc.qualifiedName().getText();
            default -> ctx.getText();
        };
    }

    // =========================================================================
    // Rule definitions
    // =========================================================================

    private Rule processRuleDefinition(IskaraParser.RuleDefinitionContext ctx) {
        if (ctx.simpleRule() != null) {
            return processSimpleRule(ctx.simpleRule());
        } else if (ctx.templateRule() != null) {
            return processTemplateRule(ctx.templateRule());
        } else if (ctx.decisionTableRule() != null) {
            return processDecisionTableRule(ctx.decisionTableRule());
        }
        return null;
    }

    private Rule.SimpleRule processSimpleRule(IskaraParser.SimpleRuleContext ctx) {
        String id = expressionVisitor.extractIdentifier(ctx.identifier());
        String description = ctx.STRING() != null ? unquoteString(ctx.STRING().getText()) : null;

        // Handle local data table if present
        if (ctx.localDataTable() != null) {
            String tableId = expressionVisitor.extractIdentifier(ctx.localDataTable().identifier());
            DataTable table = tableVisitor.parseDataTable(tableId, ctx.localDataTable().tableContent());
            dataTables.add(table);
        }

        List<Statement> when = processStatementList(ctx.whenSection().statementList());
        List<Statement> then = processStatementList(ctx.thenSection().statementList());
        List<Statement> elseStatements = ctx.elseSection() != null
                ? processStatementList(ctx.elseSection().statementList())
                : List.of();

        return new Rule.SimpleRule(id, description, when, then, elseStatements);
    }

    private Rule.TemplateRule processTemplateRule(IskaraParser.TemplateRuleContext ctx) {
        String id = expressionVisitor.extractIdentifier(ctx.identifier());
        String description = ctx.STRING() != null ? unquoteString(ctx.STRING().getText()) : null;

        // Parse anonymous data table
        DataTable dataTable = tableVisitor.parseDataTable(id + "_data", ctx.anonymousDataTable().tableContent());

        List<Statement> when = processStatementList(ctx.whenSection().statementList());
        List<Statement> then = processStatementList(ctx.thenSection().statementList());

        return new Rule.TemplateRule(id, description, dataTable, when, then);
    }

    private Rule.DecisionTableRule processDecisionTableRule(IskaraParser.DecisionTableRuleContext ctx) {
        String id = expressionVisitor.extractIdentifier(ctx.identifier());
        String description = ctx.STRING() != null ? unquoteString(ctx.STRING().getText()) : null;

        // Parse decision table structure
        TableVisitor.DecisionTableStructure structure = tableVisitor.parseDecisionTableStructure(ctx.tableContent());

        // Parse aliases from where clause
        Map<String, Block> aliases = new LinkedHashMap<>();
        if (ctx.whereClause() != null) {
            for (var aliasDef : ctx.whereClause().aliasDefinition()) {
                String aliasName = expressionVisitor.extractIdentifier(aliasDef.identifier());
                Expression blockExpr = expressionVisitor.visit(aliasDef.block());
                if (blockExpr instanceof Block block) {
                    aliases.put(aliasName, block);
                }
            }
        }

        // Convert decision table rows to Rule.DecisionTableRule.Row
        List<Rule.DecisionTableRule.Row> rows = new ArrayList<>();
        for (var structRow : structure.rows()) {
            List<Statement> whenStmts = buildDecisionStatements(structure.whenColumns(), structRow.whenCells(),
                    aliases);
            List<Statement> thenStmts = buildDecisionStatements(structure.thenColumns(), structRow.thenCells(),
                    aliases);
            rows.add(new Rule.DecisionTableRule.Row(structRow.id(), whenStmts, thenStmts));
        }

        return new Rule.DecisionTableRule(id, description, rows, aliases);
    }

    private List<Statement> buildDecisionStatements(List<TableVisitor.ColumnDef> columns, List<String> cells,
            Map<String, Block> aliases) {
        List<Statement> statements = new ArrayList<>();

        for (int i = 0; i < columns.size() && i < cells.size(); i++) {
            String cellValue = cells.get(i);
            String columnExpr = columns.get(i).expression();

            // Check if column references an alias
            if (columnExpr.startsWith("#")) {
                String aliasName = columnExpr.substring(1);
                if (aliasName.startsWith("`") && aliasName.endsWith("`")) {
                    aliasName = aliasName.substring(1, aliasName.length() - 1);
                }

                // Skip empty cells for aliases, but wildcards mean "invoke with no param"
                if (cellValue.isEmpty()) {
                    continue;
                }

                // Alias reference - inline the alias block with cell value
                if (aliases.containsKey(aliasName)) {
                    Block aliasBlock = aliases.get(aliasName);
                    List<Statement> aliasStatements = aliasBlock.statements();
                    List<String> parameters = aliasBlock.parameters();

                    // Check if block has explicit parameters (new style [:paramName | ...])
                    if (!parameters.isEmpty()) {
                        // Substitute the cell value for the first parameter
                        Expression cellExpr = parseCellAsExpression(cellValue);
                        statements.add(new Statement.LetStatement(parameters.getFirst(), cellExpr));
                        // Add all the block statements
                        statements.addAll(aliasStatements);
                    } else {
                        // [ ] or { } - no parameter, just inline the statements (wildcards invoke too)
                        for (var stmt : aliasStatements) {
                            statements.add(stmt);
                        }
                    }
                }
            } else {
                // Direct expression - skip empty cells or wildcards
                if (cellValue.isEmpty() || cellValue.equals("*")) {
                    continue;
                }
                // Combine column expression with cell value
                String fullExpr = columnExpr + " " + cellValue;
                Expression expr = parseCellAsExpression(fullExpr.trim());
                statements.add(new Statement.ExpressionStatement(expr));
            }
        }

        return statements;
    }

    private Expression parseCellAsExpression(String text) {
        if (text.isEmpty()) {
            return new Expression.Literal.NullLiteral();
        }
        try {
            var lexer = new work.spell.iskibal.parser.IskaraLexer(org.antlr.v4.runtime.CharStreams.fromString(text));
            var tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
            var parser = new IskaraParser(tokens);
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            var exprCtx = parser.expression();
            if (parser.getNumberOfSyntaxErrors() == 0) {
                return expressionVisitor.visit(exprCtx);
            }
        } catch (Exception e) {
            // Fall through
        }
        return new Expression.Literal.StringLiteral(text);
    }

    // =========================================================================
    // Statement list handling
    // =========================================================================

    private List<Statement> processStatementList(IskaraParser.StatementListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<Statement> statements = new ArrayList<>();
        for (var stmtCtx : ctx.statement()) {
            Statement stmt = statementVisitor.visit(stmtCtx);
            if (stmt != null) {
                statements.add(stmt);
            }
        }
        return statements;
    }

    // =========================================================================
    // Utility methods
    // =========================================================================

    private String unquoteString(String s) {
        if (s == null || s.length() < 2)
            return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
