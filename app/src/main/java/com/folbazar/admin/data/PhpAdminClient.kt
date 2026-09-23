package com.folbazar.admin.data

import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** Laravel admin API client. Keeps the old Repository interface while using the live Laravel API. */
class PhpAdminClient {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String get() = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun bearer(builder: Request.Builder): Request.Builder {
        Session.accessToken?.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        return builder
    }

    /**
     * Laravel returns {ok:true,data:...}. The existing Repository expects arrays,
     * so object data is wrapped in a one-item array here.
     */
    private fun normalizeData(text: String): String {
        val root = try { json.parseToJsonElement(text) } catch (e: Exception) {
            throw IOException("Admin API returned invalid JSON: ${text.take(300)}", e)
        }

        if (root is JsonObject && root["ok"] != null && root.containsKey("data")) {
            val data = root["data"] ?: JsonNull
            return when (data) {
                is JsonArray -> data.toString()
                JsonNull -> "[]"
                else -> buildJsonArray { add(data) }.toString()
            }
        }
        return when (root) {
            is JsonArray -> root.toString()
            else -> buildJsonArray { add(root) }.toString()
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val clean = runCatching { normalizeData(text) }.getOrElse { text.take(500) }
                throw IOException("Admin API ${resp.code}: $clean")
            }
            return text.ifBlank { "[]" }
        }
    }

    private fun request(path: String): Request.Builder =
        bearer(Request.Builder().url("$baseUrl/$path").addHeader("Accept", "application/json"))

    private fun idFromFilter(filter: String): String? =
        Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.getOrNull(1)

    private fun queryValue(query: String, key: String): String? =
        Regex("(?:^|&)${Regex.escape(key)}=eq\\.([^&]+)").find(query.removePrefix("?"))?.groupValues?.getOrNull(1)

    private fun resourcePath(table: String): String = when (table) {
        "products" -> "admin/products"
        "categories" -> "admin/categories"
        "orders" -> "admin/orders"
        "profiles" -> "admin/customers"
        "coupons" -> "admin/coupons"
        "site_banners" -> "admin/banners"
        "site_settings" -> "admin/settings"
        else -> throw IllegalArgumentException("Unsupported Laravel admin resource: $table")
    }

    fun get(table: String, query: String = "?select=*"): String {
        return when (table) {
            "product_variants" -> {
                val productId = queryValue(query, "product_id")
                val path = if (productId != null) "admin/products/${encode(productId)}" else "admin/products"
                val raw = execute(request(path).get().build())
                val root = json.parseToJsonElement(raw)
                val data = if (root is JsonObject && root["data"] != null) root["data"] else root
                val variants = if (data is JsonObject) data["variants"] ?: JsonArray(emptyList()) else JsonArray(emptyList())
                variants.toString()
            }
            "order_items" -> {
                val orderId = queryValue(query, "order_id")
                if (orderId == null) "[]" else {
                    val raw = execute(request("admin/orders/${encode(orderId)}").get().build())
                    val root = json.parseToJsonElement(raw)
                    val data = if (root is JsonObject && root["data"] != null) root["data"] else root
                    val items = if (data is JsonObject) data["items"] ?: JsonArray(emptyList()) else JsonArray(emptyList())
                    items.toString()
                }
            }
            "complaints" -> {
                val raw = execute(request("complaints.php?action=admin-list").post("{}".toRequestBody(jsonMedia)).build())
                normalizeData(raw)
            }
            "wishlists" -> "[]"
            else -> {
                val raw = execute(request(resourcePath(table)).get().build())
                val array = Json.parseToJsonElement(normalizeData(raw)).jsonArray.toMutableList()
                val id = queryValue(query, "id")
                val key = when {
                    id != null -> "id"
                    queryValue(query, "key") != null -> "setting_key"
                    else -> null
                }
                val value = id ?: queryValue(query, "key")
                val filtered = if (key != null && value != null) array.filter {
                    it is JsonObject && it[key]?.jsonPrimitive?.contentOrNull == value
                } else array
                JsonArray(filtered).toString()
            }
        }
    }

    fun getWithBearer(table: String, query: String, accessToken: String): String = get(table, query)

    fun post(table: String, jsonBody: String): String {
        val path = when (table) {
            "products" -> "admin/products"
            "categories" -> "admin/categories"
            "coupons" -> "admin/coupons"
            "site_banners" -> "admin/banners"
            "product_variants" -> "manage.php?resource=variants&action=save"
            else -> throw IllegalArgumentException("Unsupported Laravel POST resource: $table")
        }
        val b = request(path).addHeader("Content-Type", "application/json")
        return execute(b.post(jsonBody.toRequestBody(jsonMedia)).build()).let(::normalizeData)
    }

    fun patch(table: String, filter: String, jsonBody: String): String {
        val id = idFromFilter(filter)
        val path = when (table) {
            "products" -> "admin/products/${encode(id ?: throw IllegalArgumentException("Product id required"))}"
            "categories" -> "admin/categories/${encode(id ?: throw IllegalArgumentException("Category id required"))}"
            "coupons" -> "admin/coupons/${encode(id ?: throw IllegalArgumentException("Coupon id required"))}"
            "site_banners" -> "admin/banners/${encode(id ?: throw IllegalArgumentException("Banner id required"))}"
            "orders" -> "admin/orders/${encode(id ?: throw IllegalArgumentException("Order id required"))}"
            "profiles" -> "admin/customers/${encode(id ?: throw IllegalArgumentException("Customer id required"))}"
            "site_settings" -> "admin/settings"
            "complaints" -> "complaints.php?action=admin-update"
            "product_variants" -> "manage.php?resource=variants&action=save"
            else -> throw IllegalArgumentException("Unsupported Laravel PATCH resource: $table")
        }
        val method = if (table == "site_settings" || table == "complaints" || table == "product_variants") "POST" else "PATCH"
        val b = request(path).addHeader("Content-Type", "application/json")
        val body = if (table == "complaints" && id != null) {
            val obj = json.parseToJsonElement(jsonBody).jsonObject.toMutableMap()
            obj["id"] = kotlinx.serialization.json.JsonPrimitive(id)
            JsonObject(obj).toString()
        } else jsonBody
        val req = if (method == "POST") b.post(body.toRequestBody(jsonMedia)).build()
        else b.patch(body.toRequestBody(jsonMedia)).build()
        return execute(req).let(::normalizeData)
    }

    fun delete(table: String, filter: String): String {
        val id = idFromFilter(filter) ?: throw IllegalArgumentException("id filter required")
        val path = when (table) {
            "products" -> "admin/products/${encode(id)}"
            "categories" -> "admin/categories/${encode(id)}"
            "coupons" -> "admin/coupons/${encode(id)}"
            "site_banners" -> "admin/banners/${encode(id)}"
            "product_variants" -> "manage.php?resource=variants&action=delete&id=${encode(id)}"
            else -> throw IllegalArgumentException("Unsupported Laravel DELETE resource: $table")
        }
        val req = request(path).delete().build()
        return execute(req).let(::normalizeData)
    }

    fun signIn(email: String, password: String): String {
        val body = "{\"identifier\":${jsonString(email)},\"password\":${jsonString(password)}}"
        return execute(
            Request.Builder().url("$baseUrl/auth/login")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMedia)).build()
        )
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
