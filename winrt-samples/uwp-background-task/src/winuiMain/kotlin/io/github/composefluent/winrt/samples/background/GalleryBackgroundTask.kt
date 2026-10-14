package io.github.composefluent.winrt.samples.background

import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
import windows.applicationmodel.background.IBackgroundTaskInstance
import windows.storage.ApplicationData

// Same boundary as .cswinrt/src/Samples/BgTaskComponent/ToastBgTask.cs:
// Windows activates the authored runtime class and calls IBackgroundTask.Run.
@WinRTAuthoredRuntimeClass(interfaceNames = ["windows.applicationmodel.background.IBackgroundTask"])
class GalleryBackgroundTask {
    fun run(taskInstance: IBackgroundTaskInstance?) {
        val instance = checkNotNull(taskInstance)
        val deferral = instance.getDeferral()
        try {
            val values = checkNotNull(ApplicationData.current?.localSettings?.values)
            values["LastOutOfProcessBackgroundActivation"] = instance.task?.name.orEmpty()
        } finally { deferral?.complete() }
    }
}
