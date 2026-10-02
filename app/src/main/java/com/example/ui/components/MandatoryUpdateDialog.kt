package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.AppUpdateInfo
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.util.ApkCompatibilityReport
import com.example.util.ApkUpdateInstaller
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

private val AmberGold = AmberPrimary
private val DarkCard = Slate900
private val DarkSurfaceVariant = Slate800
private val EmeraldGreen = SuccessGreen
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun MandatoryUpdateDialog(
    updateInfo: AppUpdateInfo,
    onMarkUpdateInstalled: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDownloading by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var downloadedMb by remember { mutableFloatStateOf(0f) }
    var totalMb by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var compatibilityReport by remember { mutableStateOf<ApkCompatibilityReport?>(null) }
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    var installAttemptedInSession by remember { mutableStateOf(false) }
    var showReplaceExistingHelper by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = (progressPercent / 100f).coerceIn(0f, 1f),
        label = "apk_download_progress"
    )

    // If this app instance was already updated after the Drive APK was uploaded, mark installed immediately
    LaunchedEffect(updateInfo.signature) {
        if (ApkUpdateInstaller.didAppUpdateComplete(context, updateInfo)) {
            onMarkUpdateInstalled(updateInfo.signature)
        }
    }

    // Handle returning from "Install Unknown Apps" settings OR returning from system PackageInstaller
    DisposableEffect(lifecycleOwner, waitingForInstallPermission, downloadedApkFile, installAttemptedInSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (ApkUpdateInstaller.didAppUpdateComplete(context, updateInfo)) {
                    onMarkUpdateInstalled(updateInfo.signature)
                    return@LifecycleEventObserver
                }
                val apkFile = downloadedApkFile
                if (waitingForInstallPermission && apkFile != null && apkFile.exists() && ApkUpdateInstaller.canRequestPackageInstalls(context)) {
                    waitingForInstallPermission = false
                    installAttemptedInSession = true
                    val report = compatibilityReport ?: ApkUpdateInstaller.inspectApkCompatibility(context, apkFile)
                    compatibilityReport = report
                    if (report.requiresUninstallToReplace) {
                        showReplaceExistingHelper = true
                        errorMessage = "Old installed version has a different signature/version. Tap 'Replace Old App & Install New' below to replace it cleanly!"
                        ApkUpdateInstaller.replaceConflictingAppAndInstall(
                            context = context,
                            apkFile = apkFile,
                            targetPackageName = report.archivePackageName.ifBlank { context.packageName }
                        )
                    } else {
                        ApkUpdateInstaller.launchApkInstaller(context, apkFile, updateInfo.signature)
                    }
                } else if (installAttemptedInSession && apkFile != null && apkFile.exists()) {
                    // User returned from PackageInstaller and the package wasn't replaced yet (e.g., "App not installed" due to old conflicting install)
                    showReplaceExistingHelper = true
                    errorMessage = "If Android showed 'App not installed' because an older version is already installed, tap 'Replace Old App & Install New' below!"
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun closeAppImmediately() {
        val activity = context as? Activity
        activity?.finishAffinity()
        exitProcess(0)
    }

    fun executeInstallForDownloadedApk(apkFile: File) {
        if (!ApkUpdateInstaller.canRequestPackageInstalls(context)) {
            waitingForInstallPermission = true
            errorMessage = "Please allow 'Install unknown apps' permission on the next screen to install the update."
            ApkUpdateInstaller.openInstallUnknownAppsSettings(context)
            return
        }
        val report = ApkUpdateInstaller.inspectApkCompatibility(context, apkFile)
        compatibilityReport = report

        // If the downloaded APK is the exact same version & signature already installed and up-to-date, mark installed
        if (!report.hasSignatureConflict &&
            report.installedVersionCode > 0L &&
            report.archiveVersionCode == report.installedVersionCode &&
            ApkUpdateInstaller.wasInstallAttemptedForSignature(context, updateInfo.signature)
        ) {
            onMarkUpdateInstalled(updateInfo.signature)
            return
        }

        installAttemptedInSession = true
        if (report.requiresUninstallToReplace) {
            showReplaceExistingHelper = true
            errorMessage = "Existing app conflict detected. Uninstalling old version first — your new update is saved in Downloads/KingoKing_Update.apk!"
            ApkUpdateInstaller.replaceConflictingAppAndInstall(
                context = context,
                apkFile = apkFile,
                targetPackageName = report.archivePackageName.ifBlank { context.packageName }
            )
        } else {
            val launched = ApkUpdateInstaller.launchApkInstaller(context, apkFile, updateInfo.signature)
            if (!launched) {
                showReplaceExistingHelper = true
                errorMessage = "Tap 'Install Update' or 'Replace Old App & Install New' below to complete installation."
            }
        }
    }

    fun triggerInstallOrDownload() {
        val existingFile = downloadedApkFile
        if (existingFile != null && existingFile.exists()) {
            executeInstallForDownloadedApk(existingFile)
            return
        }

        isDownloading = true
        errorMessage = null
        progressPercent = 2
        scope.launch {
            val result = ApkUpdateInstaller.downloadUpdateApk(
                context = context,
                updateInfo = updateInfo,
                onProgress = { pct, dlMb, totMb ->
                    progressPercent = pct
                    downloadedMb = dlMb
                    totalMb = totMb
                }
            )
            isDownloading = false
            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                executeInstallForDownloadedApk(apkFile)
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to download update from Google Drive."
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Mandatory update cannot be dismissed by tapping outside */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(AmberGold, EmeraldGreen.copy(alpha = 0.7f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("mandatory_update_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top glowing icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AmberGold.copy(alpha = 0.28f),
                                    AmberGold.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(1.5.dp, AmberGold.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (downloadedApkFile != null) Icons.Default.SecurityUpdateGood else Icons.Default.SystemUpdate,
                        contentDescription = "Mandatory App Update",
                        tint = AmberGold,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AmberGold.copy(alpha = 0.16f))
                        .border(1.dp, AmberGold.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "MANDATORY UPDATE REQUIRED",
                        color = AmberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Please Update App",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A new version of the app is available. Please update now to replace the old version and continue using the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // File info card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = updateInfo.fileName.ifBlank { "KingoKing_Update.apk" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeText = if (updateInfo.fileSize > 0L) {
                            String.format(Locale.US, "%.1f MB • Saved to Downloads/KingoKing_Update.apk", updateInfo.fileSize / (1024.0 * 1024.0))
                        } else {
                            "Official Google Drive Update Package"
                        }
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = AmberGold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloading Update...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberGold,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = EmeraldGreen,
                            trackColor = DarkSurfaceVariant
                        )

                        if (downloadedMb > 0f) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val mbStr = if (totalMb > 0f) {
                                String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb)
                            } else {
                                String.format(Locale.US, "%.1f MB downloaded", downloadedMb)
                            }
                            Text(
                                text = mbStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AlertRed.copy(alpha = 0.14f))
                            .border(1.dp, AlertRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 1-Tap Clean Replacement Button when an older conflicting APK is installed on the phone
                val readyApk = downloadedApkFile
                if (readyApk != null && readyApk.exists() && (showReplaceExistingHelper || compatibilityReport?.requiresUninstallToReplace == true)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val targetPkg = compatibilityReport?.archivePackageName?.ifBlank { context.packageName } ?: context.packageName
                            ApkUpdateInstaller.replaceConflictingAppAndInstall(
                                context = context,
                                apkFile = readyApk,
                                targetPackageName = targetPkg
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("replace_existing_app_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Replace Old App & Install New",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Cancel (closes app) & Update (downloads and installs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { closeAppImmediately() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("mandatory_update_cancel_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = { triggerInstallOrDownload() },
                        enabled = !isDownloading,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .testTag("mandatory_update_confirm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGold,
                            contentColor = Color.Black,
                            disabledContainerColor = AmberGold.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isDownloading -> "Updating..."
                                downloadedApkFile != null -> "Install Update"
                                else -> "Update"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Note: Updating is mandatory. Selecting Cancel will close the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
