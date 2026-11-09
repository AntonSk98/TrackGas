package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Unsaved Add/Edit form contents and picker state, not a reading stored in the database.
 * The saver preserves typed input across screen recreation, including incomplete or invalid text.
 * Session and dialog IDs prevent restoring that input into a different form; Cancel discards it.
 */
internal class ReadingDraft(
    val sessionId: String,
    val dialogId: String,
    date: String,
    value: String,
    note: String,
    choosingDate: Boolean = false,
    duplicateDismissed: Boolean = false,
    time: String,
    val timezone: String,
    choosingTime: Boolean = false,
) {
    var dateIso by mutableStateOf(date)
    var time by mutableStateOf(time)
    var value by mutableStateOf(value)
    var note by mutableStateOf(note)
    var choosingDate by mutableStateOf(choosingDate)
    var choosingTime by mutableStateOf(choosingTime)
    var duplicateDismissed by mutableStateOf(duplicateDismissed)
    val date: LocalDate get() = LocalDate.parse(dateIso)

    fun matches(session: String, dialog: String) = sessionId == session && dialogId == dialog

    companion object {
        val saver = Saver<ReadingDraft, ArrayList<String>>(
            save = {
                arrayListOf(
                    it.sessionId, it.dialogId, it.dateIso, it.value, it.note,
                    it.choosingDate.toString(), it.duplicateDismissed.toString(),
                    it.time, it.timezone, it.choosingTime.toString(),
                )
            },
            restore = { ReadingDraft(it[0], it[1], it[2], it[3], it[4], it[5].toBoolean(), it[6].toBoolean(), it[7], it[8],
                choosingTime = it[9].toBoolean()) },
        )
    }
}
