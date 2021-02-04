/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Disposer
import com.intellij.util.io.exists
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.lang.core.macros.proc.ProcMacroServer
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toDebugString
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.runWithEnabledFeature
import org.rust.singleWorkspace
import org.rust.stdext.RsResult
import org.rust.stdext.toPath

class RsProcMacroExpansionTest : RsWithToolchainTestBase() {
    fun `test 1`(): Unit = runWithEnabledFeature(RsExperiments.EVALUATE_BUILD_SCRIPTS) {
        buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = ["my_proc_macro", "mylib"]
            """)
            dir("my_proc_macro") {
                toml("Cargo.toml", """
                    [package]
                    name = "my_proc_macro"
                    version = "1.0.0"
                    edition = "2018"

                    [lib]
                    proc-macro = true

                    [dependencies]
                """)
                dir("src") {
                    rust("lib.rs", """
                        extern crate proc_macro;
                        use proc_macro::TokenStream;

                        #[proc_macro]
                        pub fn as_is(input: TokenStream) -> TokenStream {
                            return input;
                        }

                        #[proc_macro]
                        pub fn do_panic(input: TokenStream) -> TokenStream {
                            panic!("panic message");
                        }

                        #[proc_macro]
                        pub fn wait_100_seconds(input: TokenStream) -> TokenStream {
                            std::thread::sleep(std::time::Duration::from_secs(100));
                            return input;
                        }

                        #[proc_macro]
                        pub fn process_exit(input: TokenStream) -> TokenStream {
                            std::process::exit(101)
                        }

                        #[proc_macro]
                        pub fn process_abort(input: TokenStream) -> TokenStream {
                            std::process::abort()
                        }
                    """)
                }
            }
            dir("mylib") {
                toml("Cargo.toml", """
                    [package]
                    name = "mylib"
                    version = "1.0.0"

                    [dependencies]
                    my_proc_macro = { path = "../my_proc_macro" }
                """)
                dir("src") {
                    rust("lib.rs", """

                    """)
                }
            }
        }
        val pkg = project.cargoProjects.singleWorkspace().packages
            .find { it.name == "my_proc_macro" }!!
        val lib = pkg.procMacroArtifact?.path?.toString()
            ?: error("Procedural macro artifact is not found. This most likely means a compilation failure")
        val server = ProcMacroServer.tryCreate() ?: return@runWithEnabledFeature // native-helper is not available
        Disposer.register(testRootDisposable, server)
        val expander = ProcMacroExpander(project, server)

        expander.apply {
            checkExpansion(lib, "as_is", "", "")
            checkExpansion(lib, "as_is", ".", ".")
            checkExpansion(lib, "as_is", "..", "..")
            checkExpansion(lib, "as_is", "fn foo() {}", "fn foo() {}")
            checkError<ProcMacroExpansionError.Expansion>(lib, "do_panic", "")
            checkError<ProcMacroExpansionError.Timeout>(lib, "wait_100_seconds", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "process_exit", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "process_abort", "")
            checkExpansion(lib, "as_is", "", "") // Insure it works after errors
        }
    }

    fun `test CantRunExpander error`() {
        val nonExistingFile = myFixture.tempDirPath.toPath().resolve("non/existing/file")
        assertFalse(nonExistingFile.exists())
        val invalidServer = ProcMacroServer.createUnchecked(nonExistingFile)
        Disposer.register(testRootDisposable, invalidServer)
        val expander = ProcMacroExpander(project, invalidServer)
        expander.checkError<ProcMacroExpansionError.CantRunExpander>("", "", "")
    }

    fun `test ExecutableNotFound error`() {
        val expander = ProcMacroExpander(project, null)
        expander.checkError<ProcMacroExpansionError.ExecutableNotFound>("", "", "")
    }

    private fun ProcMacroExpander.checkExpansion(lib: String, name: String, macroCall: String, expected: String) {
        val expansionResult = expandWithErr(project.createRustPsiBuilder(macroCall).parseSubtree().subtree, name, lib)
        val expansion = when (expansionResult) {
            is RsResult.Ok -> expansionResult.ok
            is RsResult.Err -> error("Expanded with error: ${expansionResult.err}")
        }
        assertEquals(
            project.createRustPsiBuilder(expected).parseSubtree().subtree.toDebugString(),
            expansion.toDebugString()
        )
    }

    private inline fun <reified T> ProcMacroExpander.checkError(
        lib: String,
        name: String,
        macroCall: String
    ) where T : ProcMacroExpansionError {
        val result = expandWithErr(project.createRustPsiBuilder(macroCall).parseSubtree().subtree, name, lib)
        check(result.err() is T) { "Expected error ${T::class}, got result $result" }
    }
}
