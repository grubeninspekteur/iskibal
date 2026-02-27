package work.spell.iskibal.compiler.java.api;

import module java.base;

import module iskibal.rule.model;

/// Compiler interface for generating Java source code from a [RuleModule].
///
/// Implementations are loaded via [ServiceLoader]. Use [load()] to
/// obtain a compiler instance.
public interface JavaCompiler {

    /// Compiles the given rule module to Java source code.
    ///
    /// @param module
    ///            the rule module to compile
    /// @param options
    ///            compilation options
    /// @return the compilation result containing generated source files
    CompilationResult compile(RuleModule module, JavaCompilerOptions options);

    /// Compiles the given rule module using default options.
    ///
    /// @param module
    ///            the rule module to compile
    /// @return the compilation result
    default CompilationResult compile(RuleModule module) {
        return compile(module, JavaCompilerOptions.defaults());
    }

    /// Loads a Java compiler using the ServiceLoader mechanism.
    ///
    /// @return a Java compiler instance
    /// @throws IllegalStateException
    ///             if no compiler implementation is found
    static JavaCompiler load() {
        return ServiceLoader.load(JavaCompiler.class).findFirst().orElseThrow(() -> new IllegalStateException(
                "No JavaCompiler implementation found. Ensure iskibal-compiler-java is on the module path."));
    }
}
