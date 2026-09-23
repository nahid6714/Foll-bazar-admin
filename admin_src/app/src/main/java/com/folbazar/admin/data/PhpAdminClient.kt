package com.folbazar.admin.data

import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** REST client for the Fol Bazar PHP/MySQL admin API. */
class PhpAdminClient {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String get() = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("Admin API ${resp.code}: $text")
            return text.ifBlank { "[]" }
        }
    }

    private fun builder(path: String): Request.Builder {
        val b = Request.Builder().url("$baseUrl/$path")
        Session.accessToken?.takeIf { it.isNotBlank() }?.let { b.addHeader("Authorization", "Bearer $it") }
        return b
    }

    private fun filterParams(filter: String): String {
        val parts = filter.split('&').mapNotNull { part ->
            val m = Regex("^([a-zA-Z_]+)=eq\\.(.*)$").find(part) ?: return@mapNotNull null
            "${m.groupValues[1]}=${encode(m.groupValues[2])}"
        }
        return if (parts.isEmpty()) "" else "&" + parts.joinToString("&")
    }

    fun get(table: String, query: String = "?select=*"): String {
        val filters = query.removePrefix("?").split('&')
            .filter { it.startsWith("id=eq.") || it.startsWith("product_id=eq.") || it.startsWith("order_id=eq.") || it.startsWith("key=eq.") || it.startsWith("active=eq.") }
            .joinToString("&")
        val suffix = if (filters.isBlank()) "" else "&$filters".replace("&active=eq.", "&active=")
        return execute(builder("admin.php?resource=${encode(table)}$suffix").get().build())
    }

    fun getWithBearer(table: String, query: String, accessToken: String): String {
        val filters = query.removePrefix("?").split('&')
            .filter { it.startsWith("id=eq.") || it.startsWith("product_id=eq.") || it.startsWith("order_id=eq.") || it.startsWith("key=eq.") }
            .joinToString("&")
        val suffix = if (filters.isBlank()) "" else "&$filters"
        return execute(Request.Builder().url("$baseUrl/admin.php?resource=${encode(table)}$suffix")
            .addHeader("Authorization", "Bearer $accessToken").get().build())
    }

    fun post(table: String, jsonBody: String): String = execute(
        builder("admin.php?resource=${encode(table)}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(jsonMedia)).build()
    )

    fun patch(table: String, filter: String, jsonBody: String): String = execute(
        builder("admin.php?resource=${encode(table)}${filterParams(filter)}")
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(jsonMedia)).build()
    )

    fun delete(table: String, filter: String): String = execute(
        builder("admin.php?resource=${encode(table)}${filterParams(filter)}").delete().build()
    )

    fun signIn(email: String, password: String): String {
        val body = "{\"email\":${jsonString(email)},\"password\":${jsonString(password)}}"
        return execute(Request.Builder().url("$baseUrl/admin.php?action=login")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia)).build())
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
