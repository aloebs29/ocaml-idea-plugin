package com.reason.ide;

import com.intellij.psi.*;
import com.reason.comp.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.nio.file.*;

@SuppressWarnings("ConstantConditions")
@RunWith(JUnit4.class)
public class ORFileUtilsTest extends ORBasePlatformTestCase {
    @Test
    public void test_relative_source_with_namespace() {
        myFixture.configureByText(ORConstants.BS_CONFIG_FILENAME, toJson("{'name': 'foo', 'namespace': 'foo'}"));
        PsiFile binary = myFixture.configureByText("Config-Foo.cm", "binary, should be .cmt");
        FileSystem fs = FileSystems.getDefault();
        String relativeSource = ORFileUtils.toRelativeSourceName(getProject(), ORFileUtils.getVirtualFile(binary), fs.getPath("src/Config-Foo.cmt"));
        assertEquals(fs.getPath("src", "Config.re").toString(), relativeSource);
    }

    // A dune module is compiled behind its stanza's namespace, and under the module name rather than
    // the file name: `bin/main.ml` of an executable ends up as `dune__exe__Main.cmt`
    @Test
    public void test_cmt_candidates_of_a_wrapped_module() {
        assertEquals("[dune__exe__Main.cmt, dune__exe__main.cmt, Main.cmt, main.cmt]",
                ORFileUtils.cmtCandidates("dune__exe__", "main").toString());
    }

    @Test
    public void test_cmt_candidates_without_a_namespace() {
        assertEquals("[Main.cmt, main.cmt]", ORFileUtils.cmtCandidates(null, "main").toString());
    }
}
