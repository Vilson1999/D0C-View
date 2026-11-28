package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashMap

data class ScanUpdate(
    val documents: List<DocumentItem>,
    val isComplete: Boolean
)

data class DocumentStoragePermissions(
    val hasReadPermission: Boolean,
    val hasWritePermission: Boolean
) {
    val hasFullAccess: Boolean get() = hasReadPermission && hasWritePermission
}

class DocumentScanner(private val context: Context) {

    fun getStoragePermissions(): DocumentStoragePermissions {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isManager = Environment.isExternalStorageManager()
            val hasLegacyRead = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            DocumentStoragePermissions(
                hasReadPermission = isManager || hasLegacyRead,
                hasWritePermission = isManager
            )
        } else {
            val hasRead = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val hasWrite = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            DocumentStoragePermissions(
                hasReadPermission = hasRead,
                hasWritePermission = hasWrite
            )
        }
    }

    fun hasReadPermission(): Boolean = getStoragePermissions().hasReadPermission

    fun hasWritePermission(): Boolean = getStoragePermissions().hasWritePermission

    fun hasFullStorageAccess(): Boolean = getStoragePermissions().hasFullAccess

    suspend fun scanAllDocuments(settings: AppSettings): List<DocumentItem> =
        streamScanAllDocuments(settings).last().documents

    fun streamScanAllDocuments(settings: AppSettings): Flow<ScanUpdate> = flow {
        val found = LinkedHashMap<String, DocumentItem>()
        val visitedCanonicalDirs = HashSet<String>()
        var lastEmitMs = System.currentTimeMillis()
        var pendingCount = 0

        suspend fun maybeEmitBatch(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if ((force && pendingCount > 0) ||
                pendingCount >= 12 ||
                (pendingCount > 0 && now - lastEmitMs >= 90L)
            ) {
                emit(ScanUpdate(found.values.toList(), isComplete = false))
                pendingCount = 0
                lastEmitMs = now
            }
        }

        // 1. Fast indexed MediaStore query first so documents show almost immediately
        val beforeMediaStore = found.size
        scanMediaStoreFiles(settings.scanDotFolders, found)
        pendingCount += (found.size - beforeMediaStore).coerceAtLeast(0)
        maybeEmitBatch(force = true)

        // 2. Include explicitly imported files immediately
        val beforeImported = found.size
        settings.importedFileUris.forEach { fileUriStr ->
            runCatching {
                resolveContentUriToDocument(fileUriStr.toUri())?.let { item ->
                    found.putIfAbsent(item.id, item)
                }
            }
        }
        pendingCount += (found.size - beforeImported).coerceAtLeast(0)
        maybeEmitBatch(force = true)

        // 3. Collect all internal + primary external + removable SD/USB storage roots
        val storageRoots = LinkedHashSet<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { storageRoots.add(it) }
        runCatching { context.filesDir }.getOrNull()?.let { storageRoots.add(it) }
        runCatching { context.noBackupFilesDir }.getOrNull()?.let { storageRoots.add(it) }

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && storageManager != null) {
            storageManager.storageVolumes.forEach { volume ->
                volume.directory?.let { storageRoots.add(it) }
            }
        }
        // Derive any external volume roots (SD card / USB) on Android 9-10 as well
        context.getExternalFilesDirs(null).filterNotNull().forEach { appExtDir ->
            storageRoots.add(appExtDir)
            var cursor: File? = appExtDir
            while (cursor != null) {
                val parent = cursor.parentFile ?: break
                if (cursor.name == "Android" && parent.canRead()) {
                    storageRoots.add(parent)
                    break
                }
                cursor = parent
            }
        }

        val onItemDiscovered: suspend () -> Unit = {
            pendingCount++
            maybeEmitBatch(force = false)
        }

        // 4. Prioritize common document directories before traversing full storage + dot folders
        val priorityFolderNames = listOf("Documents", "Download", "Downloads", "Document")
        storageRoots.forEach { root ->
            if (root.exists() && root.canRead()) {
                for (folderName in priorityFolderNames) {
                    val priorityDir = File(root, folderName)
                    if (priorityDir.exists() && priorityDir.isDirectory && priorityDir.canRead()) {
                        scanFileDirectoryRecursive(
                            dir = priorityDir,
                            scanDotFolders = settings.scanDotFolders,
                            visitedDirs = visitedCanonicalDirs,
                            output = found,
                            inDotHierarchy = false,
                            onItemAdded = onItemDiscovered
                        )
                    }
                }
            }
        }
        maybeEmitBatch(force = true)

        // 5. Recursively scan remaining internal/external roots including (.) dot folders
        storageRoots.forEach { root ->
            if (root.exists() && root.canRead()) {
                scanFileDirectoryRecursive(
                    dir = root,
                    scanDotFolders = settings.scanDotFolders,
                    visitedDirs = visitedCanonicalDirs,
                    output = found,
                    inDotHierarchy = false,
                    onItemAdded = onItemDiscovered
                )
            }
        }
        maybeEmitBatch(force = true)

        // 6. Scan user-picked SAF folder trees (including dot folders inside them)
        settings.customFolderUris.forEach { treeUriStr ->
            runCatching {
                val treeUri = treeUriStr.toUri()
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                scanSafTreeRecursive(
                    treeUri = treeUri,
                    parentDocId = rootDocId,
                    parentName = "Folder",
                    inDotHierarchy = rootDocId.contains("/."),
                    scanDotFolders = settings.scanDotFolders,
                    output = found,
                    onItemAdded = onItemDiscovered
                )
            }
        }

        emit(ScanUpdate(found.values.toList(), isComplete = true))
    }.flowOn(Dispatchers.IO)

    private suspend fun scanFileDirectoryRecursive(
        dir: File,
        scanDotFolders: Boolean,
        visitedDirs: HashSet<String>,
        output: LinkedHashMap<String, DocumentItem>,
        inDotHierarchy: Boolean,
        onItemAdded: suspend () -> Unit
    ) {
        val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!visitedDirs.add(canonical)) return

        // Skip Android app private sandbox directories that cannot be read or contain app caches
        if (canonical.endsWith("/Android/data") || canonical.endsWith("/Android/obb")) {
            return
        }

        val dirName = dir.name
        val isCurrentDotDir = dirName.startsWith(".") && dirName != "." && dirName != ".."
        if (isCurrentDotDir && !scanDotFolders) return

        val effectiveDotHierarchy = inDotHierarchy || isCurrentDotDir
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return

        for (child in children) {
            if (child.isDirectory) {
                scanFileDirectoryRecursive(
                    dir = child,
                    scanDotFolders = scanDotFolders,
                    visitedDirs = visitedDirs,
                    output = output,
                    inDotHierarchy = effectiveDotHierarchy,
                    onItemAdded = onItemAdded
                )
            } else if (child.isFile && child.length() >= 0L) {
                val name = child.name
                val isHiddenFile = name.startsWith(".")
                if (isHiddenFile && !scanDotFolders) continue

                val docType = DocType.fromFileName(name) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val key = child.absolutePath
                val parentName = child.parentFile?.name?.ifBlank { "Storage" } ?: "Storage"

                val existed = output.containsKey(key)
                output[key] = DocumentItem(
                    id = key,
                    name = name,
                    path = child.absolutePath,
                    uriString = Uri.fromFile(child).toString(),
                    type = docType,
                    extension = ext,
                    sizeBytes = child.length(),
                    lastModified = child.lastModified(),
                    parentFolderName = parentName,
                    isFromDotFolder = effectiveDotHierarchy || isHiddenFile,
                    isFilePath = true
                )
                if (!existed) {
                    onItemAdded()
                }
            }
        }
    }

    private fun scanMediaStoreFiles(
        scanDotFolders: Boolean,
        output: LinkedHashMap<String, DocumentItem>
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val docType = DocType.fromFileName(name) ?: DocType.fromMimeType(mime) ?: continue
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (dataPath != null &&
                        (dataPath.contains("/Android/data/") || dataPath.contains("/Android/obb/"))
                    ) {
                        continue
                    }

                    val hasDotSegment = (dataPath?.split('/')?.any { it.startsWith(".") } == true) ||
                        name.startsWith(".")
                    if (hasDotSegment && !scanDotFolders) continue

                    // If we already found this exact file path via File traversal, skip duplicate
                    if (!dataPath.isNullOrBlank() && output.containsKey(dataPath)) continue

                    val fileExistsDirectly = !dataPath.isNullOrBlank() && File(dataPath).let { it.exists() && it.canRead() }
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val size = cursor.getLong(sizeCol)
                    val modSeconds = cursor.getLong(modCol)
                    val lastMod = if (modSeconds > 0L) modSeconds * 1000L else 0L
                    val ext = name.substringAfterLast('.', docType.extensions.first()).lowercase()
                    val parentFolder = dataPath?.let { File(it).parentFile?.name } ?: "Device Storage"
                    val key = dataPath ?: contentUri.toString()

                    output[key] = DocumentItem(
                        id = key,
                        name = name,
                        path = dataPath ?: contentUri.toString(),
                        uriString = (if (fileExistsDirectly) Uri.fromFile(File(dataPath)) else contentUri).toString(),
                        type = docType,
                        extension = ext,
                        sizeBytes = size,
                        lastModified = lastMod,
                        parentFolderName = parentFolder,
                        isFromDotFolder = hasDotSegment,
                        isFilePath = fileExistsDirectly
                    )
                }
            }
        }
    }

    private suspend fun scanSafTreeRecursive(
        treeUri: Uri,
        parentDocId: String,
        parentName: String,
        inDotHierarchy: Boolean,
        scanDotFolders: Boolean,
        output: LinkedHashMap<String, DocumentItem>,
        depth: Int = 0,
        onItemAdded: suspend () -> Unit
    ) {
        if (depth > 16) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: ""
                    val isDot = name.startsWith(".")
                    if (isDot && !scanDotFolders) continue

                    val effectiveDot = inDotHierarchy || isDot
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanSafTreeRecursive(
                            treeUri = treeUri,
                            parentDocId = docId,
                            parentName = name,
                            inDotHierarchy = effectiveDot,
                            scanDotFolders = scanDotFolders,
                            output = output,
                            depth = depth + 1,
                            onItemAdded = onItemAdded
                        )
                    } else {
                        val docType = DocType.fromFileName(name) ?: DocType.fromMimeType(mime) ?: continue
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val mod = if (modCol >= 0) cursor.getLong(modCol) else 0L
                        val ext = name.substringAfterLast('.', docType.extensions.first()).lowercase()
                        val key = docUri.toString()

                        val prev = output.putIfAbsent(
                            key,
                            DocumentItem(
                                id = key,
                                name = name,
                                path = "$parentName/$name",
                                uriString = docUri.toString(),
                                type = docType,
                                extension = ext,
                                sizeBytes = size,
                                lastModified = mod,
                                parentFolderName = parentName,
                                isFromDotFolder = effectiveDot,
                                isFilePath = false
                            )
                        )
                        if (prev == null) {
                            onItemAdded()
                        }
                    }
                }
            }
        }
    }

    fun resolveContentUriToDocument(uri: Uri, mimeTypeHint: String? = null): DocumentItem? {
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) } ?: return null
            if (!file.exists()) return null
            val docType = DocType.fromFileName(file.name)
                ?: DocType.fromMimeType(mimeTypeHint)
                ?: return null
            val ext = file.extension.lowercase().ifBlank { docType.extensions.first() }
            return DocumentItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                uriString = uri.toString(),
                type = docType,
                extension = ext,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                parentFolderName = file.parentFile?.name ?: "Storage",
                isFromDotFolder = file.absolutePath.split('/').any { it.startsWith(".") },
                isFilePath = true
            )
        }

        val readable = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrElse {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }.getOrDefault(false)
        }
        if (!readable) return null

        val resolverMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        val docType = DocType.fromFileName(name)
            ?: DocType.fromMimeType(resolverMime)
            ?: DocType.fromMimeType(mimeTypeHint)
            ?: DocType.TEXT
        val ext = name.substringAfterLast('.', docType.extensions.first()).lowercase()

        return DocumentItem(
            id = uri.toString(),
            name = name,
            path = uri.toString(),
            uriString = uri.toString(),
            type = docType,
            extension = ext,
            sizeBytes = size,
            lastModified = System.currentTimeMillis(),
            parentFolderName = "Imported",
            isFromDotFolder = name.startsWith("."),
            isFilePath = false
        )
    }

    suspend fun renameDocument(doc: DocumentItem, requestedName: String): Result<DocumentItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = requestedName.trim()
                require(trimmed.isNotEmpty()) { "File name cannot be empty" }
                val finalName = if (trimmed.contains('.') || doc.extension.isBlank()) {
                    trimmed
                } else {
                    "$trimmed.${doc.extension}"
                }

                if (doc.isFilePath) {
                    val oldFile = File(doc.path)
                    require(oldFile.exists()) { "File no longer exists" }
                    val isAppPrivate = oldFile.absolutePath.startsWith(context.filesDir.absolutePath) ||
                        oldFile.absolutePath.startsWith(context.cacheDir.absolutePath)
                    if (!isAppPrivate && !hasWritePermission()) {
                        error("Document write permission is required to rename files")
                    }
                    val parentDir = oldFile.parentFile ?: error("Invalid parent folder")
                    val newFile = File(parentDir, finalName)
                    require(!newFile.exists()) { "A file named \"$finalName\" already exists" }
                    val renamed = oldFile.renameTo(newFile)
                    require(renamed) { "Could not rename file (write permission required)" }

                    renamedItem(
                        doc = doc,
                        newId = newFile.absolutePath,
                        newName = finalName,
                        newPath = newFile.absolutePath,
                        newUri = Uri.fromFile(newFile),
                        newLastModified = newFile.lastModified()
                    )
                } else {
                    val newUri = DocumentsContract.renameDocument(
                        context.contentResolver,
                        doc.uri,
                        finalName
                    ) ?: error("Could not rename document")
                    val folderPrefix = doc.path.substringBeforeLast('/', doc.parentFolderName)
                    val updatedDisplayPath = if (doc.path.contains('/') && !doc.path.startsWith("content://")) {
                        "$folderPrefix/$finalName"
                    } else {
                        finalName
                    }
                    renamedItem(
                        doc = doc,
                        newId = newUri.toString(),
                        newName = finalName,
                        newPath = updatedDisplayPath,
                        newUri = newUri,
                        newLastModified = System.currentTimeMillis()
                    )
                }
            }
        }

    private fun renamedItem(
        doc: DocumentItem,
        newId: String,
        newName: String,
        newPath: String,
        newUri: Uri,
        newLastModified: Long
    ): DocumentItem = doc.copy(
        id = newId,
        name = newName,
        path = newPath,
        uriString = newUri.toString(),
        type = DocType.fromFileName(newName) ?: doc.type,
        extension = newName.substringAfterLast('.', doc.extension).lowercase(),
        lastModified = newLastModified
    )

    suspend fun deleteDocument(doc: DocumentItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (doc.isFilePath) {
                val file = File(doc.path)
                val isAppPrivate = file.absolutePath.startsWith(context.filesDir.absolutePath) ||
                    file.absolutePath.startsWith(context.cacheDir.absolutePath)
                if (!isAppPrivate && !hasWritePermission()) {
                    return@runCatching false
                }
                file.delete()
            } else {
                val deletedViaDocs = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, doc.uri)
                }.getOrDefault(false)
                if (deletedViaDocs) {
                    true
                } else {
                    context.contentResolver.delete(doc.uri, null, null) > 0
                }
            }
        }.getOrDefault(false)
    }
}
