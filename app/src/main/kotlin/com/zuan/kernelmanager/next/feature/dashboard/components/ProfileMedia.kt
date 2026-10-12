/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.R
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

/** Jenis media foto profil. */
enum class ProfileMediaType { IMAGE, ANIMATED, VIDEO }

/**
 * [aspect]  = lebar/tinggi asli media.
 * [scale]   = zoom (>= 1) di atas posisi "cover".
 * [offsetX]/[offsetY] = geser, dalam pecahan dari ukuran viewport (kotak avatar).
 */
data class ProfileMedia(
    val uri: String,
    val type: ProfileMediaType,
    val aspect: Float = 1f,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    /** Batas geser supaya area kosong tidak pernah terlihat di dalam viewport. */
    fun maxOffsetX(scale: Float = this.scale): Float = max(0f, (max(aspect, 1f) * scale - 1f) / 2f)
    fun maxOffsetY(scale: Float = this.scale): Float = max(0f, (max(1f / aspect, 1f) * scale - 1f) / 2f)

    fun withCrop(scale: Float, ox: Float, oy: Float): ProfileMedia {
        val sc = scale.coerceIn(1f, 5f)
        return copy(
            scale = sc,
            offsetX = ox.coerceIn(-maxOffsetX(sc), maxOffsetX(sc)),
            offsetY = oy.coerceIn(-maxOffsetY(sc), maxOffsetY(sc))
        )
    }
}

sealed interface ProfileImportResult {
    /** Foto statis biasa -> lanjut ke crop OVAL seperti sebelumnya. */
    data class NeedsCrop(val source: Uri) : ProfileImportResult
    /** GIF / WebP animasi / video / Live (Motion) Photo -> sudah disalin & siap dipakai. */
    data class Ready(val media: ProfileMedia) : ProfileImportResult
    data object TooLarge : ProfileImportResult
    data object Failed : ProfileImportResult
}

/**
 * Penyimpanan + import media foto profil.
 * File disalin ke filesDir/profile_avatar supaya tidak hilang
 * (URI dari picker / cache cropper bisa kedaluwarsa).
 */
object ProfileMediaStore {
    private const val PREFS = "user_profile_prefs"
    private const val KEY_URI = "profile_image_uri"      // key lama, tetap dipakai (kompatibel)
    private const val KEY_TYPE = "profile_media_type"
    private const val KEY_ASPECT = "profile_media_aspect"
    private const val KEY_SCALE = "profile_media_scale"
    private const val KEY_OX = "profile_media_ox"
    private const val KEY_OY = "profile_media_oy"
    private const val DIR = "profile_avatar"
    private const val MAX_BYTES = 50L * 1024 * 1024
    private const val HEAD_BYTES = 256 * 1024

    fun load(context: Context): ProfileMedia? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = p.getString(KEY_URI, null) ?: return null
        val type = runCatching {
            ProfileMediaType.valueOf(p.getString(KEY_TYPE, ProfileMediaType.IMAGE.name)!!)
        }.getOrDefault(ProfileMediaType.IMAGE)
        var aspect = p.getFloat(KEY_ASPECT, -1f)
        if (aspect <= 0f) {
            // Data dari versi lama (belum ada aspect) -> hitung sekali lalu simpan
            aspect = if (type == ProfileMediaType.IMAGE) 1f else probeAspect(context, uri.toUri(), type)
            p.edit().putFloat(KEY_ASPECT, aspect).apply()
        }
        return ProfileMedia(uri, type, aspect).withCrop(
            p.getFloat(KEY_SCALE, 1f), p.getFloat(KEY_OX, 0f), p.getFloat(KEY_OY, 0f)
        )
    }

    /** Batalkan media yang baru dipilih tapi tidak jadi dipakai. */
    fun discard(context: Context, pending: ProfileMedia, current: ProfileMedia?) {
        runCatching {
            val path = pending.uri.toUri().path ?: return
            if (path != current?.uri?.toUri()?.path) File(path).delete()
        }
    }

    fun save(context: Context, media: ProfileMedia) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URI, media.uri)
            .putString(KEY_TYPE, media.type.name)
            .putFloat(KEY_ASPECT, media.aspect)
            .putFloat(KEY_SCALE, media.scale)
            .putFloat(KEY_OX, media.offsetX)
            .putFloat(KEY_OY, media.offsetY)
            .apply()
        // Hapus file avatar lama yang sudah tidak dipakai
        runCatching {
            val keep = media.uri.toUri().path
            File(context.filesDir, DIR).listFiles()?.forEach { if (it.absolutePath != keep) it.delete() }
        }
    }

    /** Hasil crop (PNG) disalin ke penyimpanan permanen. */
    suspend fun adoptCropped(context: Context, cropped: Uri): ProfileMedia? = withContext(Dispatchers.IO) {
        runCatching {
            val out = newFile(context, "png")
            context.contentResolver.openInputStream(cropped)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
            ProfileMedia(Uri.fromFile(out).toString(), ProfileMediaType.IMAGE)
        }.getOrNull()
    }

    suspend fun import(context: Context, uri: Uri): ProfileImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.lowercase().orEmpty()

            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_BYTES) return@runCatching ProfileImportResult.TooLarge

            when {
                mime.startsWith("video/") -> {
                    val ext = if (mime.contains("webm")) "webm" else "mp4"
                    ProfileImportResult.Ready(copy(context, uri, ext, ProfileMediaType.VIDEO))
                }
                mime == "image/gif" ->
                    ProfileImportResult.Ready(copy(context, uri, "gif", ProfileMediaType.ANIMATED))
                mime == "image/webp" ->
                    if (isAnimatedWebp(context, uri))
                        ProfileImportResult.Ready(copy(context, uri, "webp", ProfileMediaType.ANIMATED))
                    else ProfileImportResult.NeedsCrop(uri)
                mime == "image/jpeg" || mime == "image/heic" || mime == "image/heif" ->
                    extractMotionPhoto(context, uri)?.let { ProfileImportResult.Ready(it) }
                        ?: ProfileImportResult.NeedsCrop(uri)
                else -> ProfileImportResult.NeedsCrop(uri)
            }
        }.getOrElse { ProfileImportResult.Failed }
    }

    // ---------------------------------------------------------------- helpers

    private fun newFile(context: Context, ext: String): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        // nama unik -> cache Coil tidak menampilkan gambar lama
        return File(dir, "avatar_${System.currentTimeMillis()}.$ext")
    }

    private fun copy(context: Context, uri: Uri, ext: String, type: ProfileMediaType): ProfileMedia {
        val out = newFile(context, ext)
        context.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
        val fileUri = Uri.fromFile(out)
        return ProfileMedia(fileUri.toString(), type, probeAspect(context, fileUri, type))
    }

    private fun probeAspect(context: Context, uri: Uri, type: ProfileMediaType): Float = runCatching {
        if (type == ProfileMediaType.VIDEO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toFloat()
                var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toFloat()
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                w / h
            } finally {
                r.release()
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
            o.outWidth.toFloat() / o.outHeight.toFloat()
        }
    }.getOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f

    private fun readHead(context: Context, uri: Uri, n: Int): ByteArray {
        context.contentResolver.openInputStream(uri)!!.use { s ->
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = s.read(buf, read, n - read)
                if (r <= 0) break
                read += r
            }
            return buf.copyOf(read)
        }
    }

    /** WebP animasi: RIFF....WEBPVP8X, flag animasi = bit 0x02 di byte ke-20. */
    private fun isAnimatedWebp(context: Context, uri: Uri): Boolean {
        val h = readHead(context, uri, 32)
        if (h.size < 21) return false
        val riff = String(h, 0, 4, Charsets.ISO_8859_1)
        val webp = String(h, 8, 4, Charsets.ISO_8859_1)
        val vp8x = String(h, 12, 4, Charsets.ISO_8859_1)
        return riff == "RIFF" && webp == "WEBP" && vp8x == "VP8X" && (h[20].toInt() and 0x02) != 0
    }

    /**
     * Live Photo di Android = Motion Photo: JPEG/HEIC + video MP4 di akhir file.
     * (Google Pixel, Samsung, Xiaomi, dll.) Video diekstrak lalu dipakai sebagai avatar.
     */
    private fun extractMotionPhoto(context: Context, uri: Uri): ProfileMedia? {
        val head = readHead(context, uri, HEAD_BYTES)
        val xmp = String(head, Charsets.ISO_8859_1)
        val isMotion = xmp.contains("MotionPhoto") || xmp.contains("MicroVideo") || xmp.contains("GCamera:Micro")
        if (!isMotion) return null

        val all = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val total = all.size

        var fromEnd = Regex("""MicroVideoOffset\s*=\s*"(\d+)"""").find(xmp)?.groupValues?.get(1)?.toLongOrNull()
        if (fromEnd == null) {
            val tag = Regex("""<Container:Item[^>]*Item:Mime\s*=\s*"video/mp4"[^>]*>""").find(xmp)?.value
            fromEnd = tag?.let { Regex("""Item:Length\s*=\s*"(\d+)"""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
        }

        var start = if (fromEnd != null && fromEnd in 1 until total) (total - fromEnd).toInt() else -1

        // Fallback: cari signature "ftyp" (header MP4) di luar header gambar
        if (start < 0 || !hasFtyp(all, start)) {
            start = -1
            var i = 64
            while (i < total - 8) {
                if (all[i] == 'f'.code.toByte() && all[i + 1] == 't'.code.toByte() &&
                    all[i + 2] == 'y'.code.toByte() && all[i + 3] == 'p'.code.toByte()
                ) { start = i - 4; break }
                i++
            }
        }
        if (start <= 0 || total - start < 1024) return null

        val out = newFile(context, "mp4")
        out.outputStream().use { it.write(all, start, total - start) }
        val fileUri = Uri.fromFile(out)
        return ProfileMedia(fileUri.toString(), ProfileMediaType.VIDEO, probeAspect(context, fileUri, ProfileMediaType.VIDEO))
    }

    private fun hasFtyp(b: ByteArray, start: Int): Boolean =
        start >= 0 && start + 8 <= b.size &&
            b[start + 4] == 'f'.code.toByte() && b[start + 5] == 't'.code.toByte() &&
            b[start + 6] == 'y'.code.toByte() && b[start + 7] == 'p'.code.toByte()
}

/**
 * Avatar profil: mendukung foto, GIF/WebP animasi, video, dan Live (Motion) Photo,
 * lengkap dengan posisi crop (zoom + geser) yang dipilih user.
 * Video diputar loop, tanpa suara, tidak mengambil audio focus.
 */
@Composable
fun ProfileAvatar(
    media: ProfileMedia,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    // false = video ditampilkan sebagai gambar diam (frame pertama), hemat ExoPlayer
    // untuk preview yang banyak (mis. grid Avatar Decoration).
    liveVideo: Boolean = true
) {
    CropFrame(media, media.scale, media.offsetX, media.offsetY, modifier) {
        ProfileMediaContent(media, contentDescription, liveVideo)
    }
}

/** Viewport persegi; media di-"cover" lalu di-zoom/geser sesuai crop. */
@Composable
private fun CropFrame(
    media: ProfileMedia,
    scale: Float,
    ox: Float,
    oy: Float,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val s = min(constraints.maxWidth, constraints.maxHeight)
        val a = media.aspect.coerceIn(0.05f, 20f)
        val w = if (a >= 1f) (s * a).roundToInt() else s
        val h = if (a >= 1f) s else (s / a).roundToInt()
        // Konten bisa kosong sesaat (mis. frame video belum ter-decode) -> jangan crash
        val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(w, h))
            ?: return@Layout layout(s, s) {}
        layout(s, s) {
            placeable.placeRelativeWithLayer((s - w) / 2, (s - h) / 2) {
                scaleX = scale
                scaleY = scale
                translationX = ox * s
                translationY = oy * s
            }
        }
    }
}

@Composable
private fun ProfileMediaContent(media: ProfileMedia, contentDescription: String?, liveVideo: Boolean = true) {
    when (media.type) {
        ProfileMediaType.IMAGE -> AsyncImage(
            model = media.uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        ProfileMediaType.ANIMATED -> {
            val context = LocalContext.current
            val loader = remember(context) {
                ImageLoader.Builder(context)
                    .components { add(AnimatedImageDecoder.Factory()) } // minSdk 29 -> aman
                    .build()
            }
            AsyncImage(
                model = media.uri,
                imageLoader = loader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        ProfileMediaType.VIDEO ->
            if (liveVideo) ProfileVideoView(media.uri.toUri())
            else VideoStillFrame(media.uri.toUri(), contentDescription)
    }
}

/** Cache kecil frame pertama video (sudah diperkecil) supaya grid preview tidak decode berulang. */
private val videoStillCache = LruCache<String, Bitmap>(6)

/** Frame pertama video sebagai gambar diam (di-decode sekali di IO, lalu di-cache). */
@Composable
private fun VideoStillFrame(uri: Uri, contentDescription: String?) {
    val context = LocalContext.current
    val key = uri.toString()
    val frame by produceState<Bitmap?>(initialValue = videoStillCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(context, uri)
                        val raw = r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: return@runCatching null
                        val k = 360f / max(raw.width, raw.height).toFloat()
                        val out = if (k < 1f) {
                            Bitmap.createScaledBitmap(
                                raw,
                                max(1, (raw.width * k).roundToInt()),
                                max(1, (raw.height * k).roundToInt()),
                                true
                            )
                        } else raw
                        if (out !== raw) raw.recycle()
                        out
                    } finally {
                        r.release()
                    }
                }.getOrNull()?.also { videoStillCache.put(key, it) }
            }
        }
    }
    // Selalu emit satu node (Box) supaya CropFrame tidak pernah punya anak kosong
    Box(modifier = Modifier.fillMaxSize()) {
        frame?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ProfileVideoView(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            playWhenReady = true
        }
    }

    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    // Pause saat app di background, lanjut saat kembali
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player.setVideoTextureView(this)
            }
        }
    )
}

/**
 * Editor crop untuk video / GIF / Live Photo: cubit untuk zoom, seret untuk geser.
 */
@Composable
fun ProfileCropDialog(
    media: ProfileMedia,
    onCancel: () -> Unit,
    onConfirm: (ProfileMedia) -> Unit
) {
    var scale by remember { mutableFloatStateOf(media.scale) }
    var ox by remember { mutableFloatStateOf(media.offsetX) }
    var oy by remember { mutableFloatStateOf(media.offsetY) }

    fun updateCrop(newScale: Float, newOx: Float, newOy: Float) {
        val c = media.withCrop(newScale, newOx, newOy)
        scale = c.scale; ox = c.offsetX; oy = c.offsetY
    }

    com.zuan.kernelmanager.next.ui.adaptive.ZkmWindowDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.profile_crop_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Box280 {
                    CropFrame(media, scale, ox, oy, Modifier.fillMaxSize()) {
                        ProfileMediaContent(media, null)
                    }
                    // Scrim di luar lingkaran + penangkap gesture
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .pointerInput(media) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val sz = size.width.toFloat()
                                    updateCrop(scale * zoom, ox + pan.x / sz, oy + pan.y / sz)
                                }
                            }
                    ) {
                        drawRect(Color.Black.copy(alpha = 0.6f))
                        val r = size.minDimension / 2f
                        drawCircle(Color.Black, radius = r, center = Offset(size.width / 2f, size.height / 2f), blendMode = BlendMode.Clear)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(width = 2.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(12.dp))
                ZkmSlider(
                    value = scale,
                    onValueChange = { updateCrop(it, ox, oy) },
                    valueRange = 1f..5f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.profile_crop_cancel)) }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Button(onClick = { onConfirm(media.withCrop(scale, ox, oy)) }) {
                        Text(stringResource(R.string.profile_crop_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun Box280(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    // Area crop mengecil bila jendela pendek (landscape / Rotation 16:9 / PC mode) supaya
    // slider dan tombol Simpan tetap terlihat.
    val side = (com.zuan.kernelmanager.next.ui.adaptive.zkmDialogMaxHeight() - 210.dp)
        .coerceIn(120.dp, 280.dp)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(side),
        content = content
    )
}
