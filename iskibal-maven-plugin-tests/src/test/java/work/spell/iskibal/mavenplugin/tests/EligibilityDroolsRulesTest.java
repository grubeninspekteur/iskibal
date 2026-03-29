package work.spell.iskibal.mavenplugin.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import work.spell.iskibal.mavenplugin.tests.drools.EligibilityRulesOutputs;
import work.spell.iskibal.mavenplugin.tests.drools.EligibilityRulesRuleAdapter;

/// Tests for the Drools rules generated from `eligibility_rules.iskara` by
/// the iskibal-maven-plugin with `language=drools`. The DRL file, outputs
/// POJO, and rule adapter are all generated during the `generate-sources`
/// phase.
class EligibilityDroolsRulesTest {

    private static KieBase kieBase;

    @BeforeAll
    static void buildKieBase() throws IOException {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        // Read the generated DRL from the build output directory
        Path drlPath = Path.of("target/generated-sources/drools/work/spell/iskibal/mavenplugin/tests/drools/eligibility_rules.drl");
        assertThat(drlPath).as("Generated DRL file should exist").exists();
        String drl = Files.readString(drlPath);
        kfs.write("src/main/resources/eligibility_rules.drl", drl);

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        assertThat(kb.getResults().hasMessages(Message.Level.ERROR))
                .as("DRL should compile without errors: %s", kb.getResults().getMessages())
                .isFalse();

        KieContainer kc = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
        kieBase = kc.getKieBase();
    }

    @Test
    @DisplayName("Customer with enough points is eligible (using adapter)")
    void eligibleCustomerViaAdapter() {
        var adapter = new EligibilityRulesRuleAdapter(kieBase);
        adapter.setCustomer(new Customer("Alice", 500));
        EligibilityRulesOutputs outputs = adapter.evaluate();

        assertThat(outputs.getEligible()).isEqualTo("yes");
        assertThat(outputs.getReason()).isEqualTo("sufficient points");
    }

    @Test
    @DisplayName("Customer with insufficient points is not eligible (using adapter)")
    void ineligibleCustomerViaAdapter() {
        var adapter = new EligibilityRulesRuleAdapter(kieBase);
        adapter.setCustomer(new Customer("Bob", 50));
        EligibilityRulesOutputs outputs = adapter.evaluate();

        assertThat(outputs.getEligible()).isEqualTo("no");
        assertThat(outputs.getReason()).isEqualTo("insufficient points");
    }

    @Test
    @DisplayName("Manual session setup produces same results as adapter")
    void manualSessionSetup() {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(new Customer("Charlie", 200));
            EligibilityRulesOutputs outputs = new EligibilityRulesOutputs();
            session.setGlobal("__outputs", outputs);
            session.fireAllRules();

            assertThat(outputs.getEligible()).isEqualTo("yes");
            assertThat(outputs.getReason()).isEqualTo("sufficient points");
        } finally {
            session.dispose();
        }
    }
}
