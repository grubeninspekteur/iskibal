module iskibal.integration.tests {
	requires iskibal.rule.model;
	requires iskibal.parser;
	requires iskibal.compiler.common;
	requires iskibal.compiler.java;
	requires java.compiler;

	exports work.spell.iskibal.testing;

	uses work.spell.iskibal.parser.api.Parser;
	uses work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
	uses work.spell.iskibal.compiler.java.api.JavaCompiler;
}
