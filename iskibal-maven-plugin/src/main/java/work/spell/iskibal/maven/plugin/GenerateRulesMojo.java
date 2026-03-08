package work.spell.iskibal.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import work.spell.iskibal.asciidoc.AsciiDocParser;
import work.spell.iskibal.compiler.common.api.AnalysisResult;
import work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
import work.spell.iskibal.compiler.java.api.CompilationResult;
import work.spell.iskibal.compiler.java.api.JavaCompiler;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;

/// Maven plugin goal that compiles Iskara rule sources to Java source files.
///
/// Rule sources are `.iskara` files (pure Iskara) or `.adoc` files (AsciiDoc
/// with embedded Iskara). The generated Java source is written to the output
/// directory and registered as a compile source root so Maven picks it up
/// automatically.
///
/// Each source file produces one Java class whose name is derived from the
/// filename in PascalCase (e.g. `discount_rules.iskara` → `DiscountRules`).
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false)
public class GenerateRulesMojo extends AbstractMojo {

    /// Directory to scan for rule source files.
    @Parameter(defaultValue = "${project.basedir}/src/main/iskibal", property = "iskibal.sourceDirectory")
    private File sourceDirectory;

    /// Directory where generated Java source files are written.
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/iskibal",
            property = "iskibal.outputDirectory")
    private File outputDirectory;

    /// Java package name for generated classes. Defaults to the unnamed package.
    @Parameter(defaultValue = "", property = "iskibal.packageName")
    private String packageName;

    /// Whether to generate null safety checks for navigation expressions.
    @Parameter(defaultValue = "true", property = "iskibal.generateNullChecks")
    private boolean generateNullChecks;

    /// Whether to skip execution of this goal.
    @Parameter(defaultValue = "false", property = "iskibal.skip")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Iskibal code generation skipped.");
            return;
        }

        if (!sourceDirectory.exists()) {
            getLog().debug("Iskibal source directory does not exist, skipping: " + sourceDirectory);
            return;
        }

        List<Path> sourceFiles = collectSourceFiles();

        if (sourceFiles.isEmpty()) {
            getLog().info("No .iskara or .adoc files found in " + sourceDirectory);
            return;
        }

        outputDirectory.mkdirs();

        Parser parser = Parser.load();
        SemanticAnalyzer analyzer = SemanticAnalyzer.load();
        JavaCompiler compiler = JavaCompiler.load();

        try (AsciiDocParser adocParser = new AsciiDocParser()) {
            for (Path sourceFile : sourceFiles) {
                processFile(sourceFile, parser, analyzer, compiler, adocParser);
            }
        }

        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("Added compile source root: " + outputDirectory);
    }

    private List<Path> collectSourceFiles() throws MojoExecutionException {
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            return paths.filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".iskara") || name.endsWith(".adoc");
            }).toList();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan source directory: " + sourceDirectory, e);
        }
    }

    private void processFile(Path sourceFile, Parser parser, SemanticAnalyzer analyzer, JavaCompiler compiler,
            AsciiDocParser adocParser) throws MojoExecutionException, MojoFailureException {
        String fileName = sourceFile.getFileName().toString();
        boolean isAsciiDoc = fileName.endsWith(".adoc");
        getLog().info("Compiling rule source: " + sourceFile);

        // Parse
        RuleModule module;
        if (isAsciiDoc) {
            AsciiDocParser.ParseResult adocResult = adocParser.parseFile(sourceFile);
            if (!adocResult.isSuccess()) {
                throw new MojoFailureException("Parse errors in " + fileName + ":\n  "
                        + formatErrors(adocResult.diagnostics()));
            }
            module = adocResult.module();
        } else {
            String content;
            try {
                content = Files.readString(sourceFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read file: " + sourceFile, e);
            }
            ParseResult<RuleModule> parseResult = parser.parse(content, ParseOptions.iskara(fileName));
            if (!parseResult.isSuccess()) {
                throw new MojoFailureException("Parse errors in " + fileName + ":\n  "
                        + formatErrors(parseResult.getDiagnostics()));
            }
            module = parseResult.getValue().orElseThrow();
        }

        // Semantic analysis
        AnalysisResult analysisResult = analyzer.analyze(module);
        if (!analysisResult.isSuccess()) {
            throw new MojoFailureException("Semantic errors in " + fileName + ":\n  "
                    + formatErrors(analysisResult.getDiagnostics()));
        }

        // Code generation
        String className = deriveClassName(fileName);
        JavaCompilerOptions options = new JavaCompilerOptions(packageName, className, generateNullChecks, null);
        CompilationResult codegenResult = compiler.compile(module, options);

        if (!codegenResult.isSuccess()) {
            String errors = String.join("\n  ", codegenResult.getErrors());
            throw new MojoFailureException("Code generation errors for " + fileName + ":\n  " + errors);
        }

        // Write generated files
        Map<String, String> generatedFiles = codegenResult.getSourceFiles().orElseThrow();
        for (Map.Entry<String, String> entry : generatedFiles.entrySet()) {
            File outputFile = new File(outputDirectory, entry.getKey());
            outputFile.getParentFile().mkdirs();
            try {
                Files.writeString(outputFile.toPath(), entry.getValue());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write generated file: " + outputFile, e);
            }
            getLog().info("Generated: " + outputFile);
        }
    }

    private static String formatErrors(List<?> diagnostics) {
        return diagnostics.stream().map(Object::toString).collect(Collectors.joining("\n  "));
    }

    /// Derives a PascalCase Java class name from a source filename.
    ///
    /// Examples:
    /// - `discount_rules.iskara` → `DiscountRules`
    /// - `order-rules.adoc` → `OrderRules`
    /// - `pricing.iskara` → `Pricing`
    static String deriveClassName(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        String base = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        String[] parts = base.split("[_\\-\\.]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.isEmpty() ? "GeneratedRules" : sb.toString();
    }
}
