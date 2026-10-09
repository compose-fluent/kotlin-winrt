package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.swing.Action
import javax.swing.JComponent

internal object WinRTPackageIdentity {
    /** appmodel.h PACKAGE_ID, delegated to Windows rather than reproducing its publisher hash. */
    fun familyName(name: String, publisher: String): String? {
        if (name.isBlank() || publisher.isBlank()) return null
        val function = NativeLibrary.getInstance("kernel32").getFunction("PackageFamilyNameFromId", Function.ALT_CONVENTION)
        fun wide(value: String) = Memory((value.length + 1L) * Native.WCHAR_SIZE).apply { setWideString(0, value) }
        wide(name).use { nativeName -> wide(publisher).use { nativePublisher -> wide("").use { resource ->
            Memory(16L + 4L * Native.POINTER_SIZE).use { id ->
                id.clear(); id.setInt(4, 11) // PROCESSOR_ARCHITECTURE_NEUTRAL
                id.setPointer(16, nativeName); id.setPointer(16L + Native.POINTER_SIZE, nativePublisher)
                id.setPointer(16L + 2L * Native.POINTER_SIZE, resource)
                val length = IntByReference(0)
                val result = function.invokeInt(arrayOf(id, length, Pointer.NULL))
                if (result != 122 || length.value !in 1..256) return null
                Memory(length.value.toLong() * Native.WCHAR_SIZE).use { output ->
                    return if (function.invokeInt(arrayOf(id, length, output)) == 0) output.getWideString(0) else null
                }
            }
        } } }
    }

    fun certificateSubject(path: Path, password: CharArray = charArrayOf()): String {
        require(Files.size(path) in 1..4_194_304) { "Choose a certificate smaller than 4 MB." }
        val certificate = Files.newInputStream(path).use { input ->
            if (path.fileName.toString().substringAfterLast('.').lowercase() in listOf("pfx", "p12")) {
                val store = KeyStore.getInstance("PKCS12").apply { load(input, password) }
                val aliases = store.aliases().toList()
                (aliases.firstOrNull { store.isKeyEntry(it) } ?: aliases.firstOrNull())?.let { store.getCertificate(it) } as? X509Certificate
                    ?: error("No X.509 certificate found.")
            } else CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
        // CertGetNameString with the RDN format produces the exact Windows
        // publisher spelling (including order/escaping) used by SignTool.
        val crypto = NativeLibrary.getInstance("crypt32")
        val bytes = certificate.encoded
        Memory(bytes.size.toLong()).use { encoded ->
            encoded.write(0, bytes, 0, bytes.size)
            val context = crypto.getFunction("CertCreateCertificateContext", Function.ALT_CONVENTION).invokePointer(arrayOf(0x10001, encoded, bytes.size))
                ?: error("Windows could not read this certificate.")
            try {
                Memory(4).use { format ->
                    format.setInt(0, 0x02000003) // CERT_X500_NAME_STR | CERT_NAME_STR_REVERSE_FLAG
                    val getName = crypto.getFunction("CertGetNameStringW", Function.ALT_CONVENTION)
                    val size = getName.invokeInt(arrayOf(context, 2, 0, format, Pointer.NULL, 0))
                    require(size in 2..16_384) { "The certificate has no publisher name." }
                    Memory(size.toLong() * Native.WCHAR_SIZE).use { output ->
                        getName.invokeInt(arrayOf(context, 2, 0, format, output, size))
                        return output.getWideString(0)
                    }
                }
            } finally { crypto.getFunction("CertFreeCertificateContext", Function.ALT_CONVENTION).invokeInt(arrayOf(context)) }
        }
    }

    fun chooseCertificate(project: Project, manifest: VirtualFile, onSubject: (String) -> Unit, onFailure: (String) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle("Choose signing certificate")
                .withFileFilter { it.extension?.lowercase() in listOf("cer", "crt", "pem", "pfx", "p12") }, project, manifest.parent) { file ->
                fun load(password: CharArray, retry: Boolean) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        val result = runCatching { certificateSubject(file.toNioPath(), password) }
                        password.fill('\u0000')
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed && manifest.isValid) result.fold(onSubject) {
                                if (retry && file.extension?.lowercase() in listOf("pfx", "p12")) CertificatePasswordDialog(project) { entered -> load(entered, false) }.show()
                                else onFailure("Unable to read the certificate. Check the file and its password.")
                            }
                        }
                    }
                }
                load(charArrayOf(), true)
            }
        }
    }
}

private class CertificatePasswordDialog(project: Project, private val onPassword: (CharArray) -> Unit) : DialogWrapper(project) {
    private val password = TextFieldState()
    init { title = "Certificate password"; init() }
    override fun createActions(): Array<Action> = emptyArray()
    override fun createCenterPanel(): JComponent = compose {
        Column(Modifier.width(360.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter the password to read the certificate's publisher.")
            TextField(password, outputTransformation = OutputTransformation { replace(0, length, "•".repeat(length)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onClick = {
                    val value = password.text.toString().toCharArray()
                    password.edit { replace(0, length, "") }
                    close(OK_EXIT_CODE); onPassword(value)
                }) { Text("Open") }
                OutlinedButton(onClick = { close(CANCEL_EXIT_CODE) }) { Text("Cancel") }
            }
        }
    }
    override fun dispose() { password.edit { replace(0, length, "") }; super.dispose() }
}
