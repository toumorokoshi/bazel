package com.google.devtools.build.lib.buildtool;

import com.google.devtools.build.lib.actions.BuildFailedException;
import com.google.devtools.build.lib.buildtool.util.BuildIntegrationTestCase;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertThrows;

@RunWith(TestParameterInjector.class)
public class RunfilesConflictTest extends BuildIntegrationTestCase {

    @Before
    public void setUp() throws Exception {
        writeFile();
    }

    @Test
    public void testRunfilesConflict() throws Exception {
        buildTarget("//x:echo_edition");
        assertContents("print('community')", "//x:echo_edition");
        buildTarget("//x:hello_world_community");
        assertContents("print('community')", "//x:hello_world_community");
        buildTarget("//x:hello_world_enterprise");
        assertContents("print('enterprise')", "//x:hello_world_enterprise");
        buildTarget("//x:combined");
        assertContents("print('community')\nprint('enterprise')", "//x:combined");
    }

    private void writeFile() throws IOException {
        write("BUILD", "");
        write (
                "config/BUILD",
                """
                       load("//:rules.bzl", "edition_flag")
                       
                       config_setting(
                           name = "community_edition",
                           flag_values = {
                                ":edition": "community",
                           },
                           visibility = ["//visibility:public"]
                       )
                       
                       config_setting(
                            name = "enterprise_edition",
                            flag_values = {
                                ":edition": "enterprise",
                            },
                            visibility = ["//visibility:public"]
                       )
                      
                       edition_flag(
                            name="edition",
                            build_setting_default="community",
                       ) 
                       """);
        write(
                "rules.bzl",
                """
                       
                        # attr is passed implicitly from the rule,
                        # wiring this up to the transition.
                        def _edition_transition_impl(settings, attr):
                            edition = attr.edition
                            return {"//config:edition": edition}
                                                
                        edition_transition = transition(
                            implementation = _edition_transition_impl,
                            inputs = [],
                            outputs = ["//config:edition"],
                        )
                                                
                        def _impl(ctx):
                            dep = ctx.attr.dep[DefaultInfo]
                            input_file = dep.files.to_list()[0]
                            out_file = ctx.actions.declare_file(ctx.attr.out)
                            
                            ctx.actions.run_shell(
                                inputs = [input_file],
                                outputs = [out_file],
                                command = "cp $1 $2",
                                arguments = [input_file.path, out_file.path],
                            )
                            return DefaultInfo(
                                files=depset([out_file]),
                            )

                        copy = rule(
                            implementation = _impl,
                            attrs = {
                                "dep": attr.label(mandatory=True, providers=[DefaultInfo]),
                                "edition": attr.string(), 
                                "out": attr.string(),
                            },
                            # this line selects the edition based on the
                            # attr passed.
                            cfg = edition_transition,
                        )
                        
                        EditionProvider = provider(fields = ["edition"])
                        
                        def _edition_flag_impl(ctx):
                            return EditionProvider(edition = ctx.build_setting_value)
                                                
                        edition_flag = rule(
                            implementation = _edition_flag_impl,
                            build_setting = config.string(flag = True),
                        )
                        """);
        write(
                "x/BUILD",
                """
                       load("//:rules.bzl", "copy")
                        
                       genrule(
                            name = "echo_edition",
                            outs = ["edition.txt"],
                            cmd = select({
                                    "//config:community_edition": '''echo "print('community')" > $(location edition.txt)''',
                                    "//config:enterprise_edition": '''echo "print('enterprise')" > $(location edition.txt)'''
                            }),
                       )
                       
                       copy(
                          name="hello_world_community",
                          dep=":echo_edition",
                          edition="community",
                          out="community.txt"
                       )
                       
                       copy(
                          name="hello_world_enterprise",
                          dep=":echo_edition",
                          edition="enterprise",
                          out="enterprise.txt"
                       )
                       
                       genrule(
                           name = "combined",
                           srcs = [
                             ":hello_world_community",
                             ":hello_world_enterprise",
                           ],
                           outs = ["combined.txt"],
                           cmd = "cat $(SRCS) > $(location combined.txt)",
                       )
                       """);
    }

}