package com.trackgas.app.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.trackgas.app.R
import java.time.LocalDate
import java.util.Locale

/**
 * Shared Add/Edit dialog that owns an unsaved draft, never the authoritative reading list.
 *
 * A null [reading] selects Add; otherwise the original date and full-precision meter
 * value are prefilled. [initialDate] is captured when the form opens. Picker dates
 * are converted through UTC helpers, while [today] disables future selections.
 * [readings] supplies the authoritative snapshot for immediate date feedback.
 *
 * [onSave] validates and publishes through the ViewModel, returning inline field errors.
 * Only an error-free save closes the form. Cancel/dismiss discards the draft without
 * publishing it. Compact-height layouts scroll the whole form so a landscape
 * keyboard cannot squeeze a field behind a pinned action row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReadingForm(
    reading: Reading?,
    readings: List<Reading>,
    initialDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSave: (LocalDate?, String) -> ReadingErrors,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf<LocalDate?>(reading?.date ?: initialDate) }
    var value by remember { mutableStateOf(reading?.let { numberText(it.cubicMetres, locale, raw = true) } ?: "") }
    var errors by remember { mutableStateOf(ReadingErrors()) }
    var choosingDate by remember { mutableStateOf(false) }
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            })
            Surface(
                modifier = Modifier.safeDrawingPadding().imePadding()
                    .padding(if (compactHeight) 8.dp else 16.dp)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                ) {
                    Column(
                        modifier = (if (compactHeight) Modifier else Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()))
                            .padding(horizontal = 24.dp, vertical = if (compactHeight) 8.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 12.dp),
                    ) {
                        Text(
                            stringResource(if (reading == null) R.string.add_reading else R.string.edit_reading),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        val displayedDate = (reading?.date ?: date)?.let { dateText(it, locale) }
                            ?: stringResource(R.string.date_required)
                        Box {
                            OutlinedTextField(
                                value = displayedDate,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth().then(
                                    if (reading == null) Modifier.clearAndSetSemantics {} else Modifier,
                                ),
                                label = { Text(stringResource(R.string.date)) },
                                readOnly = true,
                                singleLine = true,
                            )
                            if (reading == null) {
                                val chooseDateLabel = stringResource(R.string.choose_date, displayedDate)
                                Box(
                                    Modifier.matchParentSize()
                                        .clickable(role = Role.Button) { choosingDate = true }
                                        .semantics { contentDescription = chooseDateLabel },
                                )
                            }
                        }
                        errors.date?.let { DateError(it) }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it; errors = errors.copy(value = null) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.value_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = errors.value != null,
                            supportingText = errors.value?.let { error ->
                                { Text(stringResource(errorResource(error))) }
                            },
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = if (compactHeight) 4.dp else 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            errors = onSave(reading?.date ?: date, value)
                            if (reading == null && errors.date == ReadingError.DUPLICATE_DATE) {
                                errors = errors.copy(date = null)
                                choosingDate = true
                            } else if (errors.isEmpty) {
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
    if (reading == null && choosingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date?.toPickerMillis(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = pickerDate(utcTimeMillis) <= today
                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                }
            },
        )
        val pickerDateError = picker.selectedDateMillis?.let {
            validateReadingDate(readings, reading?.id, pickerDate(it), today)
        }
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null && pickerDateError == null,
                    onClick = {
                        date = picker.selectedDateMillis?.let(::pickerDate)
                        errors = errors.copy(date = null)
                        choosingDate = false
                    },
                ) { Text(stringResource(R.string.confirm_date)) }
            },
            dismissButton = { TextButton(onClick = { choosingDate = false }) { Text(stringResource(R.string.cancel)) } },
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
}

@Composable
private fun DateError(error: ReadingError, modifier: Modifier = Modifier) {
    Text(
        stringResource(errorResource(error)),
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun errorResource(error: ReadingError): Int = when (error) {
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
