package com.plantdoctor.app.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Exports and imports all plants, analyses and photos as one zip file. */
object Backup {

    fun export(store: Store, out: OutputStream) {
        val root = store.root
        ZipOutputStream(out.buffered()).use { zip ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(root).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Merges a backup into the current data (existing files with the same name are replaced). Returns files imported. */
    fun import(store: Store, input: InputStream): Int {
        val root = store.root.canonicalFile
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val allowed = name.startsWith("images/") || name.startsWith("analyses/") || name.startsWith("plants/")
                val target = File(root, name).canonicalFile
                // Ignore anything that would land outside the data folder.
                if (!entry.isDirectory && allowed && target.path.startsWith(root.path + File.separator)) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
                zip.closeEntry()
            }
        }
        if (count == 0) throw AppException(ErrorReason.GENERIC)
        return count
    }
}
