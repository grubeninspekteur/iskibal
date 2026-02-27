package work.spell.iskibal.compiler.java.types;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import work.spell.iskibal.compiler.java.types.JavaType.ClassType;
import work.spell.iskibal.compiler.java.types.JavaType.PrimitiveType;
import work.spell.iskibal.compiler.java.types.JavaType.UnknownType;

/// Resolves Java types using reflection.
///
/// This resolver uses a ClassLoader to load and inspect classes, determining
/// their type characteristics (record, collection, etc.) and resolving property
/// types including generic type arguments.
public final class JavaTypeResolver {

    private final ClassLoader classLoader;
    private final Map<String, JavaType> typeCache = new ConcurrentHashMap<>();
    private final Map<PropertyKey, JavaType> propertyCache = new ConcurrentHashMap<>();

    /// Creates a resolver using the thread context class loader.
    public JavaTypeResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /// Creates a resolver using the specified class loader.
    public JavaTypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    /// Resolves a Java type by its fully qualified name.
    ///
    /// @param qualifiedName
    ///            the fully qualified class name
    /// @return the resolved JavaType, or UnknownType if the class cannot be loaded
    public JavaType resolve(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return UnknownType.INSTANCE;
        }

        // Don't use computeIfAbsent to avoid recursive update issues
        JavaType cached = typeCache.get(qualifiedName);
        if (cached != null) {
            return cached;
        }

        JavaType resolved = doResolve(qualifiedName);
        typeCache.putIfAbsent(qualifiedName, resolved);
        return resolved;
    }

    private JavaType doResolve(String qualifiedName) {
        // Check for primitive types
        PrimitiveType primitive = PrimitiveType.forName(qualifiedName);
        if (primitive != null) {
            return primitive;
        }

        // Handle array types
        if (qualifiedName.endsWith("[]")) {
            String componentName = qualifiedName.substring(0, qualifiedName.length() - 2);
            return new JavaType.ArrayType(resolve(componentName));
        }

        // Try to load the class
        try {
            Class<?> clazz = classLoader.loadClass(qualifiedName);
            return fromClass(clazz);
        } catch (ClassNotFoundException e) {
            return UnknownType.withHint(qualifiedName);
        }
    }

    /// Creates a JavaType from a Class object.
    public JavaType fromClass(Class<?> clazz) {
        if (clazz == null) {
            return UnknownType.INSTANCE;
        }

        String qualifiedName = clazz.getName();

        // Check cache first
        JavaType cached = typeCache.get(qualifiedName);
        if (cached != null) {
            return cached;
        }

        // Handle primitives
        if (clazz.isPrimitive()) {
            PrimitiveType primitive = PrimitiveType.forName(clazz.getName());
            if (primitive != null) {
                return primitive;
            }
        }

        // Handle arrays
        if (clazz.isArray()) {
            return new JavaType.ArrayType(fromClass(clazz.getComponentType()));
        }

        // Determine the type kind
        ClassType.TypeKind kind = determineTypeKind(clazz);
        boolean isRecord = clazz.isRecord();

        ClassType classType = new ClassType(qualifiedName, List.of(), isRecord, kind);
        // Use putIfAbsent to avoid overwriting if another thread resolved it
        typeCache.putIfAbsent(qualifiedName, classType);

        return classType;
    }

    /// Creates a JavaType from a reflection Type, preserving generic information.
    public JavaType fromType(Type type) {
        if (type == null) {
            return UnknownType.INSTANCE;
        }

        if (type instanceof Class<?> clazz) {
            return fromClass(clazz);
        }

        if (type instanceof ParameterizedType pt) {
            return fromParameterizedType(pt);
        }

        if (type instanceof WildcardType wt) {
            // For wildcards like ? extends Foo, use the upper bound
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0) {
                return fromType(upperBounds[0]);
            }
            return JavaType.OBJECT;
        }

        // For other type kinds (TypeVariable, GenericArrayType), fall back to Object
        return JavaType.OBJECT;
    }

    private JavaType fromParameterizedType(ParameterizedType pt) {
        Type rawType = pt.getRawType();
        if (!(rawType instanceof Class<?> rawClass)) {
            return UnknownType.INSTANCE;
        }

        String qualifiedName = rawClass.getName();
        ClassType.TypeKind kind = determineTypeKind(rawClass);
        boolean isRecord = rawClass.isRecord();

        // Resolve type arguments
        Type[] actualArgs = pt.getActualTypeArguments();
        List<JavaType> typeArgs = new java.util.ArrayList<>();
        for (Type arg : actualArgs) {
            typeArgs.add(fromType(arg));
        }

        return new ClassType(qualifiedName, typeArgs, isRecord, kind);
    }

    private ClassType.TypeKind determineTypeKind(Class<?> clazz) {
        String name = clazz.getName();

        // Check for String
        if (name.equals("java.lang.String")) {
            return ClassType.TypeKind.STRING;
        }

        // Check for BigDecimal/BigInteger
        if (name.equals("java.math.BigDecimal") || name.equals("java.math.BigInteger")) {
            return ClassType.TypeKind.BIG_NUMERIC;
        }

        // Check for boxed primitives
        if (name.equals("java.lang.Integer") || name.equals("java.lang.Long") || name.equals("java.lang.Double")
                || name.equals("java.lang.Float") || name.equals("java.lang.Boolean") || name.equals("java.lang.Byte")
                || name.equals("java.lang.Short") || name.equals("java.lang.Character")) {
            return ClassType.TypeKind.BOXED_PRIMITIVE;
        }

        // Check for Map
        if (Map.class.isAssignableFrom(clazz)) {
            return ClassType.TypeKind.MAP;
        }

        // Check for Collection (includes List, Set)
        if (Collection.class.isAssignableFrom(clazz)) {
            return ClassType.TypeKind.COLLECTION;
        }

        return ClassType.TypeKind.REGULAR;
    }

    /// Resolves the type of a property on the given type.
    ///
    /// @param ownerType
    ///            the type that owns the property
    /// @param propertyName
    ///            the property name
    /// @return the property type, or UnknownType if not found
    public JavaType resolveProperty(JavaType ownerType, String propertyName) {
        if (ownerType instanceof UnknownType) {
            return UnknownType.INSTANCE;
        }

        // For collection types, resolve property on the element type
        if (ownerType.isCollection()) {
            JavaType elementType = ownerType.elementType();
            return resolveProperty(elementType, propertyName);
        }

        if (!(ownerType instanceof ClassType ct)) {
            return UnknownType.INSTANCE;
        }

        PropertyKey key = new PropertyKey(ct.qualifiedName(), propertyName);

        // Don't use computeIfAbsent to avoid recursive update issues
        JavaType cached = propertyCache.get(key);
        if (cached != null) {
            return cached;
        }

        JavaType resolved = doResolveProperty(ct, propertyName);
        propertyCache.putIfAbsent(key, resolved);
        return resolved;
    }

    private JavaType doResolveProperty(ClassType ownerType, String propertyName) {
        try {
            Class<?> clazz = classLoader.loadClass(ownerType.qualifiedName());

            // For records, look for the component accessor (same name as property)
            if (clazz.isRecord()) {
                return resolveRecordComponent(clazz, propertyName);
            }

            // For regular classes, look for getter methods
            return resolveGetter(clazz, propertyName);

        } catch (ClassNotFoundException e) {
            return UnknownType.withHint(ownerType.qualifiedName() + "." + propertyName);
        }
    }

    private JavaType resolveRecordComponent(Class<?> recordClass, String componentName) {
        // Record accessors have the same name as the component
        try {
            Method accessor = recordClass.getMethod(componentName);
            return fromType(accessor.getGenericReturnType());
        } catch (NoSuchMethodException e) {
            return UnknownType.withHint(recordClass.getName() + "." + componentName);
        }
    }

    private JavaType resolveGetter(Class<?> clazz, String propertyName) {
        String capitalizedName = capitalize(propertyName);

        // Try getX() first
        Optional<Method> getter = findMethod(clazz, "get" + capitalizedName);
        if (getter.isPresent()) {
            return fromType(getter.get().getGenericReturnType());
        }

        // Try isX() for booleans
        getter = findMethod(clazz, "is" + capitalizedName);
        if (getter.isPresent()) {
            return fromType(getter.get().getGenericReturnType());
        }

        // Try direct property access (for records that aren't detected or public
        // fields)
        getter = findMethod(clazz, propertyName);
        if (getter.isPresent() && getter.get().getParameterCount() == 0) {
            return fromType(getter.get().getGenericReturnType());
        }

        return UnknownType.withHint(clazz.getName() + "." + propertyName);
    }

    private Optional<Method> findMethod(Class<?> clazz, String name) {
        try {
            Method method = clazz.getMethod(name);
            if (method.getParameterCount() == 0) {
                return Optional.of(method);
            }
        } catch (NoSuchMethodException ignored) {
        }
        return Optional.empty();
    }

    /// Checks if the given type is a record type.
    public boolean isRecord(JavaType type) {
        if (type instanceof ClassType ct) {
            if (ct.isRecord()) {
                return true;
            }
            // Double-check with reflection if not marked
            try {
                Class<?> clazz = classLoader.loadClass(ct.qualifiedName());
                return clazz.isRecord();
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return false;
    }

    /// Checks if the given type is a collection type.
    public boolean isCollection(JavaType type) {
        if (type.isCollection()) {
            return true;
        }
        if (type instanceof ClassType ct) {
            try {
                Class<?> clazz = classLoader.loadClass(ct.qualifiedName());
                return Collection.class.isAssignableFrom(clazz);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return false;
    }

    /// Clears all cached type information.
    public void clearCache() {
        typeCache.clear();
        propertyCache.clear();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record PropertyKey(String ownerType, String propertyName) {
    }
}
