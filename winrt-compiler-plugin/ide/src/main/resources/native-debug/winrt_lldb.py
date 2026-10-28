"""Adapt the selected Kotlin toolchain's formatters without copying object layout."""

from pathlib import Path
import sys
import lldb


def load_kotlin_formatters(debugger, konan_home):
    script = Path(konan_home) / "tools" / "konan_lldb.py"
    if not script.is_file():
        raise RuntimeError("Kotlin/Native LLDB formatters were not found: " + str(script))
    # LLDB also needs the module in its interpreter namespace, not just sys.modules.
    command = lldb.SBCommandReturnObject()
    path = str(script).replace("\\", "/").replace('"', '\\"')
    debugger.GetCommandInterpreter().HandleCommand('command script import "' + path + '"', command)
    if not command.Succeeded():
        raise RuntimeError(command.GetError())
    module = sys.modules["konan_lldb"]

    # Kotlin 2.4.0's expression has an unmatched trailing ')'. Limit this fix
    # to that exact implementation; later toolchains keep their own classifier.
    classifier = getattr(module, "_is_string_or_array", None)
    if classifier and ")) ? 2 : 0)" in classifier.__code__.co_consts:
        def classify(value):
            address = module._hex(value.unsigned)
            string_type = module._hex(module._symbol_loaded_address("kclass:kotlin.String"))
            return module._evaluate(
                f"(int)Konan_DebugIsInstance({address}, {string_type}) ? 1 "
                f": ((int)Konan_DebugIsArray({address}) ? 2 : 0)"
            ).unsigned
        module._is_string_or_array = classify
    return module
