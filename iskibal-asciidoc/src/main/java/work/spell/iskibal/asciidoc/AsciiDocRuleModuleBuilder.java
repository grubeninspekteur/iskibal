package work.spell.iskibal.asciidoc;

import module java.base;

import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;

import module iskibal.parser;
import module iskibal.rule.model;
import work.spell.iskibal.model.Expression.Block;
import work.spell.iskibal.model.Rule.DecisionTableRule;

/// Builds a [RuleModule] from an AsciidoctorJ [Document].
///
/// Traverses the document tree and extracts rule components based on block
/// roles:
/// - `.rule` - Rule source blocks
/// - `.imports` - Import definition lists
/// - `.facts` - Fact tables
/// - `.globals` - Global tables
/// - `.outputs` - Output tables
/// - `.data-table` - Data tables
/// - `.decision-table` - Decision tables
public class AsciiDocRuleModuleBuilder {

    private static final Pattern CAPTION_PATTERN = Pattern.compile("^([A-Z0-9_-]+):\\s*(.*)$");

    private final ExpressionParser expressionParser;
    private final TableParser tableParser;
    private final DecisionTableBuilder decisionTableBuilder;
    private final AliasResolver aliasResolver;
    private final Parser iskaraParser;
    private final Locale locale;
    private final java.util.List<Diagnostic> diagnostics = new ArrayList<>();

    /// Creates an AsciiDocRuleModuleBuilder with the default locale.
    public AsciiDocRuleModuleBuilder() {
        this(Locale.getDefault());
    }

    /// Creates an AsciiDocRuleModuleBuilder with the specified locale.
    ///
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocRuleModuleBuilder(Locale locale) {
        this(Parser.load(), locale);
    }

    /// Creates an AsciiDocRuleModuleBuilder with the specified parser and
    /// locale.
    ///
    /// @param parser
    ///            the parser to use
    /// @param locale
    ///            the locale for number parsing
    public AsciiDocRuleModuleBuilder(Parser parser, Locale locale) {
        this.locale = locale;
        this.iskaraParser = parser;
        this.expressionParser = new ExpressionParser(parser, locale);
        this.tableParser = new TableParser(expressionParser);
        this.decisionTableBuilder = new DecisionTableBuilder(expressionParser);
        this.aliasResolver = new AliasResolver(expressionParser);
    }

    /// Result of building a [RuleModule] from an AsciiDoc document.
    ///
    /// @param module
    ///            the built RuleModule
    /// @param diagnostics
    ///            any warnings or errors encountered during building
    public record BuildResult(RuleModule module, java.util.List<Diagnostic> diagnostics) {
        /// Returns true if there are any errors.
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    /// Builds a RuleModule from an AsciiDoc document.
    ///
    /// @param document
    ///            the parsed AsciiDoc document
    /// @return the build result containing the module and any diagnostics
    public BuildResult build(Document document) {
        diagnostics.clear();
        java.util.List<Import> imports = new ArrayList<>();
        java.util.List<Fact> facts = new ArrayList<>();
        java.util.List<Global> globals = new ArrayList<>();
        java.util.List<Output> outputs = new ArrayList<>();
        java.util.List<DataTable> dataTables = new ArrayList<>();
        java.util.List<Rule> rules = new ArrayList<>();

        processBlocks(document, document, imports, facts, globals, outputs, dataTables, rules);

        // Resolve import aliases in fact/global/output types
        Map<String, String> aliasMap = new HashMap<>();
        for (Import imp : imports) {
            aliasMap.put(imp.alias(), imp.type());
        }
        facts = facts.stream()
                .map(f -> new Fact.Definition(f.name(), resolveType(f.type(), aliasMap), f.description()))
                .collect(java.util.stream.Collectors.toList());
        globals = globals.stream()
                .map(g -> new Global.Definition(g.name(), resolveType(g.type(), aliasMap), g.description()))
                .collect(java.util.stream.Collectors.toList());
        outputs = outputs.stream()
                .map(o -> new Output.Definition(o.name(), resolveType(o.type(), aliasMap), o.initialValue(),
                        o.description()))
                .collect(java.util.stream.Collectors.toList());

        RuleModule module = new RuleModule.Default(imports, facts, globals, outputs, dataTables, rules);
        return new BuildResult(module, java.util.List.copyOf(diagnostics));
    }

    /// Known roles that trigger special processing.
    private static final Set<String> KNOWN_ROLES = Set.of("rule", "imports", "facts", "globals", "outputs",
            "data-table", "decision-table", "aliases");

    /// Recursively processes blocks in the document.
    private void processBlocks(Document document, StructuralNode node,
            java.util.List<Import> imports, java.util.List<Fact> facts, java.util.List<Global> globals,
            java.util.List<Output> outputs, java.util.List<DataTable> dataTables, java.util.List<Rule> rules) {

        for (StructuralNode child : node.getBlocks()) {
            String role = findKnownRole(child);

            if (role != null) {
                switch (role) {
                    case "rule" -> {
                        Rule rule = parseRuleBlock(child);
                        if (rule != null) {
                            rules.add(rule);
                        }
                    }
                    case "imports" -> imports.addAll(parseImports(child));
                    case "facts" -> {
                        if (child instanceof Table table) {
                            facts.addAll(tableParser.parseFacts(table));
                        }
                    }
                    case "globals" -> {
                        if (child instanceof Table table) {
                            globals.addAll(tableParser.parseGlobals(table));
                        }
                    }
                    case "outputs" -> {
                        if (child instanceof Table table) {
                            outputs.addAll(tableParser.parseOutputs(table));
                        }
                    }
                    case "data-table" -> {
                        if (child instanceof Table table) {
                            String id = child.getId();
                            dataTables.add(tableParser.parseDataTable(table, id));
                        }
                    }
                    case "decision-table" -> {
                        if (child instanceof Table table) {
                            DecisionTableRule dtRule = parseDecisionTable(document, table);
                            if (dtRule != null) {
                                rules.add(dtRule);
                            }
                        }
                    }
                    case "aliases" -> {
                        // Aliases are processed when building decision tables
                    }
                    default -> {
                        // Recurse into other blocks
                        processBlocks(document, child, imports, facts, globals, outputs, dataTables, rules);
                    }
                }
            } else {
                // No role, check if it's a source block with iskara language
                if (isIskaraSourceBlock(child)) {
                    Rule rule = parseRuleBlock(child);
                    if (rule != null) {
                        rules.add(rule);
                    }
                } else {
                    // Recurse into blocks without roles
                    processBlocks(document, child, imports, facts, globals, outputs, dataTables, rules);
                }
            }
        }
    }

    /// Finds the first known Iskara role from a block's roles list.
    ///
    /// Searches all roles rather than just the first one, since the role
    /// position in AsciiDoc attribute lists can vary (e.g., `[.facts]` vs
    /// `[cols="1,1,3",.facts]`).
    private String findKnownRole(StructuralNode block) {
        java.util.List<String> roles = block.getRoles();
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        for (String role : roles) {
            if (KNOWN_ROLES.contains(role)) {
                return role;
            }
        }
        return null;
    }

    /// Checks if a block is an Iskara source block.
    private boolean isIskaraSourceBlock(StructuralNode block) {
        if (!(block instanceof org.asciidoctor.ast.Block listing)) {
            return false;
        }
        String style = listing.getStyle();
        Object language = listing.getAttribute("language");
        return "source".equals(style) && "iskara".equals(language);
    }

    /// Parses a rule from a source block.
    private Rule parseRuleBlock(StructuralNode block) {
        if (!(block instanceof org.asciidoctor.ast.Block listing)) {
            return null;
        }

        String source = listing.getSource();
        if (source == null || source.isBlank()) {
            return null;
        }

        // Extract rule ID and description from caption
        String caption = listing.getCaption();
        String title = listing.getTitle();
        String id = null;
        String description = null;

        // Try to parse caption in format "RULE-001: Description"
        String captionText = title != null ? title : caption;
        if (captionText != null) {
            Matcher m = CAPTION_PATTERN.matcher(captionText.trim());
            if (m.matches()) {
                id = m.group(1);
                description = m.group(2);
            }
        }

        // Parse the Iskara source
        String fullSource = buildRuleSource(id, description, source);
        ParseResult<RuleModule> result = iskaraParser.parse(fullSource, parseOptions());

        if (result.isSuccess() && !result.getValue().get().rules().isEmpty()) {
            return result.getValue().get().rules().getFirst();
        }

        // Collect parse errors as diagnostics
        String ruleLabel = id != null ? id : source.lines().findFirst().orElse("<unknown>");
        if (!result.isSuccess()) {
            for (Diagnostic d : result.getDiagnostics()) {
                diagnostics.add(Diagnostic.error(
                        "Rule '" + ruleLabel + "': " + d.message(), d.location()));
            }
        } else {
            diagnostics.add(Diagnostic.error(
                    "Rule '" + ruleLabel + "': parsed successfully but produced no rules",
                    SourceLocation.at("<asciidoc>", 0, 0)));
        }
        return null;
    }

    /// Builds the full Iskara source for a rule.
    private String buildRuleSource(String id, String description, String source) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("rule ").append(quoteIdIfNeeded(id));
            if (description != null && !description.isBlank()) {
                sb.append(" \"").append(description.replace("\"", "\\\"")).append("\"");
            }
            sb.append("\n");
        }
        sb.append(source);
        // Ensure rule ends with 'end' if not present
        if (!source.trim().endsWith("end")) {
            sb.append("\nend");
        }
        return sb.toString();
    }

    /// Quotes an identifier if it contains non-identifier characters.
    /// Uses backticks for quoted identifiers: `RULE-001`
    private String quoteIdIfNeeded(String id) {
        // Check if ID is a valid unquoted identifier (starts with letter/underscore,
        // contains only letters, digits, underscores)
        if (id.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return id;
        }
        // Quote with backticks
        return "`" + id + "`";
    }

    /// Parses imports from a definition list or example block containing one.
    private java.util.List<Import> parseImports(StructuralNode block) {
        java.util.List<Import> imports = new ArrayList<>();

        if (block instanceof DescriptionList descList) {
            parseDescriptionListImports(descList, imports);
        } else {
            // Check children for description lists (e.g., example block containing dlist)
            for (StructuralNode child : block.getBlocks()) {
                if (child instanceof DescriptionList descList) {
                    parseDescriptionListImports(descList, imports);
                }
            }

            // Fallback: try to parse from block content
            if (imports.isEmpty()) {
                String content = getBlockContent(block);
                if (content != null) {
                    for (String line : content.split("\n")) {
                        if (line.contains("::")) {
                            String[] parts = line.split("::", 2);
                            if (parts.length == 2) {
                                String alias = parts[0].trim();
                                String type = parts[1].trim();
                                if (!alias.isBlank() && !type.isBlank()) {
                                    imports.add(new Import.Definition(alias, type));
                                }
                            }
                        }
                    }
                }
            }
        }

        return imports;
    }

    private void parseDescriptionListImports(DescriptionList descList, java.util.List<Import> imports) {
        for (DescriptionListEntry entry : descList.getItems()) {
            String alias = extractTermText(entry);
            String type = extractDescriptionText(entry);
            if (alias != null && type != null && !alias.isBlank() && !type.isBlank()) {
                imports.add(new Import.Definition(alias.trim(), type.trim()));
            }
        }
    }

    /// Parses a decision table.
    private DecisionTableRule parseDecisionTable(Document document, Table table) {
        String id = table.getId();
        String title = table.getTitle();
        String caption = table.getCaption();
        String description = title != null ? title : caption;

        // Resolve aliases for this table
        Map<String, Block> aliases = aliasResolver.resolveAliases(document, id);

        return decisionTableBuilder.build(table, id, description, aliases);
    }

    private String extractTermText(DescriptionListEntry entry) {
        java.util.List<ListItem> terms = entry.getTerms();
        if (terms == null || terms.isEmpty()) {
            return null;
        }
        return terms.getFirst().getText();
    }

    private String extractDescriptionText(DescriptionListEntry entry) {
        ListItem description = entry.getDescription();
        if (description == null) {
            return null;
        }
        return description.getText();
    }

    private String getBlockContent(StructuralNode block) {
        if (block instanceof org.asciidoctor.ast.Block listing) {
            String source = listing.getSource();
            if (source != null) {
                return source;
            }
            // Fallback to content
            Object content = listing.getContent();
            return content != null ? content.toString() : null;
        }
        return null;
    }

    /// Resolves a type name against import aliases. Handles collection type
    /// suffixes (`[]` for lists, `{}` for sets).
    private String resolveType(String type, Map<String, String> aliases) {
        if (type.endsWith("[]")) {
            String baseType = type.substring(0, type.length() - 2);
            return aliases.getOrDefault(baseType, baseType) + "[]";
        }
        if (type.endsWith("{}")) {
            String baseType = type.substring(0, type.length() - 2);
            return aliases.getOrDefault(baseType, baseType) + "{}";
        }
        return aliases.getOrDefault(type, type);
    }

    private ParseOptions parseOptions() {
        return new ParseOptions(locale, SourceType.ISKARA, "<asciidoc>");
    }
}
