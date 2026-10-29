package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.LocalDate
import java.util.Locale

/**
 * Shared Add/Edit dialog that owns an unsaved draft, never the authoritative reading list.
 *
 * The semantic [dialog] selects Add or Edit. Picker dates
 * are converted through UTC helpers, while [today] disables future selections.
 * [readings] supplies the authoritative snapshot for immediate date feedback.
 *
 * [onSave] starts an asynchronous operation; committed success closes the form.
 * Cancel/dismiss discards the draft without
 * publishing it. Compact-height layouts scroll the whole form so a landscape
 * keyboard cannot squeeze a field behind a pinned action row.
 */
@Composable
internal fun ReadingForm(
    sessionId: String,
    dialog: ReadingDialog.Editing,
    readings: List<Reading>,
    today: LocalDate,
    locale: Locale,
    loadState: ReadingLoadState,
    onRetryLoad: () -> Unit,
    onClearError: () -> Unit,
    onSave: (LocalDate?, String) -> Unit,
    onDismiss: () -> Unit,
    tracker: TrackerData = TrackerData(readings),
) {
    val reading = (dialog.target as? ReadingTarget.Edit)?.reading
    val initialDate = when (val target = dialog.target) {
        is ReadingTarget.Add -> target.openingDate
        is ReadingTarget.Edit -> target.reading.date
    }
    fun newDraft() = ReadingDraft(sessionId, dialog.dialogId, initialDate.toString(), reading?.let {
        numberText(it.cubicMetres, locale, raw = true)
    } ?: "")
    var restoredDraft by rememberSaveable(stateSaver = ReadingDraft.saver) { mutableStateOf(newDraft()) }
    // rememberSaveable inputs alone do not validate identities restored from a saved registry.
    if (!restoredDraft.matches(sessionId, dialog.dialogId)) restoredDraft = newDraft()
    val draft = restoredDraft
    val date = draft.date
    val pending = dialog.pending
    val errors = when (val phase = dialog.phase) {
        is SavePhase.Invalid -> phase.errors
        else -> ReadingErrors()
    }
    val duplicate = reading == null && errors.date == ReadingError.DUPLICATE_DATE
    val showPicker = draft.choosingDate || (duplicate && !draft.duplicateDismissed)
    ReadingFormLayout(
        editing = reading != null,
        displayedDate = dateText(reading?.date ?: date, locale),
        value = draft.value,
        errors = errors.copy(date = errors.date.takeUnless { duplicate }),
        pending = pending,
        failed = dialog.phase is SavePhase.Failed,
        loadState = loadState,
        onValueChange = { draft.value = it; onClearError() },
        onChooseDate = { draft.choosingDate = true },
        onRetryLoad = onRetryLoad,
        onSave = {
            draft.duplicateDismissed = false
            onSave(reading?.date ?: date, draft.value)
        },
        onDismiss = onDismiss,
    )
    if (reading == null && showPicker) {
        AddReadingDatePicker(
            date, today, tracker,
            onDismiss = { draft.choosingDate = false; draft.duplicateDismissed = true },
            onConfirm = {
                draft.dateIso = it.toString()
                onClearError()
                draft.choosingDate = false
                draft.duplicateDismissed = true
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReadingDatePicker(
    date: LocalDate,
    today: LocalDate,
    tracker: TrackerData,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.toPickerMillis(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = pickerDate(utcTimeMillis) <= today
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        },
    )
    val pickerDateError = picker.selectedDateMillis?.let {
        validateReadingDate(tracker, null, pickerDate(it), today)
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null && pickerDateError == null,
                onClick = { picker.selectedDateMillis?.let { onConfirm(pickerDate(it)) } },
            ) { Text(stringResource(R.string.confirm_date)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        Column {
            pickerDateError?.let {
                DateError(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            DatePicker(
                picker,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
internal fun DateError(error: ReadingError, modifier: Modifier = Modifier) {
    Text(
        stringResource(errorResource(error)),
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

internal fun errorResource(error: ReadingError): Int = when (error) {
    ReadingError.DATE_REQUIRED -> R.string.date_required
    ReadingError.FUTURE_DATE -> R.string.future_date
    ReadingError.DUPLICATE_DATE -> R.string.duplicate_date
    ReadingError.VALUE_REQUIRED -> R.string.value_required
    ReadingError.INVALID_NUMBER -> R.string.invalid_number
    ReadingError.NEGATIVE_VALUE -> R.string.negative_value
    ReadingError.BELOW_PREVIOUS -> R.string.below_previous
    ReadingError.ABOVE_NEXT -> R.string.above_next
    ReadingError.MISSING_READING -> R.string.reading_missing
}
