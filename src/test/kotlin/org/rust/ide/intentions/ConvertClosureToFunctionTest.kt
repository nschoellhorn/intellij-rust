/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertClosureToFunctionTest : RsIntentionTestBase(ConvertClosureToFunctionIntention::class) {

    fun `test conversion from closure to function`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test conversion not available for wildcard binding`() = doUnavailableTest("""
        fn main() {
            let _ = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """)

}
