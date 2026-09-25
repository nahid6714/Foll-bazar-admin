package com.folbazar.admin.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.folbazar.admin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Checks the cPanel-hosted update.json manifest and downloads the APK.
 * The APK is validated before Android's package installer is opened.
 */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseName: String,
    val downloadUrl: String,
    val releaseUrl: String
)

sealed class UpdateResult {
    data object UpToDate : UpdateResult()
    data class Available(val info: AppUpdateInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

data class DownloadState(
    val running: Boolean = false,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val file: File? = null,
    val error: String? = null
)

object UpdateManager {
    private val updateManifestUrls: List<String>
        get() = listOf(
            "https://lakebazar.com/backend/api/update.php?action=manifest",
            BuildConfig.ADMIN_UPDATE_MANIFEST_URL.trim(),
            BuildConfig.ADMIN_UPDATE_BASE_URL.trim().trimEnd('/') + "/update.json"
        ).filter { it.isNotBlank() }.distinct()
    private const val APK_MIME = "application/vnd.android.package-archive"

    private fun httpGet(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "FolBazar-Admin/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        for (manifestUrl in updateManifestUrls) {
            try {
                val conn = httpGet(manifestUrl, "application/json")
                try {
                    val code = conn.responseCode
                    val contentType = conn.contentType.orEmpty()
                    val body = conn.inputStream.bufferedReader().use { it.readText() }

                    if (code !in 200..299) {
                        errors += "HTTP $code ${conn.responseMessage ?: ""}\nURL: $manifestUrl"
                        continue
                    }

                    val trimmed = body.trimStart()
                    if (trimmed.startsWith("<")) {
                        errors += buildString {
                            append("Update server JSON না দিয়ে HTML পাঠাচ্ছে.\n")
                            append("URL: $manifestUrl\n")
                            append("Content-Type: ${contentType.ifBlank { "unknown" }}\n")
                            append("সম্ভবত update.json নেই, অথবা website .htaccess request-টি index/404 HTML-এ পাঠাচ্ছে।")
                        }
                        continue
                    }

                    val root = try {
                        Json.parseToJsonElement(body).jsonObject
                    } catch (e: Exception) {
                        errors += "Update manifest-এর JSON invalid.\nURL: $manifestUrl\nResponse: ${body.take(500)}"
                        continue
                    }

                    val versionCode = root["versionCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val versionName = root["versionName"]?.jsonPrimitive?.content.orEmpty()
                    val releaseName = root["releaseName"]?.jsonPrimitive?.content
                        ?: "Fol Bazar Admin $versionName"
                    val rawDownloadUrl = root["downloadUrl"]?.jsonPrimitive?.content.orEmpty()
                    val downloadUrl = if (rawDownloadUrl.startsWith("http://", true) || rawDownloadUrl.startsWith("https://", true)) {
                        rawDownloadUrl
                    } else {
                        BuildConfig.ADMIN_UPDATE_BASE_URL.trim().trimEnd('/') + "/" + rawDownloadUrl.trimStart('/')
                    }
                    val releaseUrl = root["releaseUrl"]?.jsonPrimitive?.content.orEmpty()

                    if (versionCode <= 0 || downloadUrl.isBlank()) {
                        errors += "Update manifest-এ versionCode/downloadUrl সঠিক নেই.\nURL: $manifestUrl"
                        continue
                    }

                    return@withContext if (versionCode > BuildConfig.VERSION_CODE) {
                        UpdateResult.Available(
                            AppUpdateInfo(versionCode, versionName, releaseName, downloadUrl, releaseUrl)
                        )
                    } else {
                        UpdateResult.UpToDate
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: java.net.SocketTimeoutException) {
                errors += "Update check timeout.\nURL: $manifestUrl"
            } catch (e: java.net.UnknownHostException) {
                errors += "Update server domain পাওয়া যায়নি.\nURL: $manifestUrl"
            } catch (e: java.net.ConnectException) {
                errors += "Update server-এ connect করা যায়নি.\nURL: $manifestUrl"
            } catch (e: Exception) {
                errors += "Update check error: ${e.message ?: e::class.java.simpleName}\nURL: $manifestUrl"
            }
        }

        UpdateResult.Error(
            "Update check ব্যর্থ। যেসব URL পরীক্ষা করা হয়েছে:\n\n" +
                errors.joinToString("\n\n---\n\n").ifBlank { "কোনো response পাওয়া যায়নি" }
        )
    }

    suspend fun downloadUpdate(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (DownloadState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "FolBazar-Admin-${info.versionCode}.apk")
        val partial = File(updatesDir, "${target.name}.part")

        try {
            // Never trust a cached APK just because the file exists. Validate it first.
            if (target.exists()) {
                val cached = validateApk(context, target, info)
                if (cached.isSuccess) {
                    onProgress(
                        DownloadState(
                            progress = 100,
                            downloadedBytes = target.length(),
                            totalBytes = target.length(),
                            file = target
                        )
                    )
                    return@withContext Result.success(target)
                }
                target.delete()
            }

            partial.delete()
            val conn = httpGet(info.downloadUrl, "application/vnd.android.package-archive")
            try {
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.failure(
                        Exception("APK download failed: HTTP ${conn.responseCode}")
                    )
                }

                val contentType = conn.contentType.orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                    return@withContext Result.failure(
                        Exception("Update server থেকে APK নয়, অন্য ধরনের ফাইল পাওয়া গেছে")
                    )
                }

                val total = conn.contentLengthLong
                var done = 0L
                onProgress(DownloadState(running = true, totalBytes = total))

                FileOutputStream(partial).use { output ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            val percent = if (total > 0) {
                                ((done * 100) / total).toInt().coerceIn(0, 100)
                            } else 0
                            onProgress(
                                DownloadState(
                                    running = true,
                                    progress = percent,
                                    downloadedBytes = done,
                                    totalBytes = total
                                )
                            )
                        }
                    }
                }

                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }

                val validation = validateApk(context, target, info)
                if (validation.isFailure) {
                    target.delete()
                    throw validation.exceptionOrNull()
                        ?: IllegalStateException("ডাউনলোড হওয়া APK যাচাই করা যায়নি")
                }

                onProgress(
                    DownloadState(
                        progress = 100,
                        downloadedBytes = target.length(),
                        totalBytes = target.length(),
                        file = target
                    )
                )
                Result.success(target)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            partial.delete()
            target.delete()
            onProgress(DownloadState(error = e.message ?: "APK download করা যায়নি"))
            Result.failure(e)
        }
    }

    /**
     * Validates that the downloaded file is a real APK for this app, has the expected
     * version, and is signed with the same certificate as the installed application.
     */
    private fun validateApk(context: Context, file: File, expected: AppUpdateInfo): Result<Unit> =
        runCatching {
            require(file.isFile && file.length() > 50_000L) { "APK file অসম্পূর্ণ বা খুব ছোট" }

            // A real APK is a ZIP container. This also catches HTML/text downloads early.
            ZipFile(file).use { zip ->
                require(zip.getEntry("AndroidManifest.xml") != null) {
                    "ডাউনলোড হওয়া ফাইলটি valid APK নয়"
                }
            }

            val archiveInfo = getPackageInfoFromArchive(context, file)
                ?: error("Android APK parser ফাইলটি চিনতে পারেনি")

            require(archiveInfo.packageName == context.packageName) {
                "ডাউনলোড হওয়া APK-র package আলাদা"
            }

            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            require(archiveVersionCode == expected.versionCode.toLong()) {
                "APK version mismatch: expected ${expected.versionCode}, got $archiveVersionCode"
            }

            require(archiveVersionCode > BuildConfig.VERSION_CODE.toLong()) {
                "ডাউনলোড হওয়া APK বর্তমান version-এর চেয়ে নতুন নয়"
            }

            require(hasSameSigningCertificate(context, archiveInfo)) {
                "এই APK-র signing key বর্তমান অ্যাপের সাথে মেলে না"
            }
        }

    private fun getPackageInfoFromArchive(context: Context, file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    private fun hasSameSigningCertificate(context: Context, archiveInfo: PackageInfo): Boolean {
        val pm = context.packageManager
        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installed = installedInfo.signingInfo
            val archive = archiveInfo.signingInfo
            if (installed == null || archive == null) return false
            val installedSigners = if (installed.hasMultipleSigners()) {
                installed.apkContentsSigners
            } else {
                installed.signingCertificateHistory
            }
            val archiveSigners = if (archive.hasMultipleSigners()) {
                archive.apkContentsSigners
            } else {
                archive.signingCertificateHistory
            }
            return installedSigners.any { a -> archiveSigners.any { b -> a.toByteArray().contentEquals(b.toByteArray()) } }
        }

        @Suppress("DEPRECATION")
        val installed = installedInfo.signatures ?: return false
        @Suppress("DEPRECATION")
        val archive = archiveInfo.signatures ?: return false
        return installed.any { a -> archive.any { b -> a.toByteArray().contentEquals(b.toByteArray()) } }
    }

    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, file: File): Result<Unit> {
        return try {
            require(file.exists() && file.length() > 50_000L) { "APK file পাওয়া যায়নি" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
