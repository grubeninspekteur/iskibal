package work.spell.iskibal.compiler.java.internal;

import java.util.List;
import java.util.Map;

import work.spell.iskibal.compiler.common.api.AnalysisResult;
import work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
import work.spell.iskibal.compiler.common.api.SemanticDiagnostic;
import work.spell.iskibal.compiler.java.api.CompilationResult;
import work.spell.iskibal.compiler.java.api.JavaCompiler;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.compiler.java.internal.codegen.ModuleGenerator;
import work.spell.iskibal.model.RuleModule;

/// Implementation of the Java compiler that generates Java source code from a
/// RuleModule.
public final class JavaCompilerImpl implements JavaCompiler {

    private final SemanticAnalyzer analyzer;

    /// Creates a compiler with the default semantic analyzer loaded via
    /// ServiceLoader.
    public JavaCompilerImpl() {
        this(SemanticAnalyzer.load());
    }

    /// Creates a compiler with a specific semantic analyzer (for testing).
    public JavaCompilerImpl(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public CompilationResult compile(RuleModule module, JavaCompilerOptions options) {
        // First, run semantic analysis
        AnalysisResult analysisResult = analyzer.analyze(module);

        if (!analysisResult.isSuccess()) {
            // Convert semantic errors to compilation errors
            List<String> errors = analysisResult.getErrors().stream().map(SemanticDiagnostic::toString).toList();
            return new CompilationResult.Failure(errors);
        }

        // Generate Java source code
        ModuleGenerator generator = new ModuleGenerator(options);
        String sourceCode = generator.generate(module);

        // Return the generated source
        String filePath = options.filePath();
        return new CompilationResult.Success(Map.of(filePath, sourceCode));
    }
}
