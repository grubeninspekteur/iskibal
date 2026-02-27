package work.spell.iskibal.compiler.common.internal.validators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.model.DataTable;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.Rule;
import work.spell.iskibal.model.RuleModule;

/// Validates uniqueness of declarations within a rule module.
public final class DeclarationValidator {

    private final List<SemanticDiagnostic> diagnostics = new ArrayList<>();

    /// Validates declaration uniqueness in the module.
    ///
    /// @param module
    ///            the rule module to validate
    /// @return list of diagnostics (empty if no errors)
    public List<SemanticDiagnostic> validate(RuleModule module) {
        diagnostics.clear();

        Set<String> names = new HashSet<>();

        // Check facts for duplicates
        for (Fact fact : module.facts()) {
            if (!names.add(fact.name())) {
                diagnostics.add(SemanticDiagnostic.error("Duplicate fact declaration", fact.name()));
            }
        }

        // Check globals for duplicates (can share name with fact)
        Set<String> globalNames = new HashSet<>();
        for (Global global : module.globals()) {
            if (!globalNames.add(global.name())) {
                diagnostics.add(SemanticDiagnostic.error("Duplicate global declaration", global.name()));
            }
        }

        // Check outputs for duplicates
        for (Output output : module.outputs()) {
            if (!names.add(output.name())) {
                diagnostics.add(SemanticDiagnostic.error("Duplicate output declaration (conflicts with fact or output)",
                        output.name()));
            }
        }

        // Check data table IDs for duplicates
        Set<String> tableIds = new HashSet<>();
        for (DataTable table : module.dataTables()) {
            if (!tableIds.add(table.id())) {
                diagnostics.add(SemanticDiagnostic.error("Duplicate data table ID", table.id()));
            }
        }

        // Check rule IDs for duplicates
        Set<String> ruleIds = new HashSet<>();
        for (Rule rule : module.rules()) {
            if (!ruleIds.add(rule.id())) {
                diagnostics.add(SemanticDiagnostic.error("Duplicate rule ID", rule.id()));
            }

            // Check decision table row IDs for duplicates within the rule
            if (rule instanceof Rule.DecisionTableRule dtr) {
                Set<String> rowIds = new HashSet<>();
                for (Rule.DecisionTableRule.Row row : dtr.rows()) {
                    if (!rowIds.add(row.id())) {
                        diagnostics.add(SemanticDiagnostic
                                .error("Duplicate row ID in decision table rule '" + dtr.id() + "'", row.id()));
                    }
                }
            }
        }

        return List.copyOf(diagnostics);
    }
}
