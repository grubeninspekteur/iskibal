package work.spell.iskibal.testing;

import module java.base;
import module iskibal.compiler.common;
import module iskibal.compiler.java;
import module iskibal.rule.model;

import work.spell.iskibal.asciidoc.AsciiDocParser;

/// Fluent builder for end-to-end rule tests that start from AsciiDoc documents.
///
/// Orchestrates the full pipeline: AsciiDoc Parse → Analyze → Generate → Compile → Instantiate
public class AsciiDocRuleTestBuilder {

    private final Path file;
    private final List<Object> facts = new ArrayList<>();
    private final List<Object> globals = new ArrayList<>();
    private AsciiDocParser sharedParser;
    private String packageName = "";
    private String className = "GeneratedRules";

    private AsciiDocRuleTestBuilder(Path file) {
        this.file = file;
    }

    /// Creates a new builder for the given AsciiDoc file.
    public static AsciiDocRuleTestBuilder forFile(Path file) {
        return new AsciiDocRuleTestBuilder(file);
    }

    /// Sets a shared [AsciiDocParser] to avoid creating a new Asciidoctor instance per build.
    public AsciiDocRuleTestBuilder withParser(AsciiDocParser parser) {
        this.sharedParser = parser;
        return this;
    }

    public AsciiDocRuleTestBuilder withPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public AsciiDocRuleTestBuilder withClassName(String className) {
        this.className = className;
        return this;
    }

    public AsciiDocRuleTestBuilder withFact(Object fact) {
        this.facts.add(fact);
        return this;
    }

    public AsciiDocRuleTestBuilder withGlobal(Object global) {
        this.globals.add(global);
        return this;
    }

    /// Builds and compiles the rules from the AsciiDoc file, returning the result.
    public RuleTestResult build() {
        // Stage 1: Parse AsciiDoc
        RuleModule module;
        AsciiDocParser parser = sharedParser;
        boolean ownsParser = parser == null;
        if (ownsParser) {
            parser = new AsciiDocParser(Locale.US);
        }
        try {
            AsciiDocParser.ParseResult parseResult = parser.parseFile(file);
            if (!parseResult.isSuccess()) {
                List<String> errors = parseResult.diagnostics().stream().map(Object::toString).toList();
                return new RuleTestResult.ParseFailure(errors);
            }
            module = parseResult.module();
        } finally {
            if (ownsParser) {
                parser.close();
            }
        }

        // Stage 2: Semantic analysis
        SemanticAnalyzer analyzer = new SemanticAnalyzerImpl();
        AnalysisResult analysisResult = analyzer.analyze(module);

        if (!analysisResult.isSuccess()) {
            List<String> errors = analysisResult.getDiagnostics().stream().map(Object::toString).toList();
            return new RuleTestResult.AnalysisFailure(errors);
        }

        // Stage 3: Generate Java code
        JavaCompiler javaCompiler = new JavaCompilerImpl();
        JavaCompilerOptions options = JavaCompilerOptions.withTypeInference(packageName, className,
                Thread.currentThread().getContextClassLoader());
        CompilationResult codegenResult = javaCompiler.compile(module, options);

        if (!codegenResult.isSuccess()) {
            return new RuleTestResult.CodegenFailure(codegenResult.getErrors());
        }

        Map<String, String> sourceFiles = codegenResult.getSourceFiles().orElseThrow();
        String generatedSource = sourceFiles.values().iterator().next();
        String fullyQualifiedName = options.fullyQualifiedClassName();

        // Stage 4: Compile Java to bytecode
        InMemoryCompiler memCompiler = new InMemoryCompiler();
        InMemoryCompilationResult compileResult = memCompiler.compile(fullyQualifiedName, generatedSource);

        if (!compileResult.isSuccess()) {
            return new RuleTestResult.CompilationFailure(compileResult.getDiagnostics(), generatedSource);
        }

        // Stage 5: Instantiate
        try {
            Class<?> compiledClass = compileResult.getCompiledClass().orElseThrow();
            List<Object> args = new ArrayList<>();
            args.addAll(facts);
            args.addAll(globals);
            CompiledRules rules = CompiledRules.instantiate(compiledClass, args.toArray());
            return new RuleTestResult.Success(rules, generatedSource);
        } catch (ReflectiveOperationException e) {
            return new RuleTestResult.CompilationFailure(List.of("Failed to instantiate rules: " + e.getMessage()),
                    generatedSource);
        }
    }
}
