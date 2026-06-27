package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.IXamlServiceProvider
import microsoft.ui.xaml.data.DataErrorsChangedEventArgs
import microsoft.ui.xaml.data.DataErrorsChangedEventHandler
import microsoft.ui.xaml.data.ICustomProperty
import microsoft.ui.xaml.data.ICustomPropertyProvider
import microsoft.ui.xaml.data.INotifyDataErrorInfo
import microsoft.ui.xaml.data.INotifyPropertyChanged
import microsoft.ui.xaml.data.PropertyChangedEventArgs
import microsoft.ui.xaml.data.PropertyChangedEventHandler
import microsoft.ui.xaml.input.CanExecuteChangedEventHandler
import microsoft.ui.xaml.input.ICommand
import microsoft.ui.xaml.interop.INotifyCollectionChanged
import microsoft.ui.xaml.interop.NotifyCollectionChangedAction
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventArgs
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventHandler
import windows.foundation.EventRegistrationToken
import windows.foundation.IStringable

import kotlin.reflect.KClass

private const val muxCommandRuntimeTypeName = "Microsoft.UI.Xaml.Input.ICommand"
private const val muxCommandInteropAlias = "Microsoft.UI.Xaml.Interop.ICommand"
private const val wuxCommandRuntimeTypeName = "Windows.UI.Xaml.Input.ICommand"
private const val wuxCommandInteropAlias = "Windows.UI.Xaml.Interop.ICommand"
private const val muxPropertyChangedNotifierTypeName = "Microsoft.UI.Xaml.Data.INotifyPropertyChanged"
private const val wuxPropertyChangedNotifierTypeName = "Windows.UI.Xaml.Data.INotifyPropertyChanged"
private const val muxCollectionChangedNotifierTypeName = "Microsoft.UI.Xaml.Interop.INotifyCollectionChanged"
private const val wuxCollectionChangedNotifierTypeName = "Windows.UI.Xaml.Interop.INotifyCollectionChanged"
private const val muxNotifyCollectionChangedEventArgsTypeName = "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs"
private const val wuxNotifyCollectionChangedEventArgsTypeName = "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs"
private const val muxPropertyChangedEventArgsTypeName = "Microsoft.UI.Xaml.Data.PropertyChangedEventArgs"
private const val wuxPropertyChangedEventArgsTypeName = "Windows.UI.Xaml.Data.PropertyChangedEventArgs"
private const val muxDataErrorsChangedEventArgsTypeName = "Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs"
private const val muxDataErrorInfoTypeName = "Microsoft.UI.Xaml.Data.INotifyDataErrorInfo"
private const val muxServiceProviderTypeName = "Microsoft.UI.Xaml.IXamlServiceProvider"
private const val muxCustomPropertyTypeName = "Microsoft.UI.Xaml.Data.ICustomProperty"
private const val wuxCustomPropertyTypeName = "Windows.UI.Xaml.Data.ICustomProperty"
private const val muxCustomPropertyProviderTypeName = "Microsoft.UI.Xaml.Data.ICustomPropertyProvider"
private const val wuxCustomPropertyProviderTypeName = "Windows.UI.Xaml.Data.ICustomPropertyProvider"
private const val stringableTypeName = "Windows.Foundation.IStringable"

private val muxPropertyChangedEventArgsInterfaceId = Guid("63D0C952-396B-54F4-AF8C-BA8724A427BF")
private val wuxPropertyChangedEventArgsInterfaceId = Guid("4F33A9A0-5CF4-47A4-B16F-D7FAAF17457E")
private val dataErrorsChangedEventArgsInterfaceId = Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796")

private fun commandRuntimeTypeName(): String =
    XamlProjectionConfiguration.select(muxCommandRuntimeTypeName, wuxCommandRuntimeTypeName)

private fun propertyChangedNotifierTypeName(): String =
    XamlProjectionConfiguration.select(muxPropertyChangedNotifierTypeName, wuxPropertyChangedNotifierTypeName)

private fun collectionChangedNotifierTypeName(): String =
    XamlProjectionConfiguration.select(muxCollectionChangedNotifierTypeName, wuxCollectionChangedNotifierTypeName)

private fun notifyCollectionChangedEventArgsTypeName(): String =
    XamlProjectionConfiguration.select(muxNotifyCollectionChangedEventArgsTypeName, wuxNotifyCollectionChangedEventArgsTypeName)

private fun propertyChangedEventArgsTypeName(): String =
    XamlProjectionConfiguration.select(muxPropertyChangedEventArgsTypeName, wuxPropertyChangedEventArgsTypeName)

private fun customPropertyTypeName(): String =
    XamlProjectionConfiguration.select(muxCustomPropertyTypeName, wuxCustomPropertyTypeName)

private fun customPropertyProviderTypeName(): String =
    XamlProjectionConfiguration.select(muxCustomPropertyProviderTypeName, wuxCustomPropertyProviderTypeName)

private fun collectionChangedActionTypeName(): String =
    notifyCollectionChangedEventArgsTypeName().substringBeforeLast('.') + ".NotifyCollectionChangedAction"

private fun propertyChangedNotifierInterfaceId(): Guid =
    XamlProjectionConfiguration.select(IID.MUX_INotifyPropertyChanged, IID.WUX_INotifyPropertyChanged)

private fun collectionChangedNotifierInterfaceId(): Guid =
    XamlProjectionConfiguration.select(IID.MUX_INotifyCollectionChanged, IID.WUX_INotifyCollectionChanged)

private fun propertyChangedEventArgsInterfaceId(): Guid =
    XamlProjectionConfiguration.select(muxPropertyChangedEventArgsInterfaceId, wuxPropertyChangedEventArgsInterfaceId)

private fun notifyCollectionChangedEventArgsInterfaceId(): Guid =
    XamlProjectionConfiguration.select(IID.MUX_INotifyCollectionChangedEventArgs, IID.WUX_INotifyCollectionChangedEventArgs)

private fun commandTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(commandRuntimeTypeName(), IID.ICommand)

private fun propertyChangedNotifierTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(propertyChangedNotifierTypeName(), propertyChangedNotifierInterfaceId())

private fun collectionChangedNotifierTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(collectionChangedNotifierTypeName(), collectionChangedNotifierInterfaceId())

private fun dataErrorInfoTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(muxDataErrorInfoTypeName, IID.INotifyDataErrorInfo)

private fun serviceProviderTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(muxServiceProviderTypeName, IID.IServiceProvider)

private fun customPropertyTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(customPropertyTypeName(), IID.ICustomProperty)

private fun customPropertyProviderTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(customPropertyProviderTypeName(), IID.ICustomPropertyProvider)

private fun stringableTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(stringableTypeName, IID.IStringable)

private fun propertyChangedEventArgsTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(propertyChangedEventArgsTypeName(), propertyChangedEventArgsInterfaceId())

private fun notifyCollectionChangedEventArgsTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(notifyCollectionChangedEventArgsTypeName(), notifyCollectionChangedEventArgsInterfaceId())

private fun dataErrorsChangedEventArgsTypeHandle(): WinRTTypeHandle =
    WinRTTypeHandle(muxDataErrorsChangedEventArgsTypeName, dataErrorsChangedEventArgsInterfaceId)

private val commandEventHandlerIid =
    ParameterizedInterfaceId.createFromParameterizedInterface(IID.EventHandler, WinRTTypeSignature.object_())
private fun dataErrorsChangedHandlerIid(): Guid =
    ParameterizedInterfaceId.createFromParameterizedInterface(
        IID.EventHandler,
        WinRTTypeSignature.runtimeClass(
            muxDataErrorsChangedEventArgsTypeName,
            WinRTTypeSignature.guid(dataErrorsChangedEventArgsInterfaceId),
        ),
    )

internal object XamlSystemProjectionRuntimeHooks {
    fun ensureRegistered() {
        registerRcwFactories()
        registerCcwFactories()
    }

    fun closeRuntimeCaches() {
        WinUiXamlMetadataProviderCache.close()
        XamlProjectedObjectValueRoots.close()
    }

    internal fun retainProjectedObjectReferenceForMarshaling(reference: ComObjectReference) {
        XamlProjectedObjectValueRoots.retain(reference)
    }

    internal fun retainedProjectedObjectReferenceCountForTests(): Int =
        XamlProjectedObjectValueRoots.size

    internal fun augmentInspectableDefinition(
        value: Any,
        definition: WinRTCcwDefinition,
    ): WinRTCcwDefinition {
        if (!FeatureSwitches.enableDefaultCustomTypeMappings) {
            return definition
        }
        val withWinUiMetadataProvider = if (shouldExposeWinUiXamlMetadataProvider(definition)) {
            definition.copy(
                interfaceDefinitions = definition.interfaceDefinitions + createWinUiXamlMetadataProviderInterfaceDefinition(),
            )
        } else {
            definition
        }
        if (withWinUiMetadataProvider.interfaceDefinitions.any { it.interfaceId == IID.IStringable }) {
            return withWinUiMetadataProvider
        }
        return withWinUiMetadataProvider.copy(
            interfaceDefinitions = withWinUiMetadataProvider.interfaceDefinitions + createStringableInterfaceDefinition(value),
        )
    }

    internal fun defaultCustomPropertyProviderInterfaceDefinition(
        value: Any,
        existingInterfaceIds: Set<Guid>,
    ): WinRTInspectableInterfaceDefinition? {
        if (!FeatureSwitches.enableDefaultCustomTypeMappings ||
            !FeatureSwitches.enableICustomPropertyProviderSupport ||
            IID.ICustomPropertyProvider in existingInterfaceIds
        ) {
            return null
        }
        return createCustomPropertyProviderDefinition(value).interfaceDefinitions.singleOrNull()
    }

    private fun shouldExposeWinUiXamlMetadataProvider(definition: WinRTCcwDefinition): Boolean =
        XamlProjectionConfiguration.supportsWinUiOnlyTypes &&
            definition.interfaceDefinitions.any { it.interfaceId == WinUiXamlInterfaceIds.IApplicationOverrides } &&
            definition.interfaceDefinitions.none { it.interfaceId == WinUiXamlInterfaceIds.IXamlMetadataProvider }

    private fun createWinUiXamlMetadataProviderInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = WinUiXamlInterfaceIds.IXamlMetadataProvider,
            methods = listOf(
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
                    forwardWinUiXamlMetadataProviderCall(
                        slot = WinUiXamlMetadataProviderSlots.GetXamlType,
                        arg0 = rawArgs[0] as RawAddress,
                        arg1 = rawArgs[1] as RawAddress,
                    )
                },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
                    forwardWinUiXamlMetadataProviderCall(
                        slot = WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName,
                        arg0 = rawArgs[0] as RawAddress,
                        arg1 = rawArgs[1] as RawAddress,
                    )
                },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
                    forwardWinUiXamlMetadataProviderCall(
                        slot = WinUiXamlMetadataProviderSlots.GetXmlnsDefinitions,
                        arg0 = rawArgs[0] as RawAddress,
                        arg1 = rawArgs[1] as RawAddress,
                    )
                },
            ),
        )

    private fun forwardWinUiXamlMetadataProviderCall(
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int {
        if (FeatureSwitches.traceCcw) {
            println("winrt-xaml-metadata: forward slot=$slot")
        }
        val providers = WinUiXamlMetadataProviderCache.getOrCreateAll()
        if (providers.isEmpty()) {
            return KnownHResults.E_NOINTERFACE.value.also {
                if (FeatureSwitches.traceCcw) {
                    println("winrt-xaml-metadata: provider unavailable hr=$it")
                }
            }
        }
        var lastHr = KnownHResults.E_NOINTERFACE.value
        for (provider in providers) {
            if (slot == WinUiXamlMetadataProviderSlots.GetXamlType || slot == WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName) {
                PlatformAbi.writePointer(arg1, PlatformAbi.nullPointer)
            }
            val hr = ComVtableInvoker.invokeArgs(
                instance = provider.pointer,
                slot = slot,
                arg0 = arg0,
                arg1 = arg1,
            )
            lastHr = hr
            if (HResult(hr).isSuccess && (slot == WinUiXamlMetadataProviderSlots.GetXmlnsDefinitions || !PlatformAbi.isNull(PlatformAbi.readPointer(arg1)))) {
                if (FeatureSwitches.traceCcw) {
                    println("winrt-xaml-metadata: forward slot=$slot hr=$hr")
                }
                return hr
            }
        }
        if (FeatureSwitches.traceCcw) {
            println("winrt-xaml-metadata: forward slot=$slot hr=$lastHr")
        }
        return lastHr
    }

    private object WinUiXamlMetadataProviderCache {
        private val lock = PlatformLock()
        private var cacheKey: List<String> = emptyList()
        private var providers: List<WinUiXamlMetadataProviderReference> = emptyList()

        fun getOrCreateAll(): List<WinUiXamlMetadataProviderReference> {
            val runtimeClassNames = listOf(WinUiXamlMetadataProvider.providerRuntimeClassName) +
                WinUiXamlMetadataProviderRegistry.registeredRuntimeClassNames()
            if (runtimeClassNames == cacheKey) {
                return providers
            }
            return lock.withLock {
                if (runtimeClassNames == cacheKey) {
                    return@withLock providers
                }
                providers.forEach { it.close() }
                providers = runtimeClassNames.mapNotNull { runtimeClassName ->
                    WinUiXamlMetadataProvider.tryCreate(runtimeClassName).also { provider ->
                        if (FeatureSwitches.traceCcw) {
                            println("winrt-xaml-metadata: provider $runtimeClassName available=${provider != null}")
                        }
                    }
                }
                cacheKey = runtimeClassNames
                providers
            }
        }

        fun close() {
            lock.withLock {
                providers.forEach { provider ->
                    runCatching { provider.close() }
                }
                providers = emptyList()
                cacheKey = emptyList()
            }
        }
    }

    private fun registerRcwFactories() {
        ComWrappersSupport.registerTypedRcwFactory(commandTypeHandle()) { inspectable ->
            inspectable.use(::createCommandObject)
        }
        ComWrappersSupport.registerTypedRcwFactory(propertyChangedNotifierTypeHandle()) { inspectable ->
            inspectable.use(::createPropertyChangedNotifierObject)
        }
        ComWrappersSupport.registerTypedRcwFactory(collectionChangedNotifierTypeHandle()) { inspectable ->
            inspectable.use(::createCollectionChangedNotifierObject)
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerTypedRcwFactory(dataErrorInfoTypeHandle()) { inspectable ->
                inspectable.use(::createDataErrorInfoObject)
            }
            ComWrappersSupport.registerTypedRcwFactory(serviceProviderTypeHandle()) { inspectable ->
                inspectable.use(::createServiceProviderObject)
            }
        }
        ComWrappersSupport.registerTypedRcwFactory(customPropertyTypeHandle()) { inspectable ->
            inspectable.use(::createCustomPropertyObject)
        }
        ComWrappersSupport.registerTypedRcwFactory(customPropertyProviderTypeHandle()) { inspectable ->
            inspectable.use(::createCustomPropertyProviderObject)
        }
        ComWrappersSupport.registerTypedRcwFactory(stringableTypeHandle()) { inspectable ->
            inspectable.use(::createStringableObject)
        }
        ComWrappersSupport.registerTypedRcwFactory(propertyChangedEventArgsTypeHandle()) { inspectable ->
            inspectable.use { createPropertyChangedEventArgs(it) }
        }
        ComWrappersSupport.registerTypedRcwFactory(notifyCollectionChangedEventArgsTypeHandle()) { inspectable ->
            inspectable.use { createNotifyCollectionChangedEventArgs(it) }
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerTypedRcwFactory(dataErrorsChangedEventArgsTypeHandle()) { inspectable ->
                inspectable.use { createDataErrorsChangedEventArgs(it) }
            }
        }

        listOf(muxPropertyChangedEventArgsTypeName, wuxPropertyChangedEventArgsTypeName).forEach { runtimeClassName ->
            ComWrappersSupport.registerRuntimeClassFactory(runtimeClassName) { inspectable ->
                inspectable.use { createPropertyChangedEventArgs(it) }
            }
        }
        listOf(muxNotifyCollectionChangedEventArgsTypeName, wuxNotifyCollectionChangedEventArgsTypeName).forEach { runtimeClassName ->
            ComWrappersSupport.registerRuntimeClassFactory(runtimeClassName) { inspectable ->
                inspectable.use { createNotifyCollectionChangedEventArgs(it) }
            }
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerRuntimeClassFactory(muxDataErrorsChangedEventArgsTypeName) { inspectable ->
                inspectable.use { createDataErrorsChangedEventArgs(it) }
            }
        }
    }

    private fun registerCcwFactories() {
        ComWrappersSupport.registerCcwFactory(ICommand::class) { value ->
            createCommandDefinition(value as ICommand)
        }
        ComWrappersSupport.registerCcwFactory(INotifyPropertyChanged::class) { value ->
            createPropertyChangedNotifierDefinition(value as INotifyPropertyChanged)
        }
        ComWrappersSupport.registerCcwFactory(INotifyCollectionChanged::class) { value ->
            createCollectionChangedNotifierDefinition(value as INotifyCollectionChanged)
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerCcwFactory(INotifyDataErrorInfo::class) { value ->
                createDataErrorInfoDefinition(value as INotifyDataErrorInfo)
            }
            ComWrappersSupport.registerCcwFactory(IXamlServiceProvider::class) { value ->
                createServiceProviderDefinition(value as IXamlServiceProvider)
            }
        }
        ComWrappersSupport.registerCcwFactory(ICustomProperty::class) { value ->
            createCustomPropertyDefinition(value as ICustomProperty)
        }
        ComWrappersSupport.registerCcwFactory(ICustomPropertyProvider::class) { value ->
            createCustomPropertyProviderDefinition(value)
        }
        if (FeatureSwitches.enableICustomPropertyProviderSupport) {
            ComWrappersSupport.registerCcwFactory(WinRTBindableCustomPropertyImplementation::class) { value ->
                createCustomPropertyProviderDefinition(value)
            }
        }
        ComWrappersSupport.registerCcwFactory(PropertyChangedEventArgs::class) { value ->
            createPropertyChangedEventArgsDefinition(value as PropertyChangedEventArgs)
        }
        ComWrappersSupport.registerCcwFactory(NotifyCollectionChangedEventArgs::class) { value ->
            createNotifyCollectionChangedEventArgsDefinition(value as NotifyCollectionChangedEventArgs)
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerCcwFactory(DataErrorsChangedEventArgs::class) { value ->
                createDataErrorsChangedEventArgsDefinition(value as DataErrorsChangedEventArgs)
            }
        }
    }
}

private object XamlProjectedObjectValueRoots {
    private val roots = SnapshotList<ComObjectReference>()

    val size: Int
        get() = roots.toList().size

    fun retain(reference: ComObjectReference) {
        // XAML dependency properties can return the same object pointer after the setter call returns.
        roots.add(reference)
    }

    fun close() {
        val retained = roots.toList()
        roots.clear()
        retained.forEach { reference -> reference.close() }
    }
}

internal object XamlSystemProjectionMappings {
    fun register() {
        registerEnumMetadata()
        registerInterfaceMetadata()
        registerRuntimeClassMetadata()
    }

    private fun registerEnumMetadata() {
        WinRTTypeRegistry.update(NotifyCollectionChangedAction::class) { existing ->
            WinRTTypeId(
                kClass = NotifyCollectionChangedAction::class,
                projectedTypeName = collectionChangedActionTypeName(),
                guid = existing?.guid,
                iid = existing?.iid,
                signature = "enum(${collectionChangedActionTypeName()};i4)",
                enumAbiValue = { value -> value.ordinal },
                enumEntries = NotifyCollectionChangedAction.entries.toTypedArray(),
                helperType = existing?.helperType,
                defaultInterface = existing?.defaultInterface,
                boxedName = existing?.boxedName,
                runtimeClassName = existing?.runtimeClassName,
                vftblType = existing?.vftblType,
                isDelegate = existing?.isDelegate == true,
                isRuntimeClass = existing?.isRuntimeClass == true,
                isWindowsRuntimeType = true,
                aliases =
                    existing?.aliases.orEmpty() + setOf(
                        muxNotifyCollectionChangedEventArgsTypeName.substringBeforeLast('.') + ".NotifyCollectionChangedAction",
                        wuxNotifyCollectionChangedEventArgsTypeName.substringBeforeLast('.') + ".NotifyCollectionChangedAction",
                    ),
            )
        }
    }

    private fun registerInterfaceMetadata() {
        registerInterface(
            type = ICommand::class,
            helperType = ICommandProjection::class,
            canonicalAbiTypeName = commandRuntimeTypeName(),
            iid = IID.ICommand,
            aliases = setOf(
                muxCommandRuntimeTypeName,
                muxCommandInteropAlias,
                wuxCommandRuntimeTypeName,
                wuxCommandInteropAlias,
            ) - commandRuntimeTypeName(),
        )
        registerInterface(
            type = INotifyPropertyChanged::class,
            helperType = INotifyPropertyChangedProjection::class,
            canonicalAbiTypeName = propertyChangedNotifierTypeName(),
            iid = propertyChangedNotifierInterfaceId(),
            aliases = setOf(muxPropertyChangedNotifierTypeName, wuxPropertyChangedNotifierTypeName) - propertyChangedNotifierTypeName(),
        )
        registerInterface(
            type = INotifyCollectionChanged::class,
            helperType = INotifyCollectionChangedProjection::class,
            canonicalAbiTypeName = collectionChangedNotifierTypeName(),
            iid = collectionChangedNotifierInterfaceId(),
            aliases = setOf(muxCollectionChangedNotifierTypeName, wuxCollectionChangedNotifierTypeName) - collectionChangedNotifierTypeName(),
        )
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            registerInterface(
                type = INotifyDataErrorInfo::class,
                helperType = INotifyDataErrorInfoProjection::class,
                canonicalAbiTypeName = muxDataErrorInfoTypeName,
                iid = IID.INotifyDataErrorInfo,
            )
            registerInterface(
                type = IXamlServiceProvider::class,
                helperType = IXamlServiceProviderProjection::class,
                canonicalAbiTypeName = muxServiceProviderTypeName,
                iid = IID.IServiceProvider,
            )
        }
        registerInterface(
            type = ICustomProperty::class,
            helperType = ICustomPropertyProjection::class,
            canonicalAbiTypeName = customPropertyTypeName(),
            iid = IID.ICustomProperty,
            aliases = setOf(muxCustomPropertyTypeName, wuxCustomPropertyTypeName) - customPropertyTypeName(),
        )
        registerInterface(
            type = ICustomPropertyProvider::class,
            helperType = ICustomPropertyProviderProjection::class,
            canonicalAbiTypeName = customPropertyProviderTypeName(),
            iid = IID.ICustomPropertyProvider,
            aliases = setOf(muxCustomPropertyProviderTypeName, wuxCustomPropertyProviderTypeName) - customPropertyProviderTypeName(),
        )
        registerInterface(
            type = IStringable::class,
            helperType = IStringableProjection::class,
            canonicalAbiTypeName = stringableTypeName,
            iid = IID.IStringable,
        )
    }

    private fun registerRuntimeClassMetadata() {
        registerRuntimeClass(
            type = PropertyChangedEventArgs::class,
            helperType = PropertyChangedEventArgsProjection::class,
            runtimeClassName = propertyChangedEventArgsTypeName(),
            defaultInterfaceType = PropertyChangedEventArgsProjection::class,
            defaultInterfaceIid = propertyChangedEventArgsInterfaceId(),
            signature = "rc(${propertyChangedEventArgsTypeName()};{${propertyChangedEventArgsInterfaceId().toString().lowercase()}})",
            aliases = setOf(muxPropertyChangedEventArgsTypeName, wuxPropertyChangedEventArgsTypeName) - propertyChangedEventArgsTypeName(),
        )
        registerRuntimeClass(
            type = NotifyCollectionChangedEventArgs::class,
            helperType = NotifyCollectionChangedEventArgsProjection::class,
            runtimeClassName = notifyCollectionChangedEventArgsTypeName(),
            defaultInterfaceType = NotifyCollectionChangedEventArgsProjection::class,
            defaultInterfaceIid = notifyCollectionChangedEventArgsInterfaceId(),
            signature = "rc(${notifyCollectionChangedEventArgsTypeName()};{${notifyCollectionChangedEventArgsInterfaceId().toString().lowercase()}})",
            aliases = setOf(muxNotifyCollectionChangedEventArgsTypeName, wuxNotifyCollectionChangedEventArgsTypeName) - notifyCollectionChangedEventArgsTypeName(),
        )
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            registerRuntimeClass(
                type = DataErrorsChangedEventArgs::class,
                helperType = DataErrorsChangedEventArgsProjection::class,
                runtimeClassName = muxDataErrorsChangedEventArgsTypeName,
                defaultInterfaceType = DataErrorsChangedEventArgsProjection::class,
                defaultInterfaceIid = dataErrorsChangedEventArgsInterfaceId,
                signature = "rc($muxDataErrorsChangedEventArgsTypeName;{${dataErrorsChangedEventArgsInterfaceId.toString().lowercase()}})",
            )
        }
    }

    private fun registerInterface(
        type: KClass<out Any>,
        helperType: KClass<*>,
        canonicalAbiTypeName: String,
        iid: Guid,
        aliases: Set<String> = emptySet(),
    ) {
        @Suppress("UNCHECKED_CAST")
        val helperKClass = helperType as KClass<Any>
        Projections.registerCustomAbiTypeMapping(
            publicType = type,
            helperType = helperType,
            abiTypeName = canonicalAbiTypeName,
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = type,
            projectedTypeName = canonicalAbiTypeName,
            helperType = helperType,
            guid = iid,
            iid = iid,
            isWindowsRuntimeType = true,
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = helperKClass,
            projectedTypeName = canonicalAbiTypeName,
            guid = iid,
            iid = iid,
            isWindowsRuntimeType = true,
        )
        aliases.forEach { alias ->
            WinRTTypeRegistry.registerAlias(type, alias)
        }
    }

    private fun registerRuntimeClass(
        type: KClass<out Any>,
        helperType: KClass<*>,
        runtimeClassName: String,
        defaultInterfaceType: KClass<*>,
        defaultInterfaceIid: Guid,
        signature: String,
        aliases: Set<String> = emptySet(),
    ) {
        @Suppress("UNCHECKED_CAST")
        val helperKClass = helperType as KClass<Any>
        Projections.registerCustomAbiTypeMapping(
            publicType = type,
            helperType = helperType,
            abiTypeName = runtimeClassName,
            isRuntimeClass = true,
        )
        Projections.registerDefaultInterfaceType(type, defaultInterfaceType)
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = type,
            projectedTypeName = runtimeClassName,
            helperType = helperType,
            signature = signature,
            runtimeClassName = runtimeClassName,
            defaultInterface = defaultInterfaceType,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        CommonWinRTBuiltInProjectionMappings.registerMetadata(
            type = helperKClass,
            projectedTypeName = runtimeClassName,
            guid = defaultInterfaceIid,
            iid = defaultInterfaceIid,
            isWindowsRuntimeType = true,
        )
        aliases.forEach { alias ->
            WinRTTypeRegistry.registerAlias(type, alias)
        }
    }
}

internal object ICommandProjection
object INotifyPropertyChangedProjection {
    fun fromAbi(reference: IUnknownReference): INotifyPropertyChanged =
        createPropertyChangedNotifierObject(reference.asInspectable())

    fun fromAbi(reference: IInspectableReference): INotifyPropertyChanged =
        createPropertyChangedNotifierObject(reference)

    fun fromAbi(pointer: RawAddress): INotifyPropertyChanged? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            createPropertyChangedNotifierObject(IInspectableReference(pointer.asRawComPtr(), IID.IInspectable))
        }
}
internal object INotifyCollectionChangedProjection
object INotifyDataErrorInfoProjection {
    fun fromAbi(reference: IUnknownReference): INotifyDataErrorInfo =
        createDataErrorInfoObject(reference.asInspectable())

    fun fromAbi(pointer: RawAddress): INotifyDataErrorInfo? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            createDataErrorInfoObject(IInspectableReference(pointer.asRawComPtr(), IID.IInspectable))
        }
}
internal object IXamlServiceProviderProjection
internal object ICustomPropertyProjection
internal object ICustomPropertyProviderProjection
internal object IStringableProjection
internal object PropertyChangedEventArgsProjection
internal object NotifyCollectionChangedEventArgsProjection
internal object DataErrorsChangedEventArgsProjection

private fun createCommandObject(inspectable: IInspectableReference): ICommandObject =
    ICommandObject(resolveProjectedReference(inspectable, IID.ICommand, "ICommand"))

private fun createPropertyChangedNotifierObject(inspectable: IInspectableReference): INotifyPropertyChangedObject =
    INotifyPropertyChangedObject(
        resolveProjectedReference(
            inspectable,
            listOf(IID.MUX_INotifyPropertyChanged, IID.WUX_INotifyPropertyChanged),
            "INotifyPropertyChanged",
        ),
    )

private fun createCollectionChangedNotifierObject(inspectable: IInspectableReference): INotifyCollectionChangedObject =
    INotifyCollectionChangedObject(
        resolveProjectedReference(
            inspectable,
            listOf(IID.MUX_INotifyCollectionChanged, IID.WUX_INotifyCollectionChanged),
            "INotifyCollectionChanged",
        ),
    )

private fun createDataErrorInfoObject(inspectable: IInspectableReference): INotifyDataErrorInfoObject =
    INotifyDataErrorInfoObject(resolveProjectedReference(inspectable, IID.INotifyDataErrorInfo, "INotifyDataErrorInfo"))

private fun createServiceProviderObject(inspectable: IInspectableReference): IXamlServiceProviderObject =
    IXamlServiceProviderObject(resolveProjectedReference(inspectable, IID.IServiceProvider, "IServiceProvider"))

private fun createCustomPropertyObject(inspectable: IInspectableReference): ICustomPropertyObject =
    ICustomPropertyObject(resolveProjectedReference(inspectable, IID.ICustomProperty, "ICustomProperty"))

private fun createCustomPropertyProviderObject(inspectable: IInspectableReference): ICustomPropertyProviderObject =
    ICustomPropertyProviderObject(
        resolveProjectedReference(inspectable, IID.ICustomPropertyProvider, "ICustomPropertyProvider"),
    )

private fun createStringableObject(inspectable: IInspectableReference): IStringableObject =
    IStringableObject(resolveProjectedReference(inspectable, IID.IStringable, "IStringable"))

private fun createPropertyChangedEventArgs(inspectable: IInspectableReference): PropertyChangedEventArgs =
    inspectable.queryInterface(muxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
        ?: inspectable.queryInterface(wuxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
        ?: throw WinRTUnsupportedOperationException(
            "Inspectable value does not implement PropertyChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private fun createNotifyCollectionChangedEventArgs(inspectable: IInspectableReference): NotifyCollectionChangedEventArgs =
    inspectable.queryInterface(IID.MUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
        ?: inspectable.queryInterface(IID.WUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
        ?: throw WinRTUnsupportedOperationException(
            "Inspectable value does not implement NotifyCollectionChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private fun createDataErrorsChangedEventArgs(inspectable: IInspectableReference): DataErrorsChangedEventArgs =
    inspectable.queryInterface(dataErrorsChangedEventArgsInterfaceId).getOrNull()?.use(::readDataErrorsChangedEventArgs)
        ?: throw WinRTUnsupportedOperationException(
            "Inspectable value does not implement DataErrorsChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private class ICommandObject(
    override val nativeObject: ComObjectReference,
) : ICommand, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = commandTypeHandle()

    private val canExecuteChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        CommandCanExecuteChangedEventSource(nativeObject)
    }

    override fun canExecute(parameter: Any?): Boolean = invokeCanExecute(nativeObject, parameter)

    override fun execute(parameter: Any?) {
        invokeExecute(nativeObject, parameter)
    }

    override fun addCanExecuteChanged(handler: CanExecuteChangedEventHandler) {
        canExecuteChangedSource.subscribe(handler)
    }

    override fun removeCanExecuteChanged(handler: CanExecuteChangedEventHandler) {
        canExecuteChangedSource.unsubscribe(handler)
    }
}

private class INotifyPropertyChangedObject(
    override val nativeObject: ComObjectReference,
) : INotifyPropertyChanged, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = propertyChangedNotifierTypeHandle()

    private val propertyChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        PropertyChangedEventSource(nativeObject)
    }

    override fun addPropertyChanged(handler: PropertyChangedEventHandler) {
        propertyChangedSource.subscribe(handler)
    }

    override fun removePropertyChanged(handler: PropertyChangedEventHandler) {
        propertyChangedSource.unsubscribe(handler)
    }
}

private class INotifyCollectionChangedObject(
    override val nativeObject: ComObjectReference,
) : INotifyCollectionChanged, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = collectionChangedNotifierTypeHandle()

    private val collectionChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        CollectionChangedEventSource(nativeObject)
    }

    override fun addCollectionChanged(handler: NotifyCollectionChangedEventHandler) {
        collectionChangedSource.subscribe(handler)
    }

    override fun removeCollectionChanged(handler: NotifyCollectionChangedEventHandler) {
        collectionChangedSource.unsubscribe(handler)
    }
}

private class INotifyDataErrorInfoObject(
    override val nativeObject: ComObjectReference,
) : INotifyDataErrorInfo, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = dataErrorInfoTypeHandle()

    private val errorsChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        DataErrorsChangedEventSource(nativeObject)
    }

    override val hasErrors: Boolean
        get() = invokeBooleanGetter(nativeObject, slot = 6)

    override fun getErrors(propertyName: String?): Iterable<Any?>? {
        val resultPointer =
            withOptionalHString(propertyName) { propertyNameHandle ->
                PlatformAbi.confinedScope().use { scope ->
                    val resultOut = PlatformAbi.allocatePointerSlot(scope)
                    val hr = ComVtableInvoker.invokeArgs(
                        nativeObject.pointer,
                        9,
                        propertyNameHandle,
                        resultOut,
                    )
                    WinRTPlatformApi.checkSucceededRaw(hr)
                    PlatformAbi.readPointer(resultOut)
                }
            }
        return WinRTBindableIterableProjection.fromAbi(resultPointer)
    }

    override fun addErrorsChanged(handler: DataErrorsChangedEventHandler) {
        errorsChangedSource.subscribe(handler)
    }

    override fun removeErrorsChanged(handler: DataErrorsChangedEventHandler) {
        errorsChangedSource.unsubscribe(handler)
    }
}

private class IXamlServiceProviderObject(
    override val nativeObject: ComObjectReference,
) : IXamlServiceProvider, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = serviceProviderTypeHandle()

    override fun getService(type: KClass<*>?): Any? =
        withTypeNameArgument(type) { typePointer ->
            RawObjectAbiSupport.nullableObjectResult(
                invoke = { resultOut ->
                    ComVtableInvoker.invokeArgs(
                        nativeObject.pointer,
                        6,
                        typePointer,
                        resultOut,
                    )
                },
                wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
            )?.let(WinRTBindableObjectMarshaller::fromOwnedReference)
        }
}

private class ICustomPropertyObject(
    override val nativeObject: ComObjectReference,
) : ICustomProperty, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = customPropertyTypeHandle()

    override val canRead: Boolean
        get() = invokeBooleanGetter(nativeObject, slot = 13)

    override val canWrite: Boolean
        get() = invokeBooleanGetter(nativeObject, slot = 12)

    override val name: String
        get() = invokeHStringGetter(nativeObject, slot = 7)

    override val type: KClass<*>?
        get() = invokeTypeNameGetter(nativeObject, slot = 6)

    override fun getValue(target: Any?): Any? =
        withObjectArgument(target) { targetPointer ->
            RawObjectAbiSupport.nullableObjectResult(
                invoke = { resultOut ->
                    ComVtableInvoker.invokeArgs(
                        nativeObject.pointer,
                        8,
                        targetPointer,
                        resultOut,
                    )
                },
                wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
            )?.let(WinRTBindableObjectMarshaller::fromOwnedReference)
        }

    override fun setValue(
        target: Any?,
        value: Any?,
    ) {
        withTwoObjectArguments(target, value) { targetPointer, valuePointer ->
            val hr = ComVtableInvoker.invokeArgs(
                nativeObject.pointer,
                9,
                targetPointer,
                valuePointer,
            )
            WinRTPlatformApi.checkSucceededRaw(hr)
        }
    }

    override fun getIndexedValue(
        target: Any?,
        index: Any?,
    ): Any? =
        withTwoObjectArguments(target, index) { targetPointer, indexPointer ->
            RawObjectAbiSupport.nullableObjectResult(
                invoke = { resultOut ->
                    ComVtableInvoker.invokeArgs(
                        nativeObject.pointer,
                        10,
                        targetPointer,
                        indexPointer,
                        resultOut,
                    )
                },
                wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
            )?.let(WinRTBindableObjectMarshaller::fromOwnedReference)
        }

    override fun setIndexedValue(
        target: Any?,
        value: Any?,
        index: Any?,
    ) {
        withThreeObjectArguments(target, value, index) { targetPointer, valuePointer, indexPointer ->
            val hr = ComVtableInvoker.invokeArgs(
                nativeObject.pointer,
                11,
                targetPointer,
                valuePointer,
                indexPointer,
            )
            WinRTPlatformApi.checkSucceededRaw(hr)
        }
    }
}

private class ICustomPropertyProviderObject(
    override val nativeObject: ComObjectReference,
) : ICustomPropertyProvider, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = customPropertyProviderTypeHandle()

    override fun getCustomProperty(name: String): ICustomProperty? =
        withOptionalHString(name) { nameHandle ->
            RawObjectAbiSupport.nullableObjectResult(
                invoke = { resultOut ->
                    ComVtableInvoker.invokeArgs(
                        nativeObject.pointer,
                        6,
                        nameHandle,
                        resultOut,
                    )
                },
                wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
            )?.use { reference ->
                createCustomPropertyObject(reference.asInspectable())
            }
        }

    override fun getIndexedProperty(
        name: String,
        indexParameterType: KClass<*>?,
    ): ICustomProperty? =
        withOptionalHString(name) { nameHandle ->
            withTypeNameArgument(indexParameterType) { typePointer ->
                RawObjectAbiSupport.nullableObjectResult(
                    invoke = { resultOut ->
                        ComVtableInvoker.invokeArgs(
                            nativeObject.pointer,
                            7,
                            nameHandle,
                            typePointer,
                            resultOut,
                        )
                    },
                    wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
                )?.let { reference ->
                    reference.use { createCustomPropertyObject(it.asInspectable()) }
                }
            }
        }

    override fun getStringRepresentation(): String =
        invokeHStringGetter(nativeObject, slot = 8)

    override val type: KClass<*>
        get() = invokeTypeNameGetter(nativeObject, slot = 9)
            ?: throw WinRTIllegalStateException(
                "ICustomPropertyProvider.Type returned a null type.",
                KnownHResults.E_FAIL,
            )
}

private class IStringableObject(
    override val nativeObject: ComObjectReference,
) : IStringable, IWinRTObject {
    override val primaryTypeHandle: WinRTTypeHandle
        get() = stringableTypeHandle()

    override fun toString(): String =
        invokeHStringGetter(nativeObject, slot = 6)
}

private class CommandCanExecuteChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<CanExecuteChangedEventHandler>(objectReference, vtableIndexForAddHandler = 6) {
    override fun createMarshaler(handler: CanExecuteChangedEventHandler): WinRTDelegateHandle =
        WinRTDelegateBridge.createUnitDelegate(
            iid = commandEventHandlerIid,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeDelegateObject(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<CanExecuteChangedEventHandler> =
        object : EventSourceState<CanExecuteChangedEventHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): CanExecuteChangedEventHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class PropertyChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<PropertyChangedEventHandler>(objectReference, vtableIndexForAddHandler = 6) {
    private val delegateIid =
        if (objectReference.interfaceId == IID.WUX_INotifyPropertyChanged) {
            IID.WUX_PropertyChangedEventHandler
        } else {
            IID.MUX_PropertyChangedEventHandler
        }

    override fun createMarshaler(handler: PropertyChangedEventHandler): WinRTDelegateHandle =
        WinRTDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodePropertyChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<PropertyChangedEventHandler> =
        object : EventSourceState<PropertyChangedEventHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): PropertyChangedEventHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class CollectionChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<NotifyCollectionChangedEventHandler>(objectReference, vtableIndexForAddHandler = 6) {
    private val delegateIid =
        if (objectReference.interfaceId == IID.WUX_INotifyCollectionChanged) {
            IID.WUX_NotifyCollectionChangedEventHandler
        } else {
            IID.MUX_NotifyCollectionChangedEventHandler
        }

    override fun createMarshaler(handler: NotifyCollectionChangedEventHandler): WinRTDelegateHandle =
        WinRTDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeNotifyCollectionChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<NotifyCollectionChangedEventHandler> =
        object : EventSourceState<NotifyCollectionChangedEventHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): NotifyCollectionChangedEventHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class DataErrorsChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<DataErrorsChangedEventHandler>(objectReference, vtableIndexForAddHandler = 7) {
    override fun createMarshaler(handler: DataErrorsChangedEventHandler): WinRTDelegateHandle =
        WinRTDelegateBridge.createUnitDelegate(
            iid = dataErrorsChangedHandlerIid(),
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeDataErrorsChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<DataErrorsChangedEventHandler> =
        object : EventSourceState<DataErrorsChangedEventHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): DataErrorsChangedEventHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private fun createCommandDefinition(command: ICommand): WinRTCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<CanExecuteChangedEventHandler>("CanExecuteChangedEventHandler")
    return WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.ICommand,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val handler = NativeCommandChangedHandler(rawArgs[0] as RawAddress)
                        val token = tokenTable.addEventHandler(handler)
                        command.addCanExecuteChanged(handler)
                        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Int64),
                    ) { rawArgs ->
                        tokenTable.removeEventHandler(EventRegistrationToken(rawArgs[0] as Long))?.let { handler ->
                            try {
                                command.removeCanExecuteChanged(handler)
                            } finally {
                                (handler as? AutoCloseable)?.close()
                            }
                        }
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val parameter = decodeBorrowedInspectable(rawArgs[0] as RawAddress)
                        PlatformAbi.writeInt8(rawArgs[1] as RawAddress, if (command.canExecute(parameter)) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        command.execute(decodeBorrowedInspectable(rawArgs[0] as RawAddress))
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.ICommand,
    )
}

private fun createPropertyChangedNotifierDefinition(notifier: INotifyPropertyChanged): WinRTCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<PropertyChangedEventHandler>("PropertyChangedEventHandler")
    return WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.MUX_INotifyPropertyChanged,
                methods = listOf(
                    createPropertyChangedAddMethod(tokenTable, notifier, useWuxDelegate = false),
                    createPropertyChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.WUX_INotifyPropertyChanged,
                methods = listOf(
                    createPropertyChangedAddMethod(tokenTable, notifier, useWuxDelegate = true),
                    createPropertyChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
        ),
        defaultInterfaceId = propertyChangedNotifierInterfaceId(),
    )
}

private fun createCollectionChangedNotifierDefinition(notifier: INotifyCollectionChanged): WinRTCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<NotifyCollectionChangedEventHandler>("NotifyCollectionChangedEventHandler")
    return WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.MUX_INotifyCollectionChanged,
                methods = listOf(
                    createCollectionChangedAddMethod(tokenTable, notifier, useWuxDelegate = false),
                    createCollectionChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.WUX_INotifyCollectionChanged,
                methods = listOf(
                    createCollectionChangedAddMethod(tokenTable, notifier, useWuxDelegate = true),
                    createCollectionChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
        ),
        defaultInterfaceId = collectionChangedNotifierInterfaceId(),
    )
}

private fun createDataErrorInfoDefinition(dataErrorInfo: INotifyDataErrorInfo): WinRTCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<DataErrorsChangedEventHandler>("DataErrorsChangedEventHandler")
    return WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.INotifyDataErrorInfo,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (dataErrorInfo.hasErrors) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    createDataErrorsChangedAddMethod(tokenTable, dataErrorInfo),
                    createDataErrorsChangedRemoveMethod(tokenTable, dataErrorInfo),
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val propertyName = decodeBorrowedString(rawArgs[0] as RawAddress)
                        val result = dataErrorInfo.getErrors(propertyName)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(
                            WinRTBindableIterableProjection.fromManaged(result?.toList()),
                        )
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.INotifyDataErrorInfo,
    )
}

private fun createServiceProviderDefinition(serviceProvider: IXamlServiceProvider): WinRTCcwDefinition =
    WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.IServiceProvider,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val requestedType = TypeProjection.fromAbi(rawArgs[0] as RawAddress)
                        val service = serviceProvider.getService(requestedType)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(service))
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.IServiceProvider,
    )

private fun createCustomPropertyDefinition(customProperty: ICustomProperty): WinRTCcwDefinition =
    WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.ICustomProperty,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        TypeProjection.copyTo(customProperty.type, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        writeOptionalHString(customProperty.name, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val target = decodeBorrowedInspectable(rawArgs[0] as RawAddress)
                        val value = customProperty.getValue(target)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(value))
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        customProperty.setValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val value = customProperty.getIndexedValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                        )
                        (rawArgs[2] as RawAddress).writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(value))
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        customProperty.setIndexedValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[2] as RawAddress),
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (customProperty.canWrite) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (customProperty.canRead) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.ICustomProperty,
    )

private fun createCustomPropertyProviderDefinition(source: Any): WinRTCcwDefinition {
    val provider = explicitOrBindableCustomPropertyProvider(source)
    return WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(
                interfaceId = IID.ICustomPropertyProvider,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val property = provider?.getCustomProperty(decodeBorrowedString(rawArgs[0] as RawAddress))
                        (rawArgs[1] as RawAddress).writeReturnedPointer(propertyPointer(property))
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val property = provider?.getIndexedProperty(
                            name = decodeBorrowedString(rawArgs[0] as RawAddress),
                            indexParameterType = TypeProjection.fromAbi(rawArgs[1] as RawAddress),
                        )
                        (rawArgs[2] as RawAddress).writeReturnedPointer(propertyPointer(property))
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        writeOptionalHString(
                            provider?.getStringRepresentation() ?: source.toString(),
                            rawArgs[0] as RawAddress,
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        TypeProjection.copyTo(provider?.type ?: source::class, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.ICustomPropertyProvider,
    )
}

private fun createPropertyChangedEventArgsDefinition(value: PropertyChangedEventArgs): WinRTCcwDefinition =
    createSinglePropertyRuntimeClassDefinition(
        interfaceIds = listOf(muxPropertyChangedEventArgsInterfaceId, wuxPropertyChangedEventArgsInterfaceId),
        runtimeClassName = propertyChangedEventArgsTypeName(),
        defaultInterfaceId = propertyChangedEventArgsInterfaceId(),
        propertyName = value.propertyName,
    )

private fun createDataErrorsChangedEventArgsDefinition(value: DataErrorsChangedEventArgs): WinRTCcwDefinition =
    createSinglePropertyRuntimeClassDefinition(
        interfaceIds = listOf(dataErrorsChangedEventArgsInterfaceId),
        runtimeClassName = muxDataErrorsChangedEventArgsTypeName,
        defaultInterfaceId = dataErrorsChangedEventArgsInterfaceId,
        propertyName = value.propertyName,
    )

private fun createSinglePropertyRuntimeClassDefinition(
    interfaceIds: List<Guid>,
    runtimeClassName: String,
    defaultInterfaceId: Guid,
    propertyName: String?,
): WinRTCcwDefinition =
    WinRTCcwDefinition(
        interfaceDefinitions = interfaceIds.map { interfaceId ->
            WinRTInspectableInterfaceDefinition(
                interfaceId = interfaceId,
                methods = listOf(
                    WinRTInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        writeOptionalHString(propertyName, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                ),
            )
        },
        defaultInterfaceId = defaultInterfaceId,
        runtimeClassName = runtimeClassName,
    )

private fun createNotifyCollectionChangedEventArgsDefinition(value: NotifyCollectionChangedEventArgs): WinRTCcwDefinition =
    WinRTCcwDefinition(
        interfaceDefinitions = listOf(
            createNotifyCollectionChangedEventArgsInterfaceDefinition(IID.MUX_INotifyCollectionChangedEventArgs, value),
            createNotifyCollectionChangedEventArgsInterfaceDefinition(IID.WUX_INotifyCollectionChangedEventArgs, value),
        ),
        defaultInterfaceId = notifyCollectionChangedEventArgsInterfaceId(),
        runtimeClassName = notifyCollectionChangedEventArgsTypeName(),
    )

private fun createNotifyCollectionChangedEventArgsInterfaceDefinition(
    interfaceId: Guid,
    value: NotifyCollectionChangedEventArgs,
): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = interfaceId,
        methods = listOf(
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.action.ordinal)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                (rawArgs[0] as RawAddress).writeReturnedPointer(
                    WinRTBindableVectorProjection.fromManaged(value.newItems?.toMutableList()),
                )
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                (rawArgs[0] as RawAddress).writeReturnedPointer(
                    WinRTBindableVectorProjection.fromManaged(value.oldItems?.toMutableList()),
                )
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.newStartingIndex)
                KnownHResults.S_OK.value
            },
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.oldStartingIndex)
                KnownHResults.S_OK.value
            },
        ),
    )

private fun createStringableInterfaceDefinition(value: Any): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = IID.IStringable,
        methods = listOf(
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                writeOptionalHString(value.toString(), rawArgs[0] as RawAddress)
                KnownHResults.S_OK.value
            },
        ),
    )

private abstract class NativeAbiEventHandler<T>(
    handlerPointer: RawAddress,
    descriptor: WinRTDelegateDescriptor,
) : EventHandlerCallback<T>, AutoCloseable {
    private val reference =
        WinRTDelegateReference.fromAbi(cloneEventHandlerPointer(handlerPointer), descriptor)
            ?: throw WinRTIllegalStateException(
                "Event handler pointer was null.",
                KnownHResults.E_POINTER,
            )

    override fun close() {
        reference.close()
    }

    protected fun invokeWithAbi(
        sender: Any?,
        argsPointer: RawAddress,
    ) {
        withObjectArgument(sender) { senderPointer ->
            reference.invokeAbi(listOf(senderPointer, argsPointer)).requireSuccess("WinRT event delegate invoke")
        }
    }
}

private fun cloneEventHandlerPointer(handlerPointer: RawAddress): RawAddress =
    IUnknownReference(handlerPointer.asRawComPtr(), preventReleaseOnDispose = true).use { handler ->
        handler.getRefPointer().asRawAddress()
    }

private class NativeCommandChangedHandler(
    handlerPointer: RawAddress,
) : NativeAbiEventHandler<Any?>(
        handlerPointer,
        WinRTDelegateDescriptor(
            interfaceId = commandEventHandlerIid,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ),
    ) {
    override fun invoke(
        sender: Any?,
        args: Any?,
    ) {
        withObjectArgument(args) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private class NativePropertyChangedHandler(
    handlerPointer: RawAddress,
    useWuxDelegate: Boolean = false,
) : NativeAbiEventHandler<PropertyChangedEventArgs?>(
        handlerPointer,
        WinRTDelegateDescriptor(
            interfaceId = if (useWuxDelegate) IID.WUX_PropertyChangedEventHandler else IID.MUX_PropertyChangedEventHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ),
    ) {
    private val eventArgsInterfaceId =
        if (useWuxDelegate) {
            wuxPropertyChangedEventArgsInterfaceId
        } else {
            muxPropertyChangedEventArgsInterfaceId
        }

    override fun invoke(
        sender: Any?,
        args: PropertyChangedEventArgs?,
    ) {
        withPropertyChangedEventArgsArgument(args, eventArgsInterfaceId) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private class NativeCollectionChangedHandler(
    handlerPointer: RawAddress,
    useWuxDelegate: Boolean = false,
) : NativeAbiEventHandler<NotifyCollectionChangedEventArgs?>(
        handlerPointer,
        WinRTDelegateDescriptor(
            interfaceId = if (useWuxDelegate) IID.WUX_NotifyCollectionChangedEventHandler else IID.MUX_NotifyCollectionChangedEventHandler,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ),
    ) {
    private val eventArgsInterfaceId =
        if (useWuxDelegate) {
            IID.WUX_INotifyCollectionChangedEventArgs
        } else {
            IID.MUX_INotifyCollectionChangedEventArgs
        }

    override fun invoke(
        sender: Any?,
        args: NotifyCollectionChangedEventArgs?,
    ) {
        withNotifyCollectionChangedEventArgsArgument(args, eventArgsInterfaceId) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private class NativeDataErrorsChangedHandler(
    handlerPointer: RawAddress,
) : NativeAbiEventHandler<DataErrorsChangedEventArgs?>(
        handlerPointer,
        WinRTDelegateDescriptor(
            interfaceId = dataErrorsChangedHandlerIid(),
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, WinRTDelegateValueKind.OBJECT),
        ),
    ) {
    override fun invoke(
        sender: Any?,
        args: DataErrorsChangedEventArgs?,
    ) {
        withDataErrorsChangedEventArgsArgument(args) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private fun createPropertyChangedAddMethod(
    tokenTable: EventRegistrationTokenTable<PropertyChangedEventHandler>,
    notifier: INotifyPropertyChanged,
    useWuxDelegate: Boolean,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativePropertyChangedHandler(rawArgs[0] as RawAddress, useWuxDelegate = useWuxDelegate)
        val token = tokenTable.addEventHandler(handler)
        notifier.addPropertyChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createPropertyChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<PropertyChangedEventHandler>,
    notifier: INotifyPropertyChanged,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Int64),
    ) { rawArgs ->
        tokenTable.removeEventHandler(EventRegistrationToken(rawArgs[0] as Long))?.let { handler ->
            try {
                notifier.removePropertyChanged(handler)
            } finally {
                (handler as? AutoCloseable)?.close()
            }
        }
        KnownHResults.S_OK.value
    }

private fun createCollectionChangedAddMethod(
    tokenTable: EventRegistrationTokenTable<NotifyCollectionChangedEventHandler>,
    notifier: INotifyCollectionChanged,
    useWuxDelegate: Boolean,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativeCollectionChangedHandler(rawArgs[0] as RawAddress, useWuxDelegate = useWuxDelegate)
        val token = tokenTable.addEventHandler(handler)
        notifier.addCollectionChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createCollectionChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<NotifyCollectionChangedEventHandler>,
    notifier: INotifyCollectionChanged,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Int64),
    ) { rawArgs ->
        tokenTable.removeEventHandler(EventRegistrationToken(rawArgs[0] as Long))?.let { handler ->
            try {
                notifier.removeCollectionChanged(handler)
            } finally {
                (handler as? AutoCloseable)?.close()
            }
        }
        KnownHResults.S_OK.value
    }

private fun createDataErrorsChangedAddMethod(
    tokenTable: EventRegistrationTokenTable<DataErrorsChangedEventHandler>,
    dataErrorInfo: INotifyDataErrorInfo,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativeDataErrorsChangedHandler(rawArgs[0] as RawAddress)
        val token = tokenTable.addEventHandler(handler)
        dataErrorInfo.addErrorsChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createDataErrorsChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<DataErrorsChangedEventHandler>,
    dataErrorInfo: INotifyDataErrorInfo,
): WinRTInspectableMethodDefinition =
    WinRTInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Int64),
    ) { rawArgs ->
        tokenTable.removeEventHandler(EventRegistrationToken(rawArgs[0] as Long))?.let { handler ->
            try {
                dataErrorInfo.removeErrorsChanged(handler)
            } finally {
                (handler as? AutoCloseable)?.close()
            }
        }
        KnownHResults.S_OK.value
    }

private fun propertyPointer(property: ICustomProperty?): RawAddress =
    property?.let { ComWrappersSupport.createCCWForObject(it, IID.ICustomProperty).useAndGetRef() }
        ?: PlatformAbi.nullPointer

private fun explicitOrBindableCustomPropertyProvider(source: Any): ICustomPropertyProvider? =
    when (source) {
        is ICustomPropertyProvider -> source
        is WinRTBindableCustomPropertyImplementation ->
            if (FeatureSwitches.enableICustomPropertyProviderSupport) {
                object : ICustomPropertyProvider {
                    override fun getCustomProperty(name: String): ICustomProperty? =
                        source.getCustomProperty(name)

                    override fun getIndexedProperty(
                        name: String,
                        indexParameterType: KClass<*>?,
                    ): ICustomProperty? = source.getIndexedProperty(indexParameterType)

                    override fun getStringRepresentation(): String = source.toString()

                    override val type: KClass<*>
                        get() = source::class
                }
            } else {
                null
            }

        else -> null
    }

private fun resolveProjectedReference(
    inspectable: IInspectableReference,
    interfaceId: Guid,
    name: String,
): ComObjectReference = resolveProjectedReference(inspectable, listOf(interfaceId), name)

private fun resolveProjectedReference(
    inspectable: IInspectableReference,
    interfaceIds: List<Guid>,
    name: String,
): ComObjectReference {
    interfaceIds.forEach { interfaceId ->
        inspectable.tryQueryInterface(interfaceId)?.let { queried ->
            inspectable.close()
            return queried
        }
    }
    inspectable.close()
    throw WinRTUnsupportedOperationException(
        "Inspectable value does not implement $name.",
        KnownHResults.E_NOINTERFACE,
    )
}

private fun invokeBooleanGetter(
    reference: ComObjectReference,
    slot: Int,
): Boolean =
    RawAbiResultSupport.booleanResult { resultOut ->
        ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut)
    }

private fun invokeHStringGetter(
    reference: ComObjectReference,
    slot: Int,
): String =
    RawAbiResultSupport.hStringResult { resultOut ->
        ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut)
    }.use(HString::toKString)

private fun invokeInt32Getter(
    reference: ComObjectReference,
    slot: Int,
): Int =
    RawAbiResultSupport.int32Result { resultOut ->
        ComVtableInvoker.invokeArgs(reference.pointer, slot, resultOut)
    }

private fun readPropertyChangedEventArgs(reference: ComObjectReference): PropertyChangedEventArgs =
    PropertyChangedEventArgs(
        propertyName = invokeHStringGetter(reference, slot = 6),
    )

private fun readDataErrorsChangedEventArgs(reference: ComObjectReference): DataErrorsChangedEventArgs =
    DataErrorsChangedEventArgs(
        propertyName = invokeHStringGetter(reference, slot = 6),
    )

private fun readNotifyCollectionChangedEventArgs(reference: ComObjectReference): NotifyCollectionChangedEventArgs {
    val action = NotifyCollectionChangedAction.entries[invokeInt32Getter(reference, slot = 6)]
    val newItems =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                ComVtableInvoker.invokeArgs(reference.pointer, 7, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )?.use { listReference ->
            WinRTBindableVectorProjection.fromAbi(listReference.pointer.asRawAddress())?.use { it.toList() }
        }
    val oldItems =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                ComVtableInvoker.invokeArgs(reference.pointer, 8, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )?.use { listReference ->
            WinRTBindableVectorProjection.fromAbi(listReference.pointer.asRawAddress())?.use { it.toList() }
        }
    val newStartingIndex = invokeInt32Getter(reference, slot = 9)
    val oldStartingIndex = invokeInt32Getter(reference, slot = 10)
    return NotifyCollectionChangedEventArgs(
        action = action,
        newItems = newItems,
        oldItems = oldItems,
        newStartingIndex = newStartingIndex,
        oldStartingIndex = oldStartingIndex,
    )
}

private fun decodeDelegateObject(argument: Any?): Any? =
    when (argument) {
        null -> null
        is IUnknownReference -> WinRTBindableObjectMarshaller.fromOwnedReference(argument)
        is ComObjectReference -> WinRTBindableObjectMarshaller.fromOwnedReference(IUnknownReference(argument.getRefPointer(), argument.interfaceId))
        else -> argument
    }

fun PropertyChangedEventArgsFromAbi(argument: Any?): PropertyChangedEventArgs =
    decodePropertyChangedEventArgsArgument(argument)
        ?: error("PropertyChangedEventArgs ABI value is null")

private fun decodePropertyChangedEventArgsArgument(argument: Any?): PropertyChangedEventArgs? =
    when (argument) {
        null -> null
        is PropertyChangedEventArgs -> argument
        is IUnknownReference ->
            argument.use { reference ->
                reference.queryInterface(muxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
                    ?: reference.queryInterface(wuxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
            }

        is ComObjectReference ->
            argument.use { reference ->
                reference.queryInterface(muxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
                    ?: reference.queryInterface(wuxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
            }

        else -> error("Unsupported PropertyChangedEventArgs ABI value: ${argument::class.typeDisplayName()}")
    }

private fun decodeNotifyCollectionChangedEventArgsArgument(argument: Any?): NotifyCollectionChangedEventArgs? =
    when (argument) {
        null -> null
        is NotifyCollectionChangedEventArgs -> argument
        is IUnknownReference ->
            argument.use { reference ->
                reference.queryInterface(IID.MUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
                    ?: reference.queryInterface(IID.WUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
            }

        is ComObjectReference ->
            argument.use { reference ->
                reference.queryInterface(IID.MUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
                    ?: reference.queryInterface(IID.WUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
            }

        else -> error("Unsupported NotifyCollectionChangedEventArgs ABI value: ${argument::class.typeDisplayName()}")
    }

private fun decodeDataErrorsChangedEventArgsArgument(argument: Any?): DataErrorsChangedEventArgs? =
    when (argument) {
        null -> null
        is DataErrorsChangedEventArgs -> argument
        is IUnknownReference ->
            argument.use { reference ->
                reference.queryInterface(dataErrorsChangedEventArgsInterfaceId).getOrNull()?.use(::readDataErrorsChangedEventArgs)
            }

        is ComObjectReference ->
            argument.use { reference ->
                reference.queryInterface(dataErrorsChangedEventArgsInterfaceId).getOrNull()?.use(::readDataErrorsChangedEventArgs)
            }

        else -> error("Unsupported DataErrorsChangedEventArgs ABI value: ${argument::class.typeDisplayName()}")
    }

private fun decodeBorrowedInspectable(pointer: RawAddress): Any? =
    WinRTBindableObjectMarshaller.fromBorrowedAbi(pointer)

private fun decodeBorrowedString(pointer: RawAddress): String =
    if (PlatformAbi.isNull(pointer)) {
        ""
    } else {
        HString.fromHandle(pointer, owner = false).toKString()
    }

private fun writeOptionalHString(
    value: String?,
    destination: RawAddress,
) {
    if (value == null) {
        PlatformAbi.writePointer(destination, PlatformAbi.nullPointer)
        return
    }
    PlatformAbi.writePointer(destination, HString.create(value).handle)
}

private fun invokeTypeNameGetter(
    reference: ComObjectReference,
    slot: Int,
): KClass<*>? =
    PlatformAbi.confinedScope().use { scope ->
        val buffer = PlatformAbi.allocateBytes(scope, TypeProjection.LAYOUT.sizeBytes)
        val hr = ComVtableInvoker.invokeArgs(reference.pointer, slot, buffer)
        WinRTPlatformApi.checkSucceededRaw(hr)
        try {
            TypeProjection.fromAbi(buffer)
        } finally {
            TypeProjection.disposeAbi(buffer)
        }
    }

private inline fun <T> withTypeNameArgument(
    type: KClass<*>?,
    block: (RawAddress) -> T,
): T =
    PlatformAbi.confinedScope().use { scope ->
        val buffer = PlatformAbi.allocateBytes(scope, TypeProjection.LAYOUT.sizeBytes)
        TypeProjection.copyTo(type, buffer)
        try {
            block(buffer)
        } finally {
            TypeProjection.disposeAbi(buffer)
        }
    }

private inline fun <T> withOptionalHString(
    value: String?,
    block: (RawAddress) -> T,
): T {
    if (value == null) {
        return block(PlatformAbi.nullPointer)
    }
    return HString.create(value).use { block(it.handle) }
}

private inline fun <T> withObjectArgument(
    value: Any?,
    block: (RawAddress) -> T,
): T =
    WinRTBindableObjectMarshaller.createMarshaler(value).use { marshaler ->
        block(marshaler?.abi ?: PlatformAbi.nullPointer)
    }

private inline fun <T> withTwoObjectArguments(
    first: Any?,
    second: Any?,
    block: (RawAddress, RawAddress) -> T,
): T =
    WinRTBindableObjectMarshaller.createMarshaler(first).use { firstMarshaler ->
        WinRTBindableObjectMarshaller.createMarshaler(second).use { secondMarshaler ->
            block(
                firstMarshaler?.abi ?: PlatformAbi.nullPointer,
                secondMarshaler?.abi ?: PlatformAbi.nullPointer,
            )
        }
    }

private inline fun <T> withThreeObjectArguments(
    first: Any?,
    second: Any?,
    third: Any?,
    block: (RawAddress, RawAddress, RawAddress) -> T,
): T =
    WinRTBindableObjectMarshaller.createMarshaler(first).use { firstMarshaler ->
        WinRTBindableObjectMarshaller.createMarshaler(second).use { secondMarshaler ->
            WinRTBindableObjectMarshaller.createMarshaler(third).use { thirdMarshaler ->
                block(
                    firstMarshaler?.abi ?: PlatformAbi.nullPointer,
                    secondMarshaler?.abi ?: PlatformAbi.nullPointer,
                    thirdMarshaler?.abi ?: PlatformAbi.nullPointer,
                )
            }
        }
    }

private inline fun <T> withPropertyChangedEventArgsArgument(
    value: PropertyChangedEventArgs?,
    interfaceId: Guid = propertyChangedEventArgsInterfaceId(),
    block: (RawAddress) -> T,
): T =
    value?.let {
        ComWrappersSupport.createCCWForObject(it, interfaceId).use { block(it.pointer.asRawAddress()) }
    } ?: block(PlatformAbi.nullPointer)

private inline fun <T> withNotifyCollectionChangedEventArgsArgument(
    value: NotifyCollectionChangedEventArgs?,
    interfaceId: Guid = notifyCollectionChangedEventArgsInterfaceId(),
    block: (RawAddress) -> T,
): T =
    value?.let {
        ComWrappersSupport.createCCWForObject(it, interfaceId).use { block(it.pointer.asRawAddress()) }
    } ?: block(PlatformAbi.nullPointer)

private inline fun <T> withDataErrorsChangedEventArgsArgument(
    value: DataErrorsChangedEventArgs?,
    block: (RawAddress) -> T,
): T =
    value?.let {
        ComWrappersSupport.createCCWForObject(it, dataErrorsChangedEventArgsInterfaceId).use { block(it.pointer.asRawAddress()) }
    } ?: block(PlatformAbi.nullPointer)

private fun invokeCanExecute(
    reference: ComObjectReference,
    parameter: Any?,
): Boolean =
    withObjectArgument(parameter) { parameterPointer ->
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            val hr = ComVtableInvoker.invokeArgs(
                reference.pointer,
                8,
                parameterPointer,
                resultOut,
            )
            WinRTPlatformApi.checkSucceededRaw(hr)
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }
    }

private fun invokeExecute(
    reference: ComObjectReference,
    parameter: Any?,
) {
    withObjectArgument(parameter) { parameterPointer ->
        val hr = ComVtableInvoker.invokeArgs(reference.pointer, 9, parameterPointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}

private fun RawAddress.writeReturnedPointer(pointer: RawAddress) {
    PlatformAbi.writePointer(this, pointer)
}
