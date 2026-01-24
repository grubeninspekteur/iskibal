package work.spell.iskibal.parser.api;

import java.util.Locale;

/**
 * Options for parsing Iskara source code.
 *
 * @param locale
 *            the locale to use for parsing numbers (determines decimal
 *            separator)
 * @param sourceType
 *            the type of source being parsed
 * @param sourceName
 *            a name for the source (used in error messages)
 */
public record ParseOptions(Locale locale, SourceType sourceType, String sourceName) {
	/**
	 * Returns default parse options with system locale, Iskara source type, and a
	 * generic source name.
	 */
	public static ParseOptions defaults() {
		return new ParseOptions(Locale.getDefault(), SourceType.ISKARA, "<source>");
	}

	/**
	 * Returns parse options for Iskara source with the given name.
	 */
	public static ParseOptions iskara(String sourceName) {
		return new ParseOptions(Locale.getDefault(), SourceType.ISKARA, sourceName);
	}

	/**
	 * Returns parse options for AsciiDoc source with the given name.
	 */
	public static ParseOptions asciidoc(String sourceName) {
		return new ParseOptions(Locale.getDefault(), SourceType.ASCIIDOC, sourceName);
	}

	/**
	 * Returns a copy of these options with a different locale.
	 */
	public ParseOptions withLocale(Locale locale) {
		return new ParseOptions(locale, this.sourceType, this.sourceName);
	}

	/**
	 * Returns a copy of these options with a different source type.
	 */
	public ParseOptions withSourceType(SourceType sourceType) {
		return new ParseOptions(this.locale, sourceType, this.sourceName);
	}

	/**
	 * Returns a copy of these options with a different source name.
	 */
	public ParseOptions withSourceName(String sourceName) {
		return new ParseOptions(this.locale, this.sourceType, sourceName);
	}
}
