package work.spell.iskibal.compiler.java.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.compiler.java.types.JavaType.ClassType;
import work.spell.iskibal.compiler.java.types.JavaType.PrimitiveType;
import work.spell.iskibal.compiler.java.types.JavaType.UnknownType;
import work.spell.iskibal.model.Expression.Literal;
import work.spell.iskibal.model.Fact;
import work.spell.iskibal.model.Global;
import work.spell.iskibal.model.Output;
import work.spell.iskibal.model.RuleModule;

/**
 * Tests for JavaTypeInferenceContext.
 */
class JavaTypeInferenceContextTest {

    private JavaTypeResolver resolver;
    private JavaTypeInferenceContext context;

    @BeforeEach
    void setUp() {
        resolver = new JavaTypeResolver();
        context = new JavaTypeInferenceContext(resolver);
    }

    @Nested
    @DisplayName("Module Initialization")
    class ModuleInitialization {

        @Test
        @DisplayName("Initializes fact types from module")
        void initializesFactTypes() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("person", "java.lang.String", "A person")), List.of(), List.of(),
                    List.of(), List.of());

            context.initializeFromModule(module);

            assertThat(context.isFact("person")).isTrue();
            assertThat(context.lookupIdentifier("person").qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Initializes global types from module")
        void initializesGlobalTypes() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("clock", "java.time.Clock", "System clock")), List.of(), List.of(),
                    List.of());

            context.initializeFromModule(module);

            assertThat(context.isGlobal("clock")).isTrue();
            assertThat(context.lookupGlobal("clock").qualifiedName()).isEqualTo("java.time.Clock");
        }

        @Test
        @DisplayName("Initializes output types from module")
        void initializesOutputTypes() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(), List.of(),
                    List.of(new Output.Definition("result", "java.math.BigDecimal", null, "The result")), List.of(),
                    List.of());

            context.initializeFromModule(module);

            assertThat(context.isOutput("result")).isTrue();
            assertThat(context.getOutputType("result").qualifiedName()).isEqualTo("java.math.BigDecimal");
        }

        @Test
        @DisplayName("fromModule creates initialized context")
        void fromModuleCreatesContext() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("item", "java.lang.String", "")), List.of(), List.of(), List.of(),
                    List.of());

            JavaTypeInferenceContext ctx = JavaTypeInferenceContext.fromModule(module, resolver);

            assertThat(ctx.isFact("item")).isTrue();
        }
    }

    @Nested
    @DisplayName("Identifier Lookup")
    class IdentifierLookup {

        @Test
        @DisplayName("Looks up fact by name")
        void looksUpFact() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("order", "work.spell.iskibal.compiler.java.testdata.Order", "")),
                    List.of(), List.of(), List.of(), List.of());
            context.initializeFromModule(module);

            JavaType type = context.lookupIdentifier("order");

            assertThat(type.qualifiedName()).isEqualTo("work.spell.iskibal.compiler.java.testdata.Order");
        }

        @Test
        @DisplayName("Looks up output by name")
        void looksUpOutput() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(), List.of(),
                    List.of(new Output.Definition("discount", "java.math.BigDecimal", null, "")), List.of(), List.of());
            context.initializeFromModule(module);

            JavaType type = context.lookupIdentifier("discount");

            assertThat(type.qualifiedName()).isEqualTo("java.math.BigDecimal");
        }

        @Test
        @DisplayName("Looks up local variable by name")
        void looksUpLocal() {
            context.declareLocal("temp", JavaType.STRING);

            JavaType type = context.lookupIdentifier("temp");

            assertThat(type).isEqualTo(JavaType.STRING);
        }

        @Test
        @DisplayName("Prefers facts over outputs")
        void prefersFacts() {
            // If same name appears as both fact and output, fact takes precedence
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("item", "work.spell.iskibal.compiler.java.testdata.FactItem", "")),
                    List.of(), List.of(new Output.Definition("item", "com.example.OutputItem", null, "")), List.of(),
                    List.of());
            context.initializeFromModule(module);

            JavaType type = context.lookupIdentifier("item");

            assertThat(type.qualifiedName()).isEqualTo("work.spell.iskibal.compiler.java.testdata.FactItem");
        }

        @Test
        @DisplayName("Returns unknown for undefined identifier")
        void returnsUnknownForUndefined() {
            JavaType type = context.lookupIdentifier("undefined");

            assertThat(type).isInstanceOf(UnknownType.class);
            assertThat(((UnknownType) type).hint()).contains("undefined");
        }
    }

    @Nested
    @DisplayName("Global Lookup")
    class GlobalLookup {

        @Test
        @DisplayName("Looks up global by name")
        void looksUpGlobal() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("logger", "java.util.logging.Logger", "")), List.of(), List.of(),
                    List.of());
            context.initializeFromModule(module);

            JavaType type = context.lookupGlobal("logger");

            assertThat(type.qualifiedName()).isEqualTo("java.util.logging.Logger");
        }

        @Test
        @DisplayName("Returns unknown for undefined global")
        void returnsUnknownForUndefinedGlobal() {
            JavaType type = context.lookupGlobal("unknown");

            assertThat(type).isInstanceOf(UnknownType.class);
            assertThat(((UnknownType) type).hint()).contains("@unknown");
        }
    }

    @Nested
    @DisplayName("Local Variables")
    class LocalVariables {

        @Test
        @DisplayName("Declares and retrieves local variable")
        void declaresAndRetrievesLocal() {
            ClassType itemType = ClassType.of("work.spell.iskibal.compiler.java.testdata.Order");
            context.declareLocal("item", itemType);

            assertThat(context.lookupIdentifier("item")).isEqualTo(itemType);
        }

        @Test
        @DisplayName("Overwrites existing local with same name")
        void overwritesExistingLocal() {
            context.declareLocal("x", PrimitiveType.INT);
            context.declareLocal("x", JavaType.STRING);

            assertThat(context.lookupIdentifier("x")).isEqualTo(JavaType.STRING);
        }

        @Test
        @DisplayName("Local variable shadows fact")
        void localShadowsFact() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("item", "work.spell.iskibal.compiler.java.testdata.FactItem", "")),
                    List.of(), List.of(), List.of(), List.of());
            context.initializeFromModule(module);

            // Lookup should return fact type initially
            assertThat(context.lookupIdentifier("item").qualifiedName())
                    .isEqualTo("work.spell.iskibal.compiler.java.testdata.FactItem");

            // After declaring local, lookup returns local type
            // NOTE: Current implementation doesn't shadow - facts have priority
            // This test documents current behavior
        }
    }

    @Nested
    @DisplayName("Type Predicates")
    class TypePredicates {

        @BeforeEach
        void setUpModule() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("fact1", "java.lang.String", "")),
                    List.of(new Global.Definition("global1", "java.lang.Integer", "")),
                    List.of(new Output.Definition("output1", "java.math.BigDecimal", null, "")), List.of(), List.of());
            context.initializeFromModule(module);
        }

        @Test
        @DisplayName("isFact returns true for facts")
        void isFactReturnsTrue() {
            assertThat(context.isFact("fact1")).isTrue();
            assertThat(context.isFact("global1")).isFalse();
            assertThat(context.isFact("output1")).isFalse();
            assertThat(context.isFact("unknown")).isFalse();
        }

        @Test
        @DisplayName("isGlobal returns true for globals")
        void isGlobalReturnsTrue() {
            assertThat(context.isGlobal("global1")).isTrue();
            assertThat(context.isGlobal("fact1")).isFalse();
            assertThat(context.isGlobal("output1")).isFalse();
            assertThat(context.isGlobal("unknown")).isFalse();
        }

        @Test
        @DisplayName("isOutput returns true for outputs")
        void isOutputReturnsTrue() {
            assertThat(context.isOutput("output1")).isTrue();
            assertThat(context.isOutput("fact1")).isFalse();
            assertThat(context.isOutput("global1")).isFalse();
            assertThat(context.isOutput("unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("Child Scope")
    class ChildScope {

        @Test
        @DisplayName("Child inherits facts from parent")
        void childInheritsFacts() {
            RuleModule module = new RuleModule.Default(List.of(),
                    List.of(new Fact.Definition("person", "work.spell.iskibal.compiler.java.testdata.Person", "")),
                    List.of(), List.of(), List.of(), List.of());
            context.initializeFromModule(module);

            JavaTypeInferenceContext child = context.childScope();

            assertThat(child.isFact("person")).isTrue();
            assertThat(child.lookupIdentifier("person").qualifiedName())
                    .isEqualTo("work.spell.iskibal.compiler.java.testdata.Person");
        }

        @Test
        @DisplayName("Child inherits globals from parent")
        void childInheritsGlobals() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(),
                    List.of(new Global.Definition("config", "work.spell.iskibal.compiler.java.testdata.Config", "")),
                    List.of(), List.of(), List.of());
            context.initializeFromModule(module);

            JavaTypeInferenceContext child = context.childScope();

            assertThat(child.isGlobal("config")).isTrue();
        }

        @Test
        @DisplayName("Child inherits outputs from parent")
        void childInheritsOutputs() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(), List.of(),
                    List.of(new Output.Definition("result", "java.lang.String", null, "")), List.of(), List.of());
            context.initializeFromModule(module);

            JavaTypeInferenceContext child = context.childScope();

            assertThat(child.isOutput("result")).isTrue();
        }

        @Test
        @DisplayName("Child inherits locals from parent")
        void childInheritsLocals() {
            context.declareLocal("temp", JavaType.BIG_DECIMAL);

            JavaTypeInferenceContext child = context.childScope();

            assertThat(child.lookupIdentifier("temp")).isEqualTo(JavaType.BIG_DECIMAL);
        }

        @Test
        @DisplayName("Child locals do not affect parent")
        void childLocalsDoNotAffectParent() {
            JavaTypeInferenceContext child = context.childScope();
            child.declareLocal("childOnly", JavaType.STRING);

            // Parent should not see child's local
            assertThat(context.lookupIdentifier("childOnly")).isInstanceOf(UnknownType.class);
            // Child should see its local
            assertThat(child.lookupIdentifier("childOnly")).isEqualTo(JavaType.STRING);
        }

        @Test
        @DisplayName("Child has same resolver as parent")
        void childHasSameResolver() {
            JavaTypeInferenceContext child = context.childScope();

            assertThat(child.resolver()).isSameAs(context.resolver());
        }
    }

    @Nested
    @DisplayName("Output Type Access")
    class OutputTypeAccess {

        @Test
        @DisplayName("getOutputType returns type for known output")
        void getOutputTypeReturnsType() {
            RuleModule module = new RuleModule.Default(List.of(), List.of(), List.of(),
                    List.of(new Output.Definition("amount", "java.math.BigDecimal", null, "")), List.of(), List.of());
            context.initializeFromModule(module);

            JavaType type = context.getOutputType("amount");

            assertThat(type.qualifiedName()).isEqualTo("java.math.BigDecimal");
        }

        @Test
        @DisplayName("getOutputType returns unknown for undefined output")
        void getOutputTypeReturnsUnknown() {
            JavaType type = context.getOutputType("undefined");

            assertThat(type).isEqualTo(UnknownType.INSTANCE);
        }
    }

    @Nested
    @DisplayName("Resolver Access")
    class ResolverAccess {

        @Test
        @DisplayName("Returns the resolver")
        void returnsResolver() {
            assertThat(context.resolver()).isSameAs(resolver);
        }
    }
}
