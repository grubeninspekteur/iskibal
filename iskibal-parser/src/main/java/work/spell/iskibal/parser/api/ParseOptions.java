package work.spell.iskibal.parser.api;

import module java.base;

/// Options for parsing Iskara source code.
///
/// @param locale
///            the locale to use for parsing numbers (determines decimal
///            separator)
/// @param sourceName
///            a name for the source (used in error messages)
public record ParseOptions(Locale locale, String sourceName) {
    /// Returns default parse options with system locale and a generic source name.
    public static ParseOptions defaults() {
        return new ParseOptions(Locale.getDefault(), "<source>");
    }

    /// Returns parse options with the given source name and the system locale.
    public static ParseOptions iskara(String sourceName) {
        return new ParseOptions(Locale.getDefault(), sourceName);
    }

    /// Returns a copy of these options with a different locale.
    public ParseOptions withLocale(Locale locale) {
        return new ParseOptions(locale, this.sourceName);
    }

    /// Returns a copy of these options with a different source name.
    public ParseOptions withSourceName(String sourceName) {
        return new ParseOptions(this.locale, sourceName);
    }
}
