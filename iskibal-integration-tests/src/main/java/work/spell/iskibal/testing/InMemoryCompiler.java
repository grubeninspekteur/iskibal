package work.spell.iskibal.testing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Compiles Java source code in memory using {@link javax.tools.JavaCompiler}.
 * <p>
 * This allows end-to-end testing of generated code without writing to the file
 * system.
 */
public class InMemoryCompiler {

    private final JavaCompiler compiler;

    /**
     * Creates a new in-memory compiler.
     *
     * @throws IllegalStateException
     *             if the system Java compiler is not available
     */
    public InMemoryCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "No system Java compiler available. Ensure you're running on a JDK, not a JRE.");
        }
    }

    /**
     * Compiles the given Java source code.
     *
     * @param className
     *            the fully qualified class name
     * @param sourceCode
     *            the Java source code
     * @return the compilation result
     */
    public InMemoryCompilationResult compile(String className, String sourceCode) {
        return compile(className, sourceCode, List.of());
    }

    /**
     * Compiles the given Java source code with additional classes available.
     *
     * @param className
     *            the fully qualified class name
     * @param sourceCode
     *            the Java source code
     * @param additionalClasses
     *            classes to make available to the compiled code
     * @return the compilation result
     */
    public InMemoryCompilationResult compile(String className, String sourceCode, List<Class<?>> additionalClasses) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();

        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);
        configureCompilationPaths(standardFileManager);

        InMemoryFileManager fileManager = new InMemoryFileManager(standardFileManager, additionalClasses);

        JavaFileObject sourceFile = new InMemorySourceFile(className, sourceCode);

        JavaCompiler.CompilationTask task = compiler.getTask(output, fileManager, diagnostics,
                List.of("--release", "25", "--enable-preview"), null, List.of(sourceFile));

        boolean success = task.call();

        if (!success) {
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errors.add(formatDiagnostic(diagnostic));
            }
            return new InMemoryCompilationResult.Failure(errors);
        }

        try {
            Class<?> compiledClass = fileManager.getClassLoader().loadClass(className);
            return new InMemoryCompilationResult.Success(compiledClass, className);
        } catch (ClassNotFoundException e) {
            return new InMemoryCompilationResult.Failure(List.of("Failed to load compiled class: " + e.getMessage()));
        }
    }

    private String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        StringBuilder sb = new StringBuilder();
        sb.append(diagnostic.getKind()).append(": ");
        if (diagnostic.getLineNumber() > 0) {
            sb.append("line ").append(diagnostic.getLineNumber()).append(": ");
        }
        sb.append(diagnostic.getMessage(null));
        return sb.toString();
    }

    /**
     * Configures the file manager with paths from the running JVM.
     * <p>
     * When running with module path (e.g., IntelliJ with module path enabled), the
     * {@link StandardJavaFileManager} doesn't automatically inherit the JVM's
     * paths. We combine both the module path (for dependencies) and class path (for
     * test classes) to ensure the compiler can resolve all types.
     * <p>
     * The compiled code ends up in the unnamed module, which can read all exported
     * packages from named modules at runtime.
     */
    private void configureCompilationPaths(StandardJavaFileManager fileManager) {
        List<File> allPaths = new ArrayList<>();

        // Add module path entries (dependencies)
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !modulePath.isEmpty()) {
            Arrays.stream(modulePath.split(File.pathSeparator)).map(File::new).forEach(allPaths::add);
        }

        // Add class path entries (test classes, etc.)
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isEmpty()) {
            Arrays.stream(classPath.split(File.pathSeparator)).map(File::new).forEach(allPaths::add);
        }

        if (!allPaths.isEmpty()) {
            try {
                fileManager.setLocation(StandardLocation.CLASS_PATH, allPaths);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to configure compilation paths", e);
            }
        }
    }

    /**
     * JavaFileObject that holds source code in memory.
     */
    private static class InMemorySourceFile extends SimpleJavaFileObject {
        private final String content;

        InMemorySourceFile(String className, String content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    /**
     * JavaFileObject that holds compiled bytecode in memory.
     */
    private static class InMemoryClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InMemoryClassFile(String className) {
            super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * ClassLoader that loads classes from in-memory bytecode.
     */
    private static class InMemoryClassLoader extends ClassLoader {
        private final Map<String, InMemoryClassFile> classFiles;

        InMemoryClassLoader(Map<String, InMemoryClassFile> classFiles, List<Class<?>> additionalClasses) {
            super(InMemoryClassLoader.class.getClassLoader());
            this.classFiles = classFiles;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            InMemoryClassFile classFile = classFiles.get(name);
            if (classFile != null) {
                byte[] bytes = classFile.getBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    /**
     * FileManager that stores compiled classes in memory.
     */
    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClassFile> classFiles = new HashMap<>();
        private final List<Class<?>> additionalClasses;
        private InMemoryClassLoader classLoader;

        InMemoryFileManager(StandardJavaFileManager fileManager, List<Class<?>> additionalClasses) {
            super(fileManager);
            this.additionalClasses = additionalClasses;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                FileObject sibling) {
            if (kind == JavaFileObject.Kind.CLASS) {
                InMemoryClassFile classFile = new InMemoryClassFile(className);
                classFiles.put(className, classFile);
                return classFile;
            }
            throw new IllegalArgumentException("Unsupported kind: " + kind);
        }

        InMemoryClassLoader getClassLoader() {
            if (classLoader == null) {
                classLoader = new InMemoryClassLoader(classFiles, additionalClasses);
            }
            return classLoader;
        }
    }
}
