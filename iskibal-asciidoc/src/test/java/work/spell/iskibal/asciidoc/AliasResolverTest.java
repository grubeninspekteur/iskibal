package work.spell.iskibal.asciidoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.model.Expression;
import work.spell.iskibal.parser.api.Parser;

class AliasResolverTest {

    private static Asciidoctor asciidoctor;
    private static AliasResolver resolver;

    @BeforeAll
    static void setup() {
        asciidoctor = Asciidoctor.Factory.create();
        ExpressionParser expressionParser = new ExpressionParser(Parser.load(), Locale.getDefault());
        resolver = new AliasResolver(expressionParser);
    }

    @AfterAll
    static void teardown() {
        asciidoctor.close();
    }

    @Test
    void resolvesSimpleAlias() {
        String adoc = """
                [.aliases,for="MY_TABLE"]
                checkAge::
                +
                [source,iskara]
                ----
                person.age >= 18
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Map<String, Expression.Block> aliases = resolver.resolveAliases(doc, "MY_TABLE");

        assertThat(aliases).containsKey("checkAge");
        assertThat(aliases.get("checkAge")).isNotNull();
    }

    @Test
    void resolvesMultipleAliases() {
        String adoc = """
                [.aliases,for="DISCOUNTS"]
                hasBirthday::
                +
                [source,iskara]
                ----
                Customer.dateOfBirth = Today
                ----
                offerCar::
                +
                [source,iskara]
                ----
                messages add: "Special car offer!"
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Map<String, Expression.Block> aliases = resolver.resolveAliases(doc, "DISCOUNTS");

        assertThat(aliases).hasSize(2);
        assertThat(aliases).containsKeys("hasBirthday", "offerCar");
    }

    @Test
    void onlyResolvesAliasesForMatchingTableId() {
        String adoc = """
                [.aliases,for="TABLE_A"]
                aliasA::
                +
                [source,iskara]
                ----
                conditionA
                ----

                [.aliases,for="TABLE_B"]
                aliasB::
                +
                [source,iskara]
                ----
                conditionB
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());

        Map<String, Expression.Block> aliasesA = resolver.resolveAliases(doc, "TABLE_A");
        assertThat(aliasesA).containsKey("aliasA");
        assertThat(aliasesA).doesNotContainKey("aliasB");

        Map<String, Expression.Block> aliasesB = resolver.resolveAliases(doc, "TABLE_B");
        assertThat(aliasesB).containsKey("aliasB");
        assertThat(aliasesB).doesNotContainKey("aliasA");
    }

    @Test
    void returnsEmptyMapWhenNoAliasesFound() {
        String adoc = """
                = Document without aliases

                Some content.
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Map<String, Expression.Block> aliases = resolver.resolveAliases(doc, "NONEXISTENT");

        assertThat(aliases).isEmpty();
    }

    @Test
    void ignoresAliasBlocksWithoutForAttribute() {
        String adoc = """
                [.aliases]
                orphanAlias::
                +
                [source,iskara]
                ----
                orphanExpression
                ----
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Map<String, Expression.Block> aliases = resolver.resolveAliases(doc, "ANY_TABLE");

        assertThat(aliases).isEmpty();
    }

    @Test
    void resolvesAliasesFromNestedSections() {
        String adoc = """
                = Main Document

                == Section One

                [.aliases,for="NESTED_TABLE"]
                nestedAlias::
                +
                [source,iskara]
                ----
                nested.condition
                ----

                == Section Two

                Some other content.
                """;

        Document doc = asciidoctor.load(adoc, Options.builder().build());
        Map<String, Expression.Block> aliases = resolver.resolveAliases(doc, "NESTED_TABLE");

        assertThat(aliases).containsKey("nestedAlias");
    }
}
