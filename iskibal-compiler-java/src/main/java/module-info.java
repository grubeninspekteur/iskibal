module iskibal.compiler.java {
	requires iskibal.rule.model;
	requires iskibal.compiler.common;

	exports work.spell.iskibal.compiler.java.api;
	exports work.spell.iskibal.compiler.java.internal to iskibal.integration.tests;

	uses work.spell.iskibal.compiler.common.api.SemanticAnalyzer;

	provides work.spell.iskibal.compiler.java.api.JavaCompiler
			with work.spell.iskibal.compiler.java.internal.JavaCompilerImpl;
}
