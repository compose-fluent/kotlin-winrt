package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

private object WinRTProjectionInboundJvmEntryPoints {
    private val arena = Arena.ofShared()
    private val linker = Linker.nativeLinker()
    private val entryPoints = ConcurrentHashMap<Pair<String, String>, RawAddress>()

    fun getOrCreate(ownerClassName: String, methodName: String): RawAddress =
        entryPoints.computeIfAbsent(ownerClassName to methodName) {
            val owner = Class.forName(ownerClassName, true, Thread.currentThread().contextClassLoader)
            val method = owner.declaredMethods.single { candidate ->
                candidate.name == methodName && candidate.returnType == Int::class.javaPrimitiveType
            }
            val parameterTypes = method.parameterTypes
            val lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
            val target = lookup.findStatic(
                owner,
                methodName,
                MethodType.methodType(Int::class.javaPrimitiveType, parameterTypes.toList()),
            )
            val layouts = parameterTypes.map(::layoutForCarrier)
            linker.upcallStub(
                target,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, *layouts.toTypedArray()),
                arena,
            ).asRawAddress()
        }

    private fun layoutForCarrier(type: Class<*>): MemoryLayout = when (type) {
        java.lang.Byte.TYPE -> ValueLayout.JAVA_BYTE
        java.lang.Short.TYPE -> ValueLayout.JAVA_SHORT
        java.lang.Integer.TYPE -> ValueLayout.JAVA_INT
        java.lang.Long.TYPE -> ValueLayout.JAVA_LONG
        java.lang.Float.TYPE -> ValueLayout.JAVA_FLOAT
        java.lang.Double.TYPE -> ValueLayout.JAVA_DOUBLE
        else -> error("Unsupported JVM inbound ABI carrier ${type.name}.")
    }
}

@PublishedApi
internal fun winRTJvmProjectionInboundEntryPoint(
    ownerClassName: String,
    methodName: String,
): RawAddress = WinRTProjectionInboundJvmEntryPoints.getOrCreate(ownerClassName, methodName)
