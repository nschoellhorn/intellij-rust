/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.macros.decl.FragmentKind
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.MacroBraces
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RS_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf

fun PsiBuilder.parseSubtree(): MappedSubtree {
    return SubtreeParser(this).parse()
}

private class SubtreeParser(
    private val lexer: PsiBuilder
) {
    private val tokenMap = mutableListOf<TokenTextRange>()

    fun parse(): MappedSubtree {
        val result = mutableListOf<TokenTree>()

        while (true) {
            val tokenType = lexer.tokenType ?: break
            val offset = lexer.currentOffset

            collectLeaf(tokenType, offset, result)
        }

        if (result.size == 1 && result.single() is TokenTree.Subtree) {
            return MappedSubtree((result.single() as TokenTree.Subtree).subtree, TokenMap(tokenMap))
        }

        return MappedSubtree(SubtreeS(null, result), TokenMap(tokenMap))
    }

    private fun collectLeaf(tokenType: IElementType, offset: Int, result: MutableList<TokenTree>) {
        val delimKind = MacroBraces.fromOpenToken(tokenType)
        if (delimKind != null) {
            val delimLeaf = Delimiter(allocDelimId(offset), delimKind)
            val subtreeResult = mutableListOf<TokenTree>()
            lexer.advanceLexer()
            while (true) {
                val tokenType2 = lexer.tokenType ?: run {
                    result += TokenTree.Leaf(LeafS.Punct(PunctS(delimKind.openText, Spacing.Alone, allocId(offset, 1))))
                    result += subtreeResult
                    return
                }
                if (tokenType2 == delimKind.closeToken) break
                val offset2 = lexer.currentOffset

                collectLeaf(tokenType2, offset2, subtreeResult)
            }
            closeDelim(delimLeaf.id, lexer.currentOffset)
            result += TokenTree.Subtree(SubtreeS(delimLeaf, subtreeResult))
        } else {
            val tokenText = lexer.tokenText!!
            when (tokenType) {
                INTEGER_LITERAL -> {
                    val tokenText2 = if (RustParserUtil.parseFloatLiteral(lexer, 0)) {
                        val m = lexer.latestDoneMarker!!
                        lexer.originalText.substring(m.startOffset, m.endOffset)
                    } else {
                        tokenText
                    }
                    result += TokenTree.Leaf(LeafS.Literal(LiteralS(tokenText2, allocId(offset, tokenText2.length))))
                }
                in RS_LITERALS -> result += TokenTree.Leaf(LeafS.Literal(LiteralS(tokenText, allocId(offset, tokenText.length))))
                in FragmentKind.IDENTIFIER_TOKENS -> result += TokenTree.Leaf(LeafS.Ident(IdentS(tokenText, allocId(offset, tokenText.length))))
                QUOTE_IDENTIFIER -> {
                    result += TokenTree.Leaf(LeafS.Punct(PunctS(tokenText[0].toString(), Spacing.Joint, allocId(offset, 1))))
                    result += TokenTree.Leaf(LeafS.Ident(IdentS(tokenText.substring(1), allocId(offset, tokenText.length - 1))))
                }
                else -> {
                    for (i in tokenText.indices) {
                        val isLast = i == tokenText.lastIndex
                        val char = tokenText[i].toString()
                        val spacing = when {
                            !isLast -> Spacing.Joint
                            else -> {
                                val next = lexer.rawLookup(1)
                                when {
                                    next == null -> Spacing.Alone
                                    next in SET -> Spacing.Alone
                                    next !in RS_LITERALS && next !in FragmentKind.IDENTIFIER_TOKENS -> Spacing.Joint
                                    else -> Spacing.Alone
                                }
                            }
                        }
                        result += TokenTree.Leaf(LeafS.Punct(PunctS(char, spacing, allocId(offset + i, 1))))
                    }
                }
            }
        }

        lexer.advanceLexer()
    }

    private fun allocId(startOffset: Int, length: Int): Int {
        val id = tokenMap.size
        tokenMap += TokenTextRange.Token(TextRange(startOffset, startOffset + length))
        return id
    }

    private fun allocDelimId(openOffset: Int): Int {
        val id = tokenMap.size
        tokenMap += TokenTextRange.Delimiter(openOffset, -1)
        return id
    }

    private fun closeDelim(tokeId: Int, closeOffset: Int) {
        tokenMap[tokeId] = (tokenMap[tokeId] as TokenTextRange.Delimiter).copy(closeOffset = closeOffset)
    }
}

private val SET = TokenSet.orSet(tokenSetOf(WHITE_SPACE), RS_COMMENTS, tokenSetOf(LBRACK, LBRACE, LPAREN))
