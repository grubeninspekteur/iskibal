package work.spell.iskibal.compiler.drools.api;

import module java.base;

import module iskibal.rule.model;

/// Compiler interface for generating Drools Rule Language (DRL) from a
/// [RuleModule].
///
/// Implementations are loaded via [ServiceLoader]. Use [load()] to obtain a
/// compiler instance.
///
/// The compiler produces two artefacts:
/// - A `.drl` file containing all rules translated to Drools syntax
/// - A companion Java POJO (`<Name>Outputs.java`) holding the output fields that
///   are written by rule actions
public interface DroolsCompiler {

    /// Compiles the given rule module to Drools DRL source.
    ///
    /// @param module
    ///            the rule module to compile
    /// @param options
    ///            compilation options
    /// @return the compilation result containing generated files
    DroolsCompilationResult compile(RuleModule module, DroolsCompilerOptions options);

    /// Compiles the given rule module using default options.
    ///
    /// @param module
    ///            the rule module to compile
    /// @return the compilation result
    default DroolsCompilationResult compile(RuleModule module) {
        return compile(module, DroolsCompilerOptions.defaults());
    }

    /// Loads a Drools compiler using the ServiceLoader mechanism.
    ///
    /// @return a Drools compiler instance
    /// @throws IllegalStateException
    ///             if no compiler implementation is found
    static DroolsCompiler load() {
        return ServiceLoader.load(DroolsCompiler.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No DroolsCompiler implementation found. Ensure iskibal-compiler-drools is on the module path."));
    }
}
