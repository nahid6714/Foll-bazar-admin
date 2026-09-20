package com.folbazar.admin.data

import android.content.Context
import androidx.core.content.edit

/** Holds the current PHP admin API session in memory and persists it to SharedPreferences
 *  so the admin stays logged in between app launches. */
object Session {
    private const val PREFS = "folbazar_session"

    var accessToken: String? = null
        private set
    var email: String? = null
        private set
    var userId: String? = null
        private set
    var refreshToken: String? = null
        private set

    private var appContext: Context? = null

    val isLoggedIn: Boolean get() = accessToken != null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        accessToken = p.getString("access_token", null)
        refreshToken = p.getString("refresh_token", null)
        email = p.getString("email", null)
        userId = p.getString("user_id", null)
    }

    fun save(context: Context, token: String, email: String, userId: String, refreshToken: String? = null) {
        appContext = context.applicationContext
        accessToken = token
        this.email = email
        this.userId = userId
        this.refreshToken = refreshToken ?: this.refreshToken
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("access_token", token)
            putString("email", email)
            putString("user_id", userId)
            this@Session.refreshToken?.let { putString("refresh_token", it) }
        }
    }

    fun updateAccessToken(token: String, refreshToken: String? = null) {
        accessToken = token
        if (!refreshToken.isNullOrBlank()) this.refreshToken = refreshToken
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit {
            putString("access_token", token)
            this@Session.refreshToken?.let { putString("refresh_token", it) }
        }
    }

    fun clear(context: Context) {
        accessToken = null
        refreshToken = null
        email = null
        userId = null
        appContext = context.applicationContext
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }
}
