plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "work.spell.iskibal"
version = "0.1.0-SNAPSHOT"

val antlr4Version = "4.13.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ANTLR4 code generation from the shared grammar
val antlr4 by configurations.creating

dependencies {
    antlr4("org.antlr:antlr4:$antlr4Version")

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
    }

    implementation("org.antlr:antlr4-runtime:$antlr4Version")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2") // Required by IntelliJ test framework
    testImplementation("org.assertj:assertj-core:3.26.3")
}

// Generate ANTLR lexer and parser from the shared grammar files
val grammarDir = rootProject.file("../iskibal-parser/src/main/antlr4/work/spell/iskibal/parser")
val antlrOutputDir = layout.buildDirectory.dir("generated-src/antlr/main/work/spell/iskibal/parser")

val generateAntlrLexer by tasks.registering(JavaExec::class) {
    val grammarFile = grammarDir.resolve("IskaraLexer.g4")

    inputs.file(grammarFile)
    outputs.dir(antlrOutputDir)

    classpath = antlr4
    mainClass = "org.antlr.v4.Tool"
    args = listOf(
        grammarFile.absolutePath,
        "-o", antlrOutputDir.get().asFile.absolutePath,
        "-package", "work.spell.iskibal.parser",
        "-no-listener",
        "-no-visitor",
    )
}

val generateAntlrParser by tasks.registering(JavaExec::class) {
    dependsOn(generateAntlrLexer) // Parser depends on lexer token vocabulary
    val grammarFile = grammarDir.resolve("IskaraParser.g4")

    inputs.file(grammarFile)
    inputs.dir(antlrOutputDir) // Needs IskaraLexer.tokens
    outputs.dir(antlrOutputDir)

    classpath = antlr4
    mainClass = "org.antlr.v4.Tool"
    args = listOf(
        grammarFile.absolutePath,
        "-o", antlrOutputDir.get().asFile.absolutePath,
        "-package", "work.spell.iskibal.parser",
        "-no-listener",
        "-no-visitor",
    )
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
        }
    }
}

tasks.compileJava {
    dependsOn(generateAntlrLexer, generateAntlrParser)
}

intellijPlatform {
    pluginConfiguration {
        id = "work.spell.iskibal.intellij"
        name = "Iskara Language Support"
        version = project.version.toString()
        description = "Language support for the Iskara business rule language (.iskara files)"
        vendor {
            name = "spell.work"
        }
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
