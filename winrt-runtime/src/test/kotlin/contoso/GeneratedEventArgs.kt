package contoso

import io.github.composefluent.winrt.runtime.IInspectableReference

class GeneratedEventArgs private constructor(
    val reference: IInspectableReference,
) {
    companion object Metadata {
        fun wrap(instance: IInspectableReference): GeneratedEventArgs =
            GeneratedEventArgs(instance)
    }
}
