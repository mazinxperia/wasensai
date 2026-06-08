package com.mazin.wasensai.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mazin.wasensai.data.model.Message
import kotlinx.serialization.json.Json
import java.io.Closeable

internal data class ViewerChatStats(
    var messageCount: Int = 0,
    var visibleMessageCount: Int = 0,
    var lastFromMe: Int = 0
)

internal class ViewerMessageStore(
    context: Context,
    private val json: Json
) : Closeable {

    private val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                    id INTEGER PRIMARY KEY,
                    chat_id INTEGER NOT NULL,
                    sort_id INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    message_type INTEGER NOT NULL,
                    from_me INTEGER NOT NULL,
                    starred INTEGER NOT NULL,
                    key_id TEXT NOT NULL,
                    search_text TEXT NOT NULL,
                    payload_json TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_chat_sort ON $TABLE_MESSAGES(chat_id, sort_id, id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_chat_key ON $TABLE_MESSAGES(chat_id, key_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_starred ON $TABLE_MESSAGES(starred, timestamp, id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
            onCreate(db)
        }
    }

    private val db: SQLiteDatabase
        get() = helper.writableDatabase

    fun reset() {
        db.delete(TABLE_MESSAGES, null, null)
    }

    fun beginBulkImport() {
        db.beginTransaction()
    }

    fun markBulkImportSuccessful() {
        db.setTransactionSuccessful()
    }

    fun endBulkImport() {
        if (db.inTransaction()) {
            db.endTransaction()
        }
    }

    fun insertMessage(message: Message, rawJson: String, baseSearchText: String) {
        val values = ContentValues(10).apply {
            put("id", message.id)
            put("chat_id", message.chatId)
            put("sort_id", message.sortId)
            put("timestamp", message.timestamp)
            put("message_type", message.messageType)
            put("from_me", message.fromMe)
            put("starred", message.starred)
            put("key_id", message.keyId)
            put("search_text", baseSearchText)
            put("payload_json", rawJson)
        }
        db.insertOrThrow(TABLE_MESSAGES, null, values)
    }

    fun appendMediaSearchText(messageId: Long, fileName: String, caption: String) {
        val extra = listOf(fileName, caption)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (extra.isEmpty()) return

        db.execSQL(
            """
            UPDATE $TABLE_MESSAGES
               SET search_text = TRIM(search_text || ' ' || ?)
             WHERE id = ?
            """.trimIndent(),
            arrayOf(extra, messageId.toString())
        )
    }

    fun getAllMessagesByChatId(chatId: Long): List<Message> =
        queryMessages(
            sql = """
                SELECT payload_json
                  FROM $TABLE_MESSAGES
                 WHERE chat_id = ?
                 ORDER BY sort_id ASC, id ASC
            """.trimIndent(),
            args = arrayOf(chatId.toString())
        )

    fun getRecentMessagesByChatId(chatId: Long, limit: Int): List<Message> =
        queryMessages(
            sql = """
                SELECT payload_json
                  FROM (
                        SELECT payload_json, sort_id, id
                          FROM $TABLE_MESSAGES
                         WHERE chat_id = ?
                         ORDER BY sort_id DESC, id DESC
                         LIMIT ?
                  )
                 ORDER BY sort_id ASC, id ASC
            """.trimIndent(),
            args = arrayOf(chatId.toString(), limit.toString())
        )

    fun getMediaMessagesByChatId(chatId: Long): List<Message> =
        queryMessages(
            sql = """
                SELECT payload_json
                  FROM $TABLE_MESSAGES
                 WHERE chat_id = ?
                   AND message_type IN (1, 2, 3, 9, 13, 20)
                 ORDER BY sort_id ASC, id ASC
            """.trimIndent(),
            args = arrayOf(chatId.toString())
        )

    fun getMessagesByKeyIds(chatId: Long, keyIds: Collection<String>): Map<String, Message> {
        if (keyIds.isEmpty()) return emptyMap()
        val deduped = keyIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (deduped.isEmpty()) return emptyMap()

        return deduped
            .chunked(SQLITE_IN_LIMIT)
            .flatMap { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val args = ArrayList<String>(chunk.size + 1).apply {
                    add(chatId.toString())
                    addAll(chunk)
                }
                queryMessages(
                    sql = """
                        SELECT payload_json
                          FROM $TABLE_MESSAGES
                         WHERE chat_id = ?
                           AND key_id IN ($placeholders)
                    """.trimIndent(),
                    args = args.toTypedArray()
                )
            }
            .filter { it.keyId.isNotEmpty() }
            .associateBy { it.keyId }
    }

    fun getStarredMessages(starredIds: Set<Long>): List<Message> {
        if (starredIds.isEmpty()) {
            return queryMessages(
                sql = """
                    SELECT payload_json
                      FROM $TABLE_MESSAGES
                     WHERE starred != 0
                     ORDER BY timestamp ASC, id ASC
                """.trimIndent(),
                args = emptyArray()
            )
        }

        return starredIds
            .toList()
            .chunked(SQLITE_IN_LIMIT)
            .flatMap { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                queryMessages(
                    sql = """
                        SELECT payload_json
                          FROM $TABLE_MESSAGES
                         WHERE id IN ($placeholders)
                         ORDER BY timestamp ASC, id ASC
                    """.trimIndent(),
                    args = chunk.map(Long::toString).toTypedArray()
                )
            }
    }

    fun searchMessages(query: String, limit: Int): List<Message> {
        if (query.isBlank()) return emptyList()
        return queryMessages(
            sql = """
                SELECT payload_json
                  FROM $TABLE_MESSAGES
                 WHERE search_text LIKE ?
                 ORDER BY timestamp DESC, id DESC
                 LIMIT ?
            """.trimIndent(),
            args = arrayOf("%${query.lowercase()}%", limit.toString())
        )
    }

    private fun queryMessages(sql: String, args: Array<String>): List<Message> {
        val results = ArrayList<Message>()
        db.rawQuery(sql, args).use { cursor ->
            val jsonIndex = cursor.getColumnIndexOrThrow("payload_json")
            while (cursor.moveToNext()) {
                results += json.decodeFromString<Message>(cursor.getString(jsonIndex))
            }
        }
        return results
    }

    override fun close() {
        helper.close()
    }

    companion object {
        private const val DB_NAME = "waview_viewer_messages.db"
        private const val DB_VERSION = 1
        private const val TABLE_MESSAGES = "viewer_messages"
        private const val SQLITE_IN_LIMIT = 900
    }
}
