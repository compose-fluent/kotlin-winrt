package microsoft.ui.xaml.input

import windows.foundation.EventHandler

typealias CanExecuteChangedEventHandler = EventHandler<Any?>

interface ICommand {
    fun canExecute(parameter: Any?): Boolean

    fun execute(parameter: Any?)

    fun addCanExecuteChanged(handler: CanExecuteChangedEventHandler)

    fun removeCanExecuteChanged(handler: CanExecuteChangedEventHandler)
}
