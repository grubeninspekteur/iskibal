package work.spell.iskibal.compiler.java.types;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.spell.iskibal.compiler.java.types.JavaType.ArrayType;
import work.spell.iskibal.compiler.java.types.JavaType.ClassType;
import work.spell.iskibal.compiler.java.types.JavaType.PrimitiveType;
import work.spell.iskibal.compiler.java.types.JavaType.UnknownType;

/// Tests for the JavaType sealed interface hierarchy.
class JavaTypeTest {

    @Nested
    @DisplayName("Primitive Types")
    class PrimitiveTypeTests {

        @Test
        @DisplayName("INT is numeric")
        void intIsNumeric() {
            assertThat(PrimitiveType.INT.isNumeric()).isTrue();
            assertThat(PrimitiveType.INT.isBoolean()).isFalse();
            assertThat(PrimitiveType.INT.qualifiedName()).isEqualTo("int");
        }

        @Test
        @DisplayName("LONG is numeric")
        void longIsNumeric() {
            assertThat(PrimitiveType.LONG.isNumeric()).isTrue();
            assertThat(PrimitiveType.LONG.qualifiedName()).isEqualTo("long");
        }

        @Test
        @DisplayName("DOUBLE is numeric")
        void doubleIsNumeric() {
            assertThat(PrimitiveType.DOUBLE.isNumeric()).isTrue();
            assertThat(PrimitiveType.DOUBLE.qualifiedName()).isEqualTo("double");
        }

        @Test
        @DisplayName("BOOLEAN is boolean type")
        void booleanIsBoolean() {
            assertThat(PrimitiveType.BOOLEAN.isBoolean()).isTrue();
            assertThat(PrimitiveType.BOOLEAN.isNumeric()).isFalse();
            assertThat(PrimitiveType.BOOLEAN.qualifiedName()).isEqualTo("boolean");
        }

        @Test
        @DisplayName("VOID is neither numeric nor boolean")
        void voidIsNeither() {
            assertThat(PrimitiveType.VOID.isNumeric()).isFalse();
            assertThat(PrimitiveType.VOID.isBoolean()).isFalse();
        }

        @Test
        @DisplayName("forName returns correct primitive")
        void forNameReturnsPrimitive() {
            assertThat(PrimitiveType.forName("int")).isEqualTo(PrimitiveType.INT);
            assertThat(PrimitiveType.forName("long")).isEqualTo(PrimitiveType.LONG);
            assertThat(PrimitiveType.forName("boolean")).isEqualTo(PrimitiveType.BOOLEAN);
            assertThat(PrimitiveType.forName("void")).isEqualTo(PrimitiveType.VOID);
        }

        @Test
        @DisplayName("forName returns null for non-primitive")
        void forNameReturnsNullForNonPrimitive() {
            assertThat(PrimitiveType.forName("String")).isNull();
            assertThat(PrimitiveType.forName("Integer")).isNull();
            assertThat(PrimitiveType.forName("unknown")).isNull();
        }

        @Test
        @DisplayName("Primitives are not collections or records")
        void primitivesNotCollectionsOrRecords() {
            for (PrimitiveType type : PrimitiveType.values()) {
                assertThat(type.isCollection()).isFalse();
                assertThat(type.isMap()).isFalse();
                assertThat(type.isRecord()).isFalse();
            }
        }

        @Test
        @DisplayName("elementType returns self for primitives")
        void elementTypeReturnsSelf() {
            assertThat(PrimitiveType.INT.elementType()).isEqualTo(PrimitiveType.INT);
        }
    }

    @Nested
    @DisplayName("Class Types")
    class ClassTypeTests {

        @Test
        @DisplayName("Simple class type creation")
        void simpleClassType() {
            ClassType type = ClassType.of("com.example.Person");

            assertThat(type.qualifiedName()).isEqualTo("com.example.Person");
            assertThat(type.simpleName()).isEqualTo("Person");
            assertThat(type.typeArguments()).isEmpty();
            assertThat(type.isRecord()).isFalse();
            assertThat(type.isCollection()).isFalse();
            assertThat(type.isMap()).isFalse();
        }

        @Test
        @DisplayName("Record type creation")
        void recordType() {
            ClassType type = ClassType.record("com.example.PersonRecord");

            assertThat(type.qualifiedName()).isEqualTo("com.example.PersonRecord");
            assertThat(type.isRecord()).isTrue();
            assertThat(type.isCollection()).isFalse();
        }

        @Test
        @DisplayName("Collection type creation")
        void collectionType() {
            ClassType elementType = ClassType.of("com.example.Item");
            ClassType type = ClassType.collection("java.util.List", elementType);

            assertThat(type.qualifiedName()).isEqualTo("java.util.List");
            assertThat(type.isCollection()).isTrue();
            assertThat(type.isMap()).isFalse();
            assertThat(type.elementType()).isEqualTo(elementType);
            assertThat(type.typeArguments()).containsExactly(elementType);
        }

        @Test
        @DisplayName("Map type creation")
        void mapType() {
            ClassType keyType = ClassType.of("java.lang.String");
            ClassType valueType = ClassType.of("java.lang.Integer");
            ClassType type = ClassType.map("java.util.Map", keyType, valueType);

            assertThat(type.qualifiedName()).isEqualTo("java.util.Map");
            assertThat(type.isMap()).isTrue();
            assertThat(type.isCollection()).isFalse();
            assertThat(type.keyType()).isEqualTo(keyType);
            assertThat(type.valueType()).isEqualTo(valueType);
            assertThat(type.typeArguments()).containsExactly(keyType, valueType);
        }

        @Test
        @DisplayName("Boxed primitive types are numeric")
        void boxedPrimitivesNumeric() {
            ClassType integerType = ClassType.of("java.lang.Integer", ClassType.TypeKind.BOXED_PRIMITIVE);
            ClassType longType = ClassType.of("java.lang.Long", ClassType.TypeKind.BOXED_PRIMITIVE);
            ClassType doubleType = ClassType.of("java.lang.Double", ClassType.TypeKind.BOXED_PRIMITIVE);

            assertThat(integerType.isNumeric()).isTrue();
            assertThat(longType.isNumeric()).isTrue();
            assertThat(doubleType.isNumeric()).isTrue();
        }

        @Test
        @DisplayName("Boxed Boolean is boolean")
        void boxedBooleanIsBoolean() {
            ClassType booleanType = new ClassType("java.lang.Boolean", List.of(), false,
                    ClassType.TypeKind.BOXED_PRIMITIVE);

            assertThat(booleanType.isBoolean()).isTrue();
            assertThat(booleanType.isNumeric()).isFalse();
        }

        @Test
        @DisplayName("BigDecimal and BigInteger are numeric")
        void bigNumericTypes() {
            ClassType bigDecimal = ClassType.of("java.math.BigDecimal", ClassType.TypeKind.BIG_NUMERIC);
            ClassType bigInteger = ClassType.of("java.math.BigInteger", ClassType.TypeKind.BIG_NUMERIC);

            assertThat(bigDecimal.isNumeric()).isTrue();
            assertThat(bigInteger.isNumeric()).isTrue();
        }

        @Test
        @DisplayName("simpleName extracts class name from qualified name")
        void simpleNameExtraction() {
            assertThat(ClassType.of("java.util.List").simpleName()).isEqualTo("List");
            assertThat(ClassType.of("Person").simpleName()).isEqualTo("Person");
            assertThat(ClassType.of("com.example.nested.DeepClass").simpleName()).isEqualTo("DeepClass");
        }

        @Test
        @DisplayName("elementType returns self for non-collection")
        void elementTypeForNonCollection() {
            ClassType type = ClassType.of("com.example.Person");
            assertThat(type.elementType()).isEqualTo(type);
        }

        @Test
        @DisplayName("keyType and valueType return unknown for non-map")
        void keyValueTypeForNonMap() {
            ClassType type = ClassType.of("com.example.Person");
            assertThat(type.keyType()).isEqualTo(UnknownType.INSTANCE);
            assertThat(type.valueType()).isEqualTo(UnknownType.INSTANCE);
        }
    }

    @Nested
    @DisplayName("Array Types")
    class ArrayTypeTests {

        @Test
        @DisplayName("Array type creation")
        void arrayTypeCreation() {
            ArrayType type = new ArrayType(PrimitiveType.INT);

            assertThat(type.qualifiedName()).isEqualTo("int[]");
            assertThat(type.componentType()).isEqualTo(PrimitiveType.INT);
            assertThat(type.elementType()).isEqualTo(PrimitiveType.INT);
        }

        @Test
        @DisplayName("Multi-dimensional array")
        void multiDimensionalArray() {
            ArrayType inner = new ArrayType(PrimitiveType.INT);
            ArrayType outer = new ArrayType(inner);

            assertThat(outer.qualifiedName()).isEqualTo("int[][]");
            assertThat(outer.componentType()).isEqualTo(inner);
            assertThat(outer.elementType()).isEqualTo(inner);
        }

        @Test
        @DisplayName("Array of class type")
        void arrayOfClassType() {
            ClassType stringType = ClassType.of("java.lang.String");
            ArrayType type = new ArrayType(stringType);

            assertThat(type.qualifiedName()).isEqualTo("java.lang.String[]");
        }

        @Test
        @DisplayName("Arrays are not collections or records")
        void arraysNotCollectionsOrRecords() {
            ArrayType type = new ArrayType(PrimitiveType.INT);

            assertThat(type.isCollection()).isFalse();
            assertThat(type.isMap()).isFalse();
            assertThat(type.isRecord()).isFalse();
        }
    }

    @Nested
    @DisplayName("Unknown Types")
    class UnknownTypeTests {

        @Test
        @DisplayName("Singleton instance")
        void singletonInstance() {
            assertThat(UnknownType.INSTANCE.hint()).isNull();
            assertThat(UnknownType.INSTANCE.qualifiedName()).isEqualTo("<unknown>");
        }

        @Test
        @DisplayName("Unknown type with hint")
        void unknownWithHint() {
            UnknownType type = UnknownType.withHint("identifier: foo");

            assertThat(type.hint()).isEqualTo("identifier: foo");
            assertThat(type.qualifiedName()).isEqualTo("<unknown: identifier: foo>");
        }

        @Test
        @DisplayName("Unknown types are not special types")
        void unknownNotSpecial() {
            assertThat(UnknownType.INSTANCE.isCollection()).isFalse();
            assertThat(UnknownType.INSTANCE.isMap()).isFalse();
            assertThat(UnknownType.INSTANCE.isRecord()).isFalse();
            assertThat(UnknownType.INSTANCE.isNumeric()).isFalse();
            assertThat(UnknownType.INSTANCE.isBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("Type Constants")
    class TypeConstantsTests {

        @Test
        @DisplayName("OBJECT constant")
        void objectConstant() {
            assertThat(JavaType.OBJECT.qualifiedName()).isEqualTo("java.lang.Object");
        }

        @Test
        @DisplayName("STRING constant")
        void stringConstant() {
            assertThat(JavaType.STRING.qualifiedName()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("BIG_DECIMAL constant")
        void bigDecimalConstant() {
            assertThat(JavaType.BIG_DECIMAL.qualifiedName()).isEqualTo("java.math.BigDecimal");
            assertThat(JavaType.BIG_DECIMAL.isNumeric()).isTrue();
        }

        @Test
        @DisplayName("BIG_INTEGER constant")
        void bigIntegerConstant() {
            assertThat(JavaType.BIG_INTEGER.qualifiedName()).isEqualTo("java.math.BigInteger");
            assertThat(JavaType.BIG_INTEGER.isNumeric()).isTrue();
        }

        @Test
        @DisplayName("Boxed type constants")
        void boxedTypeConstants() {
            assertThat(JavaType.BOOLEAN_BOXED.qualifiedName()).isEqualTo("java.lang.Boolean");
            assertThat(JavaType.INTEGER_BOXED.qualifiedName()).isEqualTo("java.lang.Integer");
            assertThat(JavaType.LONG_BOXED.qualifiedName()).isEqualTo("java.lang.Long");
            assertThat(JavaType.DOUBLE_BOXED.qualifiedName()).isEqualTo("java.lang.Double");
        }
    }
}
