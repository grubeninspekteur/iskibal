package work.spell.iskibal.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;

import javax.swing.*;

public final class IskaraFileType extends LanguageFileType {

    public static final IskaraFileType INSTANCE = new IskaraFileType();

    private IskaraFileType() {
        super(IskaraLanguage.INSTANCE);
    }

    @Override
    public String getName() {
        return "Iskara";
    }

    @Override
    public String getDescription() {
        return "Iskara business rule file";
    }

    @Override
    public String getDefaultExtension() {
        return "iskara";
    }

    @Override
    public Icon getIcon() {
        return IskaraIcons.FILE;
    }
}
