package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.WeakHashMap

/**
 * Owns generated JVM inbound stubs for an unloadable projection class loader.
 * Create this scope before registering any inbound stubs from [classLoader]. Close it only after
 * every COM object and native user of those stubs has been released. Raw ABI pointers do not retain
 * this scope. Closing invalidates all its pointers and disallows further registration for the loader.
 *
 * CsWinRT keeps vtables with their managed types. JVM FFM upcalls additionally require an arena;
 * explicit lifetime is necessary because an upcall target itself retains its declaring class loader.
 * Without a scope, generated inbound stubs retain the existing process lifetime.
 */
class WinRTJvmProjectionInboundScope(classLoader: ClassLoader) : AutoCloseable {
    private var release: (() -> Unit)? = WinRTProjectionInboundJvmEntryPoints.open(classLoader)

    @Synchronized
    override fun close() {
        release?.invoke()
        release = null
    }
}

private object WinRTProjectionInboundJvmEntryPoints {
    private class Entries {
        val arena = Arena.ofShared()
        val pointers = mutableMapOf<Pair<Class<*>, String>, RawAddress>()
    }
    private val defaults = Entries()
    private val scopes = mutableMapOf<ClassLoader, Entries>()
    private val closedLoaders = WeakHashMap<ClassLoader, Boolean>()
    private val linker = Linker.nativeLinker()

    @Synchronized
    fun open(loader: ClassLoader): () -> Unit {
        check(loader !in scopes && loader !in closedLoaders) { "Inbound scope already opened or closed for this loader." }
        check(defaults.pointers.keys.none { it.first.classLoader === loader }) {
            "Create the inbound scope before registering callbacks for this loader."
        }
        val entries = Entries()
        scopes[loader] = entries
        return { close(loader, entries) }
    }

    @Synchronized
    private fun close(loader: ClassLoader, entries: Entries) {
        check(scopes[loader] === entries)
        entries.arena.close()
        entries.pointers.clear()
        scopes.remove(loader)
        closedLoaders[loader] = true
    }

    @Synchronized
    fun getOrCreate(owner: Class<*>, methodName: String, caller: MethodHandles.Lookup): RawAddress {
        val loader = owner.classLoader
        check(loader !in closedLoaders) { "The inbound scope for this loader has been closed." }
        val entries = scopes[loader] ?: defaults
        return entries.pointers.getOrPut(owner to methodName) {
            val method = owner.declaredMethods.single { candidate ->
                candidate.name == methodName && candidate.returnType == Int::class.javaPrimitiveType
            }
            val parameterTypes = method.parameterTypes
            val lookup = MethodHandles.privateLookupIn(owner, caller)
            val target = lookup.findStatic(
                owner,
                methodName,
                MethodType.methodType(Int::class.javaPrimitiveType, parameterTypes.toList()),
            )
            val layouts = parameterTypes.map(::layoutForCarrier)
            linker.upcallStub(
                target,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, *layouts.toTypedArray()),
                entries.arena,
            ).asRawAddress()
        }
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
    caller: MethodHandles.Lookup,
): RawAddress = WinRTProjectionInboundJvmEntryPoints.getOrCreate(
    Class.forName(ownerClassName, true, caller.lookupClass().classLoader),
    methodName,
    caller,
)

/** Binary bridge for projections generated before the caller Lookup became explicit. */
@PublishedApi
internal fun winRTJvmProjectionInboundEntryPoint(ownerClassName: String, methodName: String): RawAddress {
    val caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).callerClass
    // Old classpath projections remain valid. Closed JPMS modules must be recompiled to pass their
    // own privileged Lookup; the runtime cannot manufacture that module's private access rights.
    return WinRTProjectionInboundJvmEntryPoints.getOrCreate(
        Class.forName(ownerClassName, true, caller.classLoader), methodName, MethodHandles.lookup(),
    )
}
