module iskibal.asciidoc {
    requires transitive iskibal.rule.model;
    requires transitive iskibal.parser;
    requires org.asciidoctor.asciidoctorj;
    requires org.asciidoctor.asciidoctorj.api;

    exports work.spell.iskibal.asciidoc;

    uses work.spell.iskibal.parser.api.Parser;
}
