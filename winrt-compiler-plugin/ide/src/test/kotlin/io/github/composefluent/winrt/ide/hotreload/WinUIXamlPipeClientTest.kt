@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.hotreload

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.runtime.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the actual Windows named-pipe path, including cancellation of a blocked read. */
class WinUIXamlPipeClientTest : BasePlatformTestCase() {
    fun testSessionCannotRedirectPipeToAnArbitraryPathOrAllPackages() {
        val file = Files.createTempFile("winrt-pipe-namespace-", ".session")
        val pid = ProcessHandle.current().pid()
        try {
            for (namespace in listOf("../elsewhere", "//remote/pipe", "Sessions/2/AppContainerNamedObjects/S-1-15-2-1")) {
                Files.writeString(file, "protocol=${WinUIXamlHotReloadProtocol.VERSION}\nport=0\n" +
                    "token=${"p".repeat(43)}\npid=$pid\npipe=KotlinWinRT.$pid.${"a".repeat(32)}\npipeNamespace=$namespace\n")
                try {
                    WinUIXamlHotReloadClient.read(file).close()
                    fail("Invalid namespace was accepted: $namespace")
                } catch (expected: IllegalArgumentException) {
                    assertEquals("Invalid development pipe namespace.", expected.message)
                }
            }
        } finally { Files.deleteIfExists(file) }
    }

    fun testPipeFramesAndReceipt() = withPipe(false) { client, _, process ->
        val reply = client.request()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply.status)
        assertEquals("pipe reply", reply.message)
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
    }

    fun testConsecutiveRequestsWaitForPreviousServerInstanceToDisconnect() = withPipe(false, replies = 2, releaseDelay = 250) { client, _, process ->
        repeat(2) { assertEquals("pipe reply", client.request().message) }
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
    }

    fun testClosingClientCancelsWaitingForBusyPipe() = withPipe(true) { client, directory, _ ->
        val executor = Executors.newFixedThreadPool(2)
        val first = executor.submit<WinUIXamlHotReloadReply> { client.request() }
        try {
            awaitFile(directory.resolve("received"))
            val waiting = executor.submit<WinUIXamlHotReloadReply> { client.request() }
            Thread.sleep(150)
            assertFalse("Second request must wait for the busy server", waiting.isDone)
            client.close()
            for (request in listOf(first, waiting)) {
                try { request.get(2, TimeUnit.SECONDS); fail("Closed client cannot complete") }
                catch (expected: java.util.concurrent.ExecutionException) { assertNotNull(expected.cause) }
            }
        } finally { executor.shutdownNow() }
    }

    fun testClosingClientCancelsBlockedPipeRead() = withPipe(true) { client, directory, _ ->
        val executor = Executors.newSingleThreadExecutor()
        val request = executor.submit<WinUIXamlHotReloadReply> { client.request() }
        try {
            awaitFile(directory.resolve("received"))
            val closing = System.nanoTime()
            client.close()
            assertTrue("Cancellation must not wait for the server", System.nanoTime() - closing < TimeUnit.SECONDS.toNanos(2))
            try {
                request.get(5, TimeUnit.SECONDS)
                fail("A closed pipe cannot return a reply")
            } catch (expected: java.util.concurrent.ExecutionException) {
                assertNotNull(expected.cause)
            }
        } finally { executor.shutdownNow() }
    }

    fun testStalledServerTimesOutWithoutWaitingForItsProcess() = withPipe(true) { client, _, process ->
        val executor = Executors.newSingleThreadExecutor()
        val request = executor.submit<WinUIXamlHotReloadReply> { client.request() }
        try {
            try {
                request.get(18, TimeUnit.SECONDS)
                fail("A stalled server must time out")
            } catch (expected: java.util.concurrent.ExecutionException) {
                assertTrue("Expected timeout, got ${expected.cause}", expected.cause is java.net.SocketTimeoutException)
            }
            assertTrue("Cancellation must not terminate the server", process.isAlive)
        } finally { executor.shutdownNow() }
    }

    private fun awaitFile(file: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!Files.exists(file) && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Pipe server did not publish $file", Files.exists(file))
    }

    private fun withPipe(stall: Boolean, replies: Int = 1, releaseDelay: Int = 0, action: (WinUIXamlHotReloadClient, Path, Process) -> Unit) {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val directory = Files.createTempDirectory("winrt-pipe-client-")
        val token = "p".repeat(43)
        val expected = Base64.getEncoder().encodeToString(WinUIXamlHotReloadCodec.writeRequest(token, null))
        val response = Base64.getEncoder().encodeToString(WinUIXamlHotReloadCodec.writeReply(
            WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "pipe reply")))
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}directory = '${directory.toString().replace("'", "''")}'
            ${'$'}name = 'KotlinWinRT.' + ${'$'}PID + '.' + [Guid]::NewGuid().ToString('N')
            ${'$'}pipe = [IO.Pipes.NamedPipeServerStream]::new('LOCAL\' + ${'$'}name,
                [IO.Pipes.PipeDirection]::InOut, 1, [IO.Pipes.PipeTransmissionMode]::Byte,
                [IO.Pipes.PipeOptions]::Asynchronous)
            try {
                [IO.File]::WriteAllText((Join-Path ${'$'}directory 'pipe.session'),
                    "protocol=${WinUIXamlHotReloadProtocol.VERSION}`nport=0`npipe=${'$'}name`ntoken=$token`npid=${'$'}PID`n")
                for (${'$'}iteration = 0; ${'$'}iteration -lt $replies; ${'$'}iteration++) {
                if (-not ${'$'}pipe.WaitForConnectionAsync().Wait(15000)) { throw 'No client' }
                ${'$'}expected = [Convert]::FromBase64String('$expected')
                ${'$'}actual = New-Object byte[] ${'$'}expected.Length
                ${'$'}offset = 0
                while (${'$'}offset -lt ${'$'}actual.Length) {
                    ${'$'}count = ${'$'}pipe.Read(${'$'}actual, ${'$'}offset, ${'$'}actual.Length - ${'$'}offset)
                    if (${'$'}count -le 0) { throw 'Truncated request' }
                    ${'$'}offset += ${'$'}count
                }
                if ([Convert]::ToBase64String(${'$'}actual) -ne '$expected') { throw 'Wrong request bytes' }
                [IO.File]::WriteAllText((Join-Path ${'$'}directory 'received'), 'ready')
                ${if (stall) "Start-Sleep -Seconds 30" else ""}
                ${'$'}reply = [Convert]::FromBase64String('$response')
                ${'$'}pipe.Write(${'$'}reply, 0, ${'$'}reply.Length)
                if (${'$'}pipe.ReadByte() -ne 0x6b) { throw 'Missing reply receipt' }
                ${if (releaseDelay > 0) "Start-Sleep -Milliseconds $releaseDelay" else ""}
                ${'$'}pipe.Disconnect()
                }
            } finally { ${'$'}pipe.Dispose() }
        """.trimIndent()
        val log = directory.resolve("server.log")
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
            Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)))
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            val session = directory.resolve("pipe.session")
            awaitFile(session)
            WinUIXamlHotReloadClient.read(session).use { action(it, directory, process) }
        } catch (error: Throwable) {
            error.addSuppressed(AssertionError(Files.readString(log)))
            throw error
        } finally {
            process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            listOf("received", "pipe.session", "server.log").forEach { Files.deleteIfExists(directory.resolve(it)) }
            Files.delete(directory)
        }
    }
}
