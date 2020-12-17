/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.google.gson.annotations.SerializedName
import org.rust.lang.core.psi.MacroBraces

typealias TokenId = Int

data class SubtreeS(
    val delimiter: Delimiter?,
    @SerializedName("token_trees")
    val tokenTrees: List<TokenTree>
)

data class Delimiter(
    val id: TokenId,
    val kind: MacroBraces
)

sealed class TokenTree {
    data class Leaf(@SerializedName("Leaf") val leaf: LeafS): TokenTree()
    data class Subtree(@SerializedName("Subtree") val subtree: SubtreeS): TokenTree()
}

sealed class LeafS {
    data class Literal(@SerializedName("Literal") val literal: LiteralS): LeafS()
    data class Punct(@SerializedName("Punct") val punct: PunctS): LeafS()
    data class Ident(@SerializedName("Ident") val ident: IdentS): LeafS()
}

data class LiteralS(val text: String, val id: TokenId)
data class PunctS(val char: String, val spacing: Spacing, val id: TokenId)
data class IdentS(val text: String, val id: TokenId)

enum class Spacing {
    Alone, Joint
}

fun SubtreeS.toDebugString(): String {
    val sb = StringBuilder()
    printDebugToken(sb, 0)
    return sb.toString()
}

private fun SubtreeS.printDebugToken(sb: StringBuilder, level: Int) {
    val aux = if (delimiter == null) {
        "$"
    } else {
        "${delimiter.kind.openText}${delimiter.kind.closeText} ${delimiter.id}"
    }
    sb.append("  ".repeat(level))
    sb.append("SUBTREE $aux")
    for (tokenTree in this.tokenTrees) {
        sb.append("\n")
        tokenTree.printDebugToken(sb, level + 1)
    }
}

private fun TokenTree.printDebugToken(sb: StringBuilder, level: Int) {
    when (this) {
        is TokenTree.Leaf -> {
            sb.append("  ".repeat(level)) // Alignment
            when (val leaf = leaf) {
                is LeafS.Literal -> sb.append("LITERAL ${leaf.literal.text} ${leaf.literal.id}")
                is LeafS.Punct -> sb.append("PUNCH   ${leaf.punct.char} [${leaf.punct.spacing.toString().toLowerCase()}] ${leaf.punct.id}")
                is LeafS.Ident -> sb.append("IDENT   ${leaf.ident.text} ${leaf.ident.id}")
            }
        }
        is TokenTree.Subtree -> subtree.printDebugToken(sb, level)
    }
}
