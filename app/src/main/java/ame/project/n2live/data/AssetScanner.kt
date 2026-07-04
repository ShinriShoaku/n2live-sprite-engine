package ame.project.n2live.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Scan folder yang dipilih user (via Storage Access Framework) untuk file PNG,
 * lalu copy ke Internal Storage (spriteDir) supaya bisa dibaca langsung oleh
 * OpenGL loader tanpa perlu izin runtime tambahan setiap kali.
 */
object AssetScanner {

    /**
     * @return daftar nama file PNG yang berhasil di-scan & di-copy.
     */
    fun scanAndImport(context: Context, folderUri: Uri): List<String> {
        val docTree = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val targetDir = ConfigManager.spriteDir(context)
        val importedNames = mutableListOf<String>()

        for (doc in docTree.listFiles()) {
            val name = doc.name ?: continue
            if (!name.lowercase().endsWith(".png")) continue

            try {
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    File(targetDir, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                importedNames.add(name)
            } catch (e: Exception) {
                // skip file yang gagal dibaca
            }
        }
        return importedNames.sortedWith(compareBy<String> { it.length }.thenBy { it })
    }

    /** Daftar PNG yang sudah ada di internal storage (sprite folder). */
    fun listImported(context: Context): List<String> {
        return ConfigManager.spriteDir(context)
            .listFiles { f -> f.name.lowercase().endsWith(".png") }
            ?.map { it.name }
            ?.sortedWith(compareBy<String> { it.length }.thenBy { it })
            ?: emptyList()
    }
}
