package work.spell.iskibal.compiler.java.internal.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.types.JavaTypeInferenceContext;
import work.spell.iskibal.compiler.java.types.JavaTypeInferenceVisitor;
import work.spell.iskibal.compiler.java.types.JavaTypeResolver;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Expression;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;

/**
 * Generates a complete Java class for a RuleModule.
 */
public final class ModuleGenerator {

	private final JavaCompilerOptions options;

	public ModuleGenerator(JavaCompilerOptions options) {
		this.options = options;
	}

	/**
	 * Generates Java source code for the rule module.
	 */
	public String generate(RuleModule module) {
		StringBuilder sb = new StringBuilder();

		// Package declaration
		if (options.packageName() != null && !options.packageName().isEmpty()) {
			sb.append("package ").append(options.packageName()).append(";\n\n");
		}

		// Imports
		sb.append("import java.math.BigDecimal;\n");
		sb.append("import java.util.Objects;\n");
		sb.append("import static work.spell.iskibal.compiler.java.runtime.NumericHelpers.*;\n");
		sb.append("import static work.spell.iskibal.compiler.java.runtime.CollectionHelpers.*;\n\n");

		// Class declaration
		sb.append("public class ").append(options.className()).append(" {\n\n");

		// Generate fields
		generateFields(sb, module);

		// Generate constructor
		generateConstructor(sb, module);

		// Create generators
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

		// Create type inference visitor if type inference is enabled
		JavaTypeInferenceVisitor typeVisitor = null;
		if (options.typeInferenceEnabled()) {
			JavaTypeResolver resolver = new JavaTypeResolver(options.typeClassLoader());
			JavaTypeInferenceContext context = JavaTypeInferenceContext.fromModule(module, resolver);
			typeVisitor = new JavaTypeInferenceVisitor(context);
		}

		ExpressionGenerator exprGen = new ExpressionGenerator(options, globalNames, outputNames, outputTypes,
				typeVisitor);
		StatementGenerator stmtGen = new StatementGenerator(exprGen);
		RuleGenerator ruleGen = new RuleGenerator(stmtGen, exprGen);

		// Generate rule methods
		for (Rule rule : module.rules()) {
			sb.append(ruleGen.generate(rule));
			sb.append("\n");
		}

		// Generate evaluate method
		generateEvaluateMethod(sb, module, ruleGen);

		// Generate output getters
		generateOutputGetters(sb, module);

		sb.append("}\n");
		return sb.toString();
	}

	private void generateFields(StringBuilder sb, RuleModule module) {
		// Facts (final fields)
		for (Fact fact : module.facts()) {
			sb.append("\tprivate final ").append(fact.type()).append(" ").append(fact.name()).append(";\n");
		}

		// Globals (final fields)
		for (Global global : module.globals()) {
			sb.append("\tprivate final ").append(global.type()).append(" ").append(global.name()).append(";\n");
		}

		// Outputs (mutable fields with initial values)
		for (Output output : module.outputs()) {
			sb.append("\tprivate ").append(output.type()).append(" ").append(output.name()).append(";\n");
		}

		// Data tables (final fields initialized inline from literal values)
		if (!module.dataTables().isEmpty()) {
			ExpressionGenerator litGen = new ExpressionGenerator(options, Set.of(), Set.of());
			for (DataTable table : module.dataTables()) {
				generateDataTableField(sb, table, litGen);
			}
		}

		if (!module.facts().isEmpty() || !module.globals().isEmpty() || !module.outputs().isEmpty()
				|| !module.dataTables().isEmpty()) {
			sb.append("\n");
		}
	}

	private void generateDataTableField(StringBuilder sb, DataTable table, ExpressionGenerator exprGen) {
		if (table.rows().isEmpty()) {
			return;
		}

		List<String> columns = new ArrayList<>(table.rows().getFirst().values().keySet());

		if (columns.size() == 2) {
			// Two-column table: Map<Object, Object> keyed by first column
			String keyCol = columns.get(0);
			String valueCol = columns.get(1);
			sb.append("\tprivate final java.util.Map<Object, Object> ").append(table.id())
					.append(" = java.util.Map.ofEntries(\n");
			for (int i = 0; i < table.rows().size(); i++) {
				DataTable.Row row = table.rows().get(i);
				sb.append("\t\t\tjava.util.Map.entry(").append(exprGen.generate(row.values().get(keyCol))).append(", ")
						.append(exprGen.generate(row.values().get(valueCol))).append(")");
				if (i < table.rows().size() - 1) {
					sb.append(",");
				}
				sb.append("\n");
			}
			sb.append("\t\t);\n");
		} else {
			// Multi-column table: List<Map<String, Object>>
			sb.append("\tprivate final java.util.List<java.util.Map<String, Object>> ").append(table.id())
					.append(" = java.util.List.of(\n");
			for (int i = 0; i < table.rows().size(); i++) {
				DataTable.Row row = table.rows().get(i);
				sb.append("\t\t\tjava.util.Map.ofEntries(\n");
				List<Map.Entry<String, Expression>> entries = new ArrayList<>(row.values().entrySet());
				for (int j = 0; j < entries.size(); j++) {
					Map.Entry<String, Expression> entry = entries.get(j);
					sb.append("\t\t\t\tjava.util.Map.entry(\"").append(entry.getKey()).append("\", ")
							.append(exprGen.generate(entry.getValue())).append(")");
					if (j < entries.size() - 1) {
						sb.append(",");
					}
					sb.append("\n");
				}
				sb.append("\t\t\t)");
				if (i < table.rows().size() - 1) {
					sb.append(",");
				}
				sb.append("\n");
			}
			sb.append("\t\t);\n");
		}
	}

	private void generateConstructor(StringBuilder sb, RuleModule module) {
		Set<String> globalNames = new HashSet<>();
		for (Global g : module.globals()) {
			globalNames.add(g.name());
		}
		Set<String> outputNames = new HashSet<>();
		for (Output o : module.outputs()) {
			outputNames.add(o.name());
		}

		ExpressionGenerator exprGen = new ExpressionGenerator(options, globalNames, outputNames);

		// Constructor parameters: facts + globals
		List<String> params = new ArrayList<>();
		for (Fact fact : module.facts()) {
			params.add(fact.type() + " " + fact.name());
		}
		for (Global global : module.globals()) {
			params.add(global.type() + " " + global.name());
		}

		sb.append("\tpublic ").append(options.className()).append("(");
		sb.append(String.join(", ", params));
		sb.append(") {\n");

		// Assign facts
		for (Fact fact : module.facts()) {
			sb.append("\t\tthis.").append(fact.name()).append(" = ").append(fact.name()).append(";\n");
		}

		// Assign globals
		for (Global global : module.globals()) {
			sb.append("\t\tthis.").append(global.name()).append(" = ").append(global.name()).append(";\n");
		}

		// Initialize outputs
		for (Output output : module.outputs()) {
			if (output.initialValue() != null) {
				sb.append("\t\tthis.").append(output.name()).append(" = ");
				sb.append(exprGen.generate(output.initialValue())).append(";\n");
			}
		}

		sb.append("\t}\n\n");
	}

	private void generateEvaluateMethod(StringBuilder sb, RuleModule module, RuleGenerator ruleGen) {
		sb.append("\t/**\n");
		sb.append("\t * Evaluates all rules in order.\n");
		sb.append("\t */\n");
		sb.append("\tpublic void evaluate() {\n");

		for (Rule rule : module.rules()) {
			for (String methodName : ruleGen.getMethodNames(rule)) {
				sb.append("\t\t").append(methodName).append("();\n");
			}
		}

		sb.append("\t}\n\n");
	}

	private void generateOutputGetters(StringBuilder sb, RuleModule module) {
		for (Output output : module.outputs()) {
			String capitalizedName = capitalize(output.name());
			sb.append("\tpublic ").append(output.type()).append(" get").append(capitalizedName).append("() {\n");
			sb.append("\t\treturn this.").append(output.name()).append(";\n");
			sb.append("\t}\n\n");
		}
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
