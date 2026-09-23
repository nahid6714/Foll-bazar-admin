package com.folbazar.admin.data

import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import java.util.concurrent.TimeUnit

/** Laravel admin API client. Keeps the old Repository interface while using the live Laravel API. */
class PhpAdminClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
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

    private fun executeWithCompatibilityFallback(primary: Request, fallback: Request): String {
        return try {
            execute(primary)
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            if (message.contains("Admin API 404") || message.contains("Admin API 405")) {
                execute(fallback)
            } else {
                throw e
            }
        }
    }

    private fun executeWithThreeWayFallback(primary: Request, second: Request, third: Request): String {
        return try {
            execute(primary)
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            if (message.contains("Admin API 404") || message.contains("Admin API 405") || message.contains("Unknown admin action") || message.contains("Unknown admin resource")) {
                try { execute(second) } catch (e2: IOException) {
                    val m2 = e2.message.orEmpty()
                    if (m2.contains("Admin API 404") || m2.contains("Admin API 405") || m2.contains("Unknown admin action") || m2.contains("Unknown admin resource")) execute(third) else throw e2
                }
            } else throw e
        }
    }

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

    private fun legacyResourcePath(table: String, query: String = ""): String {
        val params = mutableListOf("resource=${encode(table)}")
        listOf("id", "product_id", "order_id", "key", "active").forEach { key ->
            queryValue(query, key)?.let { params += "${key}=${encode(it)}" }
        }
        return "admin.php?${params.joinToString("&")}"
    }

    fun get(table: String, query: String = "?select=*"): String {
        return when (table) {
            "product_variants" -> {
                val productId = queryValue(query, "product_id")
                val suffix = productId?.let { "&product_id=${encode(it)}" } ?: ""
                val primary = request("admin-data?resource=product_variants$suffix").get().build()
                val fallback = request(legacyResourcePath("product_variants", query)).get().build()
                val legacyManage = request("manage.php?resource=variants&action=list$suffix").get().build()
                normalizeData(executeWithThreeWayFallback(primary, fallback, legacyManage))
            }
            "order_items" -> {
                val orderId = queryValue(query, "order_id")
                if (orderId == null) "[]" else {
                    val primary = request("admin-data?resource=order_items&order_id=${encode(orderId)}").get().build()
                    val fallback = request(legacyResourcePath("order_items", query)).get().build()
                    normalizeData(executeWithCompatibilityFallback(primary, fallback))
                }
            }
            "complaints" -> {
                val primary = request("admin-data?resource=complaints").get().build()
                val fallback = request(legacyResourcePath("complaints", query)).get().build()
                normalizeData(executeWithCompatibilityFallback(primary, fallback))
            }
            "wishlists" -> {
                val primary = request("admin-data?resource=wishlists").get().build()
                val fallback = request(legacyResourcePath("wishlists", query)).get().build()
                normalizeData(executeWithCompatibilityFallback(primary, fallback))
            }
            else -> {
                val primary = request(resourcePath(table)).get().build()
                val fallback = request(legacyResourcePath(table, query)).get().build()
                val raw = executeWithCompatibilityFallback(primary, fallback)
                val array = Json.parseToJsonElement(normalizeData(raw)).jsonArray.toMutableList()
                val id = queryValue(query, "id")
                val key = when {
                    id != null -> "id"
                    queryValue(query, "key") != null -> "key"
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

    fun getWithBearer(table: String, query: String, accessToken: String): String {
        val path = when (table) {
            "wishlists" -> "admin-data?resource=wishlists"
            else -> resourcePath(table)
        }
        return execute(
            Request.Builder()
                .url("$baseUrl/$path")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .get().build()
        ).let(::normalizeData)
    }

    fun post(table: String, jsonBody: String): String {
        val path = when (table) {
            "products" -> "admin/products"
            "categories" -> "admin/categories"
            "coupons" -> "admin/coupons"
            "site_banners" -> "admin/banners"
            "product_variants" -> "admin-data?resource=product_variants&action=save"
            else -> throw IllegalArgumentException("Unsupported Laravel POST resource: $table")
        }
        val b = request(path).addHeader("Content-Type", "application/json")
        val primary = b.post(jsonBody.toRequestBody(jsonMedia)).build()
        val fallback = request(legacyResourcePath(table))
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(jsonMedia)).build()
        val raw = if (table == "product_variants") {
            val third = request("manage.php?resource=variants&action=save")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(jsonMedia)).build()
            executeWithThreeWayFallback(primary, fallback, third)
        } else executeWithCompatibilityFallback(primary, fallback)
        return raw.let(::normalizeData)
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
            "product_variants" -> "admin-data?resource=product_variants&action=save"
            else -> throw IllegalArgumentException("Unsupported Laravel PATCH resource: $table")
        }
        val method = when (table) {
            "site_settings", "complaints", "product_variants" -> if (table == "site_settings") "PUT" else "POST"
            else -> "PATCH"
        }
        val b = request(path).addHeader("Content-Type", "application/json")
        val body = if (table == "complaints" && id != null) {
            val obj = json.parseToJsonElement(jsonBody).jsonObject.toMutableMap()
            obj["id"] = kotlinx.serialization.json.JsonPrimitive(id)
            JsonObject(obj).toString()
        } else jsonBody
        val req = when (method) {
            "POST" -> b.post(body.toRequestBody(jsonMedia)).build()
            "PUT" -> b.put(body.toRequestBody(jsonMedia)).build()
            else -> b.patch(body.toRequestBody(jsonMedia)).build()
        }
        val fallbackBody = if (!id.isNullOrBlank()) {
            val obj = json.parseToJsonElement(body).jsonObject.toMutableMap()
            obj["id"] = JsonPrimitive(id)
            JsonObject(obj).toString()
        } else body
        val fallback = request(legacyResourcePath(table) + "&id=${encode(id ?: "")}")
            .addHeader("Content-Type", "application/json")
            .post(fallbackBody.toRequestBody(jsonMedia)).build()
        val raw = if (table == "product_variants") {
            val third = request("manage.php?resource=variants&action=save&id=${encode(id ?: "")}")
                .addHeader("Content-Type", "application/json")
                .post(fallbackBody.toRequestBody(jsonMedia)).build()
            executeWithThreeWayFallback(req, fallback, third)
        } else executeWithCompatibilityFallback(req, fallback)
        return raw.let(::normalizeData)
    }

    fun delete(table: String, filter: String): String {
        val id = idFromFilter(filter) ?: throw IllegalArgumentException("id filter required")
        val path = when (table) {
            "products" -> "admin/products/${encode(id)}"
            "categories" -> "admin/categories/${encode(id)}"
            "coupons" -> "admin/coupons/${encode(id)}"
            "site_banners" -> "admin/banners/${encode(id)}"
            "product_variants" -> "admin-data?resource=product_variants&action=delete&id=${encode(id)}"
            else -> throw IllegalArgumentException("Unsupported Laravel DELETE resource: $table")
        }
        val req = request(path).delete().build()
        val fallback = request(legacyResourcePath(table) + "&id=${encode(id)}").delete().build()
        val raw = if (table == "product_variants") {
            val third = request("manage.php?resource=variants&action=delete&id=${encode(id)}").delete().build()
            executeWithThreeWayFallback(req, fallback, third)
        } else executeWithCompatibilityFallback(req, fallback)
        return raw.let(::normalizeData)
    }

    fun signIn(email: String, password: String): String {
        val body = "{\"identifier\":${jsonString(email)},\"password\":${jsonString(password)}}"
        val primary = Request.Builder().url("$baseUrl/auth/login")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia)).build()
        val legacyBody = "{\"email\":${jsonString(email)},\"password\":${jsonString(password)}}"
        val fallback = Request.Builder().url("$baseUrl/admin.php?action=login")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(legacyBody.toRequestBody(jsonMedia)).build()
        val raw = executeWithCompatibilityFallback(primary, fallback)
        val root = json.parseToJsonElement(raw).jsonObject
        val data = (root["data"] as? JsonObject) ?: root
        // The legacy PHP endpoint returns user_id/email/name beside the token.
        // Normalize that shape so the rest of the app has one login contract.
        if (data["access_token"] != null && data["user"] == null && data["user_id"] != null) {
            val normalized = data.toMutableMap()
            normalized["user"] = buildJsonObject {
                put("id", data["user_id"]!!)
                put("email", data["email"] ?: JsonPrimitive(email))
                put("name", data["name"] ?: JsonPrimitive("Fol Bazar Admin"))
                put("role", JsonPrimitive("admin"))
            }
            val wrapped = if (root["data"] != null) root.toMutableMap().apply { put("data", JsonObject(normalized)) }
            else normalized
            return JsonObject(wrapped).toString()
        }
        return raw
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
