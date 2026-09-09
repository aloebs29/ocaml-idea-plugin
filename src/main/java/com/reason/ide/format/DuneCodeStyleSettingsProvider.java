package com.reason.ide.format;

import com.intellij.application.options.*;
import com.intellij.lang.*;
import com.intellij.psi.codeStyle.*;
import com.reason.lang.dune.*;
import org.jetbrains.annotations.*;

/**
 * Adds a Dune page under Settings | Editor | Code Style, and — more importantly — sets the indent defaults the
 * dune documentation uses. Without this the language would inherit the generic defaults and indent by four.
 */
public class DuneCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    private static final String CODE_SAMPLE = """
            (lang dune 3.17)
            (name test)

            (package
              (name test)
              (depends
                (ocaml (>= 4.14))
                fmt))
            """;

    @Override
    public @NotNull Language getLanguage() {
        return DuneLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getCodeSample(@NotNull SettingsType settingsType) {
        return CODE_SAMPLE;
    }

    @Override
    public void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings, CommonCodeStyleSettings.@NotNull IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 2;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;
        indentOptions.USE_TAB_CHARACTER = false;
    }

    @Override
    public @Nullable IndentOptionsEditor getIndentOptionsEditor() {
        return new IndentOptionsEditor();
    }
}
