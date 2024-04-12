package com.google.devtools.build.lib.buildtool;

import com.google.devtools.build.lib.buildtool.util.BuildIntegrationTestCase;

@RunWith(TestParameterInjector.class)
public class RunfilesConflictTest extends BuildIntegrationTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        scratch.file("BUILD", "genrule(name = 'gen',", "    outs = ['out'],", "    cmd = 'echo \"hello\" > $@')");
        scratch.file("conflict", "hello");
        scratch.file("conflict2", "hello2");
    }

    private void writeFile() throws IOException {
        write(
                "rules.bzl",
                """
                def _simple_test_impl

                shell_with_transition = rule(

                )
                """);
        write(
                "BUILD.bazel",
                """
                        load(":rules.bzl", "shell_with_transition")
                        shell_with_transition(
                            name="x",
                            text_to_print="x",
                        )

                        shell_with_transition(
                            name="y",
                            text_to_print="y",
                        )
                        """);
    }

}
