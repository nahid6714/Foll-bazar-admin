package com.folbazar.admin.data

import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** REST client for the current Fol Bazar Laravel admin API. */
class PhpAdminClient {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String get() = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("Admin API ${resp.code}: ${friendlyError(text)}")
            return text.ifBlank { "{}" }
        }
    }

    private fun friendlyError(text: String): String = try {
        val root = Json.parseToJsonElement(text).jsonObject
        val errors = root["errors"]?.jsonObject?.entries?.joinToString("; ") { (_, v) -> v.jsonArray.joinToString(", ") { it.jsonPrimitive.contentOrNull.orEmpty() } }
        errors ?: root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: text.take(400)
    } catch (_: Exception) { text.take(400) }

    private fun builder(path: String): Request.Builder = Request.Builder()
        .url("$baseUrl/$path")
        .addHeader("Accept", "application/json")
        .apply { Session.accessToken?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") } }

    private fun data(text: String): String {
        val root = Json.parseToJsonElement(text)
        if (root is JsonObject && root["ok"]?.jsonPrimitive?.booleanOrNull == true && root.containsKey("data")) {
            return root["data"].toString()
        }
        return root.toString()
    }

    fun get(table: String, query: String = "?select=*"): String {
        val id = Regex("(?:^|&)id=eq\\.([^&]+)").find(query.removePrefix("?"))?.groupValues?.get(1)
        val path = when (table) {
            "products" -> if (id != null) "admin/products/${encode(id)}" else "admin/products"
            "categories" -> if (id != null) "admin/categories/${encode(id)}" else "admin/categories"
            "orders" -> "admin/orders"
            "profiles" -> "admin/customers"
            "complaints" -> "admin/complaints"
            "coupons" -> "admin/coupons"
            "site_banners" -> "admin/banners"
            "site_settings" -> "admin/settings"
            "product_variants" -> {
                val productId = Regex("(?:^|&)product_id=eq\\.([^&]+)").find(query.removePrefix("?"))?.groupValues?.get(1)
                "manage.php?resource=variants&action=list" + (productId?.let { "&product_id=${encode(it)}" } ?: "")
            }
            else -> throw IOException("Unsupported admin resource: $table")
        }
        return data(execute(builder(path).get().build()))
    }

    fun getWithBearer(table: String, query: String, accessToken: String): String = get(table, query)

    fun post(table: String, jsonBody: String): String {
        val path = when (table) {
            "products" -> "admin/products"
            "categories" -> "admin/categories"
            "coupons" -> "admin/coupons"
            "site_banners" -> "admin/banners"
            "product_variants" -> "manage.php?resource=variants&action=save"
            else -> throw IOException("Unsupported admin resource: $table")
        }
        return data(execute(builder(path).addHeader("Content-Type", "application/json").post(jsonBody.toRequestBody(jsonMedia)).build()))
    }

    fun patch(table: String, filter: String, jsonBody: String): String {
        val id = Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.get(1)
            ?: throw IOException("id is required for $table update")
        val path = when (table) {
            "products" -> "admin/products/${encode(id)}"
            "categories" -> "admin/categories/${encode(id)}"
            "orders" -> "admin/orders/${encode(id)}"
            "profiles" -> "admin/customers/${encode(id)}"
            "complaints" -> "admin/complaints/${encode(id)}"
            "coupons" -> "admin/coupons/${encode(id)}"
            "site_banners" -> "admin/banners/${encode(id)}"
            "product_variants" -> "manage.php?resource=variants&action=save&id=${encode(id)}"
            else -> throw IOException("Unsupported admin resource: $table")
        }
        return data(execute(builder(path).addHeader("Content-Type", "application/json").patch(jsonBody.toRequestBody(jsonMedia)).build()))
    }

    fun delete(table: String, filter: String): String {
        val id = Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.get(1)
            ?: throw IOException("id is required for $table delete")
        val path = when (table) {
            "products" -> "admin/products/${encode(id)}"
            "categories" -> "admin/categories/${encode(id)}"
            "coupons" -> "admin/coupons/${encode(id)}"
            "site_banners" -> "admin/banners/${encode(id)}"
            "product_variants" -> "manage.php?resource=variants&action=delete&id=${encode(id)}"
            else -> throw IOException("Unsupported admin resource: $table")
        }
        return data(execute(builder(path).delete().build()))
    }

    fun order(orderId: String): String = data(execute(builder("admin/orders/${encode(orderId)}").get().build()))

    fun wishlistSummary(): String = data(execute(builder("admin/wishlist-summary").get().build()))

    fun setting(key: String): String = data(execute(builder("admin/settings").get().build()))

    fun saveSetting(key: String, value: JsonElement): String {
        val body = buildJsonObject { put("setting_key", key); put("setting_value", value.toString()) }.toString()
        return data(execute(builder("admin/settings").addHeader("Content-Type", "application/json").put(body.toRequestBody(jsonMedia)).build()))
    }

    fun signIn(email: String, password: String): String {
        val body = buildJsonObject { put("identifier", email); put("password", password) }.toString()
        return data(execute(Request.Builder().url("$baseUrl/auth/login")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia)).build()))
    }
}
