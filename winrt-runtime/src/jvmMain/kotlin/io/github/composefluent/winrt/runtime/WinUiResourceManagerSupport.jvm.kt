package io.github.composefluent.winrt.runtime

import java.nio.file.Files
import java.nio.file.Path

object WinUiResourceManagerSupport {
    private val iidIApplication2: Guid = guidOf("469e6d36-2e11-5b06-9e0a-c5eef0cf8f12")
    private val iidIResourceManagerRequestedEventArgs: Guid = guidOf("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a")
    private val iidIResourceManagerFactory: Guid = guidOf("d6acf18f-458a-535b-a5c4-ac2dc4e49099")
    private val iidIResourceManager: Guid = guidOf("ac2291ef-81be-5c99-a0ae-bcee0180b8a8")
    private const val resourceManagerClassName = "Microsoft.Windows.ApplicationModel.Resources.ResourceManager"
    private const val typedEventHandlerGuid = "9de1c534-6ae1-11e0-84e1-18a905bcc53f"
    private const val resourceManagerRequestedEventArgsClassName = "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs"
    private const val addResourceManagerRequestedSlot = 6
    private const val removeResourceManagerRequestedSlot = 7
    private const val createResourceManagerSlot = 6
    private const val setCustomResourceManagerSlot = 7

    class Registration internal constructor(
        private val applicationPointer: RawComPtr,
        private val token: EventRegistrationToken,
        private val delegateHandle: WinRtDelegateHandle,
        private val resourceManagerReference: ComObjectReference,
        val priPath: Path,
    ) : AutoCloseable {
        override fun close() {
            runCatching {
                IUnknownReference(applicationPointer, preventReleaseOnDispose = true).use { application ->
                    application.queryInterface(iidIApplication2).getOrThrow().use { application2 ->
                        StandardDelegates.removeEventHandler(
                            objectReference = application2,
                            removeHandlerSlot = removeResourceManagerRequestedSlot,
                            token = token,
                        )
                    }
                }
            }
            try {
                delegateHandle.close()
            } finally {
                resourceManagerReference.close()
            }
        }
    }

    fun register(applicationPointer: RawComPtr): Registration? {
        val runtimeAssetsRoot = WinRtRuntimeAssets.discoverRuntimeAssetsRoot()?.toAbsolutePath() ?: return null
        return register(applicationPointer, runtimeAssetsRoot)
    }

    fun register(
        applicationPointer: RawComPtr,
        runtimeAssetsRoot: Path,
    ): Registration? {
        if (!PlatformRuntime.isWindows || PlatformAbi.isNull(applicationPointer.asRawAddress())) {
            return null
        }
        val priPath = preferredPriPath(runtimeAssetsRoot) ?: return null
        val resourceManagerReference = createResourceManager(priPath)
        return runCatching {
            val delegateHandle = WinRtDelegateBridge.createUnitDelegate(
                iid = resourceManagerRequestedHandlerIid(),
                parameterKinds = listOf(
                    WinRtDelegateValueKind.IUNKNOWN,
                    WinRtDelegateValueKind.IUNKNOWN,
                ),
            ) { args ->
                val requestedArgs = (args[1] as? IUnknownReference)
                    ?.queryInterface(iidIResourceManagerRequestedEventArgs)
                    ?.getOrThrow()
                    ?: error("ResourceManagerRequested event did not provide event args.")
                requestedArgs.use { eventArgs ->
                    HResult(
                        ComVtableInvoker.invokeArgs(
                            eventArgs.pointer,
                            setCustomResourceManagerSlot,
                            resourceManagerReference.pointer.asRawAddress(),
                        ),
                    ).requireSuccess("ResourceManagerRequestedEventArgs.SetCustomResourceManager")
                }
            }

            val token = IUnknownReference(applicationPointer, preventReleaseOnDispose = true).use { application ->
                application.queryInterface(iidIApplication2).getOrThrow().use { application2 ->
                    delegateHandle.createReference().use { handlerReference ->
                        StandardDelegates.addEventHandler(
                            objectReference = application2,
                            addHandlerSlot = addResourceManagerRequestedSlot,
                            handler = handlerReference,
                        )
                    }
                }
            }
            Registration(
                applicationPointer = applicationPointer,
                token = token,
                delegateHandle = delegateHandle,
                resourceManagerReference = resourceManagerReference,
                priPath = priPath,
            )
        }.getOrElse { error ->
            resourceManagerReference.close()
            throw error
        }
    }

    internal fun preferredPriPath(runtimeAssetsRoot: Path): Path? =
        listOf(
            runtimeAssetsRoot.resolve("Microsoft.UI.pri"),
            runtimeAssetsRoot.resolve("Microsoft.UI.Xaml.Controls.pri"),
        ).firstOrNull(Files::isRegularFile)

    internal fun resourceManagerRequestedHandlerIid(): Guid {
        val argsSignature = WinRtTypeSignature.runtimeClass(
            resourceManagerRequestedEventArgsClassName,
            WinRtTypeSignature.guid(iidIResourceManagerRequestedEventArgs),
        )
        val signature = WinRtTypeSignature.parameterizedInterface(
            typedEventHandlerGuid,
            WinRtTypeSignature.object_(),
            argsSignature,
        )
        return ParameterizedInterfaceId.createFromSignature(signature)
    }

    private fun createResourceManager(priPath: Path): ComObjectReference {
        val factory = ActivationFactory.get(resourceManagerClassName, iidIResourceManagerFactory)
        return factory.use { resourceManagerFactory ->
            HString.create(priPath.toAbsolutePath().toString()).use { priFileName ->
                RawObjectAbiSupport.nullableObjectResult(
                    invoke = { resultOut ->
                        ComVtableInvoker.invokeArgs(
                            resourceManagerFactory.pointer,
                            createResourceManagerSlot,
                            priFileName.handle,
                            resultOut,
                        )
                    },
                    wrap = { createdResourceManager ->
                        IUnknownReference(createdResourceManager.asRawComPtr())
                            .queryInterface(iidIResourceManager)
                            .getOrThrow()
                    },
                ) ?: throw WinRtUnsupportedOperationException(
                    "ResourceManager.CreateResourceManager returned a null object reference.",
                    KnownHResults.E_POINTER,
                )
            }
        }
    }
}
