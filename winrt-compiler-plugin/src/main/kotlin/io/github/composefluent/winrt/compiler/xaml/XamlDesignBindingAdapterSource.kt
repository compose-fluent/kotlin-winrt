package io.github.composefluent.winrt.compiler.xaml

/** SDK-only tree/DataContext adaptation for the common generated-connector session. */
internal fun xamlDesignBindingHelpers(framework: io.github.composefluent.winrt.runtime.XamlFramework): String = """
          var designBindings: WinUIXamlDesignBindingSession? = null
          var stopModelContext: (() -> Unit)? = null
          data class DesignTarget(val value: DependencyObject, val scope: WinUIXamlBindingScope?)
          val designTargets = mutableMapOf<Int, MutableList<DesignTarget>>()
          val designScopes = mutableListOf<Pair<WinUIXamlBindingScope, () -> Any?>>()
          fun designMarker(element: FrameworkElement, key: String): String = element.resources?.let {
            if (it.containsKey(key)) it[key] as? String else null
          }.orEmpty()
          fun sameDesignObject(first: Any, second: Any): Boolean = first === second ||
            (first as? IWinRTObject)?.nativeObject?.let { a -> (second as? IWinRTObject)?.nativeObject?.let(a::sameIdentity) } == true
          fun beginDesignBindings(root: FrameworkElement) {
            stopModelContext?.invoke(); stopModelContext = null
            designTargets.clear(); designScopes.clear()
            val componentMarker = designMarker(root, WinUIXamlHotReloadProtocol.PREVIEW_COMPONENT_KEY)
            val component = componentMarker.substringBefore('|')
            if (component.isEmpty() || designBindings?.className != component) {
              designBindings?.let { old -> old.close(); runCatching { old.owner.asWinRT<Window>().close() } }
              designBindings = null
            }
            if (component.isEmpty()) return
            if (designBindings == null) {
              designBindings = WinUIXamlDesignBindingSession.create(component)
            }
            val session = requireNotNull(designBindings)
            if ('|' in componentMarker) session.validateDocument(componentMarker.substringAfter('|'))
            session.beginDocument(); designTargets.clear(); designScopes.clear()
            val source = runCatching { session.owner.asWinRT<Window>().content?.asWinRT<FrameworkElement>() }.getOrNull()
              ?: runCatching { session.owner.asWinRT<FrameworkElement>() }.getOrNull()
            // The wrapper inherits the actual project's model; explicit XAML or
            // design DataContext values on descendants keep ordinary precedence.
            root.dataContext = source?.dataContext
            if (source != null) {
              fun changed(sender: FrameworkElement?, args: DataContextChangedEventArgs?) { root.dataContext = args?.newValue }
              val handler = ::changed
              source.dataContextChanged.add(handler)
              stopModelContext = { source.dataContextChanged.remove(handler) }
            }
          }
          fun bindDesignTree(root: FrameworkElement) {
            val session = designBindings
            var previous = -1
            repeat(4) {
              var visited = 0
              var pageChanged = false
              fun visit(value: DependencyObject, inherited: WinUIXamlBindingScope?, depth: Int) {
                require(++visited <= 65536 && depth <= 128) { "The design binding tree exceeds its traversal budget." }
                val element = runCatching { value.asWinRT<FrameworkElement>() }.getOrNull()
                var scope = inherited
                if (element != null) {
                  if (root.xamlRoot != null && designMarker(element, WinUIXamlHotReloadProtocol.PREVIEW_POPUP_OPEN_KEY) == "True") {
                    val popup = element.asWinRT<${if (framework == io.github.composefluent.winrt.runtime.XamlFramework.WinUI) "microsoft.ui.xaml" else "windows.ui.xaml"}.controls.primitives.Popup>()
                    popup.xamlRoot = root.xamlRoot
                    if (!popup.isOpen) popup.isOpen = true
                  }
                  val markers = designMarker(element, WinUIXamlHotReloadProtocol.PREVIEW_CONNECTION_KEY)
                  if (session != null) for (marker in markers.split(';').filter { it.isNotBlank() }) {
                    val id = marker.substringBefore(':').toInt()
                    val targets = designTargets.getOrPut(id) { mutableListOf() }
                    val connected = targets.firstOrNull { sameDesignObject(it.value, value) }
                    if (connected != null) scope = connected.scope
                    else {
                      val before = scope
                      val controlTemplate = session.isControlTemplate(id)
                      val bindingTarget = if (controlTemplate) requireNotNull(VisualTreeHelper.getParent(value)) { "The control template has no owning control." } else value
                      scope = session.connect(bindingTarget, marker, scope)
                      targets += DesignTarget(value, scope)
                      if (scope == null) pageChanged = true
                      else if (scope !== before) {
                        val created = scope
                        designScopes += created to { if (controlTemplate) bindingTarget else element.dataContext }
                        if (!controlTemplate) {
                          fun changed(sender: FrameworkElement?, args: DataContextChangedEventArgs?) { created.initialize(args?.newValue) }
                          val handler = ::changed
                          element.dataContextChanged.add(handler)
                          created.state.eventConnections.trackEvent(id, element, handler) { target, callback -> target.dataContextChanged.remove(callback) }
                        }
                      }
                    }
                  }
                }
                val count = VisualTreeHelper.getChildrenCount(value)
                require(count in 0..65536) { "The design binding collection exceeds its traversal budget." }
                for (index in 0 until count) VisualTreeHelper.getChild(value, index)?.let { visit(it, scope, depth + 1) }
                // Popup.Child is outside VisualTreeHelper's ordinary children.
                val reference = (value as? IWinRTObject)?.nativeObject
                if (reference?.tryQueryInterface(${if (framework == io.github.composefluent.winrt.runtime.XamlFramework.WinUI) "microsoft.ui.xaml" else "windows.ui.xaml"}.controls.primitives.Popup.DEFAULT_INTERFACE_IID)?.use { true } == true) {
                  value.asWinRT<${if (framework == io.github.composefluent.winrt.runtime.XamlFramework.WinUI) "microsoft.ui.xaml" else "windows.ui.xaml"}.controls.primitives.Popup>().child?.let { visit(it, scope, depth + 1) }
                }
              }
              visit(root, null, 0)
              if (pageChanged) session?.updatePage()
              designScopes.forEach { (scope, data) ->
                if (scope.dataRoot !== data()) {
                  var phase = 0
                  do { phase = scope.processBindings(data(), phase) } while (phase >= 0)
                }
              }
              root.updateLayout()
              val count = designTargets.values.sumOf { it.size }
              if (count == previous) return
              previous = count
            }
          }
""".trimIndent()
