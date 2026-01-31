package work.spell.iskibal.compiler.java.types;

import java.util.List;
import java.util.Objects;

/**
 * Represents Java types used during type inference and code generation.
 * <p>
 * This is a Java-specific type model used internally by the Java compiler to
 * generate type-correct code. It is separate from the rule model, which remains
 * target-agnostic.
 */
public sealed interface JavaType
		permits JavaType.PrimitiveType, JavaType.ClassType, JavaType.ArrayType, JavaType.UnknownType {

	/**
	 * Returns the fully qualified name of this type.
	 */
	String qualifiedName();

	/**
	 * Returns true if this type represents a Java collection (List, Set,
	 * Collection).
	 */
	default boolean isCollection() {
		return false;
	}

	/**
	 * Returns true if this type represents a Java Map.
	 */
	default boolean isMap() {
		return false;
	}

	/**
	 * Returns true if this type represents a Java record.
	 */
	default boolean isRecord() {
		return false;
	}

	/**
	 * Returns true if this type is numeric (primitive or boxed numeric type, or
	 * BigDecimal/BigInteger).
	 */
	default boolean isNumeric() {
		return false;
	}

	/**
	 * Returns true if this type is boolean (primitive or boxed).
	 */
	default boolean isBoolean() {
		return false;
	}

	/**
	 * Returns the element type if this is a collection or array, otherwise returns
	 * this type.
	 */
	default JavaType elementType() {
		return this;
	}

	/**
	 * Primitive Java types.
	 */
	enum PrimitiveType implements JavaType {
		INT("int", true, false), LONG("long", true, false), DOUBLE("double", true, false), FLOAT("float", true,
				false), BOOLEAN("boolean", false, true), CHAR("char", false,
						false), BYTE("byte", true, false), SHORT("short", true, false), VOID("void", false, false);

		private final String name;
		private final boolean numeric;
		private final boolean booleanType;

		PrimitiveType(String name, boolean numeric, boolean booleanType) {
			this.name = name;
			this.numeric = numeric;
			this.booleanType = booleanType;
		}

		@Override
		public String qualifiedName() {
			return name;
		}

		@Override
		public boolean isNumeric() {
			return numeric;
		}

		@Override
		public boolean isBoolean() {
			return booleanType;
		}

		/**
		 * Returns the primitive type for the given name, or null if not a primitive.
		 */
		public static PrimitiveType forName(String name) {
			for (PrimitiveType type : values()) {
				if (type.name.equals(name)) {
					return type;
				}
			}
			return null;
		}
	}

	/**
	 * Reference types (classes, interfaces, records).
	 */
	record ClassType(String qualifiedName, List<JavaType> typeArguments, boolean record,
			TypeKind kind) implements JavaType {

		/**
		 * Kinds of class types for special handling.
		 */
		public enum TypeKind {
			/** Regular class or interface */
			REGULAR,
			/** Collection type (List, Set, Collection) */
			COLLECTION,
			/** Map type */
			MAP,
			/** Boxed primitive (Integer, Long, etc.) */
			BOXED_PRIMITIVE,
			/** BigDecimal or BigInteger */
			BIG_NUMERIC,
			/** String */
			STRING
		}

		public ClassType {
			Objects.requireNonNull(qualifiedName, "qualifiedName");
			typeArguments = typeArguments != null ? List.copyOf(typeArguments) : List.of();
		}

		/**
		 * Creates a simple class type without type arguments.
		 */
		public static ClassType of(String qualifiedName) {
			return new ClassType(qualifiedName, List.of(), false, TypeKind.REGULAR);
		}

		/**
		 * Creates a class type with the specified kind.
		 */
		public static ClassType of(String qualifiedName, TypeKind kind) {
			return new ClassType(qualifiedName, List.of(), false, kind);
		}

		/**
		 * Creates a record type.
		 */
		public static ClassType record(String qualifiedName) {
			return new ClassType(qualifiedName, List.of(), true, TypeKind.REGULAR);
		}

		/**
		 * Creates a collection type with the specified element type.
		 */
		public static ClassType collection(String qualifiedName, JavaType elementType) {
			return new ClassType(qualifiedName, List.of(elementType), false, TypeKind.COLLECTION);
		}

		/**
		 * Creates a map type with the specified key and value types.
		 */
		public static ClassType map(String qualifiedName, JavaType keyType, JavaType valueType) {
			return new ClassType(qualifiedName, List.of(keyType, valueType), false, TypeKind.MAP);
		}

		@Override
		public boolean isCollection() {
			return kind == TypeKind.COLLECTION;
		}

		@Override
		public boolean isMap() {
			return kind == TypeKind.MAP;
		}

		@Override
		public boolean isRecord() {
			return record;
		}

		@Override
		public boolean isNumeric() {
			return kind == TypeKind.BOXED_PRIMITIVE && isNumericBoxed() || kind == TypeKind.BIG_NUMERIC;
		}

		private boolean isNumericBoxed() {
			return qualifiedName.equals("java.lang.Integer") || qualifiedName.equals("java.lang.Long")
					|| qualifiedName.equals("java.lang.Double") || qualifiedName.equals("java.lang.Float")
					|| qualifiedName.equals("java.lang.Byte") || qualifiedName.equals("java.lang.Short");
		}

		@Override
		public boolean isBoolean() {
			return kind == TypeKind.BOXED_PRIMITIVE && qualifiedName.equals("java.lang.Boolean");
		}

		@Override
		public JavaType elementType() {
			if (isCollection() && !typeArguments.isEmpty()) {
				return typeArguments.getFirst();
			}
			return this;
		}

		/**
		 * Returns the key type if this is a map, otherwise returns unknown.
		 */
		public JavaType keyType() {
			if (isMap() && typeArguments.size() >= 1) {
				return typeArguments.getFirst();
			}
			return UnknownType.INSTANCE;
		}

		/**
		 * Returns the value type if this is a map, otherwise returns unknown.
		 */
		public JavaType valueType() {
			if (isMap() && typeArguments.size() >= 2) {
				return typeArguments.get(1);
			}
			return UnknownType.INSTANCE;
		}

		/**
		 * Returns the simple (unqualified) name of this class.
		 */
		public String simpleName() {
			int lastDot = qualifiedName.lastIndexOf('.');
			return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
		}
	}

	/**
	 * Array types.
	 */
	record ArrayType(JavaType componentType) implements JavaType {

		public ArrayType {
			Objects.requireNonNull(componentType, "componentType");
		}

		@Override
		public String qualifiedName() {
			return componentType.qualifiedName() + "[]";
		}

		@Override
		public JavaType elementType() {
			return componentType;
		}
	}

	/**
	 * Represents an unknown or unresolved type.
	 */
	record UnknownType(String hint) implements JavaType {

		/** Singleton instance for types with no hint. */
		public static final UnknownType INSTANCE = new UnknownType(null);

		/**
		 * Creates an unknown type with a hint about what was expected.
		 */
		public static UnknownType withHint(String hint) {
			return new UnknownType(hint);
		}

		@Override
		public String qualifiedName() {
			return hint != null ? "<unknown: " + hint + ">" : "<unknown>";
		}
	}

	// Common type constants for convenience
	JavaType OBJECT = ClassType.of("java.lang.Object");
	JavaType STRING = ClassType.of("java.lang.String", ClassType.TypeKind.STRING);
	JavaType BIG_DECIMAL = ClassType.of("java.math.BigDecimal", ClassType.TypeKind.BIG_NUMERIC);
	JavaType BIG_INTEGER = ClassType.of("java.math.BigInteger", ClassType.TypeKind.BIG_NUMERIC);
	JavaType BOOLEAN_BOXED = ClassType.of("java.lang.Boolean", ClassType.TypeKind.BOXED_PRIMITIVE);
	JavaType INTEGER_BOXED = ClassType.of("java.lang.Integer", ClassType.TypeKind.BOXED_PRIMITIVE);
	JavaType LONG_BOXED = ClassType.of("java.lang.Long", ClassType.TypeKind.BOXED_PRIMITIVE);
	JavaType DOUBLE_BOXED = ClassType.of("java.lang.Double", ClassType.TypeKind.BOXED_PRIMITIVE);
}
