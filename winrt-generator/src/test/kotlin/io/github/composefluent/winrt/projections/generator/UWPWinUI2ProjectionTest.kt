package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.XamlFramework
import org.junit.Assert.*
import org.junit.Test

class UWPWinUI2ProjectionTest {
    @Test
    fun microsoft_ui_controls_retain_the_uwp_notification_interface_abi() {
        // WinUI 2 ItemsSourceView implements WUX INotifyCollectionChanged. CsWinRT
        // helpers.h maps both SDK contracts separately; a MUX class name is not
        // evidence of the WinUI 3 ABI (Projections.cs UseWindowsUIXamlProjections).
        val wux = WinRTTypeDefinition("Windows.UI.Xaml.Interop", "INotifyCollectionChanged",
            WinRTTypeKind.Interface, iid = Guid("28B167D5-1A31-465B-9B25-D5C3AE686C40"),
            methods = listOf(
                WinRTMethodDefinition("add_CollectionChanged", "Windows.Foundation.EventRegistrationToken",
                    parameters = listOf(WinRTParameterDefinition("handler", "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler")),
                    isSpecialName = true, methodRowId = 6),
                WinRTMethodDefinition("remove_CollectionChanged", "System.Void",
                    parameters = listOf(WinRTParameterDefinition("token", "Windows.Foundation.EventRegistrationToken")),
                    isSpecialName = true, methodRowId = 7),
            ), events = listOf(WinRTEventDefinition("CollectionChanged", "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler",
                addMethodName = "add_CollectionChanged", removeMethodName = "remove_CollectionChanged",
                addMethodRowId = 6, removeMethodRowId = 7)))
        val args = WinRTTypeDefinition("Windows.UI.Xaml.Interop", "NotifyCollectionChangedEventArgs", WinRTTypeKind.RuntimeClass)
        val handler = WinRTTypeDefinition("Windows.UI.Xaml.Interop", "NotifyCollectionChangedEventHandler",
            WinRTTypeKind.Delegate, iid = Guid("ca10b37c-f382-4591-8557-5e24965279b0"),
            methods = listOf(WinRTMethodDefinition("Invoke", "System.Void", parameters = listOf(
                WinRTParameterDefinition("sender", "System.Object"), WinRTParameterDefinition("e", args.qualifiedName)))))
        val mux = WinRTTypeDefinition("Microsoft.UI.Xaml.Interop", "INotifyCollectionChanged",
            WinRTTypeKind.Interface, iid = Guid("530155E1-28A5-5693-87CE-30724D95A06D"))
        val defaultInterface = WinRTTypeDefinition("Microsoft.UI.Xaml.Controls", "IItemsSourceView",
            WinRTTypeKind.Interface, iid = Guid("bdbb761b-4736-4a77-a750-fdd9654a447d"))
        val view = WinRTTypeDefinition("Microsoft.UI.Xaml.Controls", "ItemsSourceView", WinRTTypeKind.RuntimeClass,
            defaultInterfaceName = defaultInterface.qualifiedName,
            // Raw WinMD carries the runtime class event as well as its interface
            // contract; the member planner binds those class accessors to the ABI.
            methods = wux.methods, events = wux.events,
            implementedInterfaces = listOf(
                WinRTInterfaceImplementationDefinition(defaultInterface.qualifiedName, isDefault = true),
                WinRTInterfaceImplementationDefinition(wux.qualifiedName),
            ))
        val model = WinRTMetadataModel(listOf(wux, mux, args, handler, defaultInterface, view).groupBy { it.namespace }
            .map { (name, types) -> WinRTNamespace(name, types) })
        val context = WinRTMetadataProjectionContext(sources = emptyList(), xamlFramework = XamlFramework.UWP)
        val plan = KotlinProjectionPlanner().plan(model, context).single { it.type.qualifiedName == view.qualifiedName }
        assertFalse(plan.useWinUIXamlTypeRedirects)
        val file = KotlinProjectionGenerator(emitSupportFiles = true, projectionContext = context).generate(model)
            .single { it.relativePath == "microsoft/ui/xaml/controls/ItemsSourceView.kt" }.contents
        assertTrue(file, file.contains(wux.iid.toString(), ignoreCase = true))
        assertFalse(file, file.contains(mux.iid.toString(), ignoreCase = true))
    }
}
