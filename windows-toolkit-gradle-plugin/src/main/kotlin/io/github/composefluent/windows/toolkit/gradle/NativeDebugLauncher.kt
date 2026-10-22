package io.github.composefluent.windows.toolkit.gradle

internal const val NATIVE_DEBUG_DIRECTORY = "KOTLIN_WINRT_DEBUG_DIRECTORY"

/** Startup handoff runs outside the app, including outside UWP's AppContainer. */
internal val nativeDebugHandshakeScript = """
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
namespace KotlinWinRT.NativeDebug {
    public static class ProcessImage {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool QueryFullProcessImageName(IntPtr process, uint flags, StringBuilder name, ref uint size);
        public static string Read(IntPtr process) {
            var name = new StringBuilder(32768);
            uint size = (uint)name.Capacity;
            if (!QueryFullProcessImageName(process, 0, name, ref size))
                throw new System.ComponentModel.Win32Exception();
            return name.ToString();
        }
    }
}
'@
function Wait-NativeDebugger([int]${'$'}processId, [int]${'$'}threadId, [string]${'$'}directory) {
    ${'$'}process = Get-Process -Id ${'$'}processId -ErrorAction Stop
    ${'$'}null = ${'$'}process.Handle
    [IO.Directory]::CreateDirectory(${'$'}directory) | Out-Null
    ${'$'}token = [Guid]::NewGuid().ToString('N')
    ${'$'}descriptor = Join-Path ${'$'}directory 'debug.json'
    ${'$'}temporary = Join-Path ${'$'}directory ('debug-' + ${'$'}token + '.tmp')
    ${'$'}ready = Join-Path ${'$'}directory 'ready'
    ${'$'}cancel = Join-Path ${'$'}directory 'cancel'
    ${'$'}data = @{ pid = ${'$'}processId; thread = ${'$'}threadId; token = ${'$'}token;
        executable = [KotlinWinRT.NativeDebug.ProcessImage]::Read(${'$'}process.Handle); startedMillis = [long]((${'$'}process.StartTime.ToUniversalTime().Ticks - 621355968000000000) / 10000) }
    try {
        [IO.File]::WriteAllText(${'$'}temporary, (${'$'}data | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new(${'$'}false))
        [IO.File]::Move(${'$'}temporary, ${'$'}descriptor)
        ${'$'}deadline = [DateTime]::UtcNow.AddSeconds(60)
        while (${'$'}true) {
            if (${'$'}process.HasExited) { throw 'Native application exited before the debugger attached.' }
            if (Test-Path -LiteralPath ${'$'}cancel) { throw 'Native debug launch was cancelled.' }
            if ((Test-Path -LiteralPath ${'$'}ready) -and [IO.File]::ReadAllText(${'$'}ready) -eq ${'$'}token) { return }
            if ([DateTime]::UtcNow -gt ${'$'}deadline) { throw 'Native debugger did not attach within 60 seconds.' }
            Start-Sleep -Milliseconds 50
        }
    } finally {
        foreach (${'$'}file in @(${'$'}temporary, ${'$'}descriptor, ${'$'}ready, ${'$'}cancel)) {
            if (Test-Path -LiteralPath ${'$'}file) { [IO.File]::Delete(${'$'}file) }
        }
    }
}
""".trimIndent()

internal val nativeDebugLauncherScript = """
param([string]${'$'}Configuration)
${'$'}ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new(${'$'}false)
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
namespace KotlinWinRT.NativeDebug {
    public sealed class SuspendedProcess : IDisposable {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        struct StartupInfo {
            public int cb; public string reserved, desktop, title;
            public uint x, y, xSize, ySize, xCount, yCount, fill, flags;
            public ushort showWindow, reservedSize;
            public IntPtr reservedBytes, input, output, error;
        }
        [StructLayout(LayoutKind.Sequential)]
        struct ProcessInformation { public IntPtr process, thread; public uint pid, tid; }
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
        static extern bool CreateProcessW(string executable, StringBuilder command, IntPtr processAttributes,
            IntPtr threadAttributes, bool inherit, uint flags, IntPtr environment, string directory,
            ref StartupInfo startup, out ProcessInformation process);
        [DllImport("kernel32.dll", SetLastError=true)] static extern uint ResumeThread(IntPtr thread);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        [DllImport("kernel32.dll")] static extern bool TerminateProcess(IntPtr process, uint exitCode);
        ProcessInformation info;
        bool resumed;
        public uint ProcessId { get { return info.pid; } }
        public uint ThreadId { get { return info.tid; } }
        // CommandLineToArgvW/CRT quoting: preserve empty arguments, quotes and trailing backslashes.
        static string Quote(string argument) {
            var result = new StringBuilder("\"");
            int slashes = 0;
            foreach (char character in argument) {
                if (character == '\\') { slashes++; continue; }
                if (character == '"') result.Append('\\', slashes * 2 + 1);
                else result.Append('\\', slashes);
                slashes = 0; result.Append(character);
            }
            result.Append('\\', slashes * 2); result.Append('"');
            return result.ToString();
        }
        static string Arguments(string[] arguments) {
            var command = new StringBuilder();
            foreach (string argument in arguments) command.Append(' ').Append(Quote(argument));
            return command.ToString();
        }
        public SuspendedProcess(string executable, string[] arguments, string directory) : this(executable, Arguments(arguments), directory) { }
        public SuspendedProcess(string executable, string arguments, string directory) {
            var command = new StringBuilder(Quote(executable)).Append(' ').Append(arguments);
            var startup = new StartupInfo { cb = Marshal.SizeOf(typeof(StartupInfo)) };
            if (!CreateProcessW(executable, command, IntPtr.Zero, IntPtr.Zero, false, 4, IntPtr.Zero, directory, ref startup, out info))
                throw new System.ComponentModel.Win32Exception();
        }
        public void Resume() {
            if (ResumeThread(info.thread) == UInt32.MaxValue) throw new System.ComponentModel.Win32Exception();
            resumed = true;
        }
        public void Dispose() {
            // A cancelled handoff must not strand the newly created suspended app.
            if (!resumed && info.process != IntPtr.Zero) TerminateProcess(info.process, 1);
            if (info.thread != IntPtr.Zero) CloseHandle(info.thread);
            if (info.process != IntPtr.Zero) CloseHandle(info.process);
            info.thread = info.process = IntPtr.Zero;
        }
    }
}
'@
$nativeDebugHandshakeScript
${'$'}configurationData = Get-Content -LiteralPath ${'$'}Configuration -Raw -Encoding UTF8 | ConvertFrom-Json
if (${'$'}configurationData.PSObject.Properties['argumentString']) {
    ${'$'}launch = [KotlinWinRT.NativeDebug.SuspendedProcess]::new([string]${'$'}configurationData.executable,
        [string]${'$'}configurationData.argumentString, [string]${'$'}configurationData.directory)
} else {
    ${'$'}launch = [KotlinWinRT.NativeDebug.SuspendedProcess]::new([string]${'$'}configurationData.executable,
        [string[]]${'$'}configurationData.arguments, [string]${'$'}configurationData.directory)
}
try {
    ${'$'}process = Get-Process -Id ${'$'}launch.ProcessId -ErrorAction Stop
    ${'$'}null = ${'$'}process.Handle
    Wait-NativeDebugger ${'$'}launch.ProcessId ${'$'}launch.ThreadId ([string]${'$'}configurationData.debugDirectory)
    ${'$'}launch.Resume()
    if (${'$'}configurationData.detach) { exit 0 }
    ${'$'}process.WaitForExit()
    exit ${'$'}process.ExitCode
} finally { ${'$'}launch.Dispose() }
""".trimIndent()
