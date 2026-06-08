package com.mazin.wasensai.data.repository

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.mazin.wasensai.data.model.*
import com.mazin.wasensai.root.RootFileAccess
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.utils.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractSnapshot(
    val exportInfo: ExportInfo,
    val chats: List<Chat>,
    val contacts: List<Contact>,
    val groups: List<Group>,
    val reactions: List<Reaction>,
    val polls: List<Poll>,
    val mediaIndex: List<MediaEntry>,
    val callLogs: List<CallLog>,
    val labels: List<Label>,
    val labeledMessages: List<LabeledMessage>,
    val mentions: List<Mention>,
    val vcards: List<VCard>,
    val statuses: List<StatusUpdate>,
    val messageEdits: List<MessageEdit>,
    val starredMessages: List<Long>
)

@Singleton
class ExtractRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootFileAccess: RootFileAccess
) {
    private fun log(msg: String) = android.util.Log.d("WASensai", "[EXTRACT] $msg")
    private val exportJson = Json { prettyPrint = true }

    // ─── Public entry point ───────────────────────────────────────────────────

    suspend fun collectExportSnapshot(onProgress: (String) -> Unit): ExtractSnapshot =
        withContext(Dispatchers.IO) {
            val cacheDir = FileUtils.getCacheDir(context)

            onProgress("Reading contacts...")
            val contacts = readContacts(cacheDir)
            log("Contacts: ${contacts.size}")
            onProgress("Contacts: ${contacts.size}")

            onProgress("Reading chats...")
            val chats = readChats(cacheDir)
            log("Chats: ${chats.size}")
            onProgress("Chats: ${chats.size}")

            onProgress("Counting messages...")
            val totalMessages = countMessages(cacheDir)
            log("Total messages: $totalMessages")
            onProgress("Messages: $totalMessages")

            onProgress("Reading reactions...")
            val reactions = readReactions(cacheDir)
            log("Reactions: ${reactions.size}")
            onProgress("Reactions: ${reactions.size}")

            onProgress("Reading polls...")
            val polls = readPolls(cacheDir)
            log("Polls: ${polls.size}")
            onProgress("Polls: ${polls.size}")

            onProgress("Reading media metadata...")
            val mediaIndex = buildMediaIndex(cacheDir)
            log("Media index: ${mediaIndex.size} entries")
            onProgress("Media index: ${mediaIndex.size} entries")

            onProgress("Reading group participants...")
            val groups = readGroups(cacheDir, chats)
            log("Groups: ${groups.size}")
            onProgress("Groups: ${groups.size}")

            onProgress("Reading call logs...")
            val callLogs = readCallLogs(cacheDir)
            log("Calls: ${callLogs.size}")
            onProgress("Calls: ${callLogs.size}")

            onProgress("Reading labels...")
            val (labels, labeledMessages) = readLabels(cacheDir)
            log("Labels: ${labels.size}, labeled messages: ${labeledMessages.size}")
            onProgress("Labels: ${labels.size}")

            onProgress("Reading mentions...")
            val mentions = readMentions(cacheDir)
            log("Mentions: ${mentions.size}")
            onProgress("Mentions: ${mentions.size}")

            onProgress("Reading contact cards...")
            val vcards = readVCards(cacheDir)
            log("VCards: ${vcards.size}")
            onProgress("VCards: ${vcards.size}")

            onProgress("Reading status metadata...")
            val statuses = readStatuses(cacheDir)
            log("Statuses: ${statuses.size}")
            onProgress("Status entries: ${statuses.size} (not rendered)")

            onProgress("Reading message edit history...")
            val messageEdits = readMessageEdits(cacheDir)
            log("MessageEdits: ${messageEdits.size}")
            onProgress("Message edits: ${messageEdits.size}")

            val phoneNumber = extractPhoneNumber(cacheDir)
            val starred     = readStarredMessageIds(cacheDir)

            val mediaCount = mediaIndex.size
            log("Summary: messages=$totalMessages media=$mediaCount starred=${starred.size}")
            onProgress("Phone: +$phoneNumber | Starred: ${starred.size}")

            ExtractSnapshot(
                exportInfo = ExportInfo(
                    phoneNumber   = phoneNumber,
                    exportDate    = DateUtils.currentIso8601(),
                    appVersion    = "1.0.0",
                    formatVersion = 3,
                    totalChats    = chats.size,
                    totalMessages = totalMessages,
                    totalMedia    = mediaCount
                ),
                chats           = chats,
                contacts        = contacts,
                groups          = groups,
                reactions       = reactions,
                polls           = polls,
                mediaIndex      = mediaIndex,
                callLogs        = callLogs,
                labels          = labels,
                labeledMessages = labeledMessages,
                mentions        = mentions,
                vcards          = vcards,
                statuses        = statuses,
                messageEdits    = messageEdits,
                starredMessages = starred
            )
        }

    suspend fun writeDataJson(
        cacheDir: File,
        snapshot: ExtractSnapshot,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dataJsonFile = File(cacheDir, "data.json")
        dataJsonFile.bufferedWriter().use { writer ->
            writer.appendLine("{")
            writeNamedJsonValue(writer, "export_info", ExportInfo.serializer(), snapshot.exportInfo, trailingComma = true)
            writeSerializedArrayField(writer, "chats", Chat.serializer(), snapshot.chats, trailingComma = true)
            writeSerializedArrayField(writer, "contacts", Contact.serializer(), snapshot.contacts, trailingComma = true)
            writeSerializedArrayField(writer, "groups", Group.serializer(), snapshot.groups, trailingComma = true)
            writeMessagesArrayField(writer, cacheDir, snapshot.exportInfo.totalMessages, onProgress)
            writeSerializedArrayField(writer, "reactions", Reaction.serializer(), snapshot.reactions, trailingComma = true)
            writeSerializedArrayField(writer, "polls", Poll.serializer(), snapshot.polls, trailingComma = true)
            writeSerializedArrayField(writer, "media_index", MediaEntry.serializer(), snapshot.mediaIndex, trailingComma = true)
            writeSerializedArrayField(writer, "call_logs", CallLog.serializer(), snapshot.callLogs, trailingComma = true)
            writeSerializedArrayField(writer, "labels", Label.serializer(), snapshot.labels, trailingComma = true)
            writeSerializedArrayField(writer, "labeled_messages", LabeledMessage.serializer(), snapshot.labeledMessages, trailingComma = true)
            writeSerializedArrayField(writer, "mentions", Mention.serializer(), snapshot.mentions, trailingComma = true)
            writeSerializedArrayField(writer, "vcards", VCard.serializer(), snapshot.vcards, trailingComma = true)
            writeSerializedArrayField(writer, "statuses", StatusUpdate.serializer(), snapshot.statuses, trailingComma = true)
            writeSerializedArrayField(writer, "message_edits", MessageEdit.serializer(), snapshot.messageEdits, trailingComma = true)
            writeSerializedArrayField(writer, "starred_messages", Long.serializer(), snapshot.starredMessages, trailingComma = false)
            writer.appendLine("}")
        }
        dataJsonFile
    }

    // ─── Contacts ─────────────────────────────────────────────────────────────

    private fun readContacts(cacheDir: File): List<Contact> {
        val db = openDb(cacheDir, "wa.db") ?: return emptyList()
        return try {
            val list = mutableListOf<Contact>()
            db.rawQuery("""
                SELECT jid,
                       COALESCE(display_name, wa_name, '') as display_name,
                       COALESCE(wa_name, '') as wa_name,
                       COALESCE(number, '') as number,
                       COALESCE(status, '') as status
                FROM wa_contacts
                WHERE jid IS NOT NULL
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val jid = c.getString(0) ?: continue
                    list.add(Contact(
                        jid         = jid,
                        displayName = c.getString(1) ?: "",
                        waName      = c.getString(2) ?: "",
                        number      = c.getString(3) ?: "",
                        status      = c.getString(4) ?: "",
                        avatarFile  = buildAvatarFilename(jid)
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Contacts error: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── Chats ────────────────────────────────────────────────────────────────

    private fun readChats(cacheDir: File): List<Chat> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            // Optional columns — check existence to avoid query failure on older DB versions
            val pinnedSql = if (hasColumn(db, "chat", "pinned_message_row_id")) "COALESCE(c.pinned_message_row_id, 0)" else "0"
            val muteSql   = if (hasColumn(db, "chat", "mute_until")) "COALESCE(c.mute_until, 0)" else "0"

            val list = mutableListOf<Chat>()
            db.rawQuery("""
                SELECT
                    c._id,
                    COALESCE(c.subject, '') as subject,
                    COALESCE(lid_g.raw_string, jid.raw_string) as jid_raw,
                    jid.server,
                    c.sort_timestamp,
                    COALESCE(m.text_data, '') as last_msg,
                    COALESCE(m.message_type, 0) as last_type,
                    COALESCE(c.group_member_count, 0) as member_count,
                    COALESCE(c.archived, 0) as archived,
                    COALESCE(c.unseen_message_count, 0) as unread,
                    COALESCE(c.ephemeral_expiration, 0) as ephemeral,
                    COALESCE(c.created_timestamp, 0) as created_ts,
                    $pinnedSql as pinned_id,
                    $muteSql as mute_until
                FROM chat c
                JOIN jid ON c.jid_row_id = jid._id
                LEFT JOIN message m ON c.last_message_row_id = m._id
                LEFT JOIN jid_map jm_g ON c.jid_row_id = jm_g.lid_row_id
                LEFT JOIN jid lid_g ON jm_g.jid_row_id = lid_g._id
                ORDER BY c.sort_timestamp DESC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val jidRaw = c.getString(2) ?: continue
                    val server = c.getString(3) ?: "s.whatsapp.net"
                    val isGroup = server == "g.us" || jidRaw.contains("@g.us")
                    list.add(Chat(
                        id                  = c.getLong(0),
                        jid                 = jidRaw,
                        subject             = c.getString(1) ?: "",
                        isGroup             = isGroup,
                        sortTimestamp       = c.getLong(4),
                        lastMessage         = c.getString(5) ?: "",
                        lastMessageType     = c.getInt(6),
                        memberCount         = c.getInt(7),
                        archived            = c.getInt(8) != 0,
                        unreadCount         = c.getInt(9),
                        ephemeralExpiration = c.getInt(10),
                        createdTimestamp    = c.getLong(11),
                        pinned              = c.getLong(12) != 0L,
                        mutedUntil          = c.getLong(13),
                        avatarFile          = buildAvatarFilename(jidRaw)
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Chats error: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    private fun countMessages(cacheDir: File): Int {
        val db = openDb(cacheDir, "msgstore.db") ?: return 0
        return try {
            db.rawQuery("""
                SELECT COUNT(*)
                FROM message msg
                JOIN chat ON chat._id = msg.chat_row_id
                JOIN jid jid_chat ON jid_chat._id = chat.jid_row_id
                LEFT JOIN jid_map jm_chat ON chat.jid_row_id = jm_chat.lid_row_id
                LEFT JOIN jid lid_chat ON jm_chat.jid_row_id = lid_chat._id
                WHERE COALESCE(lid_chat.raw_string, jid_chat.raw_string) <> '-1'
            """.trimIndent(), null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            log("Message count error: ${e.message}")
            0
        } finally {
            db.close()
        }
    }

    private fun streamMessages(
        cacheDir: File,
        onMessage: (Message) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        val db = openDb(cacheDir, "msgstore.db") ?: return
        val contactNameMap = buildContactNameMap(cacheDir)
        try {
            val revokedForAll = buildRevokedSet(db)
            db.rawQuery(buildMessageQuery(db), null).use { c ->
                var processed = 0
                while (c.moveToNext()) {
                    onMessage(mapMessage(c, contactNameMap, revokedForAll))
                    processed++
                    if (processed % 5000 == 0) {
                        log("Messages streamed: $processed")
                        onProgress(processed)
                    }
                }
                onProgress(processed)
            }
        } finally {
            db.close()
        }
    }

    private fun buildMessageQuery(db: SQLiteDatabase): String {
        val placeNameSql = if (hasColumn(db, "message_location", "place_name")) "COALESCE(loc.place_name, '')" else "''"
        val placeAddrSql = if (hasColumn(db, "message_location", "address")) "COALESCE(loc.address, '')" else "''"
        return """
            SELECT
                COALESCE(lid_chat.raw_string, jid_chat.raw_string) as chat_jid,
                msg._id,
                msg.from_me,
                msg.timestamp,
                COALESCE(msg.received_timestamp, 0),
                COALESCE(msg.text_data, ''),
                msg.status,
                COALESCE(mf_ver.version, 0) as edit_version,
                msg.key_id,
                COALESCE(msg.starred, 0),
                COALESCE(msg.broadcast, 0),
                COALESCE(msg.translated_text, ''),
                msg.message_type,
                msg.sort_id,
                msg.chat_row_id,
                COALESCE(lid_sender.raw_string, jid_sender.raw_string, '') as sender_jid,
                COALESCE(chat.subject, '') as chat_subject,
                COALESCE(mq.key_id, '') as quoted_key_id,
                COALESCE(mq.from_me, 0) as quoted_from_me,
                COALESCE(mq.message_type, 0) as quoted_msg_type,
                COALESCE(mq.text_data, '') as quoted_text,
                COALESCE(lid_qsender.raw_string, jid_qsender.raw_string, '') as quoted_sender_jid,
                COALESCE(loc.latitude, 0.0) as latitude,
                COALESCE(loc.longitude, 0.0) as longitude,
                COALESCE(sys.action_type, 0) as action_type,
                COALESCE(mcl.video_call, 0) as is_video_call,
                COALESCE(mfwd.forward_score, 0) as forward_score,
                $placeNameSql as place_name,
                $placeAddrSql as place_address
            FROM message msg
            JOIN chat ON chat._id = msg.chat_row_id
            JOIN jid jid_chat ON jid_chat._id = chat.jid_row_id
            LEFT JOIN jid_map jm_chat ON chat.jid_row_id = jm_chat.lid_row_id
            LEFT JOIN jid lid_chat ON jm_chat.jid_row_id = lid_chat._id
            LEFT JOIN jid jid_sender ON jid_sender._id = msg.sender_jid_row_id
            LEFT JOIN jid_map jm_sender ON msg.sender_jid_row_id = jm_sender.lid_row_id
            LEFT JOIN jid lid_sender ON jm_sender.jid_row_id = lid_sender._id
            LEFT JOIN message_quoted mq ON mq.message_row_id = msg._id
            LEFT JOIN jid jid_qsender ON jid_qsender._id = mq.sender_jid_row_id
            LEFT JOIN jid_map jm_qsender ON mq.sender_jid_row_id = jm_qsender.lid_row_id
            LEFT JOIN jid lid_qsender ON jm_qsender.jid_row_id = lid_qsender._id
            LEFT JOIN message_location loc ON loc.message_row_id = msg._id
            LEFT JOIN message_future mf_ver ON mf_ver.message_row_id = msg._id
            LEFT JOIN message_system sys ON sys.message_row_id = msg._id
            LEFT JOIN missed_call_logs mcl ON mcl.message_row_id = msg._id
            LEFT JOIN message_forwarded mfwd ON mfwd.message_row_id = msg._id
            WHERE COALESCE(lid_chat.raw_string, jid_chat.raw_string) <> '-1'
            GROUP BY msg._id
            ORDER BY msg.timestamp ASC, msg._id ASC
        """.trimIndent()
    }

    private fun mapMessage(
        c: Cursor,
        contactNameMap: Map<String, String>,
        revokedForAll: Set<Long>
    ): Message {
        val msgId       = c.getLong(1)
        val msgType     = c.getInt(12)
        val status      = c.getInt(6)
        val editVersion = c.getInt(7)
        val fromMe      = c.getInt(2)
        val isDeleted   = msgType == 15 ||
            (fromMe == 1 && status == 5 && editVersion == 7) ||
            (fromMe == 0 && status == 0 && editVersion == 7)
        val isSystem    = status == 6
        val senderJid   = c.getString(15) ?: ""
        val forwardScore = c.getInt(26)
        return Message(
            id                  = msgId,
            chatId              = c.getLong(14),
            chatJid             = c.getString(0) ?: "",
            textData            = c.getString(5) ?: "",
            fromMe              = fromMe,
            timestamp           = c.getLong(3),
            receivedTimestamp   = c.getLong(4),
            sortId              = c.getLong(13),
            messageType         = msgType,
            status              = status,
            editVersion         = editVersion,
            keyId               = c.getString(8) ?: "",
            senderJid           = senderJid,
            senderName          = if (fromMe == 1) "You"
                                  else contactNameMap[senderJid]
                                      ?: senderJid.substringBefore("@"),
            chatSubject         = c.getString(16) ?: "",
            isDeleted           = isDeleted,
            isSystem            = isSystem,
            isVideoCall         = c.getInt(25) == 1,
            isForwarded         = forwardScore > 0,
            forwardScore        = forwardScore,
            starred             = c.getInt(9),
            broadcast           = c.getInt(10),
            translatedText      = c.getString(11) ?: "",
            latitude            = c.getDouble(22),
            longitude           = c.getDouble(23),
            placeName           = c.getString(27) ?: "",
            placeAddress        = c.getString(28) ?: "",
            quotedKeyId         = c.getString(17) ?: "",
            quotedFromMe        = c.getInt(18),
            quotedMessageType   = c.getInt(19),
            quotedText          = c.getString(20) ?: "",
            quotedSender        = c.getString(21) ?: "",
            actionType          = c.getInt(24),
            deletedForEveryone  = msgId in revokedForAll
        )
    }

    // ─── Media Index ──────────────────────────────────────────────────────────

    /**
     * Builds mediaIndex from message_media for all messages that reference a file.
     * status is "missing" at this point — ExportManager updates to "downloaded" / "skipped"
     * after the media copy step by scanning the actual mediaDir.
     */
    private fun buildMediaIndex(cacheDir: File): List<MediaEntry> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            // media_hash column name varies by WA version — check before including
            val hashSql = when {
                hasColumn(db, "message_media", "media_hash")     -> "COALESCE(mm.media_hash, '')"
                hasColumn(db, "message_media", "file_hash")      -> "COALESCE(mm.file_hash, '')"
                hasColumn(db, "message_media", "enc_file_hash")  -> "COALESCE(mm.enc_file_hash, '')"
                else                                             -> "''"
            }

            val mediaMap = mutableMapOf<Long, MediaEntry>()
            db.rawQuery("""
                SELECT
                    mm.message_row_id,
                    COALESCE(mm.file_path, '') as file_path,
                    COALESCE(mm.mime_type, '') as mime_type,
                    COALESCE(mm.file_size, mm.file_length, 0) as file_size,
                    COALESCE(mm.media_name, '') as media_name,
                    COALESCE(mm.media_caption, '') as media_caption,
                    COALESCE(mm.media_duration, 0) as duration,
                    COALESCE(mm.width, 0) as width,
                    COALESCE(mm.height, 0) as height,
                    COALESCE(mm.transferred, 0) as transferred,
                    $hashSql as media_hash
                FROM message_media mm
                JOIN message msg ON msg._id = mm.message_row_id
                JOIN chat ON chat._id = msg.chat_row_id
                JOIN jid jid_chat ON jid_chat._id = chat.jid_row_id
                LEFT JOIN jid_map jm_chat ON chat.jid_row_id = jm_chat.lid_row_id
                LEFT JOIN jid lid_chat ON jm_chat.jid_row_id = lid_chat._id
                WHERE COALESCE(lid_chat.raw_string, jid_chat.raw_string) <> '-1'
                ORDER BY mm.message_row_id ASC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val msgId    = c.getLong(0)
                    val fullPath = c.getString(1) ?: ""
                    mediaMap[msgId] = MediaEntry(
                        messageId    = msgId,
                        relativePath = toRelativePath(fullPath),
                        mimeType     = c.getString(2) ?: "",
                        size         = c.getLong(3),
                        fileName     = c.getString(4) ?: "",
                        caption      = c.getString(5) ?: "",
                        duration     = c.getInt(6),
                        width        = c.getInt(7),
                        height       = c.getInt(8),
                        transferred  = c.getInt(9),
                        status       = "missing",
                        mediaHash    = c.getString(10) ?: ""
                    )
                }
            }
            mediaMap.values.toList()
        } catch (e: Exception) { log("MediaIndex error: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    private fun readStarredMessageIds(cacheDir: File): List<Long> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val list = mutableListOf<Long>()
            db.rawQuery("""
                SELECT msg._id
                FROM message msg
                JOIN chat ON chat._id = msg.chat_row_id
                JOIN jid jid_chat ON jid_chat._id = chat.jid_row_id
                LEFT JOIN jid_map jm_chat ON chat.jid_row_id = jm_chat.lid_row_id
                LEFT JOIN jid lid_chat ON jm_chat.jid_row_id = lid_chat._id
                WHERE COALESCE(lid_chat.raw_string, jid_chat.raw_string) <> '-1'
                  AND COALESCE(msg.starred, 0) = 1
                ORDER BY msg.timestamp ASC, msg._id ASC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    list.add(c.getLong(0))
                }
            }
            list
        } catch (e: Exception) {
            log("Starred messages error: ${e.message}")
            emptyList()
        } finally {
            db.close()
        }
    }

    private fun writeMessagesArrayField(
        writer: BufferedWriter,
        cacheDir: File,
        expectedTotal: Int,
        onProgress: (String) -> Unit
    ) {
        writer.appendLine("  \"messages\": [")
        var processed = 0
        var previousEncoded: String? = null
        streamMessages(
            cacheDir = cacheDir,
            onMessage = { message ->
                val encoded = exportJson.encodeToString(Message.serializer(), message)
                previousEncoded?.let { writeArrayItem(writer, it, trailingComma = true) }
                previousEncoded = encoded
                processed++
                if (processed % 5000 == 0) {
                    onProgress("Streaming messages: $processed/$expectedTotal")
                    writer.flush()
                }
            },
            onProgress = { count ->
                if (count > 0) {
                    onProgress("Reading messages: $count/$expectedTotal")
                }
            }
        )
        previousEncoded?.let { writeArrayItem(writer, it, trailingComma = false) }
        writer.write("  ],")
        writer.newLine()
        log("Messages serialized: $processed / expected $expectedTotal")
    }

    private fun <T> writeNamedJsonValue(
        writer: BufferedWriter,
        name: String,
        serializer: KSerializer<T>,
        value: T,
        trailingComma: Boolean
    ) {
        writeNamedRawJson(writer, name, exportJson.encodeToString(serializer, value), trailingComma)
    }

    private fun writeNamedRawJson(
        writer: BufferedWriter,
        name: String,
        encoded: String,
        trailingComma: Boolean
    ) {
        val lines = encoded.lines()
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                writer.write("  \"$name\": ")
            } else {
                writer.write("  ")
            }
            writer.write(line)
            if (index == lines.lastIndex && trailingComma) writer.write(",")
            writer.newLine()
        }
    }

    private fun <T> writeSerializedArrayField(
        writer: BufferedWriter,
        name: String,
        serializer: KSerializer<T>,
        items: List<T>,
        trailingComma: Boolean
    ) {
        if (items.isEmpty()) {
            writer.write("  \"$name\": []")
            if (trailingComma) writer.write(",")
            writer.newLine()
            return
        }
        writer.appendLine("  \"$name\": [")
        items.forEachIndexed { index, item ->
            writeArrayItem(
                writer = writer,
                encoded = exportJson.encodeToString(serializer, item),
                trailingComma = index != items.lastIndex
            )
        }
        writer.write("  ]")
        if (trailingComma) writer.write(",")
        writer.newLine()
    }

    private fun writeArrayItem(
        writer: BufferedWriter,
        encoded: String,
        trailingComma: Boolean
    ) {
        val lines = encoded.lines()
        lines.forEachIndexed { index, line ->
            writer.write("    ")
            writer.write(line)
            if (index == lines.lastIndex && trailingComma) writer.write(",")
            writer.newLine()
        }
    }

    /**
     * Converts a full device path or partial path to a zip-relative path.
     *
     * Zip structure: media/WhatsApp Business/Media/...
     * So relative path = "WhatsApp Business/Media/..."
     *
     * Examples:
     *   /storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/X/f.jpg
     *     → WhatsApp Business/Media/X/f.jpg
     *   Media/WhatsApp Business Images/f.jpg   (WA Business new relative format)
     *     → WhatsApp Business/Media/WhatsApp Business Images/f.jpg
     *   WhatsApp Business/Media/X/f.jpg
     *     → WhatsApp Business/Media/X/f.jpg  (already correct)
     */
    fun toRelativePath(fullPath: String): String {
        if (fullPath.isEmpty()) return ""
        val norm = fullPath.replace("\\", "/").trim()

        // Full absolute path — find "/WhatsApp Business/" or "/WhatsApp/"
        val wbIdx = norm.indexOf("/WhatsApp Business/")
        if (wbIdx >= 0) return norm.substring(wbIdx + 1)

        val waIdx = norm.indexOf("/WhatsApp/")
        if (waIdx >= 0) return norm.substring(waIdx + 1)

        // Relative path starting with "Media/" (WhatsApp Business stores this way on some devices)
        if (norm.startsWith("Media/", ignoreCase = true)) return "WhatsApp Business/$norm"

        // Already correct relative path
        if (norm.startsWith("WhatsApp Business/", ignoreCase = true)) return norm
        if (norm.startsWith("WhatsApp/", ignoreCase = true)) return norm

        // Unknown absolute path — return filename only as last resort
        if (norm.startsWith("/")) return norm.substringAfterLast("/")

        return norm
    }

    // ─── Reactions ────────────────────────────────────────────────────────────

    private fun readReactions(cacheDir: File): List<Reaction> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val list = mutableListOf<Reaction>()
            db.rawQuery("""
                SELECT
                    mao.parent_message_row_id as message_id,
                    maor.reaction,
                    maor.sender_timestamp,
                    COALESCE(lid.raw_string, jid.raw_string, '') as sender_jid
                FROM message_add_on_reaction maor
                JOIN message_add_on mao ON mao._id = maor.message_add_on_row_id
                LEFT JOIN jid ON jid._id = mao.sender_jid_row_id
                LEFT JOIN jid_map jmap ON mao.sender_jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
                WHERE maor.reaction IS NOT NULL
                  AND maor.reaction != ''
                  AND mao.parent_message_row_id IS NOT NULL
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    list.add(Reaction(
                        messageId = c.getLong(0),
                        emoji     = c.getString(1) ?: "",
                        senderJid = c.getString(3) ?: "",
                        timestamp = c.getLong(2)
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Reactions skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── Polls ────────────────────────────────────────────────────────────────

    private fun readPolls(cacheDir: File): List<Poll> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            // Options indexed by message_row_id
            val optionsMap = mutableMapOf<Long, MutableList<PollOptionEntry>>()
            db.rawQuery("""
                SELECT _id, message_row_id, option_name, vote_total
                FROM message_poll_option
                ORDER BY message_row_id, _id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val msgId = c.getLong(1)
                    optionsMap.getOrPut(msgId) { mutableListOf() }.add(
                        PollOptionEntry(
                            id         = c.getLong(0),
                            optionName = c.getString(2) ?: "",
                            voteTotal  = c.getInt(3)
                        )
                    )
                }
            }

            // Individual votes per option (message_add_on_poll_vote — may not exist on all versions)
            val votesMap = readPollVotes(db)

            val list = mutableListOf<Poll>()
            db.rawQuery("""
                SELECT mp.message_row_id,
                       COALESCE(m.text_data, '') as question,
                       COALESCE(mp.selectable_options_count, 0),
                       COALESCE(mp.end_time, 0)
                FROM message_poll mp
                JOIN message m ON m._id = mp.message_row_id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val msgId = c.getLong(0)
                    list.add(Poll(
                        messageId       = msgId,
                        question        = c.getString(1) ?: "",
                        selectableCount = c.getInt(2),
                        endTime         = c.getLong(3),
                        options         = optionsMap[msgId] ?: emptyList(),
                        votes           = votesMap[msgId]   ?: emptyList()
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Polls skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    private fun readPollVotes(db: SQLiteDatabase): Map<Long, List<PollVote>> {
        return try {
            val map = mutableMapOf<Long, MutableList<PollVote>>()
            db.rawQuery("""
                SELECT
                    mao.parent_message_row_id,
                    COALESCE(lid.raw_string, jid.raw_string, '') as voter_jid,
                    COALESCE(maopv.selected_option_local_ids, '') as option_ids,
                    COALESCE(mao.receipt_device_timestamp, 0) as ts
                FROM message_add_on_poll_vote maopv
                JOIN message_add_on mao ON mao._id = maopv.message_add_on_row_id
                LEFT JOIN jid ON jid._id = mao.sender_jid_row_id
                LEFT JOIN jid_map jmap ON mao.sender_jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
                WHERE mao.parent_message_row_id IS NOT NULL
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val msgId = c.getLong(0)
                    map.getOrPut(msgId) { mutableListOf() }.add(
                        PollVote(
                            voterJid  = c.getString(1) ?: "",
                            optionIds = c.getString(2) ?: "",
                            timestamp = c.getLong(3)
                        )
                    )
                }
            }
            map
        } catch (e: Exception) { log("PollVotes skipped: ${e.message}"); emptyMap() }
    }

    // ─── Groups ───────────────────────────────────────────────────────────────

    private fun readGroups(cacheDir: File, chats: List<Chat>): List<Group> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val groupChats = chats.filter { it.isGroup }.associateBy { it.id }

            // Current participants — keyed by chat._id via direct jid_row_id join
            val participantsMap = mutableMapOf<Long, MutableList<GroupParticipant>>()
            db.rawQuery("""
                SELECT
                    c._id as chat_id,
                    COALESCE(lid_u.raw_string, jid_u.raw_string) as user_jid,
                    gpu.rank,
                    COALESCE(gpu.pending, 0) as pending,
                    COALESCE(gpu.add_timestamp, 0) as add_ts
                FROM group_participant_user gpu
                JOIN chat c ON c.jid_row_id = gpu.group_jid_row_id
                JOIN jid jid_u ON jid_u._id = gpu.user_jid_row_id
                LEFT JOIN jid_map jm_u ON gpu.user_jid_row_id = jm_u.lid_row_id
                LEFT JOIN jid lid_u ON jm_u.jid_row_id = lid_u._id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val chatId = c.getLong(0)
                    participantsMap.getOrPut(chatId) { mutableListOf() }.add(
                        GroupParticipant(
                            jid          = c.getString(1) ?: "",
                            rank         = c.getInt(2),
                            addTimestamp = c.getLong(4),
                            pending      = c.getInt(3) != 0
                        )
                    )
                }
            }

            // Past participants
            val pastMap = mutableMapOf<Long, MutableList<PastParticipant>>()
            db.rawQuery("""
                SELECT
                    c._id as chat_id,
                    COALESCE(lid_u.raw_string, jid_u.raw_string) as user_jid,
                    gppu.is_leave,
                    COALESCE(gppu.timestamp, 0) as ts
                FROM group_past_participant_user gppu
                JOIN chat c ON c.jid_row_id = gppu.group_jid_row_id
                JOIN jid jid_u ON jid_u._id = gppu.user_jid_row_id
                LEFT JOIN jid_map jm_u ON gppu.user_jid_row_id = jm_u.lid_row_id
                LEFT JOIN jid lid_u ON jm_u.jid_row_id = lid_u._id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val chatId = c.getLong(0)
                    pastMap.getOrPut(chatId) { mutableListOf() }.add(
                        PastParticipant(
                            jid       = c.getString(1) ?: "",
                            isLeave   = c.getInt(2) != 0,
                            timestamp = c.getLong(3)
                        )
                    )
                }
            }

            // Group invite links from group_invite table (may not exist on all versions)
            val inviteLinksMap = readGroupInviteLinks(db)

            groupChats.values.map { chat ->
                Group(
                    chatId           = chat.id,
                    jid              = chat.jid,
                    name             = chat.chatName.ifEmpty { chat.subject },
                    memberCount      = chat.memberCount,
                    participants     = participantsMap[chat.id] ?: emptyList(),
                    pastParticipants = pastMap[chat.id] ?: emptyList(),
                    inviteLink       = inviteLinksMap[chat.id] ?: ""
                )
            }
        } catch (e: Exception) { log("Groups error: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    private fun readGroupInviteLinks(db: SQLiteDatabase): Map<Long, String> {
        return try {
            val map = mutableMapOf<Long, String>()
            db.rawQuery("""
                SELECT chat_row_id, invite_link
                FROM group_invite
                WHERE invite_link IS NOT NULL AND invite_link != ''
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    map[c.getLong(0)] = c.getString(1) ?: ""
                }
            }
            map
        } catch (e: Exception) { log("GroupInviteLinks skipped: ${e.message}"); emptyMap() }
    }

    // ─── Call Logs ────────────────────────────────────────────────────────────

    private fun readCallLogs(cacheDir: File): List<CallLog> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            // Group call participants — keyed by call_log._id
            val participantsMap = readCallParticipants(db)

            val list = mutableListOf<CallLog>()
            db.rawQuery("""
                SELECT
                    cl._id,
                    COALESCE(lid_g.raw_string, jid.raw_string) as jid_raw,
                    cl.from_me,
                    cl.call_id,
                    cl.timestamp,
                    cl.duration,
                    cl.call_result,
                    COALESCE(chat.subject, '') as chat_subject
                FROM call_log cl
                JOIN jid ON cl.jid_row_id = jid._id
                LEFT JOIN chat ON cl.jid_row_id = chat.jid_row_id
                LEFT JOIN jid_map jm_g ON cl.jid_row_id = jm_g.lid_row_id
                LEFT JOIN jid lid_g ON jm_g.jid_row_id = lid_g._id
                ORDER BY cl.timestamp DESC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val callId = c.getLong(0)
                    list.add(CallLog(
                        id           = callId,
                        jid          = c.getString(1) ?: "",
                        fromMe       = c.getInt(2) == 1,
                        callId       = c.getString(3) ?: "",
                        timestamp    = c.getLong(4),
                        duration     = c.getLong(5),
                        callResult   = c.getInt(6),
                        chatSubject  = c.getString(7) ?: "",
                        isVideo      = false,
                        participants = participantsMap[callId] ?: emptyList()
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Calls error: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    private fun readCallParticipants(db: SQLiteDatabase): Map<Long, List<CallParticipant>> {
        return try {
            val map = mutableMapOf<Long, MutableList<CallParticipant>>()
            db.rawQuery("""
                SELECT
                    clp.call_log_row_id,
                    COALESCE(lid.raw_string, jid.raw_string, '') as jid_str,
                    COALESCE(clp.call_result, 0),
                    COALESCE(clp.duration, 0)
                FROM call_log_participant_v2 clp
                JOIN jid ON jid._id = clp.jid_row_id
                LEFT JOIN jid_map jmap ON clp.jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val callLogId = c.getLong(0)
                    map.getOrPut(callLogId) { mutableListOf() }.add(
                        CallParticipant(
                            jid        = c.getString(1) ?: "",
                            callResult = c.getInt(2),
                            duration   = c.getLong(3)
                        )
                    )
                }
            }
            map
        } catch (e: Exception) { log("CallParticipants skipped: ${e.message}"); emptyMap() }
    }

    // ─── Labels ───────────────────────────────────────────────────────────────

    private fun readLabels(cacheDir: File): Pair<List<Label>, List<LabeledMessage>> {
        val db = openDb(cacheDir, "msgstore.db") ?: return Pair(emptyList(), emptyList())
        return try {
            val labels = mutableListOf<Label>()
            db.rawQuery("""
                SELECT _id, COALESCE(label_name,'') as name, color_id, predefined_id
                FROM labels WHERE hidden = 0 OR hidden IS NULL
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    labels.add(Label(
                        id          = c.getLong(0),
                        name        = c.getString(1) ?: "",
                        colorId     = c.getInt(2),
                        predefinedId = c.getInt(3)
                    ))
                }
            }
            val labeled = mutableListOf<LabeledMessage>()
            db.rawQuery("SELECT label_id, message_row_id FROM labeled_messages", null).use { c ->
                while (c.moveToNext()) {
                    labeled.add(LabeledMessage(labelId = c.getLong(0), messageId = c.getLong(1)))
                }
            }
            Pair(labels, labeled)
        } catch (e: Exception) { log("Labels skipped: ${e.message}"); Pair(emptyList(), emptyList())
        } finally { db.close() }
    }

    // ─── Mentions ─────────────────────────────────────────────────────────────

    private fun readMentions(cacheDir: File): List<Mention> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val list = mutableListOf<Mention>()
            db.rawQuery("""
                SELECT
                    mm.message_row_id,
                    COALESCE(lid.raw_string, jid.raw_string, '') as jid_str,
                    COALESCE(mm.display_name, '') as display_name,
                    COALESCE(mm.mention_type, 0) as mention_type
                FROM message_mentions mm
                LEFT JOIN jid ON jid._id = mm.jid_row_id
                LEFT JOIN jid_map jmap ON mm.jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    list.add(Mention(
                        messageId   = c.getLong(0),
                        jid         = c.getString(1) ?: "",
                        displayName = c.getString(2) ?: "",
                        mentionType = c.getInt(3)
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Mentions skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── VCards ───────────────────────────────────────────────────────────────

    private fun readVCards(cacheDir: File): List<VCard> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            // Build jids map from message_vcard_jid (may not exist on all devices)
            val jidsMap = readVCardJids(db)

            val list = mutableListOf<VCard>()
            db.rawQuery("SELECT message_row_id, vcard FROM message_vcard", null).use { c ->
                while (c.moveToNext()) {
                    val msgId = c.getLong(0)
                    list.add(VCard(
                        messageId = msgId,
                        vcard     = c.getString(1) ?: "",
                        jids      = jidsMap[msgId] ?: emptyList()
                    ))
                }
            }
            list
        } catch (e: Exception) { log("VCards skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    private fun readVCardJids(db: SQLiteDatabase): Map<Long, List<String>> {
        return try {
            val map = mutableMapOf<Long, MutableList<String>>()
            db.rawQuery("""
                SELECT mvj.message_row_id,
                       COALESCE(lid.raw_string, jid.raw_string, '') as jid_str
                FROM message_vcard_jid mvj
                LEFT JOIN jid ON jid._id = mvj.contact_jid_row_id
                LEFT JOIN jid_map jmap ON mvj.contact_jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
                WHERE COALESCE(lid.raw_string, jid.raw_string, '') != ''
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val msgId = c.getLong(0)
                    map.getOrPut(msgId) { mutableListOf() }.add(c.getString(1) ?: "")
                }
            }
            map
        } catch (e: Exception) { log("VCardJids skipped: ${e.message}"); emptyMap() }
    }

    // ─── Status Updates ───────────────────────────────────────────────────────

    private fun readStatuses(cacheDir: File): List<StatusUpdate> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val list = mutableListOf<StatusUpdate>()
            db.rawQuery("""
                SELECT
                    COALESCE(lid.raw_string, jid.raw_string) as jid_str,
                    s.timestamp,
                    s.unseen_count,
                    s.total_count
                FROM status s
                JOIN jid ON jid._id = s.jid_row_id
                LEFT JOIN jid_map jmap ON s.jid_row_id = jmap.lid_row_id
                LEFT JOIN jid lid ON jmap.jid_row_id = lid._id
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    list.add(StatusUpdate(
                        jid         = c.getString(0) ?: "",
                        timestamp   = c.getLong(1),
                        unseenCount = c.getInt(2),
                        totalCount  = c.getInt(3)
                    ))
                }
            }
            list
        } catch (e: Exception) { log("Statuses skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── Phone number ─────────────────────────────────────────────────────────

    private fun extractPhoneNumber(cacheDir: File): String {
        val db = openDb(cacheDir, "msgstore.db") ?: return ""
        return try {
            db.rawQuery("""
                SELECT DISTINCT jid_chat.raw_string
                FROM message msg
                JOIN chat ON chat._id = msg.chat_row_id
                JOIN jid jid_chat ON jid_chat._id = chat.jid_row_id
                WHERE msg.from_me = 1 AND jid_chat.server = 's.whatsapp.net'
                LIMIT 1
            """.trimIndent(), null).use { c ->
                if (c.moveToFirst()) {
                    val raw = c.getString(0) ?: ""
                    if (raw.contains("@")) raw.substringBefore("@") else raw
                } else ""
            }
        } catch (e: Exception) { log("PhoneNumber error: ${e.message}"); ""
        } finally { db.close() }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildContactNameMap(cacheDir: File): Map<String, String> {
        val db = openDb(cacheDir, "wa.db") ?: return emptyMap()
        return try {
            val map = mutableMapOf<String, String>()
            db.rawQuery("""
                SELECT jid, COALESCE(display_name, wa_name, '') as name
                FROM wa_contacts WHERE jid IS NOT NULL
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val jid  = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    if (name.isNotEmpty()) map[jid] = name
                }
            }
            map
        } catch (e: Exception) { emptyMap() } finally { db.close() }
    }

    private fun openDb(cacheDir: File, name: String): SQLiteDatabase? {
        val file = File(cacheDir, name)
        if (!file.exists()) { log("DB not found: $name"); return null }
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        } catch (e: Exception) { log("openDb error ($name): ${e.message}"); null }
    }

    private fun buildAvatarFilename(jid: String): String {
        val phone  = jid.substringBefore("@")
        val server = jid.substringAfter("@").replace(".", "_").replace("@", "_")
        return "${phone}_${server}_j.jpeg"
    }

    // ─── Message edit history ─────────────────────────────────────────────────

    private fun readMessageEdits(cacheDir: File): List<MessageEdit> {
        val db = openDb(cacheDir, "msgstore.db") ?: return emptyList()
        return try {
            val list = mutableListOf<MessageEdit>()
            db.rawQuery("""
                SELECT message_row_id,
                       COALESCE(edit_timestamp, 0) as edit_ts,
                       COALESCE(previous_text, '') as prev_text
                FROM message_edit_info
                ORDER BY message_row_id, edit_ts ASC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    list.add(MessageEdit(
                        messageId       = c.getLong(0),
                        editedTimestamp = c.getLong(1),
                        previousText    = c.getString(2) ?: ""
                    ))
                }
            }
            list
        } catch (e: Exception) { log("MessageEdits skipped: ${e.message}"); emptyList()
        } finally { db.close() }
    }

    // ─── Revoked (deleted-for-everyone) messages ──────────────────────────────

    private fun buildRevokedSet(db: SQLiteDatabase): Set<Long> {
        return try {
            val set = mutableSetOf<Long>()
            // All rows in message_revoked = messages revoked for everyone by the sender
            db.rawQuery("SELECT message_row_id FROM message_revoked", null).use { c ->
                while (c.moveToNext()) set.add(c.getLong(0))
            }
            set
        } catch (e: Exception) { log("RevokedSet skipped: ${e.message}"); emptySet() }
    }

    // ─── Schema inspection helper ─────────────────────────────────────────────

    /**
     * Returns true if [column] exists in [table].
     * Used to safely include optional columns that may not exist in all WA DB versions.
     */
    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        return try {
            db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
                }
                false
            }
        } catch (_: Exception) { false }
    }
}
