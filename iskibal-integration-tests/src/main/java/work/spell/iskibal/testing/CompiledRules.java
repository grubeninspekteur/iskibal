package work.spell.iskibal.testing;

import module java.base;

import module iskibal.compiler.java;

/// Wraps a compiled rules class for reflection-based execution.
///
/// This allows tests to instantiate and invoke rules without static knowledge of
/// the generated class structure.
public class CompiledRules {

    private final Object instance;
    private final Class<?> rulesClass;

    private CompiledRules(Object instance, Class<?> rulesClass) {
        this.instance = instance;
        this.rulesClass = rulesClass;
    }

    /// Creates a new instance of the compiled rules class with the given constructor
    /// arguments.
    ///
    /// @param rulesClass
    ///            the compiled rules class
    /// @param args
    ///            constructor arguments (facts and globals)
    /// @return a CompiledRules wrapper for the instantiated object
    /// @throws ReflectiveOperationException
    ///             if instantiation fails
    public static CompiledRules instantiate(Class<?> rulesClass, Object... args) throws ReflectiveOperationException {
        Constructor<?>[] constructors = rulesClass.getConstructors();
        if (constructors.length == 0) {
            throw new IllegalArgumentException("No public constructors found in " + rulesClass.getName());
        }

        // Find a constructor that matches the argument count
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == args.length) {
                try {
                    Object instance = constructor.newInstance(args);
                    return new CompiledRules(instance, rulesClass);
                } catch (IllegalArgumentException e) {
                    // Try next constructor
                }
            }
        }

        throw new IllegalArgumentException(
                "No constructor in " + rulesClass.getName() + " matches arguments: " + args.length + " arguments");
    }

    /// Calls the evaluate() method on the rules instance.
    ///
    /// @throws ReflectiveOperationException
    ///             if the method call fails
    /// @throws RuntimeException
    ///             if the evaluation throws a runtime exception (unwrapped from
    ///             InvocationTargetException)
    public void evaluate() throws ReflectiveOperationException {
        Method evaluateMethod = rulesClass.getMethod("evaluate");
        try {
            evaluateMethod.invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            } else if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    /// Gets an output value by name using the generated getter method.
    ///
    /// @param <T>
    ///            the expected type of the output
    /// @param outputName
    ///            the name of the output (will be converted to getXxx())
    /// @return the output value
    /// @throws ReflectiveOperationException
    ///             if the getter call fails
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String outputName) throws ReflectiveOperationException {
        String getterName = "get" + JavaIdentifiers.sanitizeAndCapitalize(outputName);
        Method getter = rulesClass.getMethod(getterName);
        return (T) getter.invoke(instance);
    }

    /// Gets the underlying rules instance.
    ///
    /// @return the rules instance
    public Object getInstance() {
        return instance;
    }

    /// Gets the rules class.
    ///
    /// @return the rules class
    public Class<?> getRulesClass() {
        return rulesClass;
    }

}
