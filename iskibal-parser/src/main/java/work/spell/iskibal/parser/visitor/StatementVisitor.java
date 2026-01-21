package work.spell.iskibal.parser.visitor;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Statement;
import work.spell.iskibal.parser.IskaraParser;
import work.spell.iskibal.parser.IskaraParserBaseVisitor;
import work.spell.iskibal.parser.diagnostic.IskaraDiagnosticListener;

/**
 * Visitor that builds Statement AST nodes from the parse tree.
 */
public class StatementVisitor extends IskaraParserBaseVisitor<Statement> {

    private final ExpressionVisitor expressionVisitor;
    private final IskaraDiagnosticListener diagnostics;

    public StatementVisitor(ExpressionVisitor expressionVisitor, IskaraDiagnosticListener diagnostics) {
        this.expressionVisitor = expressionVisitor;
        this.diagnostics = diagnostics;
    }

    @Override
    public Statement visitStatement(IskaraParser.StatementContext ctx) {
        if (ctx.letStatement() != null) {
            return visit(ctx.letStatement());
        } else if (ctx.expressionStatement() != null) {
            return visit(ctx.expressionStatement());
        }
        return null;
    }

    @Override
    public Statement visitLetStatement(IskaraParser.LetStatementContext ctx) {
        String name = expressionVisitor.extractIdentifier(ctx.identifier());
        Expression value = expressionVisitor.visit(ctx.expression());
        return new Statement.LetStatement(name, value);
    }

    @Override
    public Statement visitExpressionStatement(IskaraParser.ExpressionStatementContext ctx) {
        Expression expression = expressionVisitor.visit(ctx.expression());

        // Check for assignment expression pattern: target := value
        // The grammar parses := as ASSIGN in let statements, but in expression statements
        // we need to detect assignment expressions manually

        return new Statement.ExpressionStatement(expression);
    }
}
