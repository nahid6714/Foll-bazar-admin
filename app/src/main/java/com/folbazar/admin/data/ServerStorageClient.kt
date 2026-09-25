package com.folbazar.admin.data

import android.content.Context
import android.net.Uri
import android.os.NetworkOnMainThreadException
import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Uploads admin images through the Fol Bazar PHP/MySQL upload endpoint.
 *
 * This client deliberately returns detailed, user-visible errors so an
 * upload problem can be diagnosed from inside the Admin app without having
 * to guess whether the problem is the phone, network, authentication,
 * PHP endpoint, server permissions, file size, or response format.
 */
class ServerStorageClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')
    private val maxBytes = 8L * 1024L * 1024L

    suspend fun uploadImage(
        uri: Uri,
        folder: String = "products"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {

        if (folder !in setOf("products", "banners", "categories")) {
            throw IOException("Upload folder invalid: $folder")
        }

        if (baseUrl.isBlank()) {
            throw IOException("Admin API URL is empty")
        }

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("ছবি পড়া যায়নি। Android file permission বা selected file access সমস্যা হতে পারে।")

        val temp = File.createTempFile("fol_upload_", ".tmp", context.cacheDir)

        try {
            input.use { source ->
                FileOutputStream(temp).use { target ->
                    source.copyTo(target)
                }
            }

            val size = temp.length()
            if (size <= 0L) {
                throw IOException("Selected image is empty (0 bytes)")
            }

            if (size > maxBytes) {
                throw IOException(
                    "ছবির সাইজ 8 MB-এর বেশি। বর্তমান সাইজ: ${formatBytes(size)}"
                )
            }

            val mime = context.contentResolver.getType(uri)
                ?.takeIf { it.startsWith("image/") }
                ?: guessMimeFromName(uri)
                ?: "image/jpeg"

            val fileName = queryDisplayName(uri) ?: "image"
            val endpoint = "$baseUrl/upload.php"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    temp.asRequestBody(mime.toMediaType())
                )
                .addFormDataPart("folder", folder)
                .build()

            val builder = Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("Accept", "application/json")
                .addHeader("X-FolBazar-Client", "android-admin")

            val token = Session.accessToken?.trim()
            if (token.isNullOrBlank()) {
                throw IOException(
                    "Admin session token পাওয়া যায়নি। আগে Admin app-এ আবার login করুন।"
                )
            }

            builder.addHeader("Authorization", "Bearer $token")

            val request = builder.build()

            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val contentType = response.header("Content-Type").orEmpty()

                    if (!response.isSuccessful) {
                        throw IOException(
                            buildHttpError(
                                response.code,
                                response.message,
                                endpoint,
                                contentType,
                                text
                            )
                        )
                    }

                    if (text.isBlank()) {
                        throw IOException(
                            "Upload server returned an empty response. HTTP ${response.code}. Endpoint: $endpoint"
                        )
                    }

                    val root = try {
                        Json.parseToJsonElement(text).jsonObject
                    } catch (e: Exception) {
                        throw IOException(
                            "Server JSON response invalid. HTTP ${response.code}. Content-Type: ${contentType.ifBlank { "unknown" }}. Response: ${text.take(800)}",
                            e
                        )
                    }

                    if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                        throw IOException(
                            "Upload API rejected the image. HTTP ${response.code}. ${extractServerMessage(root, text)}"
                        )
                    }

                    val data = root["data"]?.jsonObject ?: root
                    val rawUrl = data["url"]?.jsonPrimitive?.contentOrNull
                        ?: data["path"]?.jsonPrimitive?.contentOrNull
                        ?: throw IOException(
                            "Upload succeeded but server did not return an image URL. Response: ${text.take(800)}"
                        )

                    resolveUrl(rawUrl)
                }
            } catch (e: SocketTimeoutException) {
                throw IOException(
                    "Upload timeout. Server took too long to respond. Check hosting/PHP processing and internet connection. Endpoint: $endpoint",
                    e
                )
            } catch (e: UnknownHostException) {
                throw IOException(
                    "Server domain could not be resolved: ${endpoint}. Check internet/DNS.",
                    e
                )
            } catch (e: ConnectException) {
                throw IOException(
                    "Could not connect to upload server: $endpoint",
                    e
                )
            } catch (e: NetworkOnMainThreadException) {
                throw IOException(
                    "Upload was attempted on the Android main thread. The app build has been updated to move upload/network work to Dispatchers.IO.",
                    e
                )
            }

        } finally {
            temp.delete()
        }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(normalizeError(it)) }
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMimeFromName(uri: Uri): String? {
        val name = queryDisplayName(uri)?.lowercase() ?: uri.toString().lowercase()
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".gif") -> "image/gif"
            else -> null
        }
    }

    private fun buildHttpError(
        code: Int,
        status: String,
        endpoint: String,
        contentType: String,
        text: String
    ): String {
        val serverMessage = friendlyError(text)
        val detail = when {
            code == 401 -> "Authentication failed. Admin session token invalid/missing."
            code == 403 -> "Permission denied. Admin account/Authorization check failed."
            code == 404 -> "Upload endpoint not found. Check upload.php URL."
            code == 413 -> "Server rejected the file because it is too large."
            code in 500..599 -> "Server-side PHP error. Check backend/api/error_log."
            else -> "Server rejected the upload request."
        }

        return buildString {
            append("Upload failed\n")
            append("HTTP: $code ${status.ifBlank { "Unknown" }}\n")
            append("Reason: $detail\n")
            append("Endpoint: $endpoint\n")
            append("Content-Type: ${contentType.ifBlank { "unknown" }}\n")
            if (serverMessage.isNotBlank()) {
                append("Server: $serverMessage")
            }
        }
    }

    private fun extractServerMessage(root: JsonObject, raw: String): String {
        return root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: root["detail"]?.jsonPrimitive?.contentOrNull
            ?: raw.take(800)
    }

    private fun friendlyError(text: String): String {
        if (text.isBlank()) return "Empty server response"

        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            extractServerMessage(root, text)
        } catch (_: Exception) {
            val cleaned = text
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (cleaned.isBlank()) "Non-JSON server response" else cleaned.take(800)
        }
    }

    private fun normalizeError(error: Throwable): Throwable {
        val message = error.message?.trim().orEmpty()
        if (message.isNotBlank()) return IOException(message, error)
        return IOException(
            "Unknown upload error: ${error::class.java.simpleName}",
            error
        )
    }

    private fun resolveUrl(raw: String): String {
        val value = raw.trim()
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) {
            return value
        }

        val origin = baseUrl
            .substringBefore("/api", baseUrl)
            .trimEnd('/')

        return if (value.startsWith("/")) {
            origin + value
        } else {
            "$origin/${value.trimStart('/')}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024L * 1024L) {
            String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        } else {
            String.format("%.1f KB", bytes / 1024.0)
        }
    }
}
