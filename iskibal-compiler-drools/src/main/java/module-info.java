module iskibal.compiler.drools {
    requires iskibal.rule.model;
    requires iskibal.compiler.common;
    requires static org.jspecify;

    exports work.spell.iskibal.compiler.drools.api;

    uses work.spell.iskibal.compiler.common.api.SemanticAnalyzer;
    uses work.spell.iskibal.compiler.drools.api.DroolsCompiler;

    provides work.spell.iskibal.compiler.drools.api.DroolsCompiler
            with work.spell.iskibal.compiler.drools.internal.DroolsCompilerImpl;
}
