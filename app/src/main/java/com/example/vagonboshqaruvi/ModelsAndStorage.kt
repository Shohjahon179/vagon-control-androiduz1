package com.example.vagonboshqaruvi

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class User(
    val id: String = "",
    val phoneNumber: String = "",
    val username: String = "",
    val password: String = "",
    val role: String = "Tanlanmagan"
)

data class WagonRecord(
    val id: String = "",
    val trackNumber: String = "1-yo'l",
    val wagonNumber: String = "",
    val arrivalDate: String = "",
    val arrivalTime: String = "",
    val trainInNumber: String = "",
    val givenToUnloadDate: String = "",
    val givenToUnloadTime: String = "",
    val takenFromUnloadDate: String = "",
    val takenFromUnloadTime: String = "",
    val departureDate: String = "",
    val departureTime: String = "",
    val trainOutNumber: String = "",
    val durationUntilGiven: String = "",
    val durationAfterTaken: String = "",
    val totalDuration: String = "",
    val createdBy: String = ""
)

data class ChatMessage(
    val id: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val text: String = "",
    val time: String = "",
    val isEdited: Boolean = false,
    val fileName: String? = null,
    val localFilePath: String? = null
)

object TimeCalculator {
    private val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun calculateDifference(date1: String, time1: String, date2: String, time2: String): String {
        if (date1.isBlank() || time1.isBlank() || date2.isBlank() || time2.isBlank()) {
            return "Kiritilmagan"
        }
        return try {
            val d1 = format.parse("${date1.trim()} ${time1.trim()}") ?: return "Xato sana/vaqt"
            val d2 = format.parse("${date2.trim()} ${time2.trim()}") ?: return "Xato sana/vaqt"
            val diffMillis = d2.time - d1.time
            if (diffMillis < 0) return "Vaqt xato (manfiy)"
            val totalMinutes = diffMillis / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            "$hours soat $minutes daq"
        } catch (e: Exception) {
            "Format: kk.oo.yyyy va ss:dd"
        }
    }

    fun splitWagonsAndCreateRecords(rawWagons: String, baseRecord: WagonRecord): List<WagonRecord> {
        val wagonList = rawWagons.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val untilGiven = calculateDifference(
            baseRecord.arrivalDate, baseRecord.arrivalTime,
            baseRecord.givenToUnloadDate, baseRecord.givenToUnloadTime
        )
        val afterTaken = calculateDifference(
            baseRecord.takenFromUnloadDate, baseRecord.takenFromUnloadTime,
            baseRecord.departureDate, baseRecord.departureTime
        )
        val total = calculateDifference(
            baseRecord.arrivalDate, baseRecord.arrivalTime,
            baseRecord.departureDate, baseRecord.departureTime
        )

        return wagonList.mapIndexed { index, wagonNum ->
            baseRecord.copy(
                id = "${System.currentTimeMillis()}_$index",
                trackNumber = "${index + 1}-yo'l",
                wagonNumber = wagonNum,
                durationUntilGiven = untilGiven,
                durationAfterTaken = afterTaken,
                totalDuration = total
            )
        }
    }
}

object ExcelExporter {
    private fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"")
        return "\"$clean\""
    }

    fun exportAndShareCsv(context: Context, records: List<WagonRecord>): String {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(dir, "Vagonlar_Jadvali_$stamp.csv")

        val headers = listOf(
            "№",
            "Yo'l",
            "Vagon №",
            "Kirgan sana",
            "Kirgan vaqt",
            "Kirish poyezd",
            "Tushirishga berilgan sana",
            "Berilgan vaqt",
            "Keyingi yo'l (Kirish -> Tushirish)",
            "Yo'ldan olingan sana",
            "Olingan vaqt",
            "Jo'nagan sana",
            "Jo'nash vaqti",
            "Pastki qator (Olingan -> Jo'nash)",
            "Jo'nash poyezd",
            "Jami vaqt",
            "Kiritgan xodim"
        )

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(headers.joinToString(";") { csvEscape(it) }).append("\n")

        records.forEachIndexed { index, r ->
            val row = listOf(
                "${index + 1}",
                r.trackNumber,
                r.wagonNumber,
                r.arrivalDate,
                r.arrivalTime,
                r.trainInNumber,
                r.givenToUnloadDate,
                r.givenToUnloadTime,
                r.durationUntilGiven,
                r.takenFromUnloadDate,
                r.takenFromUnloadTime,
                r.departureDate,
                r.departureTime,
                r.durationAfterTaken,
                r.trainOutNumber,
                r.totalDuration,
                r.createdBy
            )
            sb.append(row.joinToString(";") { csvEscape(it) }).append("\n")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vagonlar harakati Excel jadvali")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Excel jadvalni yuklash / yuborish"))
        return file.name
    }
}

class LocalStorage(context: Context) {
    private val prefs = context.getSharedPreferences("vagon_boshqaruvi_v8_db", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val DEFAULT_FIREBASE_URL = "https://cart-glory-5c8f2-default-rtdb.firebaseio.com"
    }

    fun getFirebaseUrl(): String {
        return prefs.getString("firebase_db_url", DEFAULT_FIREBASE_URL) ?: DEFAULT_FIREBASE_URL
    }

    fun saveFirebaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        if (cleaned.isNotEmpty()) {
            prefs.edit().putString("firebase_db_url", cleaned).apply()
        }
    }

    fun saveThemeMode(isNight: Boolean) {
        prefs.edit().putBoolean("is_night_glass", isNight).apply()
    }

    fun loadThemeMode(): Boolean {
        return prefs.getBoolean("is_night_glass", true)
    }

    fun saveUsers(list: List<User>) {
        prefs.edit().putString("users", gson.toJson(list)).apply()
    }

    fun loadUsers(): MutableList<User> {
        val json = prefs.getString("users", null) ?: return mutableListOf(
            User("1", "+998901234567", "xodim", "1234", "Xodim"),
            User("2", "+998907654321", "rahbar", "1234", "Rahbar")
        )
        val type = object : TypeToken<MutableList<User>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveRecords(list: List<WagonRecord>) {
        prefs.edit().putString("records", gson.toJson(list)).apply()
    }

    fun loadRecords(): MutableList<WagonRecord> {
        val json = prefs.getString("records", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<WagonRecord>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveMessages(list: List<ChatMessage>) {
        prefs.edit().putString("messages", gson.toJson(list)).apply()
    }

    fun loadMessages(): MutableList<ChatMessage> {
        val json = prefs.getString("messages", null) ?: return mutableListOf(
            ChatMessage("1", "Rahbar", "Rahbar", "Assalomu alaykum! Vagonlar harakatini nazoratga olamiz.", "09:00")
        )
        val type = object : TypeToken<MutableList<ChatMessage>>() {}.type
        return gson.fromJson(json, type)
    }
}

class CloudSyncManager(private val storage: LocalStorage) {
    private val gson = Gson()

    private fun baseUrl(): String = storage.getFirebaseUrl().trimEnd('/')

    private suspend fun httpGet(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl()}/$path.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun httpPut(path: String, jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl()}/$path.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 6000
                readTimeout = 6000
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun httpDelete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl()}/$path.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pushUser(user: User): Boolean {
        val safeKey = user.id.ifBlank { user.username }
        return httpPut("vagon_app/users/$safeKey", gson.toJson(user))
    }

    suspend fun fetchUsers(): List<User>? {
        val raw = httpGet("vagon_app/users") ?: return null
        if (raw == "null" || raw.isBlank()) return emptyList()
        return try {
            val mapType = object : TypeToken<Map<String, User>>() {}.type
            val map: Map<String, User> = gson.fromJson(raw, mapType)
            map.values.toList()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pushRecord(record: WagonRecord): Boolean {
        val safeKey = record.id.ifBlank { System.currentTimeMillis().toString() }
        return httpPut("vagon_app/records/$safeKey", gson.toJson(record))
    }

    suspend fun deleteRecord(recordId: String): Boolean {
        return httpDelete("vagon_app/records/$recordId")
    }

    suspend fun fetchRecords(): List<WagonRecord>? {
        val raw = httpGet("vagon_app/records") ?: return null
        if (raw == "null" || raw.isBlank()) return emptyList()
        return try {
            val mapType = object : TypeToken<Map<String, WagonRecord>>() {}.type
            val map: Map<String, WagonRecord> = gson.fromJson(raw, mapType)
            map.values.sortedBy { it.id }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pushMessage(msg: ChatMessage): Boolean {
        val safeKey = msg.id.ifBlank { System.currentTimeMillis().toString() }
        return httpPut("vagon_app/messages/$safeKey", gson.toJson(msg))
    }

    suspend fun deleteMessage(msgId: String): Boolean {
        return httpDelete("vagon_app/messages/$msgId")
    }

    suspend fun fetchMessages(): List<ChatMessage>? {
        val raw = httpGet("vagon_app/messages") ?: return null
        if (raw == "null" || raw.isBlank()) return emptyList()
        return try {
            val mapType = object : TypeToken<Map<String, ChatMessage>>() {}.type
            val map: Map<String, ChatMessage> = gson.fromJson(raw, mapType)
            map.values.sortedBy { it.id }
        } catch (_: Exception) {
            null
        }
    }
}
