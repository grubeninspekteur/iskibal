package work.spell.iskibal.intellij;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;

public final class IskaraFile extends PsiFileBase {

    public IskaraFile(FileViewProvider viewProvider) {
        super(viewProvider, IskaraLanguage.INSTANCE);
    }

    @Override
    public FileType getFileType() {
        return IskaraFileType.INSTANCE;
    }
}
