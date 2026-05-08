package io.github.composefluent.winrt.runtime

import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Unpackaged WinUI JVM apps need to answer Microsoft.UI.Xaml.Application.ResourceManagerRequested
 * with a MRT ResourceManager backed by the staged Windows App SDK PRI assets. Packaged cswinrt
 * apps normally get this from their app model/resource infrastructure; this is the JVM bootstrap
 * equivalent for the runtime-assets layout staged by the Gradle plugin.
 */
object WinRtWinUiResourceManagerBootstrap {
    private val application2InterfaceId = Guid("469E6D36-2E11-5B06-9E0A-C5EEF0CF8F12")
    private val resourceManagerRequestedEventArgsInterfaceId = Guid("C35F4CF1-FCD6-5C6B-9BE2-4CFAEFB68B2A")
    private val resourceManagerFactoryInterfaceId = Guid("D6ACF18F-458A-535B-A5C4-AC2DC4E49099")
    private val resourceManagerInterfaceId = Guid("AC2291EF-81BE-5C99-A0AE-BCEE0180B8A8")
    private val typedEventHandlerInterfaceId = Guid("9DE1C534-6AE1-11E0-84E1-18A905BCC53F")
    private const val resourceManagerRuntimeClassName = "Microsoft.Windows.ApplicationModel.Resources.ResourceManager"
    private const val resourceManagerRequestedEventArgsRuntimeClassName =
        "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs"
    private const val addResourceManagerRequestedSlot = 6
    private const val removeResourceManagerRequestedSlot = 7
    private const val createResourceManagerFromFileSlot = 6
    private const val setCustomResourceManagerSlot = 7

    class Registration internal constructor(
        private val applicationReference: IUnknownReference,
        private val resourceManagerReference: IUnknownReference,
        private val delegateHandle: WinRtDelegateHandle,
        private val token: EventRegistrationToken,
        val priPath: Path,
    ) : AutoCloseable {
        override fun close() {
            runCatching {
                applicationReference.queryInterface(application2InterfaceId)
                    .getOrThrow()
                    .use { application2 ->
                        StandardDelegates.removeEventHandler(
                            objectReference = application2,
                            removeHandlerSlot = removeResourceManagerRequestedSlot,
                            token = token,
                        )
                    }
            }
            delegateHandle.close()
            resourceManagerReference.close()
            applicationReference.close()
        }
    }

    fun registerForApplication(
        application: IWinRTObject,
        runtimeAssetsRoot: Path? = WinRtWindowsAppSdkBootstrap.discoverRuntimeAssetsRoot(),
    ): Registration? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val priPath = runtimeAssetsRoot?.let(::preferredPriPath) ?: return null
        if (FeatureSwitches.traceCcw) {
            println("winrt-winui-resources: registering pri=${priPath.fileName}")
        }
        val applicationReference = IUnknownReference(
            application.nativeObject.getRefPointer(),
            application.nativeObject.interfaceId,
        )
        val resourceManagerReference = createResourceManager(priPath)
        val delegateHandle = createResourceManagerRequestedDelegate(resourceManagerReference)
        return runCatching {
            val token = applicationReference.queryInterface(application2InterfaceId)
                .getOrThrow()
                .use { application2 ->
                    delegateHandle.createReference().use { delegateReference ->
                        StandardDelegates.addEventHandler(
                            objectReference = application2,
                            addHandlerSlot = addResourceManagerRequestedSlot,
                            handler = delegateReference,
                        )
                    }
                }
            Registration(
                applicationReference = applicationReference,
                resourceManagerReference = resourceManagerReference,
                delegateHandle = delegateHandle,
                token = token,
                priPath = priPath,
            )
        }.getOrElse { error ->
            delegateHandle.close()
            resourceManagerReference.close()
            applicationReference.close()
            throw error
        }
    }

    internal fun preferredPriPath(runtimeAssetsRoot: Path): Path? =
        listOf(
            runtimeAssetsRoot.resolve("resources.pri"),
            runtimeAssetsRoot.resolve("Microsoft.UI.Xaml.Controls.pri"),
            runtimeAssetsRoot.resolve("Microsoft.UI.pri"),
        ).firstOrNull { it.isRegularFile() }

    internal fun resourceManagerRequestedHandlerIid(): Guid {
        val argsSignature = WinRtTypeSignature.runtimeClass(
            resourceManagerRequestedEventArgsRuntimeClassName,
            WinRtTypeSignature.guid(resourceManagerRequestedEventArgsInterfaceId),
        )
        val signature = WinRtTypeSignature.parameterizedInterface(
            typedEventHandlerInterfaceId,
            WinRtTypeSignature.object_(),
            argsSignature,
        )
        return ParameterizedInterfaceId.createFromSignature(signature)
    }

    private fun createResourceManager(priPath: Path): IUnknownReference {
        val factory = ActivationFactory.get(resourceManagerRuntimeClassName, resourceManagerFactoryInterfaceId)
        return factory.use {
            HString.createReference(priPath.toAbsolutePath().toString()).use { fileName ->
                PlatformAbi.confinedScope().use { scope ->
                    val resultOut = PlatformAbi.allocatePointerSlot(scope)
                    HResult(
                        ComVtableInvoker.invokeArgs(
                            instance = factory.pointer,
                            slot = createResourceManagerFromFileSlot,
                            arg0 = fileName.handle,
                            arg1 = resultOut,
                        ),
                    ).requireSuccess("ResourceManager.CreateInstance")
                    val instancePointer = PlatformAbi.readPointer(resultOut)
                    if (PlatformAbi.isNull(instancePointer)) {
                        error("WINRT_E_NULL_ABI_RETURN")
                    }
                    IUnknownReference(instancePointer.asRawComPtr())
                        .use { instance ->
                            instance.queryInterface(resourceManagerInterfaceId)
                                .getOrThrow()
                                .use { resourceManager ->
                                    IUnknownReference(
                                        resourceManager.getRefPointer(),
                                        resourceManagerInterfaceId,
                                    )
                                }
                        }
                }
            }
        }
    }

    private fun createResourceManagerRequestedDelegate(
        resourceManagerReference: IUnknownReference,
    ): WinRtDelegateHandle =
        WinRtDelegateBridge.createUnitDelegate(
            iid = resourceManagerRequestedHandlerIid(),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE),
        ) { arguments ->
            val eventArgs = arguments.getOrNull(1) as? IInspectableReference
                ?: error("ResourceManagerRequested event did not provide event args.")
            if (FeatureSwitches.traceCcw) {
                println("winrt-winui-resources: ResourceManagerRequested")
            }
            eventArgs.use {
                eventArgs.queryInterface(resourceManagerRequestedEventArgsInterfaceId)
                    .getOrThrow()
                    .use { requestedArgs ->
                        HResult(
                            ComVtableInvoker.invokeArgs(
                                instance = requestedArgs.pointer,
                                slot = setCustomResourceManagerSlot,
                                arg0 = PlatformAbi.fromRawComPtr(resourceManagerReference.pointer),
                            ),
                        ).requireSuccess("ResourceManagerRequestedEventArgs.CustomResourceManager")
                    }
            }
        }
}
