package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.WinRTXamlPageDeclaration
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId

/** Named fields use the literal XAML name, as CSharpPagePass1.tt does.
 * Kotlin cannot safely hide a projected mutable property with a read-only field.
 */
internal class XamlNameCheckers(session: FirSession, pages: Map<ClassId, WinRTXamlPageDeclaration>) :
    FirAdditionalCheckersExtension(session) {
    override val declarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers = setOf(object : FirRegularClassChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirRegularClass) {
                val page = pages[declaration.symbol.classId] ?: return
                val inherited = mutableMapOf<String, ClassId>()
                val visited = mutableSetOf<ClassId>()
                fun visit(type: FirClassSymbol<*>) {
                    if (!visited.add(type.classId)) return
                    type.processAllDeclarations(session) { member ->
                        if (member is FirPropertySymbol && member.resolvedStatus.visibility != Visibilities.Private &&
                            member.resolvedStatus.visibility != Visibilities.PrivateToThis) {
                            inherited.putIfAbsent(member.name.asString(), type.classId)
                        }
                    }
                    type.resolvedSuperTypes.forEach { superType ->
                        (superType.classId?.let(session.symbolProvider::getClassLikeSymbolByClassId) as? FirClassSymbol<*>)?.let(::visit)
                    }
                }
                declaration.symbol.resolvedSuperTypes.forEach { superType ->
                    (superType.classId?.let(session.symbolProvider::getClassLikeSymbolByClassId) as? FirClassSymbol<*>)?.let(::visit)
                }
                page.connections.filter { !it.isTemplateChild && it.fieldName != null }.forEach { connection ->
                    val owner = inherited[connection.fieldName] ?: return@forEach
                    reporter.reportOn(declaration.source, FirErrors.UNSUPPORTED,
                        "${page.resourcePath}:${connection.location.line}:${connection.location.column}: " +
                            "XAML x:Name '${connection.fieldName}' conflicts with existing property '${owner.asSingleFqName()}.${connection.fieldName}'. Rename the XAML element explicitly.")
                }
            }
        })
    }
}
