module iskibal.compiler.common {
	requires iskibal.rule.model;

	exports work.spell.iskibal.compiler.common.api;
	exports work.spell.iskibal.compiler.common.internal to iskibal.integration.tests;

	provides work.spell.iskibal.compiler.common.api.SemanticAnalyzer
			with work.spell.iskibal.compiler.common.internal.SemanticAnalyzerImpl;

	uses work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
}
