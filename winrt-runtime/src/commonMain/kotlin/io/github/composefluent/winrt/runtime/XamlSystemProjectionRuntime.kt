package io.github.composefluent.winrt.runtime

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

private fun commandTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(commandRuntimeTypeName(), IID.ICommand)

private fun propertyChangedNotifierTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(propertyChangedNotifierTypeName(), propertyChangedNotifierInterfaceId())

private fun collectionChangedNotifierTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(collectionChangedNotifierTypeName(), collectionChangedNotifierInterfaceId())

private fun dataErrorInfoTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(muxDataErrorInfoTypeName, IID.INotifyDataErrorInfo)

private fun serviceProviderTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(muxServiceProviderTypeName, IID.IServiceProvider)

private fun customPropertyTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(customPropertyTypeName(), IID.ICustomProperty)

private fun customPropertyProviderTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(customPropertyProviderTypeName(), IID.ICustomPropertyProvider)

private fun stringableTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(stringableTypeName, IID.IStringable)

private fun propertyChangedEventArgsTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(propertyChangedEventArgsTypeName(), propertyChangedEventArgsInterfaceId())

private fun notifyCollectionChangedEventArgsTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(notifyCollectionChangedEventArgsTypeName(), notifyCollectionChangedEventArgsInterfaceId())

private fun dataErrorsChangedEventArgsTypeHandle(): WinRtTypeHandle =
    WinRtTypeHandle(muxDataErrorsChangedEventArgsTypeName, dataErrorsChangedEventArgsInterfaceId)

private val commandEventHandlerIid =
    ParameterizedInterfaceId.createFromParameterizedInterface(IID.EventHandler, WinRtTypeSignature.object_())
private fun dataErrorsChangedHandlerIid(): Guid =
    ParameterizedInterfaceId.createFromParameterizedInterface(
        IID.EventHandler,
        WinRtTypeSignature.runtimeClass(
            muxDataErrorsChangedEventArgsTypeName,
            WinRtTypeSignature.guid(dataErrorsChangedEventArgsInterfaceId),
        ),
    )

internal object XamlSystemProjectionRuntimeHooks {
    fun ensureRegistered() {
        registerRcwFactories()
        registerCcwFactories()
    }

    internal fun augmentInspectableDefinition(
        value: Any,
        definition: WinRtCcwDefinition,
    ): WinRtCcwDefinition {
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

    private fun shouldExposeWinUiXamlMetadataProvider(definition: WinRtCcwDefinition): Boolean =
        XamlProjectionConfiguration.supportsWinUiOnlyTypes &&
            definition.interfaceDefinitions.any { it.interfaceId == WinUiXamlInterfaceIds.IApplicationOverrides } &&
            definition.interfaceDefinitions.none { it.interfaceId == WinUiXamlInterfaceIds.IXamlMetadataProvider }

    private fun createWinUiXamlMetadataProviderInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = WinUiXamlInterfaceIds.IXamlMetadataProvider,
            methods = listOf(
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
                    forwardWinUiXamlMetadataProviderCall(
                        slot = WinUiXamlMetadataProviderSlots.GetXamlType,
                        arg0 = rawArgs[0] as RawAddress,
                        arg1 = rawArgs[1] as RawAddress,
                    )
                },
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
                    forwardWinUiXamlMetadataProviderCall(
                        slot = WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName,
                        arg0 = rawArgs[0] as RawAddress,
                        arg1 = rawArgs[1] as RawAddress,
                    )
                },
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { rawArgs ->
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
        ComWrappersSupport.registerCcwFactory(WinRtCommand::class) { value ->
            createCommandDefinition(value as WinRtCommand)
        }
        ComWrappersSupport.registerCcwFactory(WinRtPropertyChangedNotifier::class) { value ->
            createPropertyChangedNotifierDefinition(value as WinRtPropertyChangedNotifier)
        }
        ComWrappersSupport.registerCcwFactory(WinRtCollectionChangedNotifier::class) { value ->
            createCollectionChangedNotifierDefinition(value as WinRtCollectionChangedNotifier)
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerCcwFactory(WinRtDataErrorInfo::class) { value ->
                createDataErrorInfoDefinition(value as WinRtDataErrorInfo)
            }
            ComWrappersSupport.registerCcwFactory(WinRtServiceProvider::class) { value ->
                createServiceProviderDefinition(value as WinRtServiceProvider)
            }
        }
        ComWrappersSupport.registerCcwFactory(WinRtCustomProperty::class) { value ->
            createCustomPropertyDefinition(value as WinRtCustomProperty)
        }
        ComWrappersSupport.registerCcwFactory(WinRtCustomPropertyProvider::class) { value ->
            createCustomPropertyProviderDefinition(value)
        }
        if (FeatureSwitches.enableICustomPropertyProviderSupport) {
            ComWrappersSupport.registerCcwFactory(WinRtBindableCustomPropertyImplementation::class) { value ->
                createCustomPropertyProviderDefinition(value)
            }
        }
        ComWrappersSupport.registerCcwFactory(WinRtPropertyChangedEventArgs::class) { value ->
            createPropertyChangedEventArgsDefinition(value as WinRtPropertyChangedEventArgs)
        }
        ComWrappersSupport.registerCcwFactory(WinRtNotifyCollectionChangedEventArgs::class) { value ->
            createNotifyCollectionChangedEventArgsDefinition(value as WinRtNotifyCollectionChangedEventArgs)
        }
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            ComWrappersSupport.registerCcwFactory(WinRtDataErrorsChangedEventArgs::class) { value ->
                createDataErrorsChangedEventArgsDefinition(value as WinRtDataErrorsChangedEventArgs)
            }
        }
    }
}

internal object XamlSystemProjectionMappings {
    fun register() {
        registerEnumMetadata()
        registerInterfaceMetadata()
        registerRuntimeClassMetadata()
    }

    private fun registerEnumMetadata() {
        WinRtTypeRegistry.update(WinRtNotifyCollectionChangedAction::class) { existing ->
            WinRtTypeId(
                kClass = WinRtNotifyCollectionChangedAction::class,
                projectedTypeName = collectionChangedActionTypeName(),
                guid = existing?.guid,
                iid = existing?.iid,
                signature = "enum(${collectionChangedActionTypeName()};i4)",
                enumAbiValue = { value -> value.ordinal },
                enumEntries = WinRtNotifyCollectionChangedAction.entries.toTypedArray(),
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
            type = WinRtCommand::class,
            helperType = WinRtCommandProjection::class,
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
            type = WinRtPropertyChangedNotifier::class,
            helperType = WinRtPropertyChangedNotifierProjection::class,
            canonicalAbiTypeName = propertyChangedNotifierTypeName(),
            iid = propertyChangedNotifierInterfaceId(),
            aliases = setOf(muxPropertyChangedNotifierTypeName, wuxPropertyChangedNotifierTypeName) - propertyChangedNotifierTypeName(),
        )
        registerInterface(
            type = WinRtCollectionChangedNotifier::class,
            helperType = WinRtCollectionChangedNotifierProjection::class,
            canonicalAbiTypeName = collectionChangedNotifierTypeName(),
            iid = collectionChangedNotifierInterfaceId(),
            aliases = setOf(muxCollectionChangedNotifierTypeName, wuxCollectionChangedNotifierTypeName) - collectionChangedNotifierTypeName(),
        )
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            registerInterface(
                type = WinRtDataErrorInfo::class,
                helperType = WinRtDataErrorInfoProjection::class,
                canonicalAbiTypeName = muxDataErrorInfoTypeName,
                iid = IID.INotifyDataErrorInfo,
            )
            registerInterface(
                type = WinRtServiceProvider::class,
                helperType = WinRtServiceProviderProjection::class,
                canonicalAbiTypeName = muxServiceProviderTypeName,
                iid = IID.IServiceProvider,
            )
        }
        registerInterface(
            type = WinRtCustomProperty::class,
            helperType = WinRtCustomPropertyProjection::class,
            canonicalAbiTypeName = customPropertyTypeName(),
            iid = IID.ICustomProperty,
            aliases = setOf(muxCustomPropertyTypeName, wuxCustomPropertyTypeName) - customPropertyTypeName(),
        )
        registerInterface(
            type = WinRtCustomPropertyProvider::class,
            helperType = WinRtCustomPropertyProviderProjection::class,
            canonicalAbiTypeName = customPropertyProviderTypeName(),
            iid = IID.ICustomPropertyProvider,
            aliases = setOf(muxCustomPropertyProviderTypeName, wuxCustomPropertyProviderTypeName) - customPropertyProviderTypeName(),
        )
        registerInterface(
            type = WinRtStringable::class,
            helperType = WinRtStringableProjection::class,
            canonicalAbiTypeName = stringableTypeName,
            iid = IID.IStringable,
        )
    }

    private fun registerRuntimeClassMetadata() {
        registerRuntimeClass(
            type = WinRtPropertyChangedEventArgs::class,
            helperType = WinRtPropertyChangedEventArgsProjection::class,
            runtimeClassName = propertyChangedEventArgsTypeName(),
            defaultInterfaceType = WinRtPropertyChangedEventArgsProjection::class,
            defaultInterfaceIid = propertyChangedEventArgsInterfaceId(),
            signature = "rc(${propertyChangedEventArgsTypeName()};{${propertyChangedEventArgsInterfaceId().toString().lowercase()}})",
            aliases = setOf(muxPropertyChangedEventArgsTypeName, wuxPropertyChangedEventArgsTypeName) - propertyChangedEventArgsTypeName(),
        )
        registerRuntimeClass(
            type = WinRtNotifyCollectionChangedEventArgs::class,
            helperType = WinRtNotifyCollectionChangedEventArgsProjection::class,
            runtimeClassName = notifyCollectionChangedEventArgsTypeName(),
            defaultInterfaceType = WinRtNotifyCollectionChangedEventArgsProjection::class,
            defaultInterfaceIid = notifyCollectionChangedEventArgsInterfaceId(),
            signature = "rc(${notifyCollectionChangedEventArgsTypeName()};{${notifyCollectionChangedEventArgsInterfaceId().toString().lowercase()}})",
            aliases = setOf(muxNotifyCollectionChangedEventArgsTypeName, wuxNotifyCollectionChangedEventArgsTypeName) - notifyCollectionChangedEventArgsTypeName(),
        )
        if (XamlProjectionConfiguration.supportsWinUiOnlyTypes) {
            registerRuntimeClass(
                type = WinRtDataErrorsChangedEventArgs::class,
                helperType = WinRtDataErrorsChangedEventArgsProjection::class,
                runtimeClassName = muxDataErrorsChangedEventArgsTypeName,
                defaultInterfaceType = WinRtDataErrorsChangedEventArgsProjection::class,
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
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = type,
            projectedTypeName = canonicalAbiTypeName,
            helperType = helperType,
            guid = iid,
            iid = iid,
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = helperKClass,
            projectedTypeName = canonicalAbiTypeName,
            guid = iid,
            iid = iid,
            isWindowsRuntimeType = true,
        )
        aliases.forEach { alias ->
            WinRtTypeRegistry.registerAlias(type, alias)
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
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = type,
            projectedTypeName = runtimeClassName,
            helperType = helperType,
            signature = signature,
            runtimeClassName = runtimeClassName,
            defaultInterface = defaultInterfaceType,
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        CommonWinRtBuiltInProjectionMappings.registerMetadata(
            type = helperKClass,
            projectedTypeName = runtimeClassName,
            guid = defaultInterfaceIid,
            iid = defaultInterfaceIid,
            isWindowsRuntimeType = true,
        )
        aliases.forEach { alias ->
            WinRtTypeRegistry.registerAlias(type, alias)
        }
    }
}

internal object WinRtCommandProjection
object WinRtPropertyChangedNotifierProjection {
    fun fromAbi(reference: IUnknownReference): WinRtPropertyChangedNotifier =
        createPropertyChangedNotifierObject(reference.asInspectable())

    fun fromAbi(reference: IInspectableReference): WinRtPropertyChangedNotifier =
        createPropertyChangedNotifierObject(reference)

    fun fromAbi(pointer: RawAddress): WinRtPropertyChangedNotifier? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            createPropertyChangedNotifierObject(IInspectableReference(pointer.asRawComPtr(), IID.IInspectable))
        }
}
internal object WinRtCollectionChangedNotifierProjection
object WinRtDataErrorInfoProjection {
    fun fromAbi(reference: IUnknownReference): WinRtDataErrorInfo =
        createDataErrorInfoObject(reference.asInspectable())

    fun fromAbi(pointer: RawAddress): WinRtDataErrorInfo? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            createDataErrorInfoObject(IInspectableReference(pointer.asRawComPtr(), IID.IInspectable))
        }
}
internal object WinRtServiceProviderProjection
internal object WinRtCustomPropertyProjection
internal object WinRtCustomPropertyProviderProjection
internal object WinRtStringableProjection
internal object WinRtPropertyChangedEventArgsProjection
internal object WinRtNotifyCollectionChangedEventArgsProjection
internal object WinRtDataErrorsChangedEventArgsProjection

private fun createCommandObject(inspectable: IInspectableReference): WinRtCommandObject =
    WinRtCommandObject(resolveProjectedReference(inspectable, IID.ICommand, "ICommand"))

private fun createPropertyChangedNotifierObject(inspectable: IInspectableReference): WinRtPropertyChangedNotifierObject =
    WinRtPropertyChangedNotifierObject(
        resolveProjectedReference(
            inspectable,
            listOf(IID.MUX_INotifyPropertyChanged, IID.WUX_INotifyPropertyChanged),
            "INotifyPropertyChanged",
        ),
    )

private fun createCollectionChangedNotifierObject(inspectable: IInspectableReference): WinRtCollectionChangedNotifierObject =
    WinRtCollectionChangedNotifierObject(
        resolveProjectedReference(
            inspectable,
            listOf(IID.MUX_INotifyCollectionChanged, IID.WUX_INotifyCollectionChanged),
            "INotifyCollectionChanged",
        ),
    )

private fun createDataErrorInfoObject(inspectable: IInspectableReference): WinRtDataErrorInfoObject =
    WinRtDataErrorInfoObject(resolveProjectedReference(inspectable, IID.INotifyDataErrorInfo, "INotifyDataErrorInfo"))

private fun createServiceProviderObject(inspectable: IInspectableReference): WinRtServiceProviderObject =
    WinRtServiceProviderObject(resolveProjectedReference(inspectable, IID.IServiceProvider, "IServiceProvider"))

private fun createCustomPropertyObject(inspectable: IInspectableReference): WinRtCustomPropertyObject =
    WinRtCustomPropertyObject(resolveProjectedReference(inspectable, IID.ICustomProperty, "ICustomProperty"))

private fun createCustomPropertyProviderObject(inspectable: IInspectableReference): WinRtCustomPropertyProviderObject =
    WinRtCustomPropertyProviderObject(
        resolveProjectedReference(inspectable, IID.ICustomPropertyProvider, "ICustomPropertyProvider"),
    )

private fun createStringableObject(inspectable: IInspectableReference): WinRtStringableObject =
    WinRtStringableObject(resolveProjectedReference(inspectable, IID.IStringable, "IStringable"))

private fun createPropertyChangedEventArgs(inspectable: IInspectableReference): WinRtPropertyChangedEventArgs =
    inspectable.queryInterface(muxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
        ?: inspectable.queryInterface(wuxPropertyChangedEventArgsInterfaceId).getOrNull()?.use(::readPropertyChangedEventArgs)
        ?: throw WinRtUnsupportedOperationException(
            "Inspectable value does not implement PropertyChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private fun createNotifyCollectionChangedEventArgs(inspectable: IInspectableReference): WinRtNotifyCollectionChangedEventArgs =
    inspectable.queryInterface(IID.MUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
        ?: inspectable.queryInterface(IID.WUX_INotifyCollectionChangedEventArgs).getOrNull()?.use(::readNotifyCollectionChangedEventArgs)
        ?: throw WinRtUnsupportedOperationException(
            "Inspectable value does not implement NotifyCollectionChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private fun createDataErrorsChangedEventArgs(inspectable: IInspectableReference): WinRtDataErrorsChangedEventArgs =
    inspectable.queryInterface(dataErrorsChangedEventArgsInterfaceId).getOrNull()?.use(::readDataErrorsChangedEventArgs)
        ?: throw WinRtUnsupportedOperationException(
            "Inspectable value does not implement DataErrorsChangedEventArgs.",
            KnownHResults.E_NOINTERFACE,
        )

private class WinRtCommandObject(
    override val nativeObject: ComObjectReference,
) : WinRtCommand, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
        get() = commandTypeHandle()

    private val canExecuteChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        CommandCanExecuteChangedEventSource(nativeObject)
    }

    override fun canExecute(parameter: Any?): Boolean = invokeCanExecute(nativeObject, parameter)

    override fun execute(parameter: Any?) {
        invokeExecute(nativeObject, parameter)
    }

    override fun addCanExecuteChanged(handler: WinRtCanExecuteChangedHandler) {
        canExecuteChangedSource.subscribe(handler)
    }

    override fun removeCanExecuteChanged(handler: WinRtCanExecuteChangedHandler) {
        canExecuteChangedSource.unsubscribe(handler)
    }
}

private class WinRtPropertyChangedNotifierObject(
    override val nativeObject: ComObjectReference,
) : WinRtPropertyChangedNotifier, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
        get() = propertyChangedNotifierTypeHandle()

    private val propertyChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        PropertyChangedEventSource(nativeObject)
    }

    override fun addPropertyChanged(handler: WinRtPropertyChangedHandler) {
        propertyChangedSource.subscribe(handler)
    }

    override fun removePropertyChanged(handler: WinRtPropertyChangedHandler) {
        propertyChangedSource.unsubscribe(handler)
    }
}

private class WinRtCollectionChangedNotifierObject(
    override val nativeObject: ComObjectReference,
) : WinRtCollectionChangedNotifier, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
        get() = collectionChangedNotifierTypeHandle()

    private val collectionChangedSource by lazy(LazyThreadSafetyMode.NONE) {
        CollectionChangedEventSource(nativeObject)
    }

    override fun addCollectionChanged(handler: WinRtCollectionChangedHandler) {
        collectionChangedSource.subscribe(handler)
    }

    override fun removeCollectionChanged(handler: WinRtCollectionChangedHandler) {
        collectionChangedSource.unsubscribe(handler)
    }
}

private class WinRtDataErrorInfoObject(
    override val nativeObject: ComObjectReference,
) : WinRtDataErrorInfo, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
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
                    WinRtPlatformApi.checkSucceededRaw(hr)
                    PlatformAbi.readPointer(resultOut)
                }
            }
        return WinRtBindableIterableProjection.fromAbi(resultPointer)
    }

    override fun addErrorsChanged(handler: WinRtDataErrorsChangedHandler) {
        errorsChangedSource.subscribe(handler)
    }

    override fun removeErrorsChanged(handler: WinRtDataErrorsChangedHandler) {
        errorsChangedSource.unsubscribe(handler)
    }
}

private class WinRtServiceProviderObject(
    override val nativeObject: ComObjectReference,
) : WinRtServiceProvider, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
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
            )?.let(WinRtBindableObjectMarshaller::fromOwnedReference)
        }
}

private class WinRtCustomPropertyObject(
    override val nativeObject: ComObjectReference,
) : WinRtCustomProperty, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
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
            )?.let(WinRtBindableObjectMarshaller::fromOwnedReference)
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
            WinRtPlatformApi.checkSucceededRaw(hr)
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
            )?.let(WinRtBindableObjectMarshaller::fromOwnedReference)
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
            WinRtPlatformApi.checkSucceededRaw(hr)
        }
    }
}

private class WinRtCustomPropertyProviderObject(
    override val nativeObject: ComObjectReference,
) : WinRtCustomPropertyProvider, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
        get() = customPropertyProviderTypeHandle()

    override fun getCustomProperty(name: String): WinRtCustomProperty? =
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
    ): WinRtCustomProperty? =
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
            ?: throw WinRtIllegalStateException(
                "ICustomPropertyProvider.Type returned a null type.",
                KnownHResults.E_FAIL,
            )
}

private class WinRtStringableObject(
    override val nativeObject: ComObjectReference,
) : WinRtStringable, IWinRTObject {
    override val primaryTypeHandle: WinRtTypeHandle
        get() = stringableTypeHandle()

    override fun stringRepresentation(): String =
        invokeHStringGetter(nativeObject, slot = 6)

    override fun toString(): String = stringRepresentation()
}

private class CommandCanExecuteChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<WinRtCanExecuteChangedHandler>(objectReference, vtableIndexForAddHandler = 6) {
    override fun createMarshaler(handler: WinRtCanExecuteChangedHandler): WinRtDelegateHandle =
        WinRtDelegateBridge.createUnitDelegate(
            iid = commandEventHandlerIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeDelegateObject(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<WinRtCanExecuteChangedHandler> =
        object : EventSourceState<WinRtCanExecuteChangedHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): WinRtCanExecuteChangedHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class PropertyChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<WinRtPropertyChangedHandler>(objectReference, vtableIndexForAddHandler = 6) {
    private val delegateIid =
        if (objectReference.interfaceId == IID.WUX_INotifyPropertyChanged) {
            IID.WUX_PropertyChangedEventHandler
        } else {
            IID.MUX_PropertyChangedEventHandler
        }

    override fun createMarshaler(handler: WinRtPropertyChangedHandler): WinRtDelegateHandle =
        WinRtDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodePropertyChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<WinRtPropertyChangedHandler> =
        object : EventSourceState<WinRtPropertyChangedHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): WinRtPropertyChangedHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class CollectionChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<WinRtCollectionChangedHandler>(objectReference, vtableIndexForAddHandler = 6) {
    private val delegateIid =
        if (objectReference.interfaceId == IID.WUX_INotifyCollectionChanged) {
            IID.WUX_NotifyCollectionChangedEventHandler
        } else {
            IID.MUX_NotifyCollectionChangedEventHandler
        }

    override fun createMarshaler(handler: WinRtCollectionChangedHandler): WinRtDelegateHandle =
        WinRtDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeNotifyCollectionChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<WinRtCollectionChangedHandler> =
        object : EventSourceState<WinRtCollectionChangedHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): WinRtCollectionChangedHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private class DataErrorsChangedEventSource(
    objectReference: ComObjectReference,
) : EventSource<WinRtDataErrorsChangedHandler>(objectReference, vtableIndexForAddHandler = 7) {
    override fun createMarshaler(handler: WinRtDataErrorsChangedHandler): WinRtDelegateHandle =
        WinRtDelegateBridge.createUnitDelegate(
            iid = dataErrorsChangedHandlerIid(),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        ) { args ->
            handler(
                decodeDelegateObject(args[0]),
                decodeDataErrorsChangedEventArgsArgument(args[1]),
            )
        }

    override fun createEventSourceState(): EventSourceState<WinRtDataErrorsChangedHandler> =
        object : EventSourceState<WinRtDataErrorsChangedHandler>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): WinRtDataErrorsChangedHandler =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}

private fun createCommandDefinition(command: WinRtCommand): WinRtCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<WinRtCanExecuteChangedHandler>("WinRtCanExecuteChangedHandler")
    return WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.ICommand,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val handler = NativeCommandChangedHandler(rawArgs[0] as RawAddress)
                        val token = tokenTable.addEventHandler(handler)
                        command.addCanExecuteChanged(handler)
                        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
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
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val parameter = decodeBorrowedInspectable(rawArgs[0] as RawAddress)
                        PlatformAbi.writeInt8(rawArgs[1] as RawAddress, if (command.canExecute(parameter)) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
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

private fun createPropertyChangedNotifierDefinition(notifier: WinRtPropertyChangedNotifier): WinRtCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<WinRtPropertyChangedHandler>("WinRtPropertyChangedHandler")
    return WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.MUX_INotifyPropertyChanged,
                methods = listOf(
                    createPropertyChangedAddMethod(tokenTable, notifier, useWuxDelegate = false),
                    createPropertyChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
            WinRtInspectableInterfaceDefinition(
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

private fun createCollectionChangedNotifierDefinition(notifier: WinRtCollectionChangedNotifier): WinRtCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<WinRtCollectionChangedHandler>("WinRtCollectionChangedHandler")
    return WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.MUX_INotifyCollectionChanged,
                methods = listOf(
                    createCollectionChangedAddMethod(tokenTable, notifier, useWuxDelegate = false),
                    createCollectionChangedRemoveMethod(tokenTable, notifier),
                ),
            ),
            WinRtInspectableInterfaceDefinition(
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

private fun createDataErrorInfoDefinition(dataErrorInfo: WinRtDataErrorInfo): WinRtCcwDefinition {
    val tokenTable = EventRegistrationTokenTable.create<WinRtDataErrorsChangedHandler>("WinRtDataErrorsChangedHandler")
    return WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.INotifyDataErrorInfo,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (dataErrorInfo.hasErrors) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    createDataErrorsChangedAddMethod(tokenTable, dataErrorInfo),
                    createDataErrorsChangedRemoveMethod(tokenTable, dataErrorInfo),
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val propertyName = decodeBorrowedString(rawArgs[0] as RawAddress)
                        val result = dataErrorInfo.getErrors(propertyName)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(
                            WinRtBindableIterableProjection.fromManaged(result?.toList()),
                        )
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.INotifyDataErrorInfo,
    )
}

private fun createServiceProviderDefinition(serviceProvider: WinRtServiceProvider): WinRtCcwDefinition =
    WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.IServiceProvider,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val requestedType = TypeProjection.fromAbi(rawArgs[0] as RawAddress)
                        val service = serviceProvider.getService(requestedType)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(service))
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        ),
        defaultInterfaceId = IID.IServiceProvider,
    )

private fun createCustomPropertyDefinition(customProperty: WinRtCustomProperty): WinRtCcwDefinition =
    WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.ICustomProperty,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        TypeProjection.copyTo(customProperty.type, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        writeOptionalHString(customProperty.name, rawArgs[0] as RawAddress)
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val target = decodeBorrowedInspectable(rawArgs[0] as RawAddress)
                        val value = customProperty.getValue(target)
                        (rawArgs[1] as RawAddress).writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(value))
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        customProperty.setValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val value = customProperty.getIndexedValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                        )
                        (rawArgs[2] as RawAddress).writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(value))
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        customProperty.setIndexedValue(
                            decodeBorrowedInspectable(rawArgs[0] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[1] as RawAddress),
                            decodeBorrowedInspectable(rawArgs[2] as RawAddress),
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        PlatformAbi.writeInt8(rawArgs[0] as RawAddress, if (customProperty.canWrite) 1 else 0)
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
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

private fun createCustomPropertyProviderDefinition(source: Any): WinRtCcwDefinition {
    val provider = explicitOrBindableCustomPropertyProvider(source)
    return WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            WinRtInspectableInterfaceDefinition(
                interfaceId = IID.ICustomPropertyProvider,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val property = provider?.getCustomProperty(decodeBorrowedString(rawArgs[0] as RawAddress))
                        (rawArgs[1] as RawAddress).writeReturnedPointer(propertyPointer(property))
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        val property = provider?.getIndexedProperty(
                            name = decodeBorrowedString(rawArgs[0] as RawAddress),
                            indexParameterType = TypeProjection.fromAbi(rawArgs[1] as RawAddress),
                        )
                        (rawArgs[2] as RawAddress).writeReturnedPointer(propertyPointer(property))
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
                        signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                    ) { rawArgs ->
                        writeOptionalHString(
                            provider?.getStringRepresentation() ?: source.toString(),
                            rawArgs[0] as RawAddress,
                        )
                        KnownHResults.S_OK.value
                    },
                    WinRtInspectableMethodDefinition(
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

private fun createPropertyChangedEventArgsDefinition(value: WinRtPropertyChangedEventArgs): WinRtCcwDefinition =
    createSinglePropertyRuntimeClassDefinition(
        interfaceIds = listOf(muxPropertyChangedEventArgsInterfaceId, wuxPropertyChangedEventArgsInterfaceId),
        runtimeClassName = propertyChangedEventArgsTypeName(),
        defaultInterfaceId = propertyChangedEventArgsInterfaceId(),
        propertyName = value.propertyName,
    )

private fun createDataErrorsChangedEventArgsDefinition(value: WinRtDataErrorsChangedEventArgs): WinRtCcwDefinition =
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
): WinRtCcwDefinition =
    WinRtCcwDefinition(
        interfaceDefinitions = interfaceIds.map { interfaceId ->
            WinRtInspectableInterfaceDefinition(
                interfaceId = interfaceId,
                methods = listOf(
                    WinRtInspectableMethodDefinition(
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

private fun createNotifyCollectionChangedEventArgsDefinition(value: WinRtNotifyCollectionChangedEventArgs): WinRtCcwDefinition =
    WinRtCcwDefinition(
        interfaceDefinitions = listOf(
            createNotifyCollectionChangedEventArgsInterfaceDefinition(IID.MUX_INotifyCollectionChangedEventArgs, value),
            createNotifyCollectionChangedEventArgsInterfaceDefinition(IID.WUX_INotifyCollectionChangedEventArgs, value),
        ),
        defaultInterfaceId = notifyCollectionChangedEventArgsInterfaceId(),
        runtimeClassName = notifyCollectionChangedEventArgsTypeName(),
    )

private fun createNotifyCollectionChangedEventArgsInterfaceDefinition(
    interfaceId: Guid,
    value: WinRtNotifyCollectionChangedEventArgs,
): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = interfaceId,
        methods = listOf(
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.action.ordinal)
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                (rawArgs[0] as RawAddress).writeReturnedPointer(
                    WinRtBindableVectorProjection.fromManaged(value.newItems?.toMutableList()),
                )
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                (rawArgs[0] as RawAddress).writeReturnedPointer(
                    WinRtBindableVectorProjection.fromManaged(value.oldItems?.toMutableList()),
                )
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.newStartingIndex)
                KnownHResults.S_OK.value
            },
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                PlatformAbi.writeInt32(rawArgs[0] as RawAddress, value.oldStartingIndex)
                KnownHResults.S_OK.value
            },
        ),
    )

private fun createStringableInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = IID.IStringable,
        methods = listOf(
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                writeOptionalHString(value.toString(), rawArgs[0] as RawAddress)
                KnownHResults.S_OK.value
            },
        ),
    )

private abstract class NativeAbiEventHandler<T>(
    handlerPointer: RawAddress,
    descriptor: WinRtDelegateDescriptor,
) : EventHandlerCallback<T>, AutoCloseable {
    private val reference =
        WinRtDelegateReference.fromAbi(cloneEventHandlerPointer(handlerPointer), descriptor)
            ?: throw WinRtIllegalStateException(
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
        WinRtDelegateDescriptor(
            interfaceId = commandEventHandlerIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
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
) : NativeAbiEventHandler<WinRtPropertyChangedEventArgs?>(
        handlerPointer,
        WinRtDelegateDescriptor(
            interfaceId = if (useWuxDelegate) IID.WUX_PropertyChangedEventHandler else IID.MUX_PropertyChangedEventHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
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
        args: WinRtPropertyChangedEventArgs?,
    ) {
        withPropertyChangedEventArgsArgument(args, eventArgsInterfaceId) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private class NativeCollectionChangedHandler(
    handlerPointer: RawAddress,
    useWuxDelegate: Boolean = false,
) : NativeAbiEventHandler<WinRtNotifyCollectionChangedEventArgs?>(
        handlerPointer,
        WinRtDelegateDescriptor(
            interfaceId = if (useWuxDelegate) IID.WUX_NotifyCollectionChangedEventHandler else IID.MUX_NotifyCollectionChangedEventHandler,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
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
        args: WinRtNotifyCollectionChangedEventArgs?,
    ) {
        withNotifyCollectionChangedEventArgsArgument(args, eventArgsInterfaceId) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private class NativeDataErrorsChangedHandler(
    handlerPointer: RawAddress,
) : NativeAbiEventHandler<WinRtDataErrorsChangedEventArgs?>(
        handlerPointer,
        WinRtDelegateDescriptor(
            interfaceId = dataErrorsChangedHandlerIid(),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
        ),
    ) {
    override fun invoke(
        sender: Any?,
        args: WinRtDataErrorsChangedEventArgs?,
    ) {
        withDataErrorsChangedEventArgsArgument(args) { argsPointer ->
            invokeWithAbi(sender, argsPointer)
        }
    }
}

private fun createPropertyChangedAddMethod(
    tokenTable: EventRegistrationTokenTable<WinRtPropertyChangedHandler>,
    notifier: WinRtPropertyChangedNotifier,
    useWuxDelegate: Boolean,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativePropertyChangedHandler(rawArgs[0] as RawAddress, useWuxDelegate = useWuxDelegate)
        val token = tokenTable.addEventHandler(handler)
        notifier.addPropertyChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createPropertyChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<WinRtPropertyChangedHandler>,
    notifier: WinRtPropertyChangedNotifier,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
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
    tokenTable: EventRegistrationTokenTable<WinRtCollectionChangedHandler>,
    notifier: WinRtCollectionChangedNotifier,
    useWuxDelegate: Boolean,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativeCollectionChangedHandler(rawArgs[0] as RawAddress, useWuxDelegate = useWuxDelegate)
        val token = tokenTable.addEventHandler(handler)
        notifier.addCollectionChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createCollectionChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<WinRtCollectionChangedHandler>,
    notifier: WinRtCollectionChangedNotifier,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
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
    tokenTable: EventRegistrationTokenTable<WinRtDataErrorsChangedHandler>,
    dataErrorInfo: WinRtDataErrorInfo,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
        signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
    ) { rawArgs ->
        val handler = NativeDataErrorsChangedHandler(rawArgs[0] as RawAddress)
        val token = tokenTable.addEventHandler(handler)
        dataErrorInfo.addErrorsChanged(handler)
        EventRegistrationToken.copyTo(token, rawArgs[1] as RawAddress)
        KnownHResults.S_OK.value
    }

private fun createDataErrorsChangedRemoveMethod(
    tokenTable: EventRegistrationTokenTable<WinRtDataErrorsChangedHandler>,
    dataErrorInfo: WinRtDataErrorInfo,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
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

private fun propertyPointer(property: WinRtCustomProperty?): RawAddress =
    property?.let { ComWrappersSupport.createCCWForObject(it, IID.ICustomProperty).useAndGetRef() }
        ?: PlatformAbi.nullPointer

private fun explicitOrBindableCustomPropertyProvider(source: Any): WinRtCustomPropertyProvider? =
    when (source) {
        is WinRtCustomPropertyProvider -> source
        is WinRtBindableCustomPropertyImplementation ->
            if (FeatureSwitches.enableICustomPropertyProviderSupport) {
                object : WinRtCustomPropertyProvider {
                    override fun getCustomProperty(name: String): WinRtCustomProperty? =
                        source.getCustomProperty(name)

                    override fun getIndexedProperty(
                        name: String,
                        indexParameterType: KClass<*>?,
                    ): WinRtCustomProperty? = source.getIndexedProperty(indexParameterType)

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
    throw WinRtUnsupportedOperationException(
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

private fun readPropertyChangedEventArgs(reference: ComObjectReference): WinRtPropertyChangedEventArgs =
    WinRtPropertyChangedEventArgs(
        propertyName = invokeHStringGetter(reference, slot = 6),
    )

private fun readDataErrorsChangedEventArgs(reference: ComObjectReference): WinRtDataErrorsChangedEventArgs =
    WinRtDataErrorsChangedEventArgs(
        propertyName = invokeHStringGetter(reference, slot = 6),
    )

private fun readNotifyCollectionChangedEventArgs(reference: ComObjectReference): WinRtNotifyCollectionChangedEventArgs {
    val action = WinRtNotifyCollectionChangedAction.entries[invokeInt32Getter(reference, slot = 6)]
    val newItems =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                ComVtableInvoker.invokeArgs(reference.pointer, 7, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )?.use { listReference ->
            WinRtBindableVectorProjection.fromAbi(listReference.pointer.asRawAddress())?.use { it.toList() }
        }
    val oldItems =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                ComVtableInvoker.invokeArgs(reference.pointer, 8, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )?.use { listReference ->
            WinRtBindableVectorProjection.fromAbi(listReference.pointer.asRawAddress())?.use { it.toList() }
        }
    val newStartingIndex = invokeInt32Getter(reference, slot = 9)
    val oldStartingIndex = invokeInt32Getter(reference, slot = 10)
    return WinRtNotifyCollectionChangedEventArgs(
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
        is IUnknownReference -> WinRtBindableObjectMarshaller.fromOwnedReference(argument)
        is ComObjectReference -> WinRtBindableObjectMarshaller.fromOwnedReference(IUnknownReference(argument.getRefPointer(), argument.interfaceId))
        else -> argument
    }

fun winRtPropertyChangedEventArgsFromAbi(argument: Any?): WinRtPropertyChangedEventArgs =
    decodePropertyChangedEventArgsArgument(argument)
        ?: error("PropertyChangedEventArgs ABI value is null")

private fun decodePropertyChangedEventArgsArgument(argument: Any?): WinRtPropertyChangedEventArgs? =
    when (argument) {
        null -> null
        is WinRtPropertyChangedEventArgs -> argument
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

private fun decodeNotifyCollectionChangedEventArgsArgument(argument: Any?): WinRtNotifyCollectionChangedEventArgs? =
    when (argument) {
        null -> null
        is WinRtNotifyCollectionChangedEventArgs -> argument
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

private fun decodeDataErrorsChangedEventArgsArgument(argument: Any?): WinRtDataErrorsChangedEventArgs? =
    when (argument) {
        null -> null
        is WinRtDataErrorsChangedEventArgs -> argument
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
    WinRtBindableObjectMarshaller.fromBorrowedAbi(pointer)

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
        WinRtPlatformApi.checkSucceededRaw(hr)
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
    WinRtBindableObjectMarshaller.createMarshaler(value).use { marshaler ->
        block(marshaler?.abi ?: PlatformAbi.nullPointer)
    }

private inline fun <T> withTwoObjectArguments(
    first: Any?,
    second: Any?,
    block: (RawAddress, RawAddress) -> T,
): T =
    WinRtBindableObjectMarshaller.createMarshaler(first).use { firstMarshaler ->
        WinRtBindableObjectMarshaller.createMarshaler(second).use { secondMarshaler ->
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
    WinRtBindableObjectMarshaller.createMarshaler(first).use { firstMarshaler ->
        WinRtBindableObjectMarshaller.createMarshaler(second).use { secondMarshaler ->
            WinRtBindableObjectMarshaller.createMarshaler(third).use { thirdMarshaler ->
                block(
                    firstMarshaler?.abi ?: PlatformAbi.nullPointer,
                    secondMarshaler?.abi ?: PlatformAbi.nullPointer,
                    thirdMarshaler?.abi ?: PlatformAbi.nullPointer,
                )
            }
        }
    }

private inline fun <T> withPropertyChangedEventArgsArgument(
    value: WinRtPropertyChangedEventArgs?,
    interfaceId: Guid = propertyChangedEventArgsInterfaceId(),
    block: (RawAddress) -> T,
): T =
    value?.let {
        ComWrappersSupport.createCCWForObject(it, interfaceId).use { block(it.pointer.asRawAddress()) }
    } ?: block(PlatformAbi.nullPointer)

private inline fun <T> withNotifyCollectionChangedEventArgsArgument(
    value: WinRtNotifyCollectionChangedEventArgs?,
    interfaceId: Guid = notifyCollectionChangedEventArgsInterfaceId(),
    block: (RawAddress) -> T,
): T =
    value?.let {
        ComWrappersSupport.createCCWForObject(it, interfaceId).use { block(it.pointer.asRawAddress()) }
    } ?: block(PlatformAbi.nullPointer)

private inline fun <T> withDataErrorsChangedEventArgsArgument(
    value: WinRtDataErrorsChangedEventArgs?,
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
            WinRtPlatformApi.checkSucceededRaw(hr)
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }
    }

private fun invokeExecute(
    reference: ComObjectReference,
    parameter: Any?,
) {
    withObjectArgument(parameter) { parameterPointer ->
        val hr = ComVtableInvoker.invokeArgs(reference.pointer, 9, parameterPointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }
}

private fun RawAddress.writeReturnedPointer(pointer: RawAddress) {
    PlatformAbi.writePointer(this, pointer)
}
