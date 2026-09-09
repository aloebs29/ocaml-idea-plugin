package com.reason.ide.docs;

import com.intellij.openapi.editor.*;
import com.reason.ide.*;
import com.reason.ide.files.*;
import com.reason.ide.hints.*;
import com.reason.lang.*;
import com.reason.lang.ocaml.*;
import org.jetbrains.annotations.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

/**
 * Quick doc of a plain `let` - the common case in a project, where nothing is annotated and nothing is
 * documented. All the type information there is comes from the cmt, through rincewind.
 */
@RunWith(JUnit4.class)
public class ShowInferredDoc_OCL_Test extends ORBasePlatformTestCase {
    // hello-ocaml/bin/main.ml, verbatim: the line and column of every type below come from running
    // rincewind on the cmt dune produced for it
    private static final String MAIN_ML = """
            open Base
            open Stdio

            let rec read_and_accumulate accum =
              let line = In_channel.input_line In_channel.stdin in
              match line with
              | None -> accum
              | Some x -> read_and_accumulate (accum +. Float.of_string x)

            let () =
              printf "Total: %F\\n" (read_and_accumulate 0.)""";

    @Test
    public void test_inferred_type_of_a_local_function_at_its_usage() {
        FileBase main = configureCode("Main.ml", MAIN_ML.replace("(read_and_accumulate 0.)", "(read_and_a<caret>ccumulate 0.)"));
        annotate(main,
                "Va|4.8,4.27|read_and_accumulate|Base__Float.t -> Base__Float.t",
                "Id|11.24,11.43|read_and_accumulate|read_and_accumulate|Base__Float.t -> Base__Float.t");

        assertEquals("<div class=\"definition\"><b>Main</b><p><i>let read_and_accumulate : <code>Base__Float.t -&gt; Base__Float.t</code></i></p></div>",
                getDoc(main, OclLanguage.INSTANCE));
    }

    // The usage of a function is not always typed in the cmt, the definition is what has to answer then
    @Test
    public void test_inferred_type_falls_back_to_the_definition() {
        FileBase main = configureCode("Main.ml", MAIN_ML.replace("(read_and_accumulate 0.)", "(read_and_a<caret>ccumulate 0.)"));
        annotate(main, "Va|4.8,4.27|read_and_accumulate|Base__Float.t -> Base__Float.t");

        assertEquals("<div class=\"definition\"><b>Main</b><p><i>let read_and_accumulate : <code>Base__Float.t -&gt; Base__Float.t</code></i></p></div>",
                getDoc(main, OclLanguage.INSTANCE));
    }

    // Nothing known about the type is no reason to show nothing at all
    @Test
    public void test_undocumented_let_without_any_inferred_type() {
        FileBase main = configureCode("Main.ml", MAIN_ML.replace("(read_and_accumulate 0.)", "(read_and_a<caret>ccumulate 0.)"));

        assertEquals("<div class=\"definition\"><b>Main</b><p><i>let read_and_accumulate</i></p></div>",
                getDoc(main, OclLanguage.INSTANCE));
    }

    /** Feeds rincewind output to the file the way {@link com.reason.hints.RincewindProcess} does. */
    private void annotate(@NotNull FileBase file, String @NotNull ... rincewindLines) {
        InferredTypesImplementation types = new InferredTypesImplementation();
        for (String line : rincewindLines) {
            String[] parts = line.split("\\|", 3);
            types.add(getProject(), parts[0], position(parts[1].split(",")[0]), position(parts[1].split(",")[1]), parts[2]);
        }
        InferredTypesService.annotatePsiFile(getProject(), ORLanguageProperties.cast(file.getLanguage()), ORFileUtils.getVirtualFile(file), types);
    }

    /** Rincewind counts lines from 1 and columns from 0. */
    private @NotNull LogicalPosition position(@NotNull String location) {
        String[] lineAndColumn = location.split("\\.");
        return new LogicalPosition(Integer.parseInt(lineAndColumn[0]) - 1, Integer.parseInt(lineAndColumn[1]));
    }
}
