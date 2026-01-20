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

## Project Overview

Iskibal is a business rules compiler with a 3-tier architecture:
1. **Rule Source Parser** - Parses rule sources (AsciiDoc with embedded Iskara language)
2. **Rule Model** - A tree of Java objects representing the rule structure
3. **Rule Compiler** - Transforms rule model to target language (Java, Drools, Rulebook, etc.)

Components are loaded using Java's `ServiceLoader` mechanism for pluggable implementations.

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

- **Pure Java**: The rule-model module has zero external dependencies
- **BigDecimal for Numbers**: Uses `java.math.BigDecimal` for precision in numeric expressions
- **Smalltalk-style Messaging**: `MessageSend` expression supports keyword-based method calls
- **Java Module System**: Uses JPMS with explicit exports (`module iskibal.rule.model`)

## Language Specifications

Documentation for the Iskara rule language is in `documentation/examples/specification/`:
- `Iskara_Language_Reference.adoc` - Core language syntax
- `AsciiDoc_plus_Iskara.adoc` - Embedding rules in AsciiDoc documents
