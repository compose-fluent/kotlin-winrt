package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.WinRtProjectionSupport

class JsonError private constructor() {
    companion object {
        private const val RUNTIME_CLASS_NAME = "Windows.Data.Json.JsonError"
        private const val GET_STATUS_SLOT = 6
        private val IID_IJSON_ERROR_STATICS = Guid("FE616766-BF27-4064-87B7-6563BB11CE2E")

        fun getJsonStatus(hResult: Int): JsonErrorStatus =
            WinRtProjectionSupport.withStaticInterface(RUNTIME_CLASS_NAME, IID_IJSON_ERROR_STATICS) {
                JsonErrorStatus.fromAbiValue(it.invokeUInt32MethodWithInt32Arg(GET_STATUS_SLOT, hResult).toInt())
            }
    }
}
