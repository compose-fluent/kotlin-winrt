package microsoft.ui.xaml

import kotlin.reflect.KClass

interface IXamlServiceProvider {
    fun getService(type: KClass<*>?): Any?
}
