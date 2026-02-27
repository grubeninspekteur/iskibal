package work.spell.iskibal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import module java.base;

import org.junit.jupiter.api.Test;

class RuleModuleMergerTest {

    private final RuleModuleMerger merger = new RuleModuleMerger();

    @Test
    void mergesEmptyList() {
        RuleModule result = merger.merge(List.of());

        assertThat(result.imports()).isEmpty();
        assertThat(result.facts()).isEmpty();
        assertThat(result.rules()).isEmpty();
    }

    @Test
    void returnsSingleModuleUnchanged() {
        RuleModule module = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "org.Foo")),
                List.of(new Fact.Definition("fact1", "Type", "desc")),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        RuleModule result = merger.merge(List.of(module));

        assertThat(result).isSameAs(module);
    }

    @Test
    void mergesImports() {
        RuleModule m1 = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "org.Foo")),
                List.of(), List.of(), List.of(), List.of(), List.of());
        RuleModule m2 = new RuleModule.Default(
                List.of(new Import.Definition("Bar", "org.Bar")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        RuleModule result = merger.merge(List.of(m1, m2));

        assertThat(result.imports()).hasSize(2);
        assertThat(result.imports()).extracting(Import::alias).containsExactly("Foo", "Bar");
    }

    @Test
    void deduplicatesIdenticalImports() {
        RuleModule m1 = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "org.Foo")),
                List.of(), List.of(), List.of(), List.of(), List.of());
        RuleModule m2 = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "org.Foo")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        RuleModule result = merger.merge(List.of(m1, m2));

        assertThat(result.imports()).hasSize(1);
    }

    @Test
    void detectsConflictingImports() {
        RuleModule m1 = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "org.Foo")),
                List.of(), List.of(), List.of(), List.of(), List.of());
        RuleModule m2 = new RuleModule.Default(
                List.of(new Import.Definition("Foo", "com.Foo")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> merger.merge(List.of(m1, m2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting import");
    }

    @Test
    void mergesFacts() {
        RuleModule m1 = new RuleModule.Default(
                List.of(),
                List.of(new Fact.Definition("fact1", "Type1", "desc1")),
                List.of(), List.of(), List.of(), List.of());
        RuleModule m2 = new RuleModule.Default(
                List.of(),
                List.of(new Fact.Definition("fact2", "Type2", "desc2")),
                List.of(), List.of(), List.of(), List.of());

        RuleModule result = merger.merge(List.of(m1, m2));

        assertThat(result.facts()).hasSize(2);
    }

    @Test
    void detectsConflictingFacts() {
        RuleModule m1 = new RuleModule.Default(
                List.of(),
                List.of(new Fact.Definition("fact1", "Type1", "desc1")),
                List.of(), List.of(), List.of(), List.of());
        RuleModule m2 = new RuleModule.Default(
                List.of(),
                List.of(new Fact.Definition("fact1", "Type2", "different desc")),
                List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> merger.merge(List.of(m1, m2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting fact");
    }

    @Test
    void mergesRules() {
        Rule rule1 = new Rule.SimpleRule("RULE-001", "First rule", List.of(), List.of(), List.of());
        Rule rule2 = new Rule.SimpleRule("RULE-002", "Second rule", List.of(), List.of(), List.of());

        RuleModule m1 = new RuleModule.Default(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rule1));
        RuleModule m2 = new RuleModule.Default(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rule2));

        RuleModule result = merger.merge(List.of(m1, m2));

        assertThat(result.rules()).hasSize(2);
        assertThat(result.rules()).extracting(Rule::id).containsExactly("RULE-001", "RULE-002");
    }

    @Test
    void detectsConflictingRules() {
        Rule rule1 = new Rule.SimpleRule("RULE-001", "First version", List.of(), List.of(), List.of());
        Rule rule2 = new Rule.SimpleRule("RULE-001", "Second version", List.of(), List.of(), List.of());

        RuleModule m1 = new RuleModule.Default(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rule1));
        RuleModule m2 = new RuleModule.Default(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rule2));

        assertThatThrownBy(() -> merger.merge(List.of(m1, m2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting rule");
    }
}
