/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant

class ConvertClosureToFunctionIntention : RsElementBaseIntentionAction<ConvertClosureToFunctionIntention.Context>() {

    override fun getText(): String = "Convert closure to function"
    override fun getFamilyName(): String = "Convert between local function and closure"

    data class Context(
        val assignment: RsLetDecl,
        val lambda: RsLambdaExpr,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        // We try to find a let declaration
        val possibleTarget = element.ancestorStrict<RsLetDecl>() ?: return null

        // we cannot convert lambdas bound to "_"
        if (possibleTarget.pat == null || possibleTarget.pat is RsPatWild) {
            return null
        }

        // The assignment of the let declaration should be a lambda to be a valid target
        if (possibleTarget.expr !is RsLambdaExpr) {
            return null
        }

        return Context(possibleTarget, possibleTarget.expr as RsLambdaExpr)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val name = ctx.assignment.pat?.text ?: "func"
        val parameters = ctx.lambda.valueParameters.joinToString(", ") {
            it.text
        }
        val returnText = ctx.lambda.retType?.text ?: ""
        val body = ctx.lambda.expr?.text ?: "{}"

        val function = factory.createFunction("fn $name($parameters) $returnText $body")
        val replaced = ctx.assignment.replace(function)

        editor.caretModel.moveToOffset(replaced.endOffset)
    }

}
