package com.zuan.kernelmanager.next.feature.appmanager

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.composables.core.rememberDialogState
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import com.zuan.kernelmanager.next.ui.components.ZkmDialogDefaults
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

private const val BACKUP_DIR = "/sdcard/backups/apps"

enum class AppTab(val titleRes: Int) {
    USER(R.string.app_user_apps),
    SYSTEM(R.string.app_system_apps),
    BACKUP(R.string.app_backups)
}


/**
 * [PERF] Sebelumnya setiap aplikasi menyimpan `loadIcon()` mentah — banyak
 * launcher icon adaptive beresolusi 432x432+, sehingga daftar 300+ aplikasi
 * menyimpan ratusan MB bitmap dan membuat tab lambat dibuka.
 * Ikon kini langsung di-downscale ke 144px (cukup tajam untuk ukuran tampilan
 * 44dp di layar xxxhdpi) sehingga footprint memori daftar turun drastis.
 */
private fun loadScaledIcon(pm: PackageManager, ai: ApplicationInfo, sizePx: Int = 144): Drawable? {
    val icon = runCatching { ai.loadIcon(pm) }.getOrNull() ?: return null
    if (icon is BitmapDrawable && icon.bitmap != null &&
        icon.bitmap.width <= sizePx && icon.bitmap.height <= sizePx) {
        return icon
    }
    return runCatching {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        @Suppress("DEPRECATION")
        BitmapDrawable(null, bitmap)
    }.getOrNull()
}

data class ManagedApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val path: String,
    val dir: String,
    val enabled: Boolean,
    val suspended: Boolean,
    val isSystem: Boolean,
    val updatedSystem: Boolean,
    val uid: Int,
    val versionName: String,
    val versionCode: Long,
    val stateTags: String,
    val targetSdk: Int,
    val minSdk: Int
)

enum class AppAction(val titleRes: Int) {
    STOP(R.string.app_force_stop),
    ENABLE(R.string.app_enable),
    DISABLE(R.string.app_freeze_disable),
    SUSPEND(R.string.app_suspend),
    UNSUSPEND(R.string.app_resume),
    HIDE(R.string.app_hide),
    CLEAR_DATA(R.string.app_clear_data),
    UNINSTALL(R.string.app_uninstall),
    UNINSTALL_USER(R.string.app_uninstall_user),
    DELETE_SYSTEM(R.string.app_delete_system_apk),
    BACKUP_APK(R.string.app_backup_apk),
    BACKUP_FULL(R.string.app_backup_full),
    RESTORE_APK(R.string.app_restore_apk),
    RESTORE_FULL(R.string.app_restore_full),
    RESTORE_DATA(R.string.app_restore_data),
    DELETE_BACKUP(R.string.app_delete_backup),
    DEX_SPEED(R.string.app_dex_speed),
    DEX_EVERYTHING(R.string.app_dex_everything),
    DEX_FORCE_SPEED(R.string.app_dex_force_speed),
    DEX_FORCE_EVERYTHING(R.string.app_dex_force_everything)
}


class AppManagerViewModel : ViewModel() {
    var tab by mutableStateOf(AppTab.USER)
        private set
    var apps by mutableStateOf<List<ManagedApp>>(emptyList())
        private set
    var selectedPackages by mutableStateOf<Set<String>>(emptySet())
        private set
    var query by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var output by mutableStateOf<String?>(null)
        private set

    fun setTab(value: AppTab, context: Context) {
        if (tab == value) return
        tab = value
        selectedPackages = emptySet()
        query = ""
        load(context)
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun load(context: Context) {
        if (loading || busy) return
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (tab) {
                    AppTab.USER -> loadInstalled(context.packageManager, userOnly = true)
                    AppTab.SYSTEM -> loadInstalled(context.packageManager, userOnly = false)
                    AppTab.BACKUP -> loadBackups(context.packageManager)
                }
            }.getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                apps = result
                selectedPackages = selectedPackages.intersect(result.map { it.packageName }.toSet())
                loading = false
            }
        }
    }

    fun toggleSelection(packageName: String) {
        selectedPackages = if (packageName in selectedPackages) {
            selectedPackages - packageName
        } else {
            selectedPackages + packageName
        }
    }

    fun toggleSelectAll() {
        val visible = filtered().map { it.packageName }.toSet()
        selectedPackages = if (visible.isNotEmpty() && visible.all { it in selectedPackages }) {
            selectedPackages - visible
        } else {
            selectedPackages + visible
        }
    }

    fun clearSelection() {
        selectedPackages = emptySet()
    }

    fun filtered(): List<ManagedApp> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return apps
        return apps.filter {
            it.label.lowercase(Locale.ROOT).contains(q) ||
                it.packageName.lowercase(Locale.ROOT).contains(q) ||
                it.stateTags.lowercase(Locale.ROOT).contains(q)
        }
    }

    fun perform(context: Context, action: AppAction) {
        if (busy || selectedPackages.isEmpty()) return
        val targets = apps.filter { it.packageName in selectedPackages }
        busy = true
        output = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { executeAction(context, targets, action) }
                .getOrElse { ResultText(false, it.message ?: it.javaClass.simpleName, "") }
            withContext(Dispatchers.Main) {
                busy = false
                output = result.details.takeIf { it.isNotBlank() }
                message = if (result.success) result.message else "Failed: ${result.message}"
                if (tab != AppTab.BACKUP) load(context) else load(context)
            }
        }
    }

    fun launch(context: Context, app: ManagedApp) {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent == null) error("No launcher activity")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { message = it.message ?: "Launch failed" }
    }

    fun openDetails(context: Context, app: ManagedApp) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { message = it.message ?: "Unable to open app details" }
    }

    fun openStore(context: Context, app: ManagedApp) {
        runCatching {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
            context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")))
            }
        }
    }

    fun clearMessage() {
        message = null
        output = null
    }

    private fun loadInstalled(pm: PackageManager, userOnly: Boolean): List<ManagedApp> {
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filterNot { isIgnored(it.packageName) }
            .mapNotNull { ai ->
                val source = ai.sourceDir.orEmpty()
                if (source.isBlank() || !File(ai.publicSourceDir.orEmpty()).exists()) return@mapNotNull null
                val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && !source.startsWith("/data/")
                if (userOnly == system) return@mapNotNull null
                val pkg = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
                ManagedApp(
                    label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                    packageName = ai.packageName,
                    icon = loadScaledIcon(pm, ai),
                    path = source,
                    dir = File(source).parent.orEmpty(),
                    enabled = ai.enabled,
                    suspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        (ai.flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
                    isSystem = system,
                    updatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 && source.startsWith("/data/"),
                    uid = ai.uid,
                    versionName = pkg?.versionName.orEmpty(),
                    versionCode = pkg?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong() } ?: 0L,
                    stateTags = buildStateTags(pm, ai),
                    targetSdk = ai.targetSdkVersion,
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ai.minSdkVersion else 0
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun loadBackups(pm: PackageManager): List<ManagedApp> {
        val dir = File(BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles()?.filter { it.isFile && it.extension.equals("apk", true) } ?: return emptyList()
        return files.mapNotNull { apk ->
            runCatching {
                val pkg = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_ACTIVITIES) ?: return@runCatching null
                val ai = pkg.applicationInfo ?: return@runCatching null
                ai.sourceDir = apk.absolutePath
                ai.publicSourceDir = apk.absolutePath
                ManagedApp(
                    label = "${ai.loadLabel(pm)}  (${pkg.longVersionCode})",
                    packageName = pkg.packageName,
                    icon = loadScaledIcon(pm, ai),
                    path = apk.absolutePath,
                    dir = apk.parent.orEmpty(),
                    enabled = true,
                    suspended = false,
                    isSystem = false,
                    updatedSystem = false,
                    uid = -1,
                    versionName = pkg.versionName.orEmpty(),
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else pkg.versionCode.toLong(),
                    stateTags = checkBackupInstallState(pm, pkg),
                    targetSdk = ai.targetSdkVersion,
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ai.minSdkVersion else 0
                )
            }.getOrNull()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun buildStateTags(pm: PackageManager, ai: ApplicationInfo): String {
        val tags = mutableListOf<String>()
        if (!ai.enabled) tags += "frozen"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (ai.flags and ApplicationInfo.FLAG_SUSPENDED) != 0) tags += "suspended"
        if ((ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) tags += "updated system"
        val backupApk = File(BACKUP_DIR, "${ai.packageName}.apk")
        val backupData = File(BACKUP_DIR, "${ai.packageName}.tar.gz")
        if (backupApk.exists()) {
            val b = runCatching { pm.getPackageArchiveInfo(backupApk.absolutePath, 0) }.getOrNull()
            val i = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
            when {
                b == null || i == null -> tags += "backup"
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.longVersionCode else b.versionCode.toLong()) == (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) i.longVersionCode else i.versionCode.toLong()) -> tags += "backup current"
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.longVersionCode else b.versionCode.toLong()) > (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) i.longVersionCode else i.versionCode.toLong()) -> tags += "backup newer"
                else -> tags += "backup older"
            }
        } else if (backupData.exists()) {
            tags += "data backup"
        }
        return tags.joinToString(" • ")
    }

    private fun checkBackupInstallState(pm: PackageManager, backup: PackageInfo): String {
        val installed = runCatching { pm.getPackageInfo(backup.packageName, 0) }.getOrNull() ?: return "not installed"
        return when {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()) == (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) backup.longVersionCode else backup.versionCode.toLong()) -> "installed current"
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()) > (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) backup.longVersionCode else backup.versionCode.toLong()) -> "installed newer"
            else -> "installed older"
        }
    }

    private fun isIgnored(packageName: String): Boolean =
        packageName.contains(".overlay") ||
            packageName.contains("com.android.theme.color") ||
            packageName.contains("com.android.theme.icon")

    private fun executeAction(context: Context, targets: List<ManagedApp>, action: AppAction): ResultText {
        val userId = currentUserId(context)
        val script = buildString {
            append("backup_path=${shellQuote(BACKUP_DIR)}\n")
            append("userdata_path=${shellQuote(context.dataDir.parent ?: "/data/user/0")}\n")
            append("mkdir -p \"\$backup_path\"\n")
            for (app in targets) {
                val pkg = shellQuote(app.packageName)
                val label = app.label.replace("'", "_")
                append("echo '[${action.name.lowercase(Locale.ROOT)}] $label'\n")
                when (action) {
                    AppAction.STOP -> append("am force-stop $pkg 2>/dev/null || true\n")
                    AppAction.ENABLE -> append("pm enable $pkg\n")
                    AppAction.DISABLE -> append("pm disable $pkg\n")
                    AppAction.SUSPEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) append("pm suspend $pkg\n")
                    AppAction.UNSUSPEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) append("am kill $pkg 2>/dev/null || true\npm unsuspend $pkg\n")
                    AppAction.HIDE -> append("pm hide $pkg\n")
                    AppAction.CLEAR_DATA -> append("pm clear $pkg\n")
                    AppAction.UNINSTALL -> append("pm uninstall $pkg\n")
                    AppAction.UNINSTALL_USER -> append("pm uninstall --user $userId $pkg\n")
                    AppAction.DELETE_SYSTEM -> {
                        append("pm disable $pkg 2>/dev/null || true\n")
                        if (app.dir.isNotBlank()) append("rm -rf ${shellQuote(app.dir + "/oat")} ${shellQuote(app.dir + "/lib")}\n")
                        append("rm -f ${shellQuote(app.path)}\n")
                    }
                    AppAction.BACKUP_APK -> {
                        append("rm -f \"\$backup_path/${app.packageName}.apk\"\n")
                        append("cp -f ${shellQuote(app.path)} \"\$backup_path/${app.packageName}.apk\"\n")
                    }
                    AppAction.BACKUP_FULL -> {
                        append("rm -f \"\$backup_path/${app.packageName}.apk\" \"\$backup_path/${app.packageName}.tar.gz\"\n")
                        append("cp -f ${shellQuote(app.path)} \"\$backup_path/${app.packageName}.apk\"\n")
                        append("am force-stop $pkg 2>/dev/null || true\n")
                        append("cd \"\$userdata_path/${app.packageName}\"\n")
                        append("if command -v pigz >/dev/null 2>&1; then tar cpf - * --exclude ./cache --exclude ./lib --exclude ./lib64 | pigz > \"\$backup_path/${app.packageName}.tar.gz\"; else tar -czpf \"\$backup_path/${app.packageName}.tar.gz\" * --exclude ./cache --exclude ./lib --exclude ./lib64; fi\n")
                    }
                    AppAction.RESTORE_APK -> restoreApkScript(app)
                    AppAction.RESTORE_FULL -> {
                        append(restoreApkScript(app).toString())
                        append(restoreDataScript(app, userId).toString())
                    }
                    AppAction.RESTORE_DATA -> append(restoreDataScript(app, userId).toString())
                    AppAction.DELETE_BACKUP -> {
                        append("rm -f \"\$backup_path/${app.packageName}.apk\" \"\$backup_path/${app.packageName}.tar.gz\"\n")
                    }
                    AppAction.DEX_SPEED -> append("cmd package compile -m speed $pkg\n")
                    AppAction.DEX_EVERYTHING -> append("cmd package compile -m everything $pkg\n")
                    AppAction.DEX_FORCE_SPEED -> append("cmd package compile -f -m speed $pkg\n")
                    AppAction.DEX_FORCE_EVERYTHING -> append("cmd package compile -f -m everything $pkg\n")
                }
            }
            append("sync\n")
            append("echo '[operation completed]'\n")
        }

        val result = KernelRootRuntime.executeBlocking(script)
        val details = (result.out + result.err).joinToString("\n")
        return ResultText(result.isSuccess, if (result.isSuccess) "${action.name.lowercase(Locale.ROOT)} completed" else details.ifBlank { "operation failed" }, details)
    }

    private fun restoreApkScript(app: ManagedApp): String = buildString {
        append("apk_source=${shellQuote(app.path)}\n")
        append("[ -f \"\$apk_source\" ] || apk_source=\"\$backup_path/${app.packageName}.apk\"\n")
        append("tmp_apk=\"\$userdata_path/.zkm_app_install_cache.apk\"\n")
        append("rm -f \"\$tmp_apk\"\n")
        append("cp -f \"\$apk_source\" \"\$tmp_apk\"\n")
        append("pm install -r \"\$tmp_apk\"\n")
        append("rm -f \"\$tmp_apk\"\n")
    }

    private fun restoreDataScript(app: ManagedApp, userId: Long): String = buildString {
        append("backup_file=\"\$backup_path/${app.packageName}.tar.gz\"\n")
        append("data_dir=\"\$userdata_path/${app.packageName}\"\n")
        append("if [ -f \"\$backup_file\" ] && [ -d \"\$data_dir\" ]; then\n")
        append("  am force-stop ${shellQuote(app.packageName)} 2>/dev/null || true\n")
        append("  cd \"\$data_dir\"\n")
        append("  tar -xzpf \"\$backup_file\"\n")
        append("  install_group=\$(ls -ld | awk '{print \$3}')\n")
        append("  install_own=\$(ls -ld | awk '{print \$4}')\n")
        append("  for item in *; do case \"\$item\" in lib|lib64) ;; *) chown -R \"\$install_group:\$install_own\" \"./\$item\" 2>/dev/null || true ;; esac; done\n")
        append("  sync\n")
        append("else\n")
        append("  echo '[skip data restore]'\n")
        append("fi\n")
    }

    private fun currentUserId(context: Context): Long {
        val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return 0L
        return runCatching { um.getSerialNumberForUser(Process.myUserHandle()) }.getOrDefault(0L)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private data class ResultText(val success: Boolean, val message: String, val details: String)
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel = viewModel()) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDetails by remember { mutableStateOf<ManagedApp?>(null) }
    var warningAction by remember { mutableStateOf<AppAction?>(null) }
    var showActionsSheet by remember { mutableStateOf(false) }
    val detailsDialog = rememberDialogState(initiallyVisible = false)
    val warningDialog = rememberDialogState(initiallyVisible = false)

    val localDensity = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) { viewModel.load(context) }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    val visible = remember(viewModel.query, viewModel.apps) { viewModel.filtered() }
    val selectedCount = viewModel.selectedPackages.size
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.packageName in viewModel.selectedPackages }

    // [FIX] Custom-background + glass support. Sebelumnya screen ini tidak
    // me-render wallpaper ke dalam hazeSource-nya sendiri, jadi progressive
    // blur header memburamkan area kosong (transparan), dan card tidak pernah
    // masuk mode glass. Sekarang wallpaper digambar sebagai hazeSource zIndex 0
    // dan konten sebagai zIndex 1, sama seperti screen lain.
    val prefs = remember { SettingsPreference.getInstance(context) }
    val bgType by prefs.bgType.collectAsStateWithLifecycle()
    val bgUriString by prefs.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by prefs.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by prefs.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by prefs.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by prefs.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by prefs.bgContrast.collectAsStateWithLifecycle()
    val isHazeEnabled by prefs.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by prefs.cardDarkness.collectAsStateWithLifecycle()

    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    val isDark = zkmIsDarkTheme()
    val glassCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(isDark)
        isCustomBg -> zkmCustomBackgroundCardColor(isDark, cardDarkness)
        else -> Color.Unspecified
    }
    val contentColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(isDark) else MaterialTheme.colorScheme.onSurface
    val subContentColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(isDark) else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = Color.Transparent,
        // [FIX] containerColor transparan membuat contentColor Scaffold Unspecified,
        // sehingga Text tanpa warna eksplisit (judul appbar, dll.) jatuh ke hitam
        // walau app sedang dark mode + custom background.
        contentColor = contentColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // LAYER 0: CUSTOM BACKGROUND (bagian dari hazeSource supaya blur
            // header benar-benar memburamkan wallpaper, bukan area kosong)
            Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f)) {
                if (isCustomBg && bgUriString != null) {
                    val blurModifier = when {
                        isBgBlur && blurStrength > 0f -> Modifier.blur(blurStrength.dp)
                        isBgBlur -> Modifier.blur(20.dp)
                        else -> Modifier
                    }
                    val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(bgSaturation) })
                    val uri = Uri.parse(bgUriString)
                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(blurModifier),
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter
                        )
                    }
                    if (bgContrast > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                }
            }

            // Content that scrolls behind the header
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeightDp + 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AppManagerSelectionBar(
                        selectedCount = selectedCount,
                        visibleCount = visible.size,
                        allSelected = allVisibleSelected,
                        loading = viewModel.loading || viewModel.busy,
                        onSelectAll = viewModel::toggleSelectAll,
                        onClear = viewModel::clearSelection,
                        isGlassActive = isGlassActive,
                        hazeState = hazeState,
                        glassCardColor = glassCardColor,
                        contentColor = contentColor,
                        subContentColor = subContentColor
                    )
                }

                if (viewModel.loading && visible.isEmpty()) {
                    item { AppManagerLoadingState() }
                } else if (visible.isEmpty()) {
                    item {
                        AppManagerEmptyState(
                            icon = if (viewModel.tab == AppTab.BACKUP) Icons.Rounded.FolderOpen else Icons.Rounded.Apps,
                            title = if (viewModel.query.isBlank()) stringResource(R.string.app_nothing_here) else stringResource(R.string.app_no_matching),
                            message = if (viewModel.query.isBlank()) {
                                when (viewModel.tab) {
                                    AppTab.USER -> "No user-installed apps were found."
                                    AppTab.SYSTEM -> "No system applications were found."
                                    AppTab.BACKUP -> "Backups will appear here after you create one."
                                }
                            } else {
                                "Try another name, package, or state."
                            }
                        )
                    }
                } else {
                    items(
                        items = visible,
                        key = { "${viewModel.tab.name}:${it.packageName}:${it.versionCode}" }
                    ) { app ->
                        AppManagerAppCard(
                            app = app,
                            selected = app.packageName in viewModel.selectedPackages,
                            isGlassActive = isGlassActive,
                            glassCardColor = glassCardColor,
                            hazeState = hazeState,
                            contentColor = contentColor,
                            subContentColor = subContentColor,
                            onSelect = { viewModel.toggleSelection(app.packageName) },
                            onDetails = { showDetails = app; detailsDialog.visible = true }
                        )
                    }
                }
            }

            // Header dengan Progressive Blur (theme-aware via shared helper)
            val headerHazeStyle = zkmAppBarHazeStyle(
                tintedBackground = MaterialTheme.colorScheme.surface,
                isCustomBackground = isCustomBg
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .onGloballyPositioned { coordinates -> headerHeightPx = coordinates.size.height }
                    .zkmHazeEffect(state = hazeState, style = headerHazeStyle) {
                        progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f
                        )
                    }
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(12.dp))

                AppManagerHeader(
                    appCount = visible.size,
                    selectedCount = selectedCount,
                    loading = viewModel.loading || viewModel.busy,
                    onClearSelection = viewModel::clearSelection,
                    onRefresh = { viewModel.load(context) },
                    contentColor = contentColor,
                    subContentColor = subContentColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppManagerTabs(
                    selectedTab = viewModel.tab,
                    onTabSelected = { viewModel.setTab(it, context) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.app_clear_search))
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.app_search_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.64f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.82f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Actions Bar
            AnimatedVisibility(
                visible = selectedCount > 0,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    shadowElevation = 14.dp,
                    tonalElevation = 4.dp
                ) {
                    AppManagerBottomBar(
                        selectedCount = selectedCount,
                        onManage = { showActionsSheet = true },
                        onClear = viewModel::clearSelection
                    )
                }
            }
        }
    }

    showDetails?.let { app ->
        BlurredAlertDialog(
            onDismissRequest = { detailsDialog.visible = false; showDetails = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 20.dp),
            shape = ZkmDialogDefaults.Shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dismissButton = {
                TextButton(onClick = { detailsDialog.visible = false; showDetails = null }) { Text(stringResource(R.string.common_close)) }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.launch(context, app)
                            detailsDialog.visible = false
                            showDetails = null
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_open))
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.openDetails(context, app)
                            detailsDialog.visible = false
                            showDetails = null
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_android))
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, Modifier.size(58.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppInfoGrid(
                        type = when {
                            app.updatedSystem -> "Updated system"
                            app.isSystem -> "System"
                            else -> "User"
                        },
                        enabled = if (app.suspended) "Suspended" else if (app.enabled) "Enabled" else "Frozen",
                        version = app.versionName.ifBlank { "-" },
                        sdk = "${app.minSdk} → ${app.targetSdk}"
                    )
                    AppDetailBlock("UID", app.uid.toString())
                    AppDetailBlock("Path", app.path)
                    if (app.stateTags.isNotBlank()) AppDetailBlock("State", app.stateTags)
                }
            }
        )
    }

    val actions = when (viewModel.tab) {
        AppTab.USER -> listOf(
            AppAction.STOP, AppAction.DISABLE, AppAction.ENABLE, AppAction.SUSPEND, AppAction.UNSUSPEND, AppAction.HIDE,
            AppAction.CLEAR_DATA, AppAction.BACKUP_APK, AppAction.BACKUP_FULL, AppAction.UNINSTALL,
            AppAction.DEX_SPEED, AppAction.DEX_EVERYTHING, AppAction.DEX_FORCE_SPEED, AppAction.DEX_FORCE_EVERYTHING
        )
        AppTab.SYSTEM -> listOf(
            AppAction.STOP, AppAction.DISABLE, AppAction.ENABLE, AppAction.SUSPEND, AppAction.UNSUSPEND, AppAction.HIDE,
            AppAction.CLEAR_DATA, AppAction.UNINSTALL_USER, AppAction.DELETE_SYSTEM,
            AppAction.DEX_SPEED, AppAction.DEX_EVERYTHING, AppAction.DEX_FORCE_SPEED, AppAction.DEX_FORCE_EVERYTHING
        )
        AppTab.BACKUP -> listOf(AppAction.RESTORE_APK, AppAction.RESTORE_FULL, AppAction.RESTORE_DATA, AppAction.DELETE_BACKUP)
    }

    if (showActionsSheet) {
        ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        
            onDismissRequest = { showActionsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 10.dp).size(width = 38.dp, height = 4.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                ) {}
            }
        ) {
ConfigureSheetWindowBlur()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Manage selected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$selectedCount app(s) selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(actions, key = { it.name }) { action ->
                        AppActionRow(
                            action = action,
                            onRun = {
                                val needsWarning = action in setOf(
                                    AppAction.CLEAR_DATA, AppAction.UNINSTALL, AppAction.UNINSTALL_USER, AppAction.DELETE_SYSTEM,
                                    AppAction.BACKUP_APK, AppAction.BACKUP_FULL, AppAction.RESTORE_APK, AppAction.RESTORE_FULL,
                                    AppAction.RESTORE_DATA, AppAction.DELETE_BACKUP, AppAction.DEX_FORCE_SPEED, AppAction.DEX_FORCE_EVERYTHING
                                )
                                showActionsSheet = false
                                if (needsWarning) {
                                    warningAction = action
                                    warningDialog.visible = true
                                } else {
                                    viewModel.perform(context, action)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    warningAction?.let { action ->
        BlurredAlertDialog(
            onDismissRequest = { warningDialog.visible = false; warningAction = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 20.dp),
            shape = ZkmDialogDefaults.Shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = zkmIconShape(RoundedCornerShape(14.dp)),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ZkmIconGlyph(icon = Icons.Rounded.Warning, tint = MaterialTheme.colorScheme.onErrorContainer, iconSize = 24.dp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (action == AppAction.DELETE_SYSTEM) stringResource(R.string.app_delete_system_confirm) else stringResource(action.titleRes), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    if (action == AppAction.DELETE_SYSTEM) {
                        "This removes a system APK path and can make the package unusable or affect boot."
                    } else {
                        "This operation will affect $selectedCount selected app(s). Continue?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                TextButton(onClick = { warningDialog.visible = false; warningAction = null }) { Text(stringResource(R.string.common_cancel)) }
            },
            confirmButton = {
                Button(
                    onClick = {
                        warningDialog.visible = false
                        warningAction = null
                        viewModel.perform(context, action)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (action == AppAction.DELETE_SYSTEM) stringResource(R.string.common_delete) else stringResource(R.string.common_continue)) }
            }
        )
    }
}

@Composable
private fun AppManagerHeader(
    appCount: Int,
    selectedCount: Int,
    loading: Boolean,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit,
    contentColor: Color = Color.Unspecified,
    subContentColor: Color = Color.Unspecified
) {
    val titleColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val subtitleColor = if (subContentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subContentColor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = zkmIconShape(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                ZkmIconGlyph(icon = Icons.Rounded.Apps, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 26.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_manager_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = titleColor)
            Text(
                if (selectedCount > 0) "$appCount apps · $selectedCount selected" else "$appCount apps · App controls",
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
        if (selectedCount > 0) {
            IconButton(onClick = onClearSelection) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.app_clear_selection), tint = titleColor) }
        }
        com.zuan.kernelmanager.next.feature.help.ZkmHelpButton(
            com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.AppManager.route,
            tint = titleColor
        )
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = titleColor)
        }
    }
}

@Composable
private fun AppManagerTabs(selectedTab: AppTab, onTabSelected: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.72f)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 6.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { positions ->
                if (selectedTab.ordinal < positions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedTab.ordinal]).padding(horizontal = 16.dp),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            AppTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            stringResource(tab.titleRes),
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AppManagerSelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    allSelected: Boolean,
    loading: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    isGlassActive: Boolean = false,
    hazeState: HazeState? = null,
    glassCardColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    subContentColor: Color = Color.Unspecified
) {
    val barShape = RoundedCornerShape(22.dp)
    val isDarkNow = zkmIsDarkTheme()
    val titleColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val subtitleColor = if (subContentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subContentColor
    // [FIX] Sebelumnya selalu surfaceContainerLow alpha 0.64 -> bar "Select all" tembus
    // pandang di atas wallpaper. Sekarang: glass (blur + tint solid) / scrim custom bg / solid.
    val glassOn = isGlassActive && hazeState != null
    val barTint = when {
        isGlassActive -> zkmReferenceGlassFill()
        glassCardColor != Color.Unspecified -> glassCardColor
        else -> MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.64f)
    }
    // Kaca referensi (sama dengan SetEdit): sebelumnya bar ini memakai zkmGlassCard blur 20 +
    // tint surfaceContainerHigh 50% di atas scrim -> lebih tebal/abu dari kartu lain.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (glassOn) Modifier.zkmReferenceGlass(hazeState!!, barShape) else Modifier),
        shape = barShape,
        color = barTint
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = zkmIconShape(RoundedCornerShape(13.dp)),
                color = MaterialTheme.colorScheme.primaryContainer.zkmLiteAlpha(0.82f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ZkmIconGlyph(icon = Icons.Rounded.DoneAll, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 21.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (selectedCount == 0) stringResource(R.string.app_selection) else "$selectedCount selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Text(
                    if (visibleCount == 0) "No visible apps" else "$visibleCount visible",
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor
                )
            }
            if (selectedCount > 0) {
                TextButton(onClick = onClear, enabled = !loading, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.common_clear)) }
            }
            FilledTonalButton(
                onClick = onSelectAll,
                enabled = !loading && visibleCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (allSelected) stringResource(R.string.app_clear_all) else stringResource(R.string.app_select_all))
            }
        }
    }
}

@Composable
private fun AppManagerBottomBar(selectedCount: Int, onManage: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose an operation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
        FilledTonalButton(
            onClick = onManage,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.app_manage))
        }
    }
}

@Composable
private fun AppManagerAppCard(
    app: ManagedApp,
    selected: Boolean,
    isGlassActive: Boolean = false,
    glassCardColor: Color = Color.Unspecified,
    hazeState: HazeState? = null,
    contentColor: Color = Color.Unspecified,
    subContentColor: Color = Color.Unspecified,
    onSelect: () -> Unit,
    onDetails: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    val resolvedContent = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val resolvedSubContent = if (subContentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subContentColor
    
    val baseCardColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "cardColor"
    )
    // Saat custom background: pakai glass/scrim color supaya card tidak "telanjang".
    val cardColor = if (glassCardColor != Color.Unspecified) {
        if (selected) MaterialTheme.colorScheme.primaryContainer.zkmLiteAlpha(0.75f) else glassCardColor
    } else baseCardColor
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.70f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        label = "borderColor"
    )

    val glassOn = isGlassActive && hazeState != null
    val primaryContainerTint = MaterialTheme.colorScheme.primaryContainer.zkmLiteAlpha(0.35f)
    val selectedBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val glassFill = zkmReferenceGlassFill()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Mode glass = kaca referensi SetEdit (haze 24dp/noise 0.1/bg 50% + isi 35% + border gradien).
            // Sebelumnya: haze transparan blur 20 + background scrim 30% + border outlineVariant -> beda sendiri.
            .then(if (glassOn) Modifier.zkmReferenceGlass(hazeState!!, shape, if (selected) 1.5.dp else 1.dp) else Modifier.clip(shape))
            .background(if (glassOn) glassFill else cardColor)
            .then(
                when {
                    glassOn && selected -> Modifier.background(primaryContainerTint).zkmCardBorder(1.5.dp, selectedBorder, shape)
                    glassOn -> Modifier
                    else -> Modifier.zkmCardBorder(if (selected) 1.5.dp else 1.dp, borderColor, shape)
                }
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSelectionIndicator(selected = selected)
            Spacer(Modifier.width(10.dp))
            AppIcon(app, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = resolvedContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = resolvedSubContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppMetaPill(
                        text = when {
                            app.updatedSystem -> "Updated system"
                            app.isSystem -> "System"
                            else -> "User"
                        },
                        icon = if (app.isSystem || app.updatedSystem) Icons.Rounded.Settings else Icons.Rounded.Person
                    )
                    if (app.versionName.isNotBlank()) {
                        Text(
                            "v${app.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (app.stateTags.isNotBlank()) {
                        Text(
                            app.stateTags,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.common_details), tint = resolvedSubContent)
            }
        }
    }
}

@Composable
private fun AppIcon(app: ManagedApp, modifier: Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.zkmLiteAlpha(0.55f)
    ) {
        if (app.icon != null) {
            AsyncImage(
                model = app.icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun AppSelectionIndicator(selected: Boolean) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun AppMetaPill(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.zkmLiteAlpha(0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AppManagerLoadingState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.60f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.app_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppManagerEmptyState(icon: ImageVector, title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.62f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = zkmIconShape(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer.zkmLiteAlpha(0.82f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ZkmIconGlyph(icon = icon, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 28.dp)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AppInfoGrid(type: String, enabled: String, version: String, sdk: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppInfoPill("Type", type, Modifier.weight(1f))
        AppInfoPill("State", enabled, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppInfoPill("Version", version, Modifier.weight(1f))
        AppInfoPill("SDK", sdk, Modifier.weight(1f))
    }
}

@Composable
private fun AppInfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.70f)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppActionRow(action: AppAction, onRun: () -> Unit) {
    val destructive = action in setOf(AppAction.DELETE_SYSTEM, AppAction.DELETE_BACKUP, AppAction.UNINSTALL, AppAction.CLEAR_DATA)
    val icon = when (action) {
        AppAction.DELETE_SYSTEM, AppAction.DELETE_BACKUP, AppAction.UNINSTALL, AppAction.UNINSTALL_USER, AppAction.CLEAR_DATA -> Icons.Rounded.Delete
        AppAction.BACKUP_APK, AppAction.BACKUP_FULL -> Icons.Rounded.Backup
        AppAction.RESTORE_APK, AppAction.RESTORE_FULL, AppAction.RESTORE_DATA -> Icons.Rounded.Restore
        AppAction.STOP -> Icons.Rounded.StopCircle
        AppAction.DISABLE -> Icons.Rounded.Block
        AppAction.ENABLE -> Icons.Rounded.CheckCircle
        AppAction.SUSPEND, AppAction.UNSUSPEND -> Icons.Rounded.PauseCircle
        AppAction.HIDE -> Icons.Rounded.VisibilityOff
        AppAction.DEX_SPEED, AppAction.DEX_EVERYTHING, AppAction.DEX_FORCE_SPEED, AppAction.DEX_FORCE_EVERYTHING -> Icons.Rounded.Build
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.zkmLiteAlpha(0.50f) else MaterialTheme.colorScheme.surfaceContainerLow.zkmLiteAlpha(0.72f)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = zkmIconShape(RoundedCornerShape(12.dp)),
                color = if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ZkmIconGlyph(icon = icon, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, iconSize = 19.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(action.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when (action) {
                        AppAction.BACKUP_FULL, AppAction.RESTORE_FULL, AppAction.RESTORE_DATA -> "Uses the master backup format"
                        AppAction.DEX_FORCE_SPEED, AppAction.DEX_FORCE_EVERYTHING -> "Force compilation"
                        AppAction.DELETE_SYSTEM -> "Removes the system APK path"
                        else -> "Run on selected packages"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(onClick = onRun, shape = RoundedCornerShape(13.dp)) { Text(stringResource(R.string.common_run)) }
        }
    }
}

@Composable
private fun AppDetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
