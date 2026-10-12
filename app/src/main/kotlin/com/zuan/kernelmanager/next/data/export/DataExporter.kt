package com.zuan.kernelmanager.next.data.export

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.data.database.FpsDataPoint
import com.zuan.kernelmanager.next.data.database.FpsSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataExporter {
    fun exportSessionToCsv(context: Context, session: FpsSession, points: List<FpsDataPoint>) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloadsDir, "ZKM_Reports").apply { if (!exists()) mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "FPS_${session.packageName.replace('.', '_')}_$stamp.csv")
            val csv = StringBuilder()
            csv.appendLine("=== ZKM FPS REPORT ===")
            csv.appendLine("App,${session.packageName}")
            csv.appendLine("Start,${SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date(session.startTime))}")
            csv.appendLine("Duration,${session.durationSeconds} sec")
            csv.appendLine("Sample interval,${session.sampleIntervalMs} ms")
            csv.appendLine("Samples,${session.sampleCount}")
            csv.appendLine("Valid FPS samples,${session.validFpsSamples}")
            csv.appendLine("Average FPS,${session.avgFps}")
            csv.appendLine("1% Low,${session.onePercentLow}")
            csv.appendLine("0.1% Low,${session.zeroOnePercentLow}")
            csv.appendLine("P95 Frame Time,${session.frameTimeP95Ms} ms")
            csv.appendLine("Jank,${session.jankPercent}%")
            csv.appendLine("Renderer,${session.renderer}")
            csv.appendLine("FPS Source,${session.fpsSource}")
            csv.appendLine()
            csv.appendLine("Time (s),FPS,Frame Time (ms),CPU Load (%),CPU Clock (MHz),GPU Load (%),GPU Clock (MHz),SoC Temp (C),Power (W),RAM Used (MB),RAM Total (MB),RAM (%),Battery (%),Voltage (mV),Current (mA),Refresh (Hz),Jank,Renderer,Foreground Package")
            points.forEach { p ->
                val time = String.format(Locale.US, "%.3f", p.timestamp / 1000f)
                val row = listOf(time, p.fps, p.frameTimeMs, p.cpuLoad, p.cpuFreqMHz, p.gpuLoad, p.gpuFreqMHz, p.temp, p.watt, p.ramUsageMb, p.ramTotalMb, p.ramPercent, p.batteryLevel, p.batteryVoltageMv, p.batteryCurrentMa, p.refreshRateHz, p.jank, p.renderer, p.foregroundPackage)
                    .joinToString(",") { it.toString().replace(",", ";") }
                csv.appendLine(row)
            }
            file.writeText(csv.toString())
            Toast.makeText(context, context.getString(R.string.lp_toast_saved_report, file.name), Toast.LENGTH_LONG).show()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ZKM FPS Report: ${session.packageName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Full FPS Report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.lp_toast_export_failed, e.message ?: "Unknown error"), Toast.LENGTH_SHORT).show()
        }
    }
}
