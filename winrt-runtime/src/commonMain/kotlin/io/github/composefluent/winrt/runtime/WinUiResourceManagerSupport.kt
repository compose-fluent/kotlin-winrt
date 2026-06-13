package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

object WinUiResourceManagerSupport {
    private val iidIApplication2: Guid = guidOf("469e6d36-2e11-5b06-9e0a-c5eef0cf8f12")
    private val iidIResourceManagerFactory: Guid = guidOf("d6acf18f-458a-535b-a5c4-ac2dc4e49099")
    private val iidIResourceManager: Guid = guidOf("ac2291ef-81be-5c99-a0ae-bcee0180b8a8")
    private const val resourceManagerClassName = "Microsoft.Windows.ApplicationModel.Resources.ResourceManager"
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
        if (!PlatformRuntime.isWindows || PlatformAbi.isNull(applicationPointer)) {
            return null
        }
        val priPath = preferredPriPath() ?: return null
        val resourceManagerReference = createResourceManager(priPath)
        return runCatching {
            val delegateHandle = WinRtDelegateBridge.createUnitDelegate(
                iid = WinUiResourceManagerRuntime.resourceManagerRequestedHandlerIid(),
                parameterKinds = listOf(
                    WinRtDelegateValueKind.IUNKNOWN,
                    WinRtDelegateValueKind.IUNKNOWN,
                ),
            ) { args ->
                val requestedArgs = (args[1] as? IUnknownReference)
                    ?.queryInterface(WinUiResourceManagerRuntime.resourceManagerRequestedEventArgsIid)
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

    internal fun preferredPriPath(): Path? =
        preferredPriPath { fileName -> Path(WinRtPlatformApi.resolveModulePathRaw(fileName)) }

    internal fun preferredPriPath(runtimeAssetsRoot: Path): Path? =
        preferredPriPath { fileName -> Path(runtimeAssetsRoot, fileName) }

    internal fun preferredPriPath(resolve: (String) -> Path): Path? =
        listOf(
            "resources.pri",
            "Microsoft.UI.pri",
            "Microsoft.UI.Xaml.Controls.pri",
        )
            .map(resolve)
            .firstOrNull { it.isRegularFile() }

    private fun createResourceManager(priPath: Path): ComObjectReference {
        val factory = ActivationFactory.get(resourceManagerClassName, iidIResourceManagerFactory)
        return factory.use { resourceManagerFactory ->
            HString.create(priPath.canonicalString()).use { priFileName ->
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

