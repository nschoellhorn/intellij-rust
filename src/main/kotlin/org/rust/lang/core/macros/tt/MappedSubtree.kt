/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.intellij.openapi.util.TextRange
import com.intellij.util.SmartList
import org.rust.lang.core.macros.MappedTextRange
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.macros.mergeAdd

data class MappedSubtree(val subtree: SubtreeS, val tokenMap: TokenMap)

class TokenMap(val map: List<TokenTextRange>)

sealed class TokenTextRange {
    data class Token(val range: TextRange): TokenTextRange()
    data class Delimiter(val openOffset: Int, val closeOffset: Int): TokenTextRange()
}

fun MappedSubtree.toMappedText(): Pair<CharSequence, RangeMap> {
    return SubtreeTextBuilder(subtree, tokenMap).toText()
}

private class SubtreeTextBuilder(
    private val subtree: SubtreeS,
    private val tokenMap: TokenMap
) {
    private val sb = StringBuilder()
    private val ranges = SmartList<MappedTextRange>()

    fun toText(): Pair<CharSequence, RangeMap> {
        subtree.appendSubtree()
        return sb to RangeMap.from(ranges)
    }

    private fun SubtreeS.appendSubtree() {
        delimiter?.let { appendOpenDelim(it) }
        for (tokenTree in tokenTrees) {
            when (tokenTree) {
                is TokenTree.Leaf -> tokenTree.leaf.appendLeaf()
                is TokenTree.Subtree -> tokenTree.subtree.appendSubtree()
            }
        }
        delimiter?.let { appendCloseDelim(it) }
    }

    private fun LeafS.appendLeaf() {
        when (this) {
            is LeafS.Literal -> append(literal.text, literal.id)
            is LeafS.Ident -> {
                append(ident.text, ident.id)
                sb.append(" ")
            }
            is LeafS.Punct -> {
                append(punct.char, punct.id)
                if (punct.spacing == Spacing.Alone) {
                    sb.append(" ")
                }
            }
        }
    }

    private fun append(text: CharSequence, id: Int) {
        val range = (tokenMap.map[id] as TokenTextRange.Token).range
        check(text.length == range.length)
        ranges.mergeAdd(MappedTextRange(
            range.startOffset,
            sb.length,
            text.length
        ))
        sb.append(text)
    }

    private fun appendOpenDelim(delimiter: Delimiter) {
        val offset = (tokenMap.map[delimiter.id] as TokenTextRange.Delimiter).openOffset
        ranges.mergeAdd(MappedTextRange(
            offset,
            sb.length,
            1
        ))
        sb.append(delimiter.kind.openText)
    }

    private fun appendCloseDelim(delimiter: Delimiter) {
        val offset = (tokenMap.map[delimiter.id] as TokenTextRange.Delimiter).closeOffset
        ranges.mergeAdd(MappedTextRange(
            offset,
            sb.length,
            1
        ))
        sb.append(delimiter.kind.closeText)
    }
}
