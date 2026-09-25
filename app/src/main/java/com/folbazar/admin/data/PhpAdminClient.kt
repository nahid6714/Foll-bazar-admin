package com.folbazar.admin.data

import com.folbazar.admin.BuildConfig
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * REST client for the Fol Bazar PHP/MySQL API.
 *
 * Production API:
 * https://lakebazar.com/backend/api/
 */
class PhpAdminClient {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String
        get() = BuildConfig.ADMIN_API_BASE_URL.trim().trimEnd('/')

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException(
                    "Admin API ${resp.code}: ${friendlyError(text)}"
                )
            }
            return text.ifBlank { "{}" }
        }
    }

    private fun friendlyError(text: String): String = try {
        val root = Json.parseToJsonElement(text).jsonObject
        root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: text.take(400)
    } catch (_: Exception) {
        text.take(400)
    }

    private fun builder(path: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl/$path")
            .addHeader("Accept", "application/json")
            .apply {
                Session.accessToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        addHeader(
                            "Authorization",
                            "Bearer $it"
                        )
                    }
            }

    /**
     * PHP API responses are:
     * { "ok": true, "data": ... }
     *
     * Return only data to keep the existing Repository API stable.
     */
    private fun data(text: String): String {
        val root = Json.parseToJsonElement(text)

        if (
            root is JsonObject &&
            root["ok"]?.jsonPrimitive?.booleanOrNull == true
        ) {
            return root["data"]?.toString() ?: "[]"
        }

        return root.toString()
    }

    private fun idFromFilter(filter: String): String {
        return Regex(
            "(?:^|&)id=eq\\.([^&]+)"
        ).find(filter)?.groupValues?.get(1)
            ?: throw IOException("id is required")
    }

    fun get(
        table: String,
        query: String = ""
    ): String {

        val queryWithoutQuestion =
            query.removePrefix("?")

        val id = Regex(
            "(?:^|&)id=eq\\.([^&]+)"
        ).find(queryWithoutQuestion)
            ?.groupValues
            ?.get(1)

        val path = when (table) {

            "products" ->
                if (id != null) {
                    "manage.php?resource=products&action=list"
                } else {
                    "manage.php?resource=products&action=list"
                }

            "categories" ->
                "manage.php?resource=categories&action=list"

            "product_variants" -> {
                val productId = Regex(
                    "(?:^|&)product_id=eq\\.([^&]+)"
                ).find(queryWithoutQuestion)
                    ?.groupValues
                    ?.get(1)

                "manage.php?resource=variants&action=list" +
                    (
                        productId?.let {
                            "&product_id=${encode(it)}"
                        } ?: ""
                    )
            }

            "orders" ->
                "orders.php?action=admin-list"

            "profiles" ->
                "admin.php?action=users"

            "complaints" ->
                "complaints.php?action=admin-list"

            "coupons" ->
                "manage.php?resource=coupons&action=list"

            "site_banners" ->
                "banners.php?action=admin-list"

            "site_settings" ->
                "manage.php?resource=settings&action=list"

            else ->
                throw IOException(
                    "Unsupported admin resource: $table"
                )
        }

        val raw = execute(
            builder(path)
                .get()
                .build()
        )

        return data(
            if (table == "products" && id != null) {
                /*
                 * Product detail is available through the public
                 * products endpoint. This keeps the existing product()
                 * method working without changing the backend schema.
                 */
                execute(
                    builder(
                        "products.php?action=get&id=${encode(id)}"
                    ).get().build()
                )
            } else {
                raw
            }
        )
    }

    fun getWithBearer(
        table: String,
        query: String,
        accessToken: String
    ): String = get(table, query)

    fun post(
        table: String,
        jsonBody: String
    ): String {

        val (path, method) = when (table) {

            "products" ->
                "manage.php?resource=products&action=save" to "POST"

            "categories" ->
                "manage.php?resource=categories&action=save" to "POST"

            "coupons" ->
                "manage.php?resource=coupons&action=save" to "POST"

            "site_banners" ->
                "banners.php?action=create" to "POST"

            "product_variants" ->
                "manage.php?resource=variants&action=save" to "POST"

            else ->
                throw IOException(
                    "Unsupported admin resource: $table"
                )
        }

        val request = builder(path)
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .post(
                jsonBody.toRequestBody(jsonMedia)
            )
            .build()

        return data(
            execute(request)
        )
    }

    fun patch(
        table: String,
        filter: String,
        jsonBody: String
    ): String {

        val id = idFromFilter(filter)

        val path: String
        var body = jsonBody

        when (table) {

            "products" -> {
                path =
                    "manage.php?resource=products&action=save"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "categories" -> {
                path =
                    "manage.php?resource=categories&action=save"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "product_variants" -> {
                path =
                    "manage.php?resource=variants&action=save"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "coupons" -> {
                path =
                    "manage.php?resource=coupons&action=save"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "site_banners" -> {
                path =
                    "banners.php?action=update"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "orders" -> {
                path =
                    "orders.php?action=admin-update"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "profiles" -> {
                path =
                    "admin.php?action=update-user"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            "complaints" -> {
                path =
                    "complaints.php?action=admin-update"
                body = injectId(
                    jsonBody,
                    id
                )
            }

            else -> {
                throw IOException(
                    "Unsupported admin resource: $table"
                )
            }
        }

        val responseData = data(
            execute(
                builder(path)
                    .addHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .post(
                        body.toRequestBody(jsonMedia)
                    )
                    .build()
            )
        )

        /*
         * The PHP update endpoints return an empty data array.
         * The Repository expects an object/array for its existing
         * parse methods, so return the submitted updated fields with id.
         */
        return if (
            responseData == "[]" ||
            responseData.isBlank()
        ) {
            "[$body]"
        } else {
            responseData
        }
    }

    fun delete(
        table: String,
        filter: String
    ): String {

        val id = idFromFilter(filter)

        val path = when (table) {

            "products" ->
                "manage.php?resource=products&action=delete"

            "categories" ->
                "manage.php?resource=categories&action=delete"

            "coupons" ->
                "manage.php?resource=coupons&action=delete"

            "site_banners" ->
                "banners.php?action=delete"

            "product_variants" ->
                "manage.php?resource=variants&action=delete"

            else ->
                throw IOException(
                    "Unsupported admin resource: $table"
                )
        }

        val body = buildJsonObject {
            put("id", id)
        }.toString()

        return data(
            execute(
                builder(path)
                    .addHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .post(
                        body.toRequestBody(jsonMedia)
                    )
                    .build()
            )
        )
    }

    fun order(orderId: String): String =
        data(
            execute(
                builder(
                    "orders.php?action=admin-get&id=${encode(orderId)}"
                ).get().build()
            )
        )

    fun wishlistSummary(): String =
        data(
            execute(
                builder(
                    "admin.php?action=wishlist-summary"
                ).get().build()
            )
        )

    fun setting(key: String): String =
        data(
            execute(
                builder(
                    "manage.php?resource=settings&action=list"
                ).get().build()
            )
        )

    fun saveSetting(
        key: String,
        value: JsonElement
    ): String {

        val body = buildJsonObject {
            put("setting_key", key)
            put(
                "setting_value",
                value.toString()
            )
        }.toString()

        return data(
            execute(
                builder(
                    "manage.php?resource=settings&action=save"
                )
                    .addHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .post(
                        body.toRequestBody(jsonMedia)
                    )
                    .build()
            )
        )
    }

    fun signIn(
        email: String,
        password: String
    ): String {

        val body = buildJsonObject {
            put(
                "identifier",
                email
            )
            put(
                "password",
                password
            )
        }.toString()

        return data(
            execute(
                Request.Builder()
                    .url(
                        "$baseUrl/auth.php?action=login"
                    )
                    .addHeader(
                        "Accept",
                        "application/json"
                    )
                    .addHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .post(
                        body.toRequestBody(jsonMedia)
                    )
                    .build()
            )
        )
    }

    private fun injectId(
        json: String,
        id: String
    ): String {

        val root =
            Json.parseToJsonElement(json)
                .jsonObject
                .toMutableMap()

        root["id"] =
            JsonPrimitive(id)

        return JsonObject(root).toString()
    }
}
