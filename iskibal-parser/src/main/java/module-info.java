module iskibal.parser {
	requires iskibal.rule.model;
	requires org.antlr.antlr4.runtime;

	exports work.spell.iskibal.parser.api;
	exports work.spell.iskibal.parser.diagnostic;
	exports work.spell.iskibal.parser.internal to iskibal.integration.tests;

	provides work.spell.iskibal.parser.api.Parser with work.spell.iskibal.parser.internal.IskaraParserImpl;
}
