package io.github.composefluent.winrt.compiler.xaml

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** The three projected interfaces match CSharpPagePass2's generated template binding class. */
internal fun writeXamlBindingSupportSource(root: Path, assemblyName: String? = null) {
    val suffix = assemblyName?.replace(Regex("[^A-Za-z0-9_]"), "_")?.let { "_$it" }.orEmpty()
    val className = "KotlinXamlBindingScopeConnector$suffix"
    val file = root.resolve("io/github/composefluent/winrt/generated/xaml/$className.kt")
    Files.createDirectories(file.parent)
    val pageClassName = "KotlinXamlPageBindingConnector$suffix"
    file.resolveSibling("$pageClassName.kt").writeText("""
        package io.github.composefluent.winrt.generated.xaml

        import io.github.composefluent.winrt.runtime.*
        import microsoft.ui.xaml.markup.IComponentConnector

        // The SDK invokes ordinary Connect and binding Connect independently.
        // Kotlin's native composition owner retains the live page. Forward
        // weakly, as template scopes do, to avoid a COM/managed ownership cycle.
        internal class $pageClassName(owner: WinUiXamlPageBindingOwner) : IComponentConnector {
            private val owner = WeakReference(owner)
            override fun connect(connectionId: Int, target: Any?) {
                owner.tryGetTarget()?._kotlinXamlConnectBindings(connectionId, target)
            }
            override fun getBindingConnector(connectionId: Int, target: Any?): IComponentConnector? =
                owner.tryGetTarget()?.asWinRT<IComponentConnector>()?.getBindingConnector(connectionId, target)
        }
    """.trimIndent() + "\n")
    file.writeText("""
        package io.github.composefluent.winrt.generated.xaml

        import io.github.composefluent.winrt.runtime.*
        import microsoft.ui.xaml.*
        import microsoft.ui.xaml.controls.ContainerContentChangingEventArgs
        import microsoft.ui.xaml.markup.*

        internal class $className(
            owner: WinUiXamlBindingScopeOwner,
            scopeId: Int,
            target: Any?,
            controlTemplate: Boolean,
        ) : IComponentConnector, IDataTemplateComponent, IDataTemplateExtension {
            private val scope = WinUiXamlBindingScope(owner, scopeId)
            private val root = requireNotNull(target).asWinRT<FrameworkElement>()
            private val isControlTemplate = controlTemplate
            private var dataContextHandlerRemoved = false

            init {
                // GetBindingConnector sets the source without initializing it:
                // the SDK has not connected the template children yet.
                scope.setDataRoot(if (controlTemplate) root else root.dataContext)
                if (!controlTemplate) {
                    connect(scopeId, target)
                    root.dataContextChanged.add(::dataContextChanged)
                    DataTemplate.setExtensionInstance(root, this)
                }
                XamlBindingHelper.setDataTemplateComponent(root, this)
                // CSharpPagePass2 subscribes Loading only for the file root.
                // Template instances are initialized and recycled by the SDK.
            }

            private fun dataContextChanged(sender: FrameworkElement?, args: DataContextChangedEventArgs?) {
                scope.initialize(requireNotNull(args).newValue)
            }

            override fun connect(connectionId: Int, target: Any?) {
                // CSharpPagePass2.Connect uses the ControlTemplate connection
                // as the signal that its children have finished connecting.
                val complete = isControlTemplate && connectionId == scope.scopeId
                scope.connect(connectionId, if (complete) root else target)
                if (complete) scope.initialize(root)
                if (scope.phaseOf(connectionId) != 0) XamlBindingHelper.suspendRendering(requireNotNull(target).asWinRT<UIElement>())
            }
            override fun getBindingConnector(connectionId: Int, target: Any?): IComponentConnector? =
                if (connectionId == scope.scopeId) this else
                    scope.createConnector(connectionId, target)?.asWinRT<IComponentConnector>()

            override fun processBindings(item: Any?, itemIndex: Int, phase: Int, nextPhase: WinRTOut<Int>) {
                if (phase == 0 && !dataContextHandlerRemoved && !isControlTemplate) {
                    root.dataContextChanged.remove(::dataContextChanged)
                    dataContextHandlerRemoved = true
                }
                if (phase != 0) scope.phaseTargets(phase).forEach { XamlBindingHelper.resumeRendering(it.asWinRT<UIElement>()) }
                nextPhase.value = scope.processBindings(item, phase)
            }

            override fun processBindings(arg: ContainerContentChangingEventArgs?): Int {
                requireNotNull(arg)
                val next = WinRTOut<Int>()
                processBindings(arg.item, arg.itemIndex, arg.phase.toInt(), next)
                return next.value
            }

            override fun processBinding(phase: UInt): Boolean =
                throw UnsupportedOperationException("Use IDataTemplateComponent.ProcessBindings")
            override fun resetTemplate() = recycle()
            override fun recycle() {
                scope.recycle()
                scope.phasedTargets().forEach { XamlBindingHelper.suspendRendering(it.asWinRT<UIElement>()) }
            }
        }
    """.trimIndent() + "\n")
}
