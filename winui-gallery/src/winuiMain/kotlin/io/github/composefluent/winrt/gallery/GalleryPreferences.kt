package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.WinRTRuntimeException
import microsoft.windows.storage.ApplicationData
import windows.graphics.SizeInt32
import windows.applicationmodel.Package

internal object GalleryPreferences {
    val packaged: Boolean by lazy {
        try {
            checkNotNull(checkNotNull(Package.current).id).fullName
            true
        } catch (error: WinRTRuntimeException) {
            // HRESULT_FROM_WIN32(APPMODEL_ERROR_NO_PACKAGE), not an arbitrary storage failure.
            if (error.hResult?.value?.toUInt() != 0x80073D54u) throw error
            false
        }
    }
    private val data by lazy {
        if (packaged) ApplicationData.getDefault()
        else ApplicationData.getForUnpackaged("ComposeFluent", "KotlinWinUIGallery")
    }
    private val values get() = checkNotNull(checkNotNull(data!!.localSettings).values)
    fun text(key: String, fallback: String = ""): String =
        if (values.containsKey(key)) values[key] as? String ?: fallback else fallback
    fun put(key: String, value: String) { values[key] = value }
    fun flag(key: String): Boolean = text(key) == "true"
    fun putFlag(key: String, value: Boolean) = put(key, value.toString())
    fun windowSize(defaultWidth: Int, defaultHeight: Int): SizeInt32 {
        val saved = text("WindowSize").split(',')
        val width = saved.getOrNull(0)?.toIntOrNull() ?: return SizeInt32(defaultWidth, defaultHeight)
        val height = saved.getOrNull(1)?.toIntOrNull() ?: return SizeInt32(defaultWidth, defaultHeight)
        return if (width >= 640 && height >= 500) SizeInt32(width, height) else SizeInt32(defaultWidth, defaultHeight)
    }
    fun putWindowSize(size: SizeInt32) = put("WindowSize", "${size.width},${size.height}")
    fun routes(key: String): List<String> {
        val ids = GalleryCatalog.pages.mapTo(hashSetOf()) { it.id }
        return text(key).split('\n').filter { it in ids }.distinct()
    }
    fun putRoutes(key: String, values: Collection<String>) = put(key, values.joinToString("\n"))
}
