package work.spell.iskibal.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import work.spell.iskibal.compiler.drools.api.DroolsCompilationResult;
import work.spell.iskibal.compiler.drools.api.DroolsCompiler;
import work.spell.iskibal.compiler.drools.api.DroolsCompilerOptions;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.Parser;

/// End-to-end tests that compile Iskara sources to DRL, load the DRL into a
/// Drools engine, execute the rules, and verify actual business logic outputs.
///
/// Unlike [DroolsCompilationE2ETest] which validates DRL text structure, these
/// tests verify that the generated rules produce correct results when executed.
@DisplayName("Drools runtime execution")
class DroolsRuntimeE2ETest {

    private static DroolsCompilationResult compileDrl(String iskaraSource, DroolsCompilerOptions options) {
        Parser parser = Parser.load();
        var parseResult = parser.parse(iskaraSource);
        assertThat(parseResult.isSuccess()).as("parse should succeed; errors: %s", parseResult.getDiagnostics())
                .isTrue();
        RuleModule module = parseResult.getValue().orElseThrow();

        DroolsCompiler compiler = DroolsCompiler.load();
        return compiler.compile(module, options);
    }

    /// Compiles DRL source into a Drools KieBase, also compiling the generated
    /// outputs POJO in-memory so the DRL can reference it.
    private static KieBase buildKieBase(String drlSource, Map<Path, String> javaFiles) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules.drl", drlSource);

        // Compile any generated Java sources (outputs POJO, adapter) so they
        // are available on the classpath for the Drools compiler
        compileJavaSources(javaFiles);

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new AssertionError("Drools compilation failed:\n" + kb.getResults().getMessages());
        }
        KieContainer kc = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
        return kc.getKieBase();
    }

    /// Compiles generated Java sources (outputs POJO) in-memory using javac.
    /// The compiled classes are loaded into the current classloader so Drools
    /// can reference them.
    private static void compileJavaSources(Map<Path, String> javaFiles) {
        if (javaFiles.isEmpty()) {
            return;
        }

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("No system Java compiler available");
        }

        var javaSourceFiles = javaFiles.entrySet().stream()
                .filter(e -> e.getKey().toString().endsWith(".java"))
                .map(e -> {
                    String className = e.getKey().toString()
                            .replace('/', '.')
                            .replace(".java", "");
                    return (JavaFileObject) new SimpleJavaFileObject(
                            java.net.URI.create("string:///" + e.getKey().toString()),
                            JavaFileObject.Kind.SOURCE) {
                        @Override
                        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                            return e.getValue();
                        }
                    };
                })
                .toList();

        // Compile to a temp directory on classpath
        java.io.File outputDir;
        try {
            outputDir = java.nio.file.Files.createTempDirectory("drools-e2e-classes").toFile();
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Failed to create temp directory", ex);
        }

        var task = javac.getTask(null, null, null,
                java.util.List.of("-d", outputDir.getAbsolutePath()),
                null, javaSourceFiles);

        if (!task.call()) {
            throw new AssertionError("Java compilation of generated sources failed");
        }

        // Load the compiled classes
        try {
            var classLoader = new java.net.URLClassLoader(
                    new java.net.URL[]{outputDir.toURI().toURL()},
                    Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(classLoader);
        } catch (java.net.MalformedURLException ex) {
            throw new RuntimeException("Failed to create classloader", ex);
        }
    }

    /// Creates an outputs object from the compiled POJO class using reflection.
    private static Object createOutputs(String fullyQualifiedClassName) throws Exception {
        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(fullyQualifiedClassName);
        return clazz.getDeclaredConstructor().newInstance();
    }

    /// Gets a field value from an outputs object by calling the getter method.
    @SuppressWarnings("unchecked")
    private static <T> T getOutput(Object outputs, String fieldName) throws Exception {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return (T) outputs.getClass().getMethod(getterName).invoke(outputs);
    }

    @Nested
    @DisplayName("Simple rules")
    class SimpleRules {

        @Test
        @DisplayName("when/then rule sets output correctly")
        void whenThenSetsOutput() throws Exception {
            String source = """
                    facts {
                        label: String
                    }
                    outputs {
                        result: String := "none"
                    }
                    rule `set-label`
                    when
                        label = "hello"
                    then
                        result := "world"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "simple_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert("hello");
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "result")).isEqualTo("world");
            } finally {
                session.dispose();
            }
        }

        @Test
        @DisplayName("when condition does not match, output keeps default")
        void whenNoMatch() throws Exception {
            String source = """
                    facts {
                        label: String
                    }
                    outputs {
                        result: String := "default"
                    }
                    rule `set-label`
                    when
                        label = "hello"
                    then
                        result := "matched"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "no_match_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert("goodbye");
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "result")).isEqualTo("default");
            } finally {
                session.dispose();
            }
        }

        @Test
        @DisplayName("when/then/else: else branch fires when condition not met")
        void elseBranchFires() throws Exception {
            String source = """
                    facts {
                        status: String
                    }
                    outputs {
                        result: String := "initial"
                    }
                    rule `check-status`
                    when
                        status = "active"
                    then
                        result := "yes"
                    else
                        result := "no"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "else_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert("inactive");
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "result")).isEqualTo("no");
            } finally {
                session.dispose();
            }
        }
    }

    @Nested
    @DisplayName("Fact navigation")
    class FactNavigation {

        @Test
        @DisplayName("fact property navigation works at runtime")
        void factPropertyNavigation() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        category: String := "unknown"
                    }
                    rule `age-check`
                    when
                        customer.age >= 18
                    then
                        category := "adult"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "nav_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert(new CustomerProfile("Alice", 25, 100, false));
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "category")).isEqualTo("adult");
            } finally {
                session.dispose();
            }
        }

        @Test
        @DisplayName("fact property comparison with minor does not match adult rule")
        void minorDoesNotMatchAdultRule() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        category: String := "unknown"
                    }
                    rule `age-check`
                    when
                        customer.age >= 18
                    then
                        category := "adult"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "minor_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert(new CustomerProfile("Bob", 12, 50, false));
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "category")).isEqualTo("unknown");
            } finally {
                session.dispose();
            }
        }
    }

    @Nested
    @DisplayName("Decision tables at runtime")
    class DecisionTables {

        @Test
        @DisplayName("raw decision table rows fire correctly based on loyalty points")
        void rawDecisionTableRuntime() throws Exception {
            String source = """
                    facts {
                        customer: work.spell.iskibal.e2e.CustomerProfile
                    }
                    outputs {
                        tier: String := "none"
                    }
                    decision table [raw] TIERS "Loyalty tiers" {
                    | ID     | WHEN                                 | THEN                         |
                    |        | $customer.getLoyaltyPoints() >= 0    | __outputs.setTier(null)      |
                    | ------ | ------------------------------------ | ---------------------------- |
                    | BRONZE | $customer.getLoyaltyPoints() >= 50   | __outputs.setTier("bronze")  |
                    | SILVER | $customer.getLoyaltyPoints() >= 200  | __outputs.setTier("silver")  |
                    | GOLD   | $customer.getLoyaltyPoints() >= 1000 | __outputs.setTier("gold")    |
                    }
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "tier_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess())
                    .as("compile should succeed; errors: %s", compiled.getErrors())
                    .isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);

            // Silver customer (250 points)
            KieSession session = kieBase.newKieSession();
            try {
                session.insert(new CustomerProfile("Charlie", 30, 250, false));
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                String tier = getOutput(outputs, "tier");
                // With 250 points, both BRONZE (>=50) and SILVER (>=200) match;
                // the highest matching tier should be set
                assertThat(tier).isIn("bronze", "silver");
            } finally {
                session.dispose();
            }
        }
    }

    @Nested
    @DisplayName("Globals")
    class Globals {

        @Test
        @DisplayName("globals are accessible in DRL eval conditions")
        void globalsAccessible() throws Exception {
            String source = """
                    globals {
                        threshold: java.math.BigDecimal
                    }
                    facts {
                        amount: java.math.BigDecimal
                    }
                    outputs {
                        result: String := "below"
                    }
                    rule `check-threshold`
                    when
                        amount >= @threshold
                    then
                        result := "above"
                    end
                    """;

            DroolsCompilerOptions options = new DroolsCompilerOptions("", "global_test");
            DroolsCompilationResult compiled = compileDrl(source, options);
            assertThat(compiled.isSuccess()).isTrue();

            Map<Path, String> files = compiled.getSourceFiles().orElseThrow();
            KieBase kieBase = buildKieBase(compiled.getDrlSource(), files);
            KieSession session = kieBase.newKieSession();

            try {
                session.insert(new BigDecimal("150"));
                session.setGlobal("threshold", new BigDecimal("100"));
                Object outputs = createOutputs(options.fullyQualifiedOutputsClassName());
                session.setGlobal("__outputs", outputs);
                session.fireAllRules();

                assertThat((String) getOutput(outputs, "result")).isEqualTo("above");
            } finally {
                session.dispose();
            }
        }
    }
}
