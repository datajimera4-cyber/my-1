package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ApkUpdateInstaller {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (percent: Int, downloadedMb: Float, totalMb: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val targetFile = File(updatesDir, "KingoKing_Update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val candidateUrls = mutableListOf<String>()
            if (updateInfo.fileId.isNotBlank() && !updateInfo.fileId.startsWith("apk_")) {
                candidateUrls.add("https://drive.usercontent.google.com/download?id=${updateInfo.fileId}&export=download&confirm=t")
                candidateUrls.add("https://drive.google.com/uc?export=download&id=${updateInfo.fileId}&confirm=t")
            }
            if (updateInfo.downloadUrl.isNotBlank() && !candidateUrls.contains(updateInfo.downloadUrl)) {
                candidateUrls.add(updateInfo.downloadUrl)
            }

            var lastError = "Unable to download update APK from Google Drive."

            for (url in candidateUrls) {
                val success = tryDownloadUrlToFile(url, targetFile, updateInfo.fileSize, onProgress)
                if (success.isSuccess) {
                    return@withContext Result.success(targetFile)
                } else {
                    lastError = success.exceptionOrNull()?.message ?: lastError
                }
            }

            Result.failure(Exception(lastError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryDownloadUrlToFile(
        initialUrl: String,
        targetFile: File,
        knownFileSize: Long,
        onProgress: (percent: Int, downloadedMb: Float, totalMb: Float) -> Unit
    ): Result<File> {
        var currentUrl = initialUrl
        for (attempt in 1..2) {
            val req = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .get()
                .build()

            val response = downloadClient.newCall(req).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return Result.failure(Exception("Download failed (HTTP $code). Make sure the 'update' folder or APK in Google Drive is shared with 'Anyone with the link'."))
            }

            val body = response.body ?: run {
                response.close()
                return Result.failure(Exception("Empty response from Google Drive."))
            }

            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (contentType.contains("text/html")) {
                val html = body.string()
                response.close()
                // Extract Google Drive virus-scan confirmation link if present
                val actionMatch = Regex("""action="([^"]+)"[^>]*id="download-form"""").find(html)
                    ?: Regex("""id="download-form"[^>]*action="([^"]+)"""").find(html)
                val uuidMatch = Regex("""name="uuid"\s+value="([^"]+)"""").find(html)
                val idMatch = Regex("""name="id"\s+value="([^"]+)"""").find(html)
                if (idMatch != null && attempt == 1) {
                    val fId = idMatch.groupValues[1]
                    val uuid = uuidMatch?.groupValues?.get(1) ?: ""
                    val baseAction = actionMatch?.groupValues?.get(1)?.replace("&amp;", "&")
                        ?: "https://drive.usercontent.google.com/download"
                    currentUrl = if (baseAction.contains("?")) {
                        "$baseAction&id=$fId&export=download&confirm=t&uuid=$uuid"
                    } else {
                        "$baseAction?id=$fId&export=download&confirm=t&uuid=$uuid"
                    }
                    continue
                }
                return Result.failure(
                    Exception("Google Drive returned a web page instead of the APK. Please make sure the APK file in the 'update' folder has 'Anyone with the link' access enabled.")
                )
            }

            val totalBytes = if (body.contentLength() > 0) body.contentLength() else knownFileSize
            val totalMb = if (totalBytes > 0) totalBytes.toFloat() / (1024f * 1024f) else 0f

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    var firstChunkChecked = false

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!firstChunkChecked && bytesRead >= 2) {
                            firstChunkChecked = true
                            // APK files are ZIP archives and always start with 'P' (0x50) 'K' (0x4B)
                            if (buffer[0] != 0x50.toByte() || buffer[1] != 0x4B.toByte()) {
                                output.close()
                                response.close()
                                targetFile.delete()
                                return Result.failure(
                                    Exception("Downloaded file is not a valid APK package. Please check the file in the Google Drive 'update' folder.")
                                )
                            }
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val dlMb = downloadedBytes.toFloat() / (1024f * 1024f)
                        val pct = if (totalBytes > 0) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(1, 99)
                        } else {
                            50
                        }
                        onProgress(pct, dlMb, totalMb)
                    }
                    output.flush()
                }
            }
            response.close()

            if (targetFile.exists() && targetFile.length() > 10_000L) {
                val finalMb = targetFile.length().toFloat() / (1024f * 1024f)
                onProgress(100, finalMb, finalMb)
                return Result.success(targetFile)
            } else {
                targetFile.delete()
                return Result.failure(Exception("Downloaded APK file is incomplete or corrupted."))
            }
        }
        return Result.failure(Exception("Could not download APK from Google Drive."))
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
