package com.zuan.kernelmanager.next.core.util

import android.content.Context
import android.util.Xml
import java.io.InputStream
import java.io.OutputStream
import org.xmlpull.v1.XmlPullParser

/** Full application SharedPreferences backup/restore in a portable XML format. */
object SettingsXmlBackup {
    private const val ROOT = "zkm-backup"
    private const val VERSION = "1"
    private const val PREFS_TAG = "preferences"
    private const val ENTRY_TAG = "entry"
    private const val VALUE_TAG = "value"
    private const val SET_TAG = "set"
    private const val TYPE_ATTR = "type"
    private const val NAME_ATTR = "name"
    private const val KEY_ATTR = "key"
    private const val TYPE_STRING = "string"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING_SET = "string-set"
    private const val LANGUAGE_KEY = "app_language_code"

    fun export(context: Context, output: OutputStream) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, ROOT)
        serializer.attribute(null, "version", VERSION)
        serializer.attribute(null, "package", context.packageName)

        val sharedPrefsDir = context.applicationInfo.dataDir + "/shared_prefs"
        val dir = java.io.File(sharedPrefsDir)
        val files = dir.listFiles { file -> file.isFile && file.extension == "xml" }
            ?.sortedBy { it.name }
            ?: emptyList()

        // Reading the files through SharedPreferences preserves the actual Android value types.
        for (file in files) {
            val prefName = file.nameWithoutExtension
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val values = prefs.all
            if (values.isEmpty()) continue

            serializer.startTag(null, PREFS_TAG)
            serializer.attribute(null, NAME_ATTR, prefName)
            for ((key, value) in values.entries.sortedBy { it.key }) {
                // Language selection was intentionally removed; do not carry the legacy key.
                if (key == LANGUAGE_KEY) continue
                writeEntry(serializer, key, value)
            }
            serializer.endTag(null, PREFS_TAG)
        }

        serializer.endTag(null, ROOT)
        serializer.endDocument()
        output.flush()
    }

    private fun writeEntry(serializer: org.xmlpull.v1.XmlSerializer, key: String, value: Any?) {
        if (value == null) return
        serializer.startTag(null, ENTRY_TAG)
        serializer.attribute(null, KEY_ATTR, key)
        when (value) {
            is String -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_STRING)
                serializer.text(value)
            }
            is Boolean -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_BOOLEAN)
                serializer.text(value.toString())
            }
            is Int -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_INT)
                serializer.text(value.toString())
            }
            is Long -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_LONG)
                serializer.text(value.toString())
            }
            is Float -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_FLOAT)
                serializer.text(value.toString())
            }
            is Set<*> -> {
                serializer.attribute(null, TYPE_ATTR, TYPE_STRING_SET)
                serializer.startTag(null, SET_TAG)
                value.filterIsInstance<String>().sorted().forEach { item ->
                    serializer.startTag(null, VALUE_TAG)
                    serializer.text(item)
                    serializer.endTag(null, VALUE_TAG)
                }
                serializer.endTag(null, SET_TAG)
            }
            else -> {
                serializer.endTag(null, ENTRY_TAG)
                return
            }
        }
        serializer.endTag(null, ENTRY_TAG)
    }

    fun import(context: Context, input: InputStream): Int {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val backup = linkedMapOf<String, MutableMap<String, Any?>>()
        var rootSeen = false
        var currentPrefs: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    ROOT -> {
                        if (rootSeen) throw IllegalArgumentException("Invalid XML: duplicate root")
                        rootSeen = true
                        val version = parser.getAttributeValue(null, "version")
                        if (version != VERSION) throw IllegalArgumentException("Unsupported backup version: $version")
                    }
                    PREFS_TAG -> {
                        val prefsName = parser.getAttributeValue(null, NAME_ATTR)
                            ?: throw IllegalArgumentException("Invalid preferences name")
                        if (prefsName.isBlank()) throw IllegalArgumentException("Invalid preferences name")
                        currentPrefs = prefsName
                        backup.getOrPut(prefsName) { linkedMapOf() }
                    }
                    ENTRY_TAG -> {
                        val pref = currentPrefs ?: throw IllegalArgumentException("Entry outside preferences")
                        val key = parser.getAttributeValue(null, KEY_ATTR)
                            ?: throw IllegalArgumentException("Missing key")
                        val type = parser.getAttributeValue(null, TYPE_ATTR)
                            ?: throw IllegalArgumentException("Missing type")
                        if (key.isBlank()) throw IllegalArgumentException("Invalid key")
                        if (key == LANGUAGE_KEY) {
                            skipElement(parser)
                        } else {
                            val value = readEntry(parser, type)
                            backup.getValue(pref)[key] = value
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == PREFS_TAG) {
                currentPrefs = null
            }
            event = parser.next()
        }

        if (!rootSeen) throw IllegalArgumentException("Not a ZKM XML backup")

        // Exact restore: clear every current preference file, then restore what the backup contains.
        val currentDir = java.io.File(context.applicationInfo.dataDir + "/shared_prefs")
        currentDir.listFiles { file -> file.isFile && file.extension == "xml" }
            ?.forEach { file ->
                context.getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }

        var restored = 0
        backup.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                restored++
            }
            editor.commit()
        }
        return restored
    }

    private fun readEntry(parser: XmlPullParser, type: String): Any {
        var text = ""
        val setValues = linkedSetOf<String>()
        var event = parser.next()
        while (true) {
            when (event) {
                XmlPullParser.TEXT -> text += parser.text ?: ""
                XmlPullParser.START_TAG -> {
                    if (type != TYPE_STRING_SET) throw IllegalArgumentException("Invalid XML entry")
                    if (parser.name == VALUE_TAG) setValues.add(readTextElement(parser))
                    else if (parser.name != SET_TAG) throw IllegalArgumentException("Invalid XML set")
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == ENTRY_TAG) break
                }
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("Unexpected end of XML")
            }
            event = parser.next()
        }
        return when (type) {
            TYPE_STRING -> text
            TYPE_BOOLEAN -> text.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid boolean")
            TYPE_INT -> text.toIntOrNull() ?: throw IllegalArgumentException("Invalid int")
            TYPE_LONG -> text.toLongOrNull() ?: throw IllegalArgumentException("Invalid long")
            TYPE_FLOAT -> text.toFloatOrNull() ?: throw IllegalArgumentException("Invalid float")
            TYPE_STRING_SET -> setValues
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }

    private fun readTextElement(parser: XmlPullParser): String {
        val name = parser.name
        var text = ""
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> text += parser.text ?: ""
                XmlPullParser.START_TAG -> throw IllegalArgumentException("Nested XML is not allowed in $name")
                XmlPullParser.END_TAG -> if (parser.name == name) return text
            }
            event = parser.next()
        }
        throw IllegalArgumentException("Unexpected end of XML")
    }

    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("Unexpected end of XML")
            }
        }
    }

}
