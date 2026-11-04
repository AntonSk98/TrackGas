package com.trackgas.app

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.trackgas.app.tracking.TrackerViewModel

/** Owns SAF picker registration and result delivery for CSV import/export. */
internal class CsvDocumentPickerCoordinator(
    private val registry: ActivityResultRegistry,
    private val contentResolver: ContentResolver,
    private val tracker: TrackerViewModel,
) {
    private val pendingExports = mutableSetOf<String>()
    private val pendingImports = mutableSetOf<String>()
    private val exportLaunchers = mutableMapOf<String, ActivityResultLauncher<String>>()
    private val importLaunchers = mutableMapOf<String, ActivityResultLauncher<Array<String>>>()

    fun restore(savedInstanceState: Bundle?) {
        savedInstanceState?.getStringArrayList(PENDING_EXPORTS)?.toList()?.forEach { requestId ->
            pendingExports.add(requestId)
            registerExportPicker(requestId)
        }
        savedInstanceState?.getStringArrayList(PENDING_IMPORTS)?.toList()?.forEach { requestId ->
            pendingImports.add(requestId)
            registerImportPicker(requestId)
        }
    }

    fun startExport() {
        val requestId = tracker.requestExport() ?: return
        pendingExports.add(requestId)
        val picker = registerExportPicker(requestId)
        try {
            picker.launch("trackgas-readings.csv")
        } catch (_: ActivityNotFoundException) {
            failExportPicker(requestId)
        } catch (_: SecurityException) {
            failExportPicker(requestId)
        }
    }

    fun startImport() {
        val requestId = tracker.requestCsvImport() ?: return
        pendingImports.add(requestId)
        val picker = registerImportPicker(requestId)
        try {
            picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
        } catch (_: ActivityNotFoundException) {
            failImportPicker(requestId)
        } catch (_: SecurityException) {
            failImportPicker(requestId)
        }
    }

    fun saveState(outState: Bundle) {
        outState.putStringArrayList(PENDING_EXPORTS, ArrayList(pendingExports))
        outState.putStringArrayList(PENDING_IMPORTS, ArrayList(pendingImports))
    }

    private fun registerExportPicker(requestId: String): ActivityResultLauncher<String> {
        val launcher = registry.register(
            "csv_export_$requestId",
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            pendingExports.remove(requestId)
            exportLaunchers.remove(requestId)?.unregister()
            if (uri == null) tracker.cancelExport(requestId)
            else tracker.writeExport(requestId) { contentResolver.openOutputStream(uri, "w") }
        }
        if (requestId in pendingExports) exportLaunchers[requestId] = launcher
        else launcher.unregister()
        return launcher
    }

    private fun registerImportPicker(requestId: String): ActivityResultLauncher<Array<String>> {
        val launcher = registry.register(
            "csv_import_$requestId",
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            pendingImports.remove(requestId)
            importLaunchers.remove(requestId)?.unregister()
            if (uri == null) tracker.cancelCsvSelection(requestId)
            else tracker.readCsvImport(requestId) { contentResolver.openInputStream(uri) }
        }
        if (requestId in pendingImports) importLaunchers[requestId] = launcher
        else launcher.unregister()
        return launcher
    }

    private fun failExportPicker(requestId: String) {
        pendingExports.remove(requestId)
        exportLaunchers.remove(requestId)?.unregister()
        tracker.beginExportWrite(requestId)
        tracker.finishExportWrite(requestId, false)
    }

    private fun failImportPicker(requestId: String) {
        pendingImports.remove(requestId)
        importLaunchers.remove(requestId)?.unregister()
        tracker.failCsvPicker(requestId)
    }

    private companion object {
        const val PENDING_EXPORTS = "pending_csv_pickers"
        const val PENDING_IMPORTS = "pending_csv_import_pickers"
    }
}
