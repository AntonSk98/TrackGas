package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import java.time.LocalDate

internal class ReadingDraft(
    val sessionId: String,
    val dialogId: String,
    date: String,
    value: String,
    choosingDate: Boolean = false,
    duplicateDismissed: Boolean = false,
) {
    var dateIso by mutableStateOf(date)
    var value by mutableStateOf(value)
    var choosingDate by mutableStateOf(choosingDate)
    var duplicateDismissed by mutableStateOf(duplicateDismissed)
    val date: LocalDate get() = LocalDate.parse(dateIso)

    fun matches(session: String, dialog: String) = sessionId == session && dialogId == dialog

    companion object {
        val saver = Saver<ReadingDraft, ArrayList<String>>(
            save = { arrayListOf(it.sessionId, it.dialogId, it.dateIso, it.value, it.choosingDate.toString(), it.duplicateDismissed.toString()) },
            restore = { ReadingDraft(it[0], it[1], it[2], it[3], it[4].toBoolean(), it[5].toBoolean()) },
        )
    }
}
