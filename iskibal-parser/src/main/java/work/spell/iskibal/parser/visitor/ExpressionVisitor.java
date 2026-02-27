package work.spell.iskibal.parser.visitor;

import org.antlr.v4.runtime.Token;
import module iskibal.rule.model;
import work.spell.iskibal.model.Expression.*;
import work.spell.iskibal.model.Expression.MessageSend.DefaultMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage.KeywordPart;
import work.spell.iskibal.model.Expression.MessageSend.UnaryMessage;
import work.spell.iskibal.parser.IskaraParser;
import work.spell.iskibal.parser.IskaraParserBaseVisitor;
import work.spell.iskibal.parser.diagnostic.IskaraDiagnosticListener;

import module java.base;

/// Visitor that builds Expression AST nodes from the parse tree.
public class ExpressionVisitor extends IskaraParserBaseVisitor<Expression> {

    private final IskaraDiagnosticListener diagnostics;
    private final Locale locale;
    private final StatementVisitor statementVisitor;

    public ExpressionVisitor(IskaraDiagnosticListener diagnostics, Locale locale) {
        this.diagnostics = diagnostics;
        this.locale = locale;
        this.statementVisitor = new StatementVisitor(this, diagnostics);
    }

    public StatementVisitor getStatementVisitor() {
        return statementVisitor;
    }

    // =========================================================================
    // Expression precedence rules
    // =========================================================================

    @Override
    public Expression visitCommaExpr(IskaraParser.CommaExprContext ctx) {
        Expression left = visit(ctx.expression(0));
        Expression right = visit(ctx.expression(1));
        // Comma is a logical AND with lowest precedence
        // Use a keyword message to represent the AND operation
        return new KeywordMessage(left, List.of(new KeywordPart("and", right)));
    }

    @Override
    public Expression visitAssignmentExpr(IskaraParser.AssignmentExprContext ctx) {
        Expression target = visit(ctx.target);
        Expression value = visit(ctx.value);
        return new Assignment(target, value);
    }

    @Override
    public Expression visitComparisonExpr(IskaraParser.ComparisonExprContext ctx) {
        Expression left = visit(ctx.expression(0));
        Expression right = visit(ctx.expression(1));
        Binary.Operator op = mapComparisonOp(ctx.comparisonOp());
        return new Binary(left, op, right);
    }

    @Override
    public Expression visitAdditiveExpr(IskaraParser.AdditiveExprContext ctx) {
        Expression left = visit(ctx.expression(0));
        Expression right = visit(ctx.expression(1));
        Binary.Operator op = mapAdditiveOp(ctx.additiveOp());
        return new Binary(left, op, right);
    }

    @Override
    public Expression visitMultiplicativeExpr(IskaraParser.MultiplicativeExprContext ctx) {
        Expression left = visit(ctx.expression(0));
        Expression right = visit(ctx.expression(1));
        Binary.Operator op = mapMultiplicativeOp(ctx.multiplicativeOp());
        return new Binary(left, op, right);
    }

    @Override
    public Expression visitUnaryMinusExpr(IskaraParser.UnaryMinusExprContext ctx) {
        Expression expr = visit(ctx.expression());
        // Represent unary minus as 0 - expr
        return new Binary(new Literal.NumberLiteral(BigDecimal.ZERO), Binary.Operator.MINUS, expr);
    }

    // =========================================================================
    // Message send expressions
    // =========================================================================

    @Override
    public Expression visitMessageSend(IskaraParser.MessageSendContext ctx) {
        return visit(ctx.messageSendExpr());
    }

    @Override
    public Expression visitMessageSendExpr(IskaraParser.MessageSendExprContext ctx) {
        Expression receiver = visit(ctx.navigationExpr());
        // Process all message parts in sequence, each result becoming the next receiver
        for (var part : ctx.messagePart()) {
            receiver = processMessagePart(receiver, part);
        }
        return receiver;
    }

    private Expression processMessagePart(Expression receiver, IskaraParser.MessagePartContext part) {
        if (part instanceof IskaraParser.KeywordMessagePartContext kw) {
            return visitKeywordMessagePart(receiver, kw);
        } else if (part instanceof IskaraParser.UnaryMessagePartContext un) {
            return visitUnaryMessagePart(receiver, un);
        } else if (part instanceof IskaraParser.DefaultMessagePartContext def) {
            return visitDefaultMessagePart(receiver, def);
        }
        return receiver;
    }

    private Expression visitKeywordMessagePart(Expression receiver, IskaraParser.KeywordMessagePartContext ctx) {
        List<KeywordPart> parts = new ArrayList<>();
        var selectors = ctx.messageSelector();
        var expressions = ctx.navigationExpr();
        for (int i = 0; i < selectors.size(); i++) {
            String keyword = extractMessageSelector(selectors.get(i));
            Expression arg = visit(expressions.get(i));
            parts.add(new KeywordPart(keyword, arg));
        }
        return new KeywordMessage(receiver, parts);
    }

    private Expression visitUnaryMessagePart(Expression receiver, IskaraParser.UnaryMessagePartContext ctx) {
        String selector = extractMessageSelector(ctx.messageSelector());
        return new UnaryMessage(receiver, selector);
    }

    private String extractMessageSelector(IskaraParser.MessageSelectorContext ctx) {
        // messageSelector can be IDENTIFIER or a keyword like WHERE, END, etc.
        return ctx.getText();
    }

    private Expression visitDefaultMessagePart(Expression receiver, IskaraParser.DefaultMessagePartContext ctx) {
        return new DefaultMessage(receiver);
    }

    // =========================================================================
    // Navigation expressions
    // =========================================================================

    @Override
    public Expression visitNavigationExpr(IskaraParser.NavigationExprContext ctx) {
        Expression receiver = visit(ctx.primaryExpr());
        List<IskaraParser.IdentifierContext> ids = ctx.identifier();
        if (ids.isEmpty()) {
            return receiver;
        }
        List<String> names = ids.stream().map(this::extractIdentifier).toList();
        return new Navigation(receiver, names);
    }

    // =========================================================================
    // Primary expressions
    // =========================================================================

    @Override
    public Expression visitLiteralExpr(IskaraParser.LiteralExprContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public Expression visitIdentifierExpr(IskaraParser.IdentifierExprContext ctx) {
        String name = extractIdentifier(ctx.identifier());
        return new Identifier(name);
    }

    @Override
    public Expression visitGlobalRefExpr(IskaraParser.GlobalRefExprContext ctx) {
        String name = extractIdentifier(ctx.globalRef().identifier());
        // Global references are identifiers prefixed with @
        return new Identifier("@" + name);
    }

    @Override
    public Expression visitBlockExpr(IskaraParser.BlockExprContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public Expression visitParenExpr(IskaraParser.ParenExprContext ctx) {
        return visit(ctx.expression());
    }

    // =========================================================================
    // Block expressions
    // =========================================================================

    @Override
    public Expression visitExplicitParamBlock(IskaraParser.ExplicitParamBlockContext ctx) {
        List<String> parameters = new ArrayList<>();
        for (var paramId : ctx.blockParams().identifier()) {
            parameters.add(extractIdentifier(paramId));
        }
        List<Statement> statements = collectStatements(ctx.statementList());
        return new Block(parameters, statements, false);
    }

    @Override
    public Expression visitImplicitParamBlock(IskaraParser.ImplicitParamBlockContext ctx) {
        // [| expression] - implicit 'it' parameter
        Expression bodyExpr = visit(ctx.expression());
        List<Statement> statements = List.of(new Statement.ExpressionStatement(bodyExpr));
        return new Block(List.of("it"), statements, true);
    }

    @Override
    public Expression visitStatementBlock(IskaraParser.StatementBlockContext ctx) {
        List<Statement> statements = collectStatements(ctx.statementList());
        return new Block(List.of(), statements, false);
    }

    private List<Statement> collectStatements(IskaraParser.StatementListContext ctx) {
        List<Statement> statements = new ArrayList<>();
        if (ctx != null) {
            for (var stmtCtx : ctx.statement()) {
                Statement stmt = statementVisitor.visit(stmtCtx);
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        }
        return statements;
    }

    // =========================================================================
    // Literals
    // =========================================================================

    @Override
    public Expression visitStringLiteral(IskaraParser.StringLiteralContext ctx) {
        String text = ctx.STRING().getText();
        // Remove quotes and unescape
        String value = unescapeString(text.substring(1, text.length() - 1));
        return new Literal.StringLiteral(value);
    }

    @Override
    public Expression visitTemplateStringLiteral(IskaraParser.TemplateStringLiteralContext ctx) {
        return visit(ctx.templateString());
    }

    @Override
    public Expression visitTemplateString(IskaraParser.TemplateStringContext ctx) {
        // Build template string by concatenating parts
        StringBuilder sb = new StringBuilder();
        List<Expression> parts = new ArrayList<>();

        for (var part : ctx.templatePart()) {
            if (part.TEMPLATE_TEXT() != null) {
                sb.append(unescapeString(part.TEMPLATE_TEXT().getText()));
            } else if (part.expression() != null) {
                // Flush text accumulator
                if (!sb.isEmpty()) {
                    parts.add(new Literal.StringLiteral(sb.toString()));
                    sb.setLength(0);
                }
                parts.add(visit(part.expression()));
            }
        }

        // Flush remaining text
        if (!sb.isEmpty()) {
            parts.add(new Literal.StringLiteral(sb.toString()));
        }

        // If only one part, return it directly
        if (parts.size() == 1) {
            return parts.getFirst();
        }

        // Otherwise, build a concatenation using + operations
        Expression result = parts.getFirst();
        for (int i = 1; i < parts.size(); i++) {
            result = new Binary(result, Binary.Operator.PLUS, parts.get(i));
        }
        return result;
    }

    @Override
    public Expression visitNumberLiteral(IskaraParser.NumberLiteralContext ctx) {
        String text = ctx.NUMBER().getText();
        BigDecimal value = parseNumber(text, ctx.NUMBER().getSymbol());
        return new Literal.NumberLiteral(value);
    }

    @Override
    public Expression visitTrueLiteral(IskaraParser.TrueLiteralContext ctx) {
        return new Literal.BooleanLiteral(true);
    }

    @Override
    public Expression visitFalseLiteral(IskaraParser.FalseLiteralContext ctx) {
        return new Literal.BooleanLiteral(false);
    }

    @Override
    public Expression visitNullLiteral(IskaraParser.NullLiteralContext ctx) {
        return new Literal.NullLiteral();
    }

    @Override
    public Expression visitListLit(IskaraParser.ListLitContext ctx) {
        return visit(ctx.listLiteral());
    }

    @Override
    public Expression visitListLiteral(IskaraParser.ListLiteralContext ctx) {
        List<Expression> elements = ctx.expression().stream().map(this::visit).toList();
        return new Literal.ListLiteral(elements);
    }

    @Override
    public Expression visitSetLit(IskaraParser.SetLitContext ctx) {
        return visit(ctx.setLiteral());
    }

    @Override
    public Expression visitSetLiteral(IskaraParser.SetLiteralContext ctx) {
        Set<Expression> elements = new LinkedHashSet<>();
        for (var elem : ctx.setElement()) {
            if (elem.expression().size() == 2) {
                // Range literal like {1..10}
                Expression start = visit(elem.expression(0));
                Expression end = visit(elem.expression(1));
                // Represent range as a keyword message
                elements.add(new KeywordMessage(start, List.of(new KeywordPart("to", end))));
            } else {
                elements.add(visit(elem.expression(0)));
            }
        }
        return new Literal.SetLiteral(elements);
    }

    @Override
    public Expression visitMapLit(IskaraParser.MapLitContext ctx) {
        return visit(ctx.mapLiteral());
    }

    @Override
    public Expression visitMapLiteral(IskaraParser.MapLiteralContext ctx) {
        Map<Expression, Expression> entries = new LinkedHashMap<>();
        for (var entry : ctx.mapEntry()) {
            Expression key = visit(entry.expression(0));
            Expression value = visit(entry.expression(1));
            entries.put(key, value);
        }
        return new Literal.MapLiteral(entries);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    public String extractIdentifier(IskaraParser.IdentifierContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();
        } else if (ctx.QUOTED_ID() != null) {
            String text = ctx.QUOTED_ID().getText();
            // Remove backticks
            return text.substring(1, text.length() - 1);
        }
        return "";
    }

    private Binary.Operator mapComparisonOp(IskaraParser.ComparisonOpContext ctx) {
        if (ctx.EQUALS() != null)
            return Binary.Operator.EQUALS;
        if (ctx.NOT_EQUALS() != null)
            return Binary.Operator.NOT_EQUALS;
        if (ctx.GREATER() != null)
            return Binary.Operator.GREATER_THAN;
        if (ctx.LESS() != null)
            return Binary.Operator.LESS_THAN;
        if (ctx.GREATER_EQ() != null)
            return Binary.Operator.GREATER_EQUALS;
        if (ctx.LESS_EQ() != null)
            return Binary.Operator.LESS_EQUALS;
        return Binary.Operator.EQUALS;
    }

    private Binary.Operator mapAdditiveOp(IskaraParser.AdditiveOpContext ctx) {
        if (ctx.PLUS() != null)
            return Binary.Operator.PLUS;
        if (ctx.MINUS() != null)
            return Binary.Operator.MINUS;
        return Binary.Operator.PLUS;
    }

    private Binary.Operator mapMultiplicativeOp(IskaraParser.MultiplicativeOpContext ctx) {
        if (ctx.STAR() != null)
            return Binary.Operator.MULTIPLY;
        if (ctx.SLASH() != null)
            return Binary.Operator.DIVIDE;
        return Binary.Operator.MULTIPLY;
    }

    private BigDecimal parseNumber(String text, Token token) {
        try {
            // Use locale-aware parsing
            DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance(locale);
            df.setParseBigDecimal(true);
            return (BigDecimal) df.parse(text);
        } catch (ParseException e) {
            // Fallback to standard parsing
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ex) {
                diagnostics.addError("Invalid number: " + text, token.getLine(), token.getCharPositionInLine() + 1);
                return BigDecimal.ZERO;
            }
        }
    }

    private String unescapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'b' -> {
                        sb.append('\b');
                        i++;
                    }
                    case 't' -> {
                        sb.append('\t');
                        i++;
                    }
                    case 'n' -> {
                        sb.append('\n');
                        i++;
                    }
                    case 'f' -> {
                        sb.append('\f');
                        i++;
                    }
                    case 'r' -> {
                        sb.append('\r');
                        i++;
                    }
                    case '"' -> {
                        sb.append('"');
                        i++;
                    }
                    case '\'' -> {
                        sb.append('\'');
                        i++;
                    }
                    case '\\' -> {
                        sb.append('\\');
                        i++;
                    }
                    case '$' -> {
                        sb.append('$');
                        i++;
                    }
                    case 'u' -> {
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
