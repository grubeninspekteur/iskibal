package work.spell.iskibal.compiler.common.internal.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.compiler.common.internal.Scope;
import work.spell.iskibal.compiler.common.internal.Symbol;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Expression.Assignment;
import work.spell.iskibal.model.Expression.Binary;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.model.Expression.Identifier;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Expression.MessageSend;
import work.spell.iskibal.model.Expression.MessageSend.DefaultMessage;
import work.spell.iskibal.model.Expression.MessageSend.KeywordMessage;
import work.spell.iskibal.model.Expression.MessageSend.UnaryMessage;
import work.spell.iskibal.model.Expression.Navigation;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.model.Statement;

/**
 * Validates identifier references within a rule module.
 */
public final class ReferenceValidator {

	private final List<SemanticDiagnostic> diagnostics = new ArrayList<>();

	/**
	 * Validates identifier references in the module.
	 *
	 * @param module
	 *            the rule module to validate
	 * @return list of diagnostics (empty if no errors)
	 */
	public List<SemanticDiagnostic> validate(RuleModule module) {
		diagnostics.clear();

		// Build module-level scope
		Scope moduleScope = buildModuleScope(module);

		// Validate each rule
		for (Rule rule : module.rules()) {
			validateRule(rule, moduleScope, module);
		}

		return List.copyOf(diagnostics);
	}

	private Scope buildModuleScope(RuleModule module) {
		Scope scope = new Scope();

		for (Fact fact : module.facts()) {
			scope.define(Symbol.fact(fact.name(), fact.type()));
		}

		for (Global global : module.globals()) {
			scope.define(Symbol.global(global.name(), global.type()));
		}

		for (Output output : module.outputs()) {
			scope.define(Symbol.output(output.name(), output.type()));
		}

		return scope;
	}

	private void validateRule(Rule rule, Scope moduleScope, RuleModule module) {
		switch (rule) {
			case Rule.SimpleRule sr -> validateSimpleRule(sr, moduleScope);
			case Rule.TemplateRule tr -> validateTemplateRule(tr, moduleScope);
			case Rule.DecisionTableRule dtr -> validateDecisionTableRule(dtr, moduleScope, module);
		}
	}

	private void validateSimpleRule(Rule.SimpleRule rule, Scope moduleScope) {
		Scope ruleScope = moduleScope.createChild();

		// Validate when section
		for (Statement stmt : rule.when()) {
			validateStatement(stmt, ruleScope);
		}

		// Validate then section
		for (Statement stmt : rule.then()) {
			validateStatement(stmt, ruleScope);
		}

		// Validate else section
		for (Statement stmt : rule.elseStatements()) {
			validateStatement(stmt, ruleScope);
		}
	}

	private void validateTemplateRule(Rule.TemplateRule rule, Scope moduleScope) {
		Scope ruleScope = moduleScope.createChild();

		// Add table columns as symbols
		DataTable table = rule.dataTable();
		if (table != null && !table.rows().isEmpty()) {
			Map<String, Expression> firstRow = table.rows().getFirst().values();
			for (String columnName : firstRow.keySet()) {
				ruleScope.define(Symbol.column(columnName));
			}
		}

		// Validate when section
		for (Statement stmt : rule.when()) {
			validateStatement(stmt, ruleScope);
		}

		// Validate then section
		for (Statement stmt : rule.then()) {
			validateStatement(stmt, ruleScope);
		}
	}

	private void validateDecisionTableRule(Rule.DecisionTableRule rule, Scope moduleScope, RuleModule module) {
		// Decision table rules have aliases that can be referenced
		for (Rule.DecisionTableRule.Row row : rule.rows()) {
			Scope rowScope = moduleScope.createChild();

			// Validate when section
			for (Statement stmt : row.when()) {
				validateStatement(stmt, rowScope);
			}

			// Validate then section
			for (Statement stmt : row.then()) {
				validateStatement(stmt, rowScope);
			}
		}

		// Validate aliases
		for (Map.Entry<String, Block> entry : rule.aliases().entrySet()) {
			Scope aliasScope = moduleScope.createChild();
			validateExpression(entry.getValue(), aliasScope);
		}
	}

	private void validateStatement(Statement stmt, Scope scope) {
		switch (stmt) {
			case Statement.ExpressionStatement es -> validateExpression(es.expression(), scope);
			case Statement.LetStatement ls -> {
				// Check if this is a block parameter placeholder (Identifier("param"))
				// Block parameters are represented as LetStatement(name, Identifier("param"))
				boolean isBlockParam = ls.expression() instanceof Identifier id && "param".equals(id.name());
				if (!isBlockParam) {
					// First validate the expression, then add the local to scope
					validateExpression(ls.expression(), scope);
				}
				scope.define(Symbol.local(ls.name()));
			}
		}
	}

	private void validateExpression(Expression expr, Scope scope) {
		switch (expr) {
			case Identifier id -> validateIdentifier(id, scope);
			case Literal lit -> validateLiteral(lit, scope);
			case MessageSend ms -> {
				validateExpression(ms.receiver(), scope);
				switch (ms) {
					case UnaryMessage _ -> {
						// No arguments to validate
					}
					case KeywordMessage km -> {
						for (KeywordMessage.KeywordPart part : km.parts()) {
							validateExpression(part.argument(), scope);
						}
					}
					case DefaultMessage _ -> {
						// No arguments to validate
					}
				}
			}
			case Binary bin -> {
				validateExpression(bin.left(), scope);
				validateExpression(bin.right(), scope);
			}
			case Assignment assign -> {
				validateExpression(assign.target(), scope);
				validateExpression(assign.value(), scope);
			}
			case Navigation nav -> validateExpression(nav.receiver(), scope);
			case Block block -> {
				Scope blockScope = scope.createChild();
				for (Statement stmt : block.statements()) {
					validateStatement(stmt, blockScope);
				}
			}
		}
	}

	private void validateIdentifier(Identifier id, Scope scope) {
		String name = id.name();

		// Check for @ prefix (global access)
		if (name.startsWith("@")) {
			String globalName = name.substring(1);
			var symbol = scope.lookup(globalName);
			if (symbol.isEmpty()) {
				diagnostics.add(SemanticDiagnostic.error("Undefined global", name));
			} else if (symbol.get().kind() != Symbol.Kind.GLOBAL) {
				diagnostics
						.add(SemanticDiagnostic.error("'" + globalName + "' is not a global (remove @ prefix)", name));
			}
		} else {
			// Regular identifier - should not be a global
			var symbol = scope.lookup(name);
			if (symbol.isEmpty()) {
				diagnostics.add(SemanticDiagnostic.error("Undefined identifier", name));
			} else if (symbol.get().kind() == Symbol.Kind.GLOBAL) {
				diagnostics.add(SemanticDiagnostic.error("Global '" + name + "' must be accessed with @ prefix", name));
			}
		}
	}

	private void validateLiteral(Literal lit, Scope scope) {
		switch (lit) {
			case Literal.ListLiteral ll -> {
				for (Expression elem : ll.elements()) {
					validateExpression(elem, scope);
				}
			}
			case Literal.SetLiteral sl -> {
				for (Expression elem : sl.elements()) {
					validateExpression(elem, scope);
				}
			}
			case Literal.MapLiteral ml -> {
				for (Map.Entry<Expression, Expression> entry : ml.entries().entrySet()) {
					validateExpression(entry.getKey(), scope);
					validateExpression(entry.getValue(), scope);
				}
			}
			default -> {
				// Primitive literals need no validation
			}
		}
	}
}
