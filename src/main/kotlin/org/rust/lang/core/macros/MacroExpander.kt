/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.stdext.RsResult

abstract class MacroExpander<in T: RsMacroData, out E> {
    open fun expandMacroAsText(def: T, call: RsMacroCallData): Pair<CharSequence, RangeMap>? {
        return expandMacroAsTextWithErr(def, call).ok()
    }

    abstract fun expandMacroAsTextWithErr(def: T, call: RsMacroCallData): RsResult<Pair<CharSequence, RangeMap>, out E>
}

class BangMacroExpander(
    private val decl: DeclMacroExpander,
    private val proc: ProcMacroExpander
) : MacroExpander<RsMacroData, MacroExpansionError>() {
    override fun expandMacroAsText(def: RsMacroData, call: RsMacroCallData): Pair<CharSequence, RangeMap>? {
        return when (def) {
            is RsDeclMacroData -> decl.expandMacroAsText(def, call)
            is RsProcMacroData -> proc.expandMacroAsText(def, call)
        }
    }

    override fun expandMacroAsTextWithErr(
        def: RsMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, out MacroExpansionError> {
        return when (def) {
            is RsDeclMacroData -> decl.expandMacroAsTextWithErr(def, call)
            is RsProcMacroData -> proc.expandMacroAsTextWithErr(def, call)
        }
    }
}
