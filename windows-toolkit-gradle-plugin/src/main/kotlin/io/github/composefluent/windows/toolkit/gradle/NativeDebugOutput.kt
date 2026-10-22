package io.github.composefluent.windows.toolkit.gradle

/** Win32 debugging events, owned by the activation helper rather than the app's AppContainer. */
internal val nativeDebugOutputSource = """
namespace KotlinWinRT.UWP {
    public static class DebugOutput {
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool DebugActiveProcess(uint process);
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool DebugActiveProcessStop(uint process);
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool DebugSetProcessKillOnExit(bool kill);
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool WaitForDebugEventEx(System.IntPtr data, uint timeout);
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool ContinueDebugEvent(uint process, uint thread, uint status);
        [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
        static extern bool ReadProcessMemory(System.IntPtr process, System.IntPtr address, byte[] buffer, System.UIntPtr size, out System.UIntPtr read);
        [System.Runtime.InteropServices.DllImport("kernel32.dll")]
        static extern bool CloseHandle(System.IntPtr handle);

        public static void Capture(uint processId, uint activationThread, string output) {
            if (System.IntPtr.Size != 8) throw new System.PlatformNotSupportedException("Native debug output requires an x64 host.");
            // DEBUG_EVENT is 176 bytes on x64; its union starts at offset 16.
            // EXCEPTION_RECORD is 152 bytes, followed by dwFirstChance.
            // https://learn.microsoft.com/windows/win32/api/minwinbase/ns-minwinbase-debug_event
            var data = System.Runtime.InteropServices.Marshal.AllocHGlobal(176);
            System.IntPtr process = System.IntPtr.Zero;
            bool attached = false, exited = false, initialBreakpoint = true;
            try {
                using (var stream = new System.IO.FileStream(output, System.IO.FileMode.Create, System.IO.FileAccess.Write, System.IO.FileShare.ReadWrite))
                using (var writer = new System.IO.StreamWriter(stream, new System.Text.UTF8Encoding(false))) {
                    writer.AutoFlush = true;
                    if (!DebugActiveProcess(processId)) throw new System.ComponentModel.Win32Exception();
                    attached = true;
                    if (!DebugSetProcessKillOnExit(false)) throw new System.ComponentModel.Win32Exception();
                    Launcher.ResumeActivation(processId, activationThread);
                    while (!exited) {
                        if (!WaitForDebugEventEx(data, 1000)) {
                            int error = System.Runtime.InteropServices.Marshal.GetLastWin32Error();
                            if (error == 121) continue; // ERROR_SEM_TIMEOUT
                            throw new System.ComponentModel.Win32Exception(error);
                        }
                        uint code = (uint)System.Runtime.InteropServices.Marshal.ReadInt32(data, 0);
                        uint pid = (uint)System.Runtime.InteropServices.Marshal.ReadInt32(data, 4);
                        uint tid = (uint)System.Runtime.InteropServices.Marshal.ReadInt32(data, 8);
                        uint status = 0x00010002; // DBG_CONTINUE
                        try {
                            if (code == 3) { // CREATE_PROCESS_DEBUG_EVENT
                                var file = System.Runtime.InteropServices.Marshal.ReadIntPtr(data, 16);
                                if (file != System.IntPtr.Zero) CloseHandle(file);
                                process = System.Runtime.InteropServices.Marshal.ReadIntPtr(data, 24);
                                // Windows closes process/thread event handles at their exit events.
                            } else if (code == 6) { // LOAD_DLL_DEBUG_EVENT
                                var file = System.Runtime.InteropServices.Marshal.ReadIntPtr(data, 16);
                                if (file != System.IntPtr.Zero) CloseHandle(file);
                            } else if (code == 8 && process != System.IntPtr.Zero) {
                                var address = System.Runtime.InteropServices.Marshal.ReadIntPtr(data, 16);
                                bool unicode = System.Runtime.InteropServices.Marshal.ReadInt16(data, 24) != 0;
                                int length = (ushort)System.Runtime.InteropServices.Marshal.ReadInt16(data, 26);
                                var bytes = new byte[length * (unicode ? 2 : 1)];
                                System.UIntPtr read;
                                if (bytes.Length > 0 && ReadProcessMemory(process, address, bytes, (System.UIntPtr)bytes.Length, out read)) {
                                    int count = System.Math.Min(bytes.Length, checked((int)read.ToUInt64()));
                                    string text = (unicode ? System.Text.Encoding.Unicode : System.Text.Encoding.Default).GetString(bytes, 0, count);
                                    int terminator = text.IndexOf('\0');
                                    writer.Write(terminator < 0 ? text : text.Substring(0, terminator));
                                }
                            } else if (code == 1) {
                                uint exception = (uint)System.Runtime.InteropServices.Marshal.ReadInt32(data, 16);
                                bool first = System.Runtime.InteropServices.Marshal.ReadInt32(data, 168) != 0;
                                if (initialBreakpoint && exception == 0x80000003) initialBreakpoint = false;
                                else {
                                    writer.WriteLine("[Native exception 0x{0:X8}, {1} chance, thread {2}]", exception, first ? "first" : "second", tid);
                                    status = 0x80010001; // DBG_EXCEPTION_NOT_HANDLED: preserve application handlers.
                                }
                            } else if (code == 5) {
                                exited = true;
                                writer.WriteLine("[Native process exited: {0}]", System.Runtime.InteropServices.Marshal.ReadInt32(data, 16));
                            }
                        } finally {
                            if (!ContinueDebugEvent(pid, tid, status)) throw new System.ComponentModel.Win32Exception();
                        }
                    }
                }
            } finally {
                if (attached && !exited) DebugActiveProcessStop(processId);
                System.Runtime.InteropServices.Marshal.FreeHGlobal(data);
            }
        }
    }
}
""".trimIndent()
