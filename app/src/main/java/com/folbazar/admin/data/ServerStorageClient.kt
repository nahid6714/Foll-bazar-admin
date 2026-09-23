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
import java.util.concurrent.TimeUnit

/** Uploads admin images to the same PHP/MySQL site's local storage. */
class ServerStorageClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val baseUrl = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')

    suspend fun uploadImage(uri: Uri, folder: String = "products"): Result<String> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("ছবি পড়া যায়নি")

        val temp = File.createTempFile("fol_upload_", ".tmp", context.cacheDir)
        input.use { source ->
            FileOutputStream(temp).use { target -> source.copyTo(target) }
        }

        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "image.${extensionForMime(mime)}",
                    temp.asRequestBody(mime.toMediaType())
                )
                .addFormDataPart("folder", folder)
                .build()

            val endpoints = listOf(
                "$baseUrl/upload",
                "$baseUrl/admin.php?action=upload"
            )
            var lastError: IOException? = null

            for ((index, endpoint) in endpoints.withIndex()) {
                try {
                    val builder = Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .addHeader("Accept", "application/json")

                    Session.accessToken?.takeIf { it.isNotBlank() }?.let {
                        builder.addHeader("Authorization", "Bearer $it")
                    }

                    client.newCall(builder.build()).execute().use { response ->
                        val text = response.body?.string() ?: "{}"
                        if (!response.isSuccessful) {
                            lastError = IOException("Server upload ${response.code}: ${text.take(500)}")
                            val canTryNext = index < endpoints.lastIndex && (response.code == 404 || response.code == 405)
                            if (!canTryNext) {
                                throw lastError!!
                            }
                        } else {

                            val root = Json.parseToJsonElement(text).jsonObject
                            if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                                val error = root["error"]?.jsonPrimitive?.contentOrNull
                                    ?: root["message"]?.jsonPrimitive?.contentOrNull
                                    ?: "ছবি আপলোড ব্যর্থ"
                                throw IOException(error)
                            }

                            val data = root["data"]?.jsonObject
                            val url = data?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: root["url"]?.jsonPrimitive?.contentOrNull
                                ?: throw IOException("Server upload URL পাওয়া যায়নি")
                            return@runCatching url
                        }
                    }
                } catch (e: IOException) {
                    lastError = e
                    if (index == endpoints.lastIndex) {
                        throw e
                    }
                    // Try the next compatible upload endpoint without using Kotlin's
                    // experimental loop-control feature.
                }
            }
            throw (lastError ?: IOException("ছবি আপলোড ব্যর্থ"))
        } finally {
            temp.delete()
        }
    }

    private fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
}
