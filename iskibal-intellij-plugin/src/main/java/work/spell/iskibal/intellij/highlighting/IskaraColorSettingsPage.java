package work.spell.iskibal.intellij.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import work.spell.iskibal.intellij.IskaraIcons;

import javax.swing.*;
import java.util.Map;

public final class IskaraColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = {
        new AttributesDescriptor("Keyword", IskaraSyntaxHighlighter.KEYWORD),
        new AttributesDescriptor("Boolean", IskaraSyntaxHighlighter.BOOLEAN),
        new AttributesDescriptor("Null", IskaraSyntaxHighlighter.NULL),
        new AttributesDescriptor("String", IskaraSyntaxHighlighter.STRING),
        new AttributesDescriptor("Template string", IskaraSyntaxHighlighter.TEMPLATE_STRING),
        new AttributesDescriptor("Number", IskaraSyntaxHighlighter.NUMBER),
        new AttributesDescriptor("Identifier", IskaraSyntaxHighlighter.IDENTIFIER),
        new AttributesDescriptor("Quoted identifier", IskaraSyntaxHighlighter.QUOTED_IDENTIFIER),
        new AttributesDescriptor("Operator", IskaraSyntaxHighlighter.OPERATOR),
        new AttributesDescriptor("Line comment", IskaraSyntaxHighlighter.LINE_COMMENT),
        new AttributesDescriptor("Block comment", IskaraSyntaxHighlighter.BLOCK_COMMENT),
        new AttributesDescriptor("Braces", IskaraSyntaxHighlighter.BRACES),
        new AttributesDescriptor("Brackets", IskaraSyntaxHighlighter.BRACKETS),
        new AttributesDescriptor("Parentheses", IskaraSyntaxHighlighter.PARENTHESES),
        new AttributesDescriptor("Comma", IskaraSyntaxHighlighter.COMMA),
        new AttributesDescriptor("Dot", IskaraSyntaxHighlighter.DOT),
    };

    @Override
    public Icon getIcon() {
        return IskaraIcons.FILE;
    }

    @Override
    public SyntaxHighlighter getHighlighter() {
        return new IskaraSyntaxHighlighter();
    }

    @Override
    public String getDemoText() {
        return """
                // Discount rules for loyal customers
                /* This module defines tiered discounts
                   based on loyalty points */
                facts {
                    customer: com.example.Customer
                }
                outputs {
                    discountPercent: BigDecimal := 0
                    tier: String := "standard"
                }

                rule VIP "VIP customer gets 20% discount"
                when
                    customer.loyaltyPoints >= 1000,
                    customer.`is active` = true
                then
                    discountPercent := 20
                    tier := $"tier-${customer.level}"
                    let bonus = customer.points * 0.1
                end

                rule DEFAULT "Default rule"
                when
                    customer.loyaltyPoints < 500
                then
                    discountPercent := 0
                    tier := null
                end
                """;
    }

    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public String getDisplayName() {
        return "Iskara";
    }
}
