package work.spell.iskibal.model;

import java.util.*;

/**
 * Merges multiple {@link RuleModule} instances into a single module.
 * <p>
 * This is useful when parsing AsciiDoc documents with include directives,
 * where each included file may produce its own partial RuleModule.
 */
public class RuleModuleMerger {

    /**
     * Merges multiple RuleModule instances into one.
     * <p>
     * Imports are deduplicated by alias. Facts, globals, and outputs are
     * deduplicated by name. Data tables and rules are deduplicated by ID.
     * Conflicting definitions (same name/ID with different values) cause an
     * exception.
     *
     * @param modules
     *            the modules to merge
     * @return a merged RuleModule containing all unique elements
     * @throws IllegalArgumentException
     *             if conflicting definitions are found
     */
    public RuleModule merge(List<RuleModule> modules) {
        if (modules.isEmpty()) {
            return new RuleModule.Default(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (modules.size() == 1) {
            return modules.getFirst();
        }

        Map<String, Import> imports = new LinkedHashMap<>();
        Map<String, Fact> facts = new LinkedHashMap<>();
        Map<String, Global> globals = new LinkedHashMap<>();
        Map<String, Output> outputs = new LinkedHashMap<>();
        Map<String, DataTable> dataTables = new LinkedHashMap<>();
        Map<String, Rule> rules = new LinkedHashMap<>();

        for (RuleModule module : modules) {
            mergeImports(imports, module.imports());
            mergeFacts(facts, module.facts());
            mergeGlobals(globals, module.globals());
            mergeOutputs(outputs, module.outputs());
            mergeDataTables(dataTables, module.dataTables());
            mergeRules(rules, module.rules());
        }

        return new RuleModule.Default(new ArrayList<>(imports.values()), new ArrayList<>(facts.values()),
                new ArrayList<>(globals.values()), new ArrayList<>(outputs.values()),
                new ArrayList<>(dataTables.values()), new ArrayList<>(rules.values()));
    }

    /**
     * Convenience method to merge two modules.
     */
    public RuleModule merge(RuleModule first, RuleModule second) {
        return merge(List.of(first, second));
    }

    private void mergeImports(Map<String, Import> target, List<Import> source) {
        for (Import imp : source) {
            Import existing = target.get(imp.alias());
            if (existing != null && !existing.type().equals(imp.type())) {
                throw new IllegalArgumentException(
                        "Conflicting import for alias '%s': '%s' vs '%s'".formatted(imp.alias(), existing.type(),
                                imp.type()));
            }
            target.putIfAbsent(imp.alias(), imp);
        }
    }

    private void mergeFacts(Map<String, Fact> target, List<Fact> source) {
        for (Fact fact : source) {
            Fact existing = target.get(fact.name());
            if (existing != null && !factsEqual(existing, fact)) {
                throw new IllegalArgumentException(
                        "Conflicting fact definition for '%s'".formatted(fact.name()));
            }
            target.putIfAbsent(fact.name(), fact);
        }
    }

    private void mergeGlobals(Map<String, Global> target, List<Global> source) {
        for (Global global : source) {
            Global existing = target.get(global.name());
            if (existing != null && !globalsEqual(existing, global)) {
                throw new IllegalArgumentException(
                        "Conflicting global definition for '%s'".formatted(global.name()));
            }
            target.putIfAbsent(global.name(), global);
        }
    }

    private void mergeOutputs(Map<String, Output> target, List<Output> source) {
        for (Output output : source) {
            Output existing = target.get(output.name());
            if (existing != null && !outputsEqual(existing, output)) {
                throw new IllegalArgumentException(
                        "Conflicting output definition for '%s'".formatted(output.name()));
            }
            target.putIfAbsent(output.name(), output);
        }
    }

    private void mergeDataTables(Map<String, DataTable> target, List<DataTable> source) {
        for (DataTable table : source) {
            DataTable existing = target.get(table.id());
            if (existing != null && !dataTablesEqual(existing, table)) {
                throw new IllegalArgumentException(
                        "Conflicting data table definition for '%s'".formatted(table.id()));
            }
            target.putIfAbsent(table.id(), table);
        }
    }

    private void mergeRules(Map<String, Rule> target, List<Rule> source) {
        for (Rule rule : source) {
            Rule existing = target.get(rule.id());
            if (existing != null && !rulesEqual(existing, rule)) {
                throw new IllegalArgumentException(
                        "Conflicting rule definition for '%s'".formatted(rule.id()));
            }
            target.putIfAbsent(rule.id(), rule);
        }
    }

    private boolean factsEqual(Fact a, Fact b) {
        return a.name().equals(b.name()) && a.type().equals(b.type())
                && Objects.equals(a.description(), b.description());
    }

    private boolean globalsEqual(Global a, Global b) {
        return a.name().equals(b.name()) && a.type().equals(b.type())
                && Objects.equals(a.description(), b.description());
    }

    private boolean outputsEqual(Output a, Output b) {
        return a.name().equals(b.name()) && a.type().equals(b.type())
                && Objects.equals(a.initialValue(), b.initialValue())
                && Objects.equals(a.description(), b.description());
    }

    private boolean dataTablesEqual(DataTable a, DataTable b) {
        return a.id().equals(b.id()) && a.rows().equals(b.rows());
    }

    private boolean rulesEqual(Rule a, Rule b) {
        return a.equals(b);
    }
}
