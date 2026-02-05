package work.spell.iskibal.asciidoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.StructuralNode;

import work.spell.iskibal.model.Expression;

/**
 * Resolves alias definitions for decision tables.
 * <p>
 * Aliases are defined in definition lists with the role {@code .aliases} and a
 * {@code for} attribute pointing to the target decision table ID:
 *
 * <pre>
 * [.aliases,for="TABLE_ID"]
 * alias1::
 * +
 * [source,iskara]
 * ----
 * expression
 * ----
 * </pre>
 */
public class AliasResolver {

    private final ExpressionParser expressionParser;

    /**
     * Creates an AliasResolver with the given expression parser.
     *
     * @param expressionParser
     *            the parser for alias expressions
     */
    public AliasResolver(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    /**
     * Finds and resolves aliases for a given decision table ID.
     *
     * @param document
     *            the AsciiDoc document
     * @param tableId
     *            the ID of the decision table
     * @return map of alias names to their block expressions
     */
    public Map<String, Expression.Block> resolveAliases(Document document, String tableId) {
        Map<String, Expression.Block> aliases = new LinkedHashMap<>();

        for (StructuralNode block : findAllBlocks(document)) {
            if (isAliasBlockFor(block, tableId)) {
                parseAliasDefinitions(block, aliases);
            }
        }

        return aliases;
    }

    /**
     * Checks if a block is an alias definition list for the given table ID.
     */
    private boolean isAliasBlockFor(StructuralNode block, String tableId) {
        if (!(block instanceof DescriptionList)) {
            return false;
        }
        java.util.List<String> roles = block.getRoles();
        if (roles == null || !roles.contains("aliases")) {
            return false;
        }
        Object forAttr = block.getAttribute("for");
        return forAttr != null && forAttr.toString().equals(tableId);
    }

    /**
     * Parses alias definitions from a description list.
     */
    private void parseAliasDefinitions(StructuralNode block, Map<String, Expression.Block> aliases) {
        if (!(block instanceof DescriptionList descList)) {
            return;
        }

        for (DescriptionListEntry entry : descList.getItems()) {
            String aliasName = extractAliasName(entry);
            if (aliasName == null || aliasName.isBlank()) {
                continue;
            }

            String iskaraSource = extractIskaraSource(entry);
            if (iskaraSource != null && !iskaraSource.isBlank()) {
                Expression.Block blockExpr = parseAsBlock(iskaraSource);
                if (blockExpr != null) {
                    aliases.put(aliasName, blockExpr);
                }
            }
        }
    }

    /**
     * Extracts the alias name from a description list entry.
     */
    private String extractAliasName(DescriptionListEntry entry) {
        java.util.List<ListItem> terms = entry.getTerms();
        if (terms == null || terms.isEmpty()) {
            return null;
        }
        return terms.getFirst().getText();
    }

    /**
     * Extracts the Iskara source code from a description list entry.
     * <p>
     * The source is expected to be in a [source,iskara] listing block within
     * the entry's description.
     */
    private String extractIskaraSource(DescriptionListEntry entry) {
        ListItem description = entry.getDescription();
        if (description == null) {
            return null;
        }

        // Look for source blocks in the description
        for (StructuralNode child : description.getBlocks()) {
            if (child instanceof Block listing) {
                if ("listing".equals(listing.getContext())) {
                    String style = listing.getStyle();
                    Object language = listing.getAttribute("language");
                    if ("source".equals(style) && "iskara".equals(language)) {
                        return listing.getSource();
                    }
                }
            }
        }

        // If no source block found, try to use the description text directly
        String text = description.getText();
        if (text != null && !text.isBlank()) {
            return text;
        }

        return null;
    }

    /**
     * Parses Iskara source as a block expression.
     * <p>
     * If the source doesn't already have block brackets, wraps it in a block.
     */
    private Expression.Block parseAsBlock(String source) {
        String trimmed = source.trim();

        // If already wrapped in brackets, parse as block
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return expressionParser.parseBlock(trimmed);
        }

        // Otherwise wrap in a block and parse
        String wrapped = "[" + trimmed + "]";
        return expressionParser.parseBlock(wrapped);
    }

    /**
     * Recursively finds all blocks in a document.
     */
    private java.util.List<StructuralNode> findAllBlocks(StructuralNode node) {
        java.util.List<StructuralNode> blocks = new ArrayList<>();
        blocks.add(node);
        for (StructuralNode child : node.getBlocks()) {
            blocks.addAll(findAllBlocks(child));
        }
        return blocks;
    }
}
