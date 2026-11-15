package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.DriveBackup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The backup zip, kept in a folder of the owner's own Google Drive.
 *
 * **Why this exists beside the folder backup in `Backup.kt`.** That one goes through Android's
 * document picker, which is the right answer for OneDrive, Dropbox, Nextcloud and an SD card - but
 * Google Drive does not offer itself to `ACTION_OPEN_DOCUMENT_TREE`, so the one cloud nearly every
 * Android phone already has could never be chosen there. This is the way to it, and it costs what
 * the folder backup was designed to avoid: a Google sign-in and a Google Cloud OAuth client.
 *
 * **`drive.file` and nothing wider.** The app sees only the files it created itself - its folder and
 * the zips in it - and never the rest of the owner's Drive. The same OAuth client on another phone
 * sees the same files, which is what lets a new phone restore from them. It is also the scope Google
 * does not put through a security review.
 *
 * **Five REST calls over `HttpURLConnection`**, the way every other request in this app is made,
 * rather than Google's Drive client: that library is several megabytes and a dozen transitive
 * dependencies for a find, a create, a list, an upload and a delete.
 *
 * **The file is the folder backup's file.** Same zip, same name, same seven kept, same rule that the
 * old copies are pruned only after the new one is whole - so a restore reads either kind through the
 * one `readBackup`. A resumable upload is used because Drive makes the file appear only when the last
 * byte has arrived: there is no half-written zip under a finished name, which is what the folder
 * backup's `.part` rename exists to prevent.
 */

/** The only scope asked for. See the note at the top of this file. */
internal const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** The folder in the owner's Drive, at its top level, that every backup goes into. */
internal const val DRIVE_FOLDER_NAME = "EGX Analyzer"

private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val API = "https://www.googleapis.com/drive/v3"
private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"

/** Drive's search for the app's folder. Only folders this app made can match, under `drive.file`. */
internal fun driveFolderQuery(): String =
    "name = '${DRIVE_FOLDER_NAME}' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"

/** Drive's search for everything directly in [folderId]. */
internal fun driveChildrenQuery(folderId: String): String =
    "'${folderId.replace("'", "\\'")}' in parents and trashed = false"

/** The body that creates the folder. */
internal fun driveFolderMetadata(): String =
    JSONObject().put("name", DRIVE_FOLDER_NAME).put("mimeType", FOLDER_MIME_TYPE).toString()

/** The body that opens an upload of [name] into [folderId]. */
internal fun driveUploadMetadata(name: String, folderId: String): String =
    JSONObject()
        .put("name", name)
        .put("mimeType", BACKUP_MIME_TYPE)
        .put("parents", JSONArray().put(folderId))
        .toString()

/** A `files.list` answer as backups. Anything without an id or a name is skipped. */
internal fun parseDriveFiles(json: String): List<DriveBackup> {
    val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
    return (0 until files.length()).mapNotNull { index ->
        val file = files.optJSONObject(index) ?: return@mapNotNull null
        val id = file.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val name = file.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
        // Drive sends size as a string, and leaves it out for anything that is not a plain file.
        DriveBackup(id, name, file.optString("size").toLongOrNull())
    }
}

/** The backups among [files], newest first: by name, which carries an ISO date. */
internal fun driveBackupsNewestFirst(files: List<DriveBackup>): List<DriveBackup> =
    files.filter { isBackupFileName(it.name) }.sortedByDescending(DriveBackup::name)

/**
 * The backups to delete once a new one is whole: every earlier copy under today's name, then the
 * oldest beyond [keep].
 *
 * Today's earlier copies go so a day is one file, as in the folder - a phone opened six times would
 * otherwise push six days out of the seven kept. Only files this app names are ever considered.
 */
internal fun driveFilesToRemove(
    files: List<DriveBackup>,
    writtenId: String,
    writtenName: String,
    keep: Int = BACKUPS_KEPT,
): List<DriveBackup> {
    val ours = files.filter { isBackupFileName(it.name) }
    val sameDay = ours.filter { it.name == writtenName && it.id != writtenId }
    val remaining = (ours - sameDay.toSet()).sortedByDescending(DriveBackup::name)
    return sameDay + remaining.drop(keep).filter { it.id != writtenId }
}

/** Drive answered 401: the token has expired or been revoked, and one fresh one is worth asking for. */
internal class DriveUnauthorizedException : IOException("Google Drive did not accept the sign-in")

/**
 * The five requests, given a way to get a token.
 *
 * [token] is asked with `fresh = true` once after a 401, so an expired token is replaced rather than
 * failing a backup that would have worked a second later.
 */
internal class DriveClient(private val token: suspend (fresh: Boolean) -> String) {

    /** The signed-in account's address, for the line in Settings that names it. */
    suspend fun accountEmail(): String? = authorized { bearer ->
        val body = request("GET", "$API/about?fields=user(emailAddress)", bearer)
        JSONObject(body).optJSONObject("user")?.optString("emailAddress")?.takeIf(String::isNotBlank)
    }

    /**
     * The app's folder, made if it is not there.
     *
     * Looked up every time rather than remembered, so a folder the owner deleted or trashed in Drive
     * is made again instead of every later backup failing against an id that has gone. If two phones
     * made one each at the same moment, the older is used by both from then on.
     */
    suspend fun folder(): String = authorized { bearer ->
        val found = request(
            "GET",
            "$API/files?q=${encode(driveFolderQuery())}&orderBy=createdTime&fields=files(id,name)&spaces=drive",
            bearer,
        )
        parseDriveFiles(found).firstOrNull()?.id ?: JSONObject(
            request("POST", "$API/files?fields=id", bearer, json = driveFolderMetadata()),
        ).getString("id")
    }

    /** Every file in the folder. */
    suspend fun list(folderId: String): List<DriveBackup> = authorized { bearer ->
        parseDriveFiles(
            request(
                "GET",
                "$API/files?q=${encode(driveChildrenQuery(folderId))}" +
                    "&fields=files(id,name,size)&pageSize=200&spaces=drive",
                bearer,
            ),
        )
    }

    /** Uploads [file] as [name] into [folderId], and returns the new file's id. */
    suspend fun upload(file: File, name: String, folderId: String): String = authorized { bearer ->
        val start = open("POST", "$UPLOAD_API/files?uploadType=resumable&fields=id", bearer)
        val session = try {
            start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            start.setRequestProperty("X-Upload-Content-Type", BACKUP_MIME_TYPE)
            start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
            start.doOutput = true
            start.outputStream.use { it.write(driveUploadMetadata(name, folderId).toByteArray()) }
            check(start)
            start.getHeaderField("Location") ?: throw IOException("Google Drive did not open an upload")
        } finally {
            start.disconnect()
        }
        val put = open("PUT", session, bearer)
        try {
            put.setRequestProperty("Content-Type", BACKUP_MIME_TYPE)
            put.doOutput = true
            // Streamed, not buffered: a record is tens of megabytes and HttpURLConnection would
            // otherwise hold all of it in memory to learn its length.
            put.setFixedLengthStreamingMode(file.length())
            put.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            JSONObject(check(put)).getString("id")
        } finally {
            put.disconnect()
        }
    }

    /** Fetches a backup into [target]. */
    suspend fun download(id: String, target: File) = authorized { bearer ->
        val connection = open("GET", "$API/files/${encode(id)}?alt=media", bearer)
        try {
            if (connection.responseCode == 401) throw DriveUnauthorizedException()
            if (connection.responseCode !in 200..299) throw failure(connection)
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    /** Deletes a file this app made. */
    suspend fun delete(id: String) = authorized { bearer ->
        request("DELETE", "$API/files/${encode(id)}", bearer)
        Unit
    }

    private suspend fun <T> authorized(block: (bearer: String) -> T): T =
        try {
            block(token(false))
        } catch (expired: DriveUnauthorizedException) {
            block(token(true))
        }

    private fun request(method: String, address: String, bearer: String, json: String? = null): String {
        val connection = open(method, address, bearer)
        try {
            if (json != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(json.toByteArray()) }
            }
            return check(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, address: String, bearer: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $bearer")
        }

    /** The body of a successful answer, or the reason Drive gave for refusing. */
    private fun check(connection: HttpURLConnection): String {
        val code = connection.responseCode
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299) throw failure(connection)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun failure(connection: HttpURLConnection): IOException {
        val detail = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.let { JSONObject(it).optJSONObject("error")?.optString("message") }
        }.getOrNull()
        return IOException(
            "Google Drive refused the request (${connection.responseCode})" +
                (detail?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""),
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
