# Iskibal — Business Rules Compiler

Iskibal is a business rules compiler that transforms rules written in the **Iskara** language (optionally embedded in AsciiDoc documents) into executable code. It supports two compilation targets:

- **Java** — generates a plain Java class whose `evaluate()` method executes the rules
- **Drools** — generates Drools Rule Language (DRL) files ready for use with the Drools engine

## Architecture

Iskibal uses a three-tier pipeline:

```
Rule Source (Iskara / AsciiDoc)
        ↓
   Rule Model (RuleModule)
        ↓
  Target Language (Java / Drools DRL)
```

Each tier is a pluggable Maven module loaded via Java's `ServiceLoader`:

| Module | Description |
|---|---|
| `iskibal-rule-model` | Core rule model (sealed types and records) |
| `iskibal-parser` | ANTLR4-based Iskara parser |
| `iskibal-asciidoc` | AsciiDoc document parser (AsciidoctorJ) |
| `iskibal-compiler-common` | Semantic analysis and validation |
| `iskibal-compiler-java` | Generates Java source code |
| `iskibal-compiler-drools` | Generates Drools Rule Language (DRL) |
| `iskibal-runtime` | Runtime helpers for generated Java classes |
| `iskibal-maven-plugin` | Maven build integration |

## Quick start

### Maven plugin

Add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>work.spell.iskibal</groupId>
  <artifactId>iskibal-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
      <configuration>
        <!-- "java" (default) or "drools" -->
        <language>java</language>
        <sourceDirectory>${project.basedir}/src/main/iskibal</sourceDirectory>
        <packageName>com.example.rules</packageName>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Place `.iskara` or `.adoc` rule files under `src/main/iskibal/`. The plugin generates sources during the `generate-sources` phase.

### Plugin parameters

| Parameter | Default | Description |
|---|---|---|
| `language` | `java` | Target language: `java` or `drools` |
| `sourceDirectory` | `src/main/iskibal` | Directory scanned for rule sources |
| `outputDirectory` | `target/generated-sources/iskibal` | Where generated files are written |
| `packageName` | *(unnamed)* | Java package / DRL package declaration |
| `generateNullChecks` | `true` | Generate null-safe navigation (Java only) |
| `diagnostics` | `false` | Inject `RuleListener` call sites (Java only) |
| `skip` | `false` | Skip goal execution |

## Iskara language

Iskara is a rule language with Smalltalk-style messaging and support for AsciiDoc embedding.

### Simple rule

```iskara
rule "check-age"
when
    customer.age >= 18
then
    eligible := "yes"
else
    eligible := "no"
end
```

### Decision table

```iskara
decision table TIER_RULES
| ID     | WHEN          | THEN             |
| ------ | ------------- | ---------------- |
| BRONZE | score >= 100  | tier := "Bronze" |
| SILVER | score >= 500  | tier := "Silver" |
| GOLD   | score >= 1000 | tier := "Gold"   |
```

### Template rule

```iskara
template rule COUPON "Coupon code discounts"
data table {
    | code      | pct |
    | --------- | --- |
    | "SAVE10"  | 10  |
    | "VIP50"   | 50  |
}
when
    order.couponCode = code
then
    discountPercent := pct
end
```

See [`documentation/examples/specification/Iskara_Language_Reference.adoc`](documentation/examples/specification/Iskara_Language_Reference.adoc) for the full syntax.

## AsciiDoc embedding

Rule metadata (facts, outputs, globals, data tables) can be declared in standard AsciiDoc structures:

```asciidoc
.Facts
[.facts]
|===
| Name | Type

| order
| `com.example.Order`
|===

.PRICE_001: Calculate total
[source,iskara,.rule]
----
when
    order.total > 0
then
    eligible := "yes"
end
----
```

See [`documentation/examples/specification/AsciiDoc_plus_Iskara.adoc`](documentation/examples/specification/AsciiDoc_plus_Iskara.adoc) for the full AsciiDoc format.

## Drools target

Set `<language>drools</language>` in the plugin configuration to generate Drools DRL instead of Java.

The compiler produces:
- A `.drl` file containing all rules in Drools syntax
- A `<Name>Outputs.java` POJO that collects output values

### Injecting the outputs holder

```java
KieSession session = kieContainer.newKieSession();

PricingRulesOutputs outputs = new PricingRulesOutputs();
session.setGlobal("__outputs", outputs);
session.setGlobal("taxRate", new BigDecimal("1.19"));

session.insert(order);
session.insert(customer);
session.fireAllRules();
session.dispose();

System.out.println(outputs.getEligible());       // "yes"
System.out.println(outputs.getDiscountPercent()); // 10
```

See [`documentation/examples/specification/AsciiDoc_plus_Drools.adoc`](documentation/examples/specification/AsciiDoc_plus_Drools.adoc) for detailed instructions.

### Drools limitations

The following Iskara features are not yet supported for the Drools target (TODO):

- Salience / priority (not in the rule model)
- Agenda groups (not in the rule model)
- Block / lambda expressions in then sections
- Template string interpolation (`$"..."`)
- Collection aggregation (`sum`, `each`, `all`, `where`) — use Drools `accumulate`
- `modify`, `retract`, `insert` working-memory operations

## Building

```bash
# Compile all modules
./mvnw clean compile

# Run all tests
./mvnw test -fae

# Build JAR artifacts
./mvnw clean package

# Format code (Eclipse JDT)
./mvnw spotless:apply
```

Requires Java 25 and Maven 4.x (provided via the Maven wrapper).

## Project status

Iskibal is in early development. Breaking changes may be made without notice.
