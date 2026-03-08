package work.spell.iskibal.intellij;

import com.intellij.lang.Language;

public final class IskaraLanguage extends Language {

    public static final IskaraLanguage INSTANCE = new IskaraLanguage();

    private IskaraLanguage() {
        super("Iskara");
    }
}
