/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.*
import com.intellij.icons.AllIcons
import com.vdurmont.semver4j.Semver
import org.rust.toml.StringValueInsertionHandler
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.lang.psi.TomlKeyValue

class LocalCargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val prefix = CompletionUtil.getOriginalElement(keyValue.key)?.text ?: return

        val indexService = CratesLocalIndexService.getInstance()

        val crateNames = indexService.getAllCrateNames()
        val elements = crateNames.mapNotNull { crateName ->
            val crate = indexService.getCrate(crateName) ?: return@mapNotNull null
            val latestVersion = crate.latestVersion?.version ?: ""

            PrioritizedLookupElement.withPriority(
                LookupElementBuilder
                    .create(crateName)
                    .withIcon(AllIcons.Nodes.PpLib)
                    .withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
                        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                            presentation.itemText = "$crateName = \"$latestVersion\""
                        }
                    })
                    .withInsertHandler { context, _ ->
                        context.document.insertString(context.tailOffset, " = \"$latestVersion\"")
                        val endLineOffset = context.editor.caretModel.visualLineEnd
                        // TODO: Currently moves caret to the next line
                        context.editor.caretModel.moveToOffset(endLineOffset)
                    },
                (-crateName.length).toDouble()
            )
        }
        result.withPrefixMatcher(CargoNormalizedNamesPrefixMatcher(prefix)).addAllElements(elements)
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val name = CompletionUtil.getOriginalElement(keyValue.key)?.text ?: return

        val indexService = CratesLocalIndexService.getInstance()

        val versions = indexService.getCrate(name)?.versions ?: return
        val elements = versions.mapIndexed { index, variant ->
            val lookupElement = LookupElementBuilder.create(variant.version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
                .withTailText(if (variant.isYanked) " yanked" else null)

            PrioritizedLookupElement.withPriority(
                lookupElement,
                index.toDouble()
            )
        }
        result.withRelevanceSorter(CompletionSorter.emptySorter().weigh(object : LookupElementWeigher("cratesVersionWeigher", true, false) {
            override fun weigh(element: LookupElement): Comparable<Nothing> =
                Semver(element.lookupString)
        })).addAllElements(elements)
    }
}
