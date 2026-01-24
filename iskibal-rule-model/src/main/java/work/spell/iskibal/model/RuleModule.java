package work.spell.iskibal.model;

import java.util.List;

/**
 * Root element of a rule source. A module groups together all declarations and
 * rules that belong to one rule file or logical unit.
 */
public sealed interface RuleModule permits RuleModule.Default {

	/**
	 * All import declarations used inside the module.
	 */
	List<Import> imports();

	/**
	 * Declared facts that are available to all rules.
	 */
	List<Fact> facts();

	/**
	 * Global declarations providing additional state.
	 */
	List<Global> globals();

	/**
	 * Outputs that are produced by evaluating the rules.
	 */
	List<Output> outputs();

	/**
	 * Data tables that can be referenced by rules.
	 */
	List<DataTable> dataTables();

	/**
	 * The rules contained in the module.
	 */
	List<Rule> rules();

	/**
	 * Default implementation of a {@link RuleModule}.
	 */
	record Default(List<Import> imports, List<Fact> facts, List<Global> globals, List<Output> outputs,
			List<DataTable> dataTables, List<Rule> rules) implements RuleModule {
	}
}
