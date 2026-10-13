package io.github.composefluent.winrt.gallery

import kotlinx.coroutines.*
import io.github.composefluent.winrt.runtime.await
import kotlin.coroutines.CoroutineContext
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.controls.ContentDialog

/** UI work belongs to the page and is cancelled when the page leaves the visual tree. */
internal class GalleryPageTasks(private val owner: FrameworkElement) {
    private val queue = checkNotNull(owner.galleryDispatcher)
    private val dispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            check(queue.tryEnqueue { block.run() }) { "The Gallery dispatcher has shut down" }
        }
    }
    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var dialog: ContentDialog? = null
    init {
        owner.loaded.add { _, _ ->
            if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + dispatcher)
        }
        owner.unloaded.add { _, _ -> dialog?.hide(); scope.cancel() }
    }
    fun launch(action: suspend CoroutineScope.() -> Unit): Job =
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (owner.xamlRoot == null || dialog != null) {
                    println("Kotlin WinUI Gallery: ${error.message}")
                    return@launch
                }
                val message = ContentDialog().apply {
                    xamlRoot = owner.xamlRoot; title = "Unable to complete the operation"
                    content = error.message.orEmpty(); closeButtonText = "Close"
                }
                dialog = message
                try { message.showAsync()!!.await() }
                finally { message.hide(); dialog = null }
            }
        }
}
