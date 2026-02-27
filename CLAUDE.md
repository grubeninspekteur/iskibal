# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project (uses Maven wrapper with Maven 4.x)
./mvnw clean compile

# Build JAR artifacts
./mvnw clean package

# Install to local repository
./mvnw clean install
```

Use `-pl` to build specific projects, together with `-am` to also make dependents.
Since the project builds fast enough, a `mvn test -fae` without specific projects should be enough in most cases. Reduce output with `-q` (quiet) and other optimizations so only relevant tokens are consumed.

On Windows, patch the command through PowerShell.

## Project Overview

Iskibal is a business rules compiler with a 3-tier architecture:
1. **Rule Source Parser** - Parses rule sources (AsciiDoc with embedded Iskara language)
2. **Rule Model** - A tree of Java objects representing the rule structure
3. **Rule Compiler** - Transforms rule model to target language (Java, Drools, Rulebook, etc.)

Components are loaded using Java's `ServiceLoader` mechanism for pluggable implementations.

The library is currently in the early development phase and not used by anyone. Breaking changes can be made.

## Architecture

### Multi-Module Maven Structure

- **Parent POM** (`pom.xml`): Defines shared compiler config (Java 25) and plugin management
- **iskibal-rule-model**: Core module representing parsed rule structures as Java objects

### Rule Model Design

The rule model uses sealed types and records for type-safe, immutable representations:

- **RuleModule**: Root element containing imports, facts, globals, outputs, dataTables, and rules
- **Rule**: Sealed interface with variants `SimpleRule`, `TemplateRule`, `DecisionTableRule`
- **Expression**: Sealed interface for executable parts (`Identifier`, `Literal`, `MessageSend`, `Binary`, `Assignment`, `Navigation`, `Block`)
- **Statement**: Building blocks in rule sections (`ExpressionStatement`, `LetStatement`)

Supporting types: `Fact`, `Global`, `Output`, `Import`, `DataTable` - all sealed interfaces with `Definition`/`Default` record implementations.

### Key Design Decisions

- **Module imports (JEP 511)**: Use `import module <name>;` for all module dependencies — JDK modules (`java.base`, `java.compiler`) and project modules (`iskibal.rule.model`, `iskibal.parser`, etc.). Keep explicit imports only for: nested types (e.g. `Expression.Assignment`), same-module types (can't `import module` yourself), and disambiguation when two modules export the same simple name (e.g. `import javax.tools.ToolProvider;` alongside `import module java.compiler;` to resolve the clash with `java.util.spi.ToolProvider` from `java.base`). Third-party library imports (asciidoctor, antlr, junit, assertj) stay as explicit imports since those libraries are unnamed modules.
- **BigDecimal for Numbers**: Uses `java.math.BigDecimal` for precision in numeric expressions
- **Smalltalk-style Messaging**: `MessageSend` expression supports keyword-based method calls
- **Java Module System**: Uses JPMS with explicit exports (`module iskibal.rule.model`)

## Documentation Comments

Use JEP 467 Markdown documentation comments (`///`) instead of traditional Javadoc (`/** */`):

```java
/// Short description.
///
/// Longer description with **Markdown** formatting.
///
/// @param foo the foo parameter
/// @return the result
```

- Reference Java elements with `[ClassName]` or `[ClassName#method()]`
- Use backtick code spans instead of `{@code ...}`
- Use fenced code blocks (` ``` `) instead of `<pre>`
- Use Markdown lists (`-`) instead of `<ul>`/`<li>`

## Code Formatting

The project uses Spotless with Eclipse JDT formatter. Code is automatically formatted on commit via a git hook.

```bash
# Setup git hooks (run once after cloning)
git config core.hooksPath hooks

# Manually format code
./mvnw spotless:apply

# Check formatting without applying
./mvnw spotless:check
```

## Language Specifications

Documentation for the Iskara rule language is in `documentation/examples/specification/`:
- `Iskara_Language_Reference.adoc` - Core language syntax
- `AsciiDoc_plus_Iskara.adoc` - Embedding rules in AsciiDoc documents

## Agent Plans

Larger changes (especially architectural ones) should be drafted as plans. These can be stored in `.agent-plans`. Move completed plans to `.agent-plans/archive`. If the user tells you to "resume plan", resume the plan in `.agent-plans`.

If plans get really large, split them into subplans.