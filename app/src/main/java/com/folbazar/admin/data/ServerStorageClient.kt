package com.folbazar.admin.data

import android.content.Context
import android.net.Uri
import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Uploads admin images through the Laravel admin upload endpoint. */
class ServerStorageClient(private val context: Context) {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')
    private val maxBytes = 5L * 1024L * 1024L

    suspend fun uploadImage(uri: Uri, folder: String = "products"): Result<String> = runCatching {
        require(folder in setOf("products", "banners", "categories")) { "Invalid upload folder" }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("ছবি পড়া যায়নি")

        val temp = File.createTempFile("fol_upload_", ".tmp", context.cacheDir)
        input.use { source ->
            FileOutputStream(temp).use { target -> source.copyTo(target) }
        }

        try {
            require(temp.length() in 1..maxBytes) { "ছবির সাইজ সর্বোচ্চ 5MB হতে হবে" }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image", temp.asRequestBody(mime.toMediaType()))
                .addFormDataPart("folder", folder)
                .build()

            val builder = Request.Builder()
                .url("$baseUrl/upload")
                .post(body)
                .addHeader("Accept", "application/json")
            Session.accessToken?.takeIf { it.isNotBlank() }?.let {
                builder.addHeader("Authorization", "Bearer $it")
            }

            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    throw IOException("Server upload ${response.code}: ${friendlyError(text)}")
                }
                val root = Json.parseToJsonElement(text).jsonObject
                if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    throw IOException(root["error"]?.jsonPrimitive?.contentOrNull ?: root["message"]?.jsonPrimitive?.contentOrNull ?: "ছবি আপলোড ব্যর্থ")
                }
                val data = root["data"]?.jsonObject ?: root
                val rawUrl = data["url"]?.jsonPrimitive?.contentOrNull
                    ?: data["path"]?.jsonPrimitive?.contentOrNull
                    ?: throw IOException("Server upload URL পাওয়া যায়নি")
                resolveUrl(rawUrl)
            }
        } finally {
            temp.delete()
        }
    }

    private fun resolveUrl(raw: String): String {
        val value = raw.trim()
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) return value
        return if (value.startsWith("/")) {
            val origin = baseUrl.substringBefore("/api", baseUrl).trimEnd('/')
            origin + value
        } else {
            val origin = baseUrl.substringBefore("/api", baseUrl).trimEnd('/')
            "$origin/${value.trimStart('/')}"
        }
    }

    private fun friendlyError(text: String): String = try {
        val root = Json.parseToJsonElement(text).jsonObject
        root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: text.take(300)
    } catch (_: Exception) { text.take(300) }
}
