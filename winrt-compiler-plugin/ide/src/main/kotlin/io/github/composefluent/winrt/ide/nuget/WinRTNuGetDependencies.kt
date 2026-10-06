package io.github.composefluent.winrt.ide.nuget

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/** Edit only declarations whose ownership and arguments are explicit in the Kotlin PSI. */
object WinRTNuGetDependencies {
    fun declaration(id: String, version: String, projection: Boolean): String {
        require(Regex("[A-Za-z0-9_.-]+").matches(id)) { "Enter a valid NuGet package ID." }
        require(Regex("[A-Za-z0-9_.+\\-]+").matches(version)) { "Select an exact package version." }
        return "nugetPackage(\"$id\", \"$version\")" + if (projection) "" else " { generateProjection = false }"
    }

    fun apply(project: Project, file: VirtualFile, id: String, version: String?, projection: Boolean) {
        val replacement = version?.let { declaration(id, it, projection) }
        WriteCommandAction.writeCommandAction(project).withName("Change WinRT NuGet dependency").run<RuntimeException> {
            val manager = PsiDocumentManager.getInstance(project)
            manager.getDocument(requireNotNull(PsiManager.getInstance(project).findFile(file)))?.let(manager::commitDocument)
            val script = PsiManager.getInstance(project).findFile(file) as? KtFile
                ?: error("Edit this dependency in build.gradle.kts.")
            val windows = script.script?.blockExpression?.children.orEmpty().map { if (it is KtScriptInitializer) it.body else it }
                .filterIsInstance<KtCallExpression>()
                .filter { it.calleeExpression?.text == "windows" }.singleOrNull()
                ?: error("A single top-level windows { } block is required. Use the suggested declaration in the source editor.")
            val body = windows.lambdaArguments.singleOrNull()?.getLambdaExpression()?.bodyExpression
                ?: error("Open the windows configuration in the source editor.")
            val blocks = body.statements.filterIsInstance<KtCallExpression>().filter { it.calleeExpression?.text == "packageReferences" }
            require(blocks.size <= 1) { "Multiple packageReferences blocks require editing in the source editor." }
            val packages = blocks.singleOrNull()?.lambdaArguments?.singleOrNull()?.getLambdaExpression()?.bodyExpression
            require(blocks.isEmpty() || packages != null) { "This packageReferences expression requires editing in the source editor." }
            val calls = packages?.statements.orEmpty().filterIsInstance<KtCallExpression>().filter { it.calleeExpression?.text == "nugetPackage" }
            require(packages == null || PsiTreeUtil.findChildrenOfType(packages, KtCallExpression::class.java)
                .count { it.calleeExpression?.text == "nugetPackage" } == calls.size) {
                "Conditional package declarations require editing in the source editor."
            }
            // A computed ID could already name the requested package; never add a duplicate blindly.
            require(calls.all { literal(it.valueArguments.firstOrNull()?.getArgumentExpression()) != null &&
                it.valueArguments.firstOrNull()?.getArgumentName()?.asName?.asString() in listOf(null, "packageId") }) {
                "Computed package IDs require editing in the source editor."
            }
            val matching = calls.filter { literal(it.valueArguments.firstOrNull()?.getArgumentExpression()).equals(id, true) }
            require(matching.size <= 1) { "Duplicate package declarations require editing in the source editor." }
            val factory = KtPsiFactory(project)
            val current = matching.singleOrNull()
            if (current != null) {
                require(current.valueArgumentList?.arguments?.size == 2 && literal(current.valueArguments[1].getArgumentExpression()) != null &&
                    current.valueArguments[1].getArgumentName()?.asName?.asString() in listOf(null, "version")) {
                    "A computed version requires editing in the source editor."
                }
                if (replacement == null) current.delete() else {
                    // Updating the literal retains package-specific options, comments and lambda body.
                    current.valueArguments[1].getArgumentExpression()!!.replace(factory.createExpression("\"$version\""))
                }
            } else if (replacement != null) {
                val document = requireNotNull(manager.getDocument(script))
                if (packages != null) {
                    val anchor = (packages.parent as? KtFunctionLiteral)?.rBrace ?: error("Complete the packageReferences block before editing.")
                    document.insertString(anchor.textRange.startOffset, "\n        $replacement\n    ")
                } else {
                    val anchor = (body.parent as? KtFunctionLiteral)?.rBrace ?: error("Complete the windows block before editing.")
                    document.insertString(anchor.textRange.startOffset, "\n    packageReferences {\n        $replacement\n    }\n")
                }
                manager.commitDocument(document)
            } else error("This package is not declared directly in this module.")
        }
    }

    private fun literal(expression: KtExpression?): String? = (expression as? KtStringTemplateExpression)
        ?.takeIf { it.entries.all { entry -> entry is KtLiteralStringTemplateEntry } }
        ?.entries?.joinToString("") { it.text }
}
