package work.spell.iskibal.compiler.drools.internal;

import module java.base;

import module iskibal.compiler.common;
import module iskibal.rule.model;
import work.spell.iskibal.compiler.drools.api.DroolsCompilationResult;
import work.spell.iskibal.compiler.drools.api.DroolsCompiler;
import work.spell.iskibal.compiler.drools.api.DroolsCompilerOptions;
import work.spell.iskibal.compiler.drools.internal.drl.DrlGenerator;

/// Implementation of [DroolsCompiler] that generates Drools Rule Language (DRL)
/// source files from a [RuleModule].
public final class DroolsCompilerImpl implements DroolsCompiler {

    private final SemanticAnalyzer analyzer;

    /// Creates a compiler with the default semantic analyzer loaded via
    /// ServiceLoader.
    public DroolsCompilerImpl() {
        this(SemanticAnalyzer.load());
    }

    /// Creates a compiler with a specific semantic analyzer (for testing).
    public DroolsCompilerImpl(SemanticAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public DroolsCompilationResult compile(RuleModule module, DroolsCompilerOptions options) {
        // Semantic analysis
        AnalysisResult analysisResult = analyzer.analyze(module);

        if (!analysisResult.isSuccess()) {
            List<String> errors = analysisResult.getErrors().stream().map(SemanticDiagnostic::toString).toList();
            return new DroolsCompilationResult.Failure(errors);
        }

        // DRL generation
        DrlGenerator generator = new DrlGenerator(options);
        Map<String, String> sourceFiles = generator.generate(module);

        return new DroolsCompilationResult.Success(sourceFiles);
    }
}
