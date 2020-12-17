/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.google.gson.annotations.SerializedName
import org.rust.lang.core.macros.tt.SubtreeS

sealed class Request {
    data class ExpansionMacro(@SerializedName("ExpansionMacro") val expansionTask: ExpansionTask) : Request()
}

data class ExpansionTask(
    @SerializedName("macro_body")
    val macroBody: SubtreeS,
    @SerializedName("macro_name")
    val macroName: String,
    val attributes: SubtreeS?,
    val lib: String,
)
