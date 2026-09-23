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

/** Uploads admin images to the same PHP/MySQL site's local storage. */
class ServerStorageClient(private val context: Context) {
    private val client = OkHttpClient()
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
                    "image",
                    temp.asRequestBody(mime.toMediaType())
                )
                .addFormDataPart("folder", folder)
                .build()

            val builder = Request.Builder()
                .url("$baseUrl/upload")
                .post(body)

            Session.accessToken?.takeIf { it.isNotBlank() }?.let {
                builder.addHeader("Authorization", "Bearer $it")
            }

            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    throw IOException("Server upload ${response.code}: $text")
                }
                val json = Json.parseToJsonElement(text).jsonObject
                if (json["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    val error = json["error"]?.jsonPrimitive?.contentOrNull
                        ?: json["message"]?.jsonPrimitive?.contentOrNull
                        ?: "ছবি আপলোড ব্যর্থ"
                    throw IOException(error)
                }
                val data = json["data"]?.jsonObject
                    ?: throw IOException("Server upload response invalid")
                data["url"]?.jsonPrimitive?.contentOrNull
                    ?: throw IOException("Server upload URL পাওয়া যায়নি")
            }
        } finally {
            temp.delete()
        }
    }
}
