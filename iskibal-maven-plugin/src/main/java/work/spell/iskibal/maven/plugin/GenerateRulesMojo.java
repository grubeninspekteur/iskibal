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
import work.spell.iskibal.compiler.drools.api.DroolsCompilationResult;
import work.spell.iskibal.compiler.drools.api.DroolsCompiler;
import work.spell.iskibal.compiler.drools.api.DroolsCompilerOptions;
import work.spell.iskibal.compiler.java.api.CompilationResult;
import work.spell.iskibal.compiler.java.api.JavaCompiler;
import work.spell.iskibal.compiler.java.api.JavaCompilerOptions;
import work.spell.iskibal.model.RuleModule;
import work.spell.iskibal.parser.api.ParseOptions;
import work.spell.iskibal.parser.api.ParseResult;
import work.spell.iskibal.parser.api.Parser;

/// Maven plugin goal that compiles rule sources to Java or Drools DRL output.
///
/// Rule sources are `.iskara` files (pure Iskara) or `.adoc` files (AsciiDoc
/// with embedded Iskara). The target language is chosen with the `language`
/// parameter:
///
/// - `java` (default): generates Java source, registered as a compile source
///   root.
/// - `drools`: generates Drools Rule Language (`.drl`) files written to the
///   output directory. A companion `<Name>Outputs.java` POJO is also written.
///   The output directory is **not** added as a compile source root; wire it
///   into the Drools runtime separately.
///
/// Languages cannot be mixed in a single plugin execution — configure separate
/// executions with different `sourceDirectory` and `language` values if needed.
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false)
public class GenerateRulesMojo extends AbstractMojo {

    /// Directory to scan for rule source files.
    @Parameter(defaultValue = "${project.basedir}/src/main/iskibal", property = "iskibal.sourceDirectory")
    private File sourceDirectory;

    /// Directory where generated source files are written.
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/iskibal",
            property = "iskibal.outputDirectory")
    private File outputDirectory;

    /// Target language for code generation. Accepted values (case-insensitive):
    /// - `java` (default) – generates Java source code
    /// - `drools` – generates Drools Rule Language (DRL) files
    ///
    /// Languages cannot be mixed in a single execution. Configure separate
    /// `<execution>` blocks with different source directories when both targets
    /// are needed.
    @Parameter(defaultValue = "java", property = "iskibal.language")
    private String language;

    /// Java package name for generated classes (Java target) or DRL package
    /// declaration (Drools target). Defaults to the unnamed package.
    @Parameter(defaultValue = "", property = "iskibal.packageName")
    private String packageName;

    /// Whether to generate null safety checks for navigation expressions.
    /// Only applies to the Java target.
    @Parameter(defaultValue = "true", property = "iskibal.generateNullChecks")
    private boolean generateNullChecks;

    /// Whether to generate a `RuleListener` field and inject listener call sites
    /// so rule firing can be observed at runtime.
    /// Only applies to the Java target.
    @Parameter(defaultValue = "false", property = "iskibal.diagnostics")
    private boolean diagnostics;

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

        TargetLanguage target = resolveLanguage();
        getLog().info("Iskibal target language: " + target.name().toLowerCase());

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

        try (AsciiDocParser adocParser = new AsciiDocParser()) {
            for (Path sourceFile : sourceFiles) {
                processFile(sourceFile, parser, analyzer, adocParser, target);
            }
        }

        if (target == TargetLanguage.JAVA) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            getLog().info("Added compile source root: " + outputDirectory);
        }
    }

    private TargetLanguage resolveLanguage() throws MojoExecutionException {
        return switch (language.trim().toLowerCase()) {
            case "java" -> TargetLanguage.JAVA;
            case "drools" -> TargetLanguage.DROOLS;
            default -> throw new MojoExecutionException(
                    "Unknown language '" + language + "'. Supported values: java, drools");
        };
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

    private void processFile(Path sourceFile, Parser parser, SemanticAnalyzer analyzer, AsciiDocParser adocParser,
            TargetLanguage target) throws MojoExecutionException, MojoFailureException {
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
        String baseName = deriveClassName(fileName);
        Map<String, String> generatedFiles = switch (target) {
            case JAVA -> generateJava(module, baseName);
            case DROOLS -> generateDrools(module, baseName, fileName);
        };

        // Write generated files
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

    private Map<String, String> generateJava(RuleModule module, String className) throws MojoFailureException {
        JavaCompiler compiler = JavaCompiler.load();
        JavaCompilerOptions options = new JavaCompilerOptions(packageName, className, generateNullChecks, null,
                diagnostics);
        CompilationResult result = compiler.compile(module, options);

        if (!result.isSuccess()) {
            String errors = String.join("\n  ", result.getErrors());
            throw new MojoFailureException("Code generation errors for Java target:\n  " + errors);
        }

        return result.getSourceFiles().orElseThrow();
    }

    private Map<String, String> generateDrools(RuleModule module, String ruleName, String fileName)
            throws MojoFailureException {
        DroolsCompiler compiler = DroolsCompiler.load();
        // Convert PascalCase class name back to snake_case for DRL rule name convention
        String drlRuleName = toSnakeCase(ruleName);
        DroolsCompilerOptions options = DroolsCompilerOptions.of(packageName, drlRuleName);
        DroolsCompilationResult result = compiler.compile(module, options);

        if (!result.isSuccess()) {
            String errors = String.join("\n  ", result.getErrors());
            throw new MojoFailureException("Code generation errors for Drools target from " + fileName + ":\n  "
                    + errors);
        }

        return result.getSourceFiles().orElseThrow();
    }

    private static String formatErrors(List<?> diagnostics) {
        return diagnostics.stream().map(Object::toString).collect(Collectors.joining("\n  "));
    }

    /// Converts a PascalCase name to snake_case for use as a DRL rule file name.
    static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
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

    enum TargetLanguage {
        JAVA, DROOLS
    }
}
