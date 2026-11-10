package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.LocalDate
import java.time.LocalTime
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
 * publishing it. The whole form scrolls within safe/IME bounds so a landscape
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
    onSave: (LocalDate?, String, String?, String, java.time.ZoneId) -> Unit,
    onDismiss: () -> Unit,
    tracker: TrackerData = TrackerData(readings),
) {
    val reading = (dialog.target as? ReadingTarget.Edit)?.reading
    val initialDate = when (val target = dialog.target) {
        is ReadingTarget.Add -> target.openingDate
        is ReadingTarget.Edit -> target.reading.date
    }
    val zone = when (val target = dialog.target) {
        is ReadingTarget.Add -> target.zone
        is ReadingTarget.Edit -> target.reading.zone
    }
    val initialTime = when (val target = dialog.target) {
        is ReadingTarget.Add -> target.observedAt.atZone(zone).toLocalTime()
        is ReadingTarget.Edit -> target.reading.localTime
    }
    fun newDraft() = ReadingDraft(sessionId, dialog.dialogId, initialDate.toString(), reading?.let {
        numberText(it.cubicMetres, locale, raw = true)
    } ?: "", reading?.note.orEmpty(), time = initialTime.toString(), timezone = zone.id)
    var restoredDraft by rememberSaveable(stateSaver = ReadingDraft.saver) { mutableStateOf(newDraft()) }
    // rememberSaveable inputs alone do not validate identities restored from a saved registry.
    if (!restoredDraft.matches(sessionId, dialog.dialogId)) restoredDraft = newDraft()
    val draft = restoredDraft
    val date = draft.date
    val selectedTime = LocalTime.parse(draft.time)
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
        displayedTime = timeText(selectedTime, locale),
        timezone = draft.timezone,
        onChooseTime = { draft.choosingTime = true },
        note = draft.note,
        errors = errors.copy(date = errors.date.takeUnless { duplicate }),
        pending = pending,
        failed = dialog.phase is SavePhase.Failed,
        loadState = loadState,
        onValueChange = { draft.value = it; onClearError() },
        onNoteChange = { draft.note = it; onClearError() },
        onChooseDate = { draft.choosingDate = true },
        onRetryLoad = onRetryLoad,
        onSave = {
            draft.duplicateDismissed = false
            onSave(reading?.date ?: date, draft.value, reading?.let { draft.note.takeUnless(String::isBlank) },
                draft.time, java.time.ZoneId.of(draft.timezone))
        },
        onDismiss = onDismiss,
    )
    if (reading == null && showPicker) {
        AddReadingDatePicker(
            date, today, tracker, locale,
            onDismiss = { draft.choosingDate = false; draft.duplicateDismissed = true },
            onConfirm = {
                draft.dateIso = it.toString()
                onClearError()
                draft.choosingDate = false
                draft.duplicateDismissed = true
            },
        )
    }
    if (draft.choosingTime) {
        ReadingTimePicker(
            time = selectedTime,
            locale = locale,
            onDismiss = { draft.choosingTime = false },
            onConfirm = {
                draft.time = it.toString()
                draft.choosingTime = false
                onClearError()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReadingTimePicker(
    time: LocalTime,
    locale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    var input by rememberSaveable { mutableStateOf(formatTimePickerInput(time)) }
    var lastSelection by rememberSaveable { mutableStateOf(formatTimePickerInput(time)) }
    val parsedInput = remember(input) { parseTimePickerInput(input) }
    val fontScale = LocalDensity.current.fontScale
    ResponsiveDialog(
        onDismissRequest = onDismiss,
    ) {
        val presentation = timePickerPresentation(
            (maxWidth - 48.dp).value.coerceAtLeast(0f),
            // Reserve title/actions and spacing at the user's font size.
            (maxHeight - 48.dp).value - 128f * fontScale,
            fontScale,
        )
        LaunchedEffect(picker.hour, picker.minute) {
            val selection = LocalTime.of(picker.hour, picker.minute)
            input = timePickerInputAfterSelection(input, lastSelection, selection)
            lastSelection = formatTimePickerInput(selection)
        }
        ResponsiveDialogContent {
            Text(
                stringResource(R.string.observation_time),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (presentation == TimePickerPresentation.INPUT) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { changed ->
                        input = changed
                        parseTimePickerInput(changed)?.let {
                            lastSelection = formatTimePickerInput(it)
                            picker.hour = it.hour
                            picker.minute = it.minute
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.observation_time)) },
                    placeholder = { Text(timeText(time, locale)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    isError = parsedInput == null,
                    supportingText = {
                        if (parsedInput == null) Text(stringResource(R.string.invalid_time))
                    },
                )
            } else {
                TimePicker(
                    state = picker,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    layoutType = if (presentation == TimePickerPresentation.HORIZONTAL)
                        TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical,
                )
            }
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = presentation != TimePickerPresentation.INPUT || parsedInput != null,
                    onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) },
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReadingDatePicker(
    date: LocalDate,
    today: LocalDate,
    tracker: TrackerData,
    locale: Locale,
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
    ResponsiveDialog(
        onDismissRequest = onDismiss,
    ) {
        val calendarWidth = maxOf(360.dp, maxWidth)
        val horizontalScroll = rememberScrollState()
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pickerDateError?.let { DateError(it, Modifier.padding(horizontal = 24.dp)) }
            Box(Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)) {
                DatePicker(
                    picker,
                    modifier = Modifier.width(calendarWidth),
                    title = { Text(stringResource(R.string.date), Modifier.padding(16.dp)) },
                    headline = {
                        picker.selectedDateMillis?.let {
                            Text(
                                dateText(pickerDate(it), locale),
                                Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    },
                    showModeToggle = false,
                )
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = picker.selectedDateMillis != null && pickerDateError == null,
                    onClick = { picker.selectedDateMillis?.let { onConfirm(pickerDate(it)) } },
                ) { Text(stringResource(R.string.confirm_date)) }
            }
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
    ReadingError.TIME_REQUIRED -> R.string.time_required
    ReadingError.INVALID_TIME -> R.string.invalid_time
    ReadingError.FUTURE_TIME -> R.string.future_time
    ReadingError.TIME_ORDER -> R.string.time_order
}
