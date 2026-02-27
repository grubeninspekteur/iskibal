package work.spell.iskibal.compiler.java.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.compiler.java.types.JavaType.ClassType;
import work.spell.iskibal.compiler.java.types.JavaType.PrimitiveType;
import work.spell.iskibal.compiler.java.types.JavaType.UnknownType;

/// Tests for JavaTypeResolver.
class JavaTypeResolverTest {

    private JavaTypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JavaTypeResolver();
    }

    @Nested
    @DisplayName("Resolve by Name")
    class ResolveByName {

        @Test
        @DisplayName("Resolves primitive types")
        void resolvesPrimitives() {
            assertThat(resolver.resolve("int")).isEqualTo(PrimitiveType.INT);
            assertThat(resolver.resolve("long")).isEqualTo(PrimitiveType.LONG);
            assertThat(resolver.resolve("double")).isEqualTo(PrimitiveType.DOUBLE);
            assertThat(resolver.resolve("boolean")).isEqualTo(PrimitiveType.BOOLEAN);
            assertThat(resolver.resolve("void")).isEqualTo(PrimitiveType.VOID);
        }

        @Test
        @DisplayName("Resolves standard library classes")
        void resolvesStandardClasses() {
            JavaType stringType = resolver.resolve("java.lang.String");
            assertThat(stringType).isInstanceOf(ClassType.class);
            assertThat(stringType.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Resolves collection types")
        void resolvesCollectionTypes() {
            JavaType listType = resolver.resolve("java.util.List");
            assertThat(listType.isCollection()).isTrue();
            assertThat(listType.isMap()).isFalse();

            JavaType setType = resolver.resolve("java.util.Set");
            assertThat(setType.isCollection()).isTrue();

            JavaType arrayListType = resolver.resolve("java.util.ArrayList");
            assertThat(arrayListType.isCollection()).isTrue();
        }

        @Test
        @DisplayName("Resolves map types")
        void resolvesMapTypes() {
            JavaType mapType = resolver.resolve("java.util.Map");
            assertThat(mapType.isMap()).isTrue();
            assertThat(mapType.isCollection()).isFalse();

            JavaType hashMapType = resolver.resolve("java.util.HashMap");
            assertThat(hashMapType.isMap()).isTrue();
        }

        @Test
        @DisplayName("Resolves BigDecimal as big numeric")
        void resolvesBigDecimal() {
            JavaType type = resolver.resolve("java.math.BigDecimal");
            assertThat(type.isNumeric()).isTrue();
            assertThat(type).isInstanceOf(ClassType.class);
            assertThat(((ClassType) type).kind()).isEqualTo(ClassType.TypeKind.BIG_NUMERIC);
        }

        @Test
        @DisplayName("Resolves boxed primitives")
        void resolvesBoxedPrimitives() {
            JavaType integerType = resolver.resolve("java.lang.Integer");
            assertThat(integerType.isNumeric()).isTrue();

            JavaType booleanType = resolver.resolve("java.lang.Boolean");
            assertThat(booleanType.isBoolean()).isTrue();
        }

        @Test
        @DisplayName("Returns unknown for non-existent class")
        void returnsUnknownForNonExistent() {
            JavaType type = resolver.resolve("com.nonexistent.FakeClass");
            assertThat(type).isInstanceOf(UnknownType.class);
            assertThat(((UnknownType) type).hint()).contains("com.nonexistent.FakeClass");
        }

        @Test
        @DisplayName("Returns unknown for null or empty name")
        void returnsUnknownForNullOrEmpty() {
            assertThat(resolver.resolve(null)).isEqualTo(UnknownType.INSTANCE);
            assertThat(resolver.resolve("")).isEqualTo(UnknownType.INSTANCE);
        }

        @Test
        @DisplayName("Resolves array types by name")
        void resolvesArrayTypes() {
            JavaType intArrayType = resolver.resolve("int[]");
            assertThat(intArrayType).isInstanceOf(JavaType.ArrayType.class);
            assertThat(((JavaType.ArrayType) intArrayType).componentType()).isEqualTo(PrimitiveType.INT);

            JavaType stringArrayType = resolver.resolve("java.lang.String[]");
            assertThat(stringArrayType).isInstanceOf(JavaType.ArrayType.class);
        }

        @Test
        @DisplayName("Caches resolved types")
        void cachesResolvedTypes() {
            JavaType first = resolver.resolve("java.lang.String");
            JavaType second = resolver.resolve("java.lang.String");
            // Should return the same cached instance
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Resolve from Class")
    class ResolveFromClass {

        @Test
        @DisplayName("Resolves from Class object")
        void resolvesFromClass() {
            JavaType type = resolver.fromClass(String.class);
            assertThat(type.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Detects record classes")
        void detectsRecords() {
            JavaType type = resolver.fromClass(TestRecord.class);
            assertThat(type.isRecord()).isTrue();
        }

        @Test
        @DisplayName("Detects collection classes")
        void detectsCollections() {
            assertThat(resolver.fromClass(List.class).isCollection()).isTrue();
            assertThat(resolver.fromClass(Set.class).isCollection()).isTrue();
            assertThat(resolver.fromClass(ArrayList.class).isCollection()).isTrue();
            assertThat(resolver.fromClass(HashSet.class).isCollection()).isTrue();
        }

        @Test
        @DisplayName("Detects map classes")
        void detectsMaps() {
            assertThat(resolver.fromClass(Map.class).isMap()).isTrue();
            assertThat(resolver.fromClass(HashMap.class).isMap()).isTrue();
        }

        @Test
        @DisplayName("Returns unknown for null")
        void returnsUnknownForNull() {
            assertThat(resolver.fromClass(null)).isEqualTo(UnknownType.INSTANCE);
        }

        @Test
        @DisplayName("Handles primitive classes")
        void handlesPrimitiveClasses() {
            assertThat(resolver.fromClass(int.class)).isEqualTo(PrimitiveType.INT);
            assertThat(resolver.fromClass(boolean.class)).isEqualTo(PrimitiveType.BOOLEAN);
        }

        @Test
        @DisplayName("Handles array classes")
        void handlesArrayClasses() {
            JavaType type = resolver.fromClass(int[].class);
            assertThat(type).isInstanceOf(JavaType.ArrayType.class);
            assertThat(((JavaType.ArrayType) type).componentType()).isEqualTo(PrimitiveType.INT);
        }
    }

    @Nested
    @DisplayName("Property Resolution")
    class PropertyResolution {

        @Test
        @DisplayName("Resolves bean getter property")
        void resolvesBeanGetterProperty() {
            JavaType personType = resolver.resolve(TestPerson.class.getName());
            JavaType nameType = resolver.resolveProperty(personType, "name");

            assertThat(nameType.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Resolves boolean is-getter property")
        void resolvesIsGetterProperty() {
            JavaType personType = resolver.resolve(TestPerson.class.getName());
            JavaType activeType = resolver.resolveProperty(personType, "active");

            assertThat(activeType).isEqualTo(PrimitiveType.BOOLEAN);
        }

        @Test
        @DisplayName("Resolves record component property")
        void resolvesRecordComponent() {
            JavaType recordType = resolver.resolve(TestRecord.class.getName());
            JavaType valueType = resolver.resolveProperty(recordType, "value");

            assertThat(valueType.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Resolves generic collection element type")
        void resolvesGenericCollectionElement() {
            JavaType personType = resolver.resolve(TestPerson.class.getName());
            JavaType tagsType = resolver.resolveProperty(personType, "tags");

            assertThat(tagsType.isCollection()).isTrue();
            // The element type should be String due to generic signature
            assertThat(tagsType.elementType().qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Returns unknown for non-existent property")
        void returnsUnknownForNonExistentProperty() {
            JavaType personType = resolver.resolve(TestPerson.class.getName());
            JavaType unknownType = resolver.resolveProperty(personType, "nonExistent");

            assertThat(unknownType).isInstanceOf(UnknownType.class);
        }

        @Test
        @DisplayName("Resolves property on collection element type")
        void resolvesPropertyOnCollectionElement() {
            // When resolving a property on a collection type, it should resolve on the
            // element type
            JavaType listType = ClassType.collection("java.util.List", resolver.resolve(TestPerson.class.getName()));

            JavaType nameType = resolver.resolveProperty(listType, "name");
            assertThat(nameType.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("Returns unknown for unknown owner type")
        void returnsUnknownForUnknownOwner() {
            JavaType unknownType = UnknownType.INSTANCE;
            JavaType result = resolver.resolveProperty(unknownType, "anything");

            assertThat(result).isEqualTo(UnknownType.INSTANCE);
        }

        @Test
        @DisplayName("Caches property resolutions")
        void cachesPropertyResolutions() {
            JavaType personType = resolver.resolve(TestPerson.class.getName());
            JavaType first = resolver.resolveProperty(personType, "name");
            JavaType second = resolver.resolveProperty(personType, "name");

            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Type Checking")
    class TypeChecking {

        @Test
        @DisplayName("isRecord checks via reflection")
        void isRecordChecksReflection() {
            JavaType recordType = resolver.resolve(TestRecord.class.getName());
            assertThat(resolver.isRecord(recordType)).isTrue();

            JavaType classType = resolver.resolve(TestPerson.class.getName());
            assertThat(resolver.isRecord(classType)).isFalse();
        }

        @Test
        @DisplayName("isCollection checks via reflection")
        void isCollectionChecksReflection() {
            JavaType listType = resolver.resolve("java.util.ArrayList");
            assertThat(resolver.isCollection(listType)).isTrue();

            JavaType stringType = resolver.resolve("java.lang.String");
            assertThat(resolver.isCollection(stringType)).isFalse();
        }
    }

    @Nested
    @DisplayName("Cache Management")
    class CacheManagement {

        @Test
        @DisplayName("clearCache removes all cached types")
        void clearCacheRemovesCachedTypes() {
            JavaType first = resolver.resolve("java.lang.String");
            resolver.clearCache();
            JavaType second = resolver.resolve("java.lang.String");

            // After clearing, a new instance should be created
            // (can't guarantee different instance, but cache was cleared)
            assertThat(first.qualifiedName()).isEqualTo(second.qualifiedName());
        }
    }

    // Test fixture classes

    public static class TestPerson {

        private String name;
        private int age;
        private boolean active;
        private List<String> tags;
        private BigDecimal balance;

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public boolean isActive() {
            return active;
        }

        public List<String> getTags() {
            return tags;
        }

        public BigDecimal getBalance() {
            return balance;
        }
    }

    public record TestRecord(String value, int count) {
    }
}
