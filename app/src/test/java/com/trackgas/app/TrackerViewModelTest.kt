package com.trackgas.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.trackgas.app.tracking.*
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-26")
    private val one = Reading("one", today.minusDays(3), "100.0001".toBigDecimal())
    private val two = Reading("two", today, "112.1250".toBigDecimal())

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun transitionIdentityRejectsStaleSessionDialogRequestAndWrongAction() {
        val request = ReadingRequest("session", "dialog", "request")
        val editing = ReadingDialog.Editing("dialog", ReadingTarget.Add(today), SavePhase.Pending("request"))
        assertEquals(ReadingDialog.Closed, editing.completeSave(request, "session", null))
        for (stale in listOf(request.copy(sessionId = "old"), request.copy(dialogId = "old"), request.copy(requestId = "old"))) {
            assertSame(editing, editing.completeSave(stale, "session", SavePhase.Invalid(ReadingErrors(value = ReadingError.INVALID_NUMBER))))
        }
        assertSame(editing, editing.completeDelete(request, "session", null))
        val deleting = ReadingDialog.ConfirmingDeletion("dialog", one, DeletePhase.Pending("request"))
        assertSame(deleting, deleting.completeSave(request, "session", null))
        assertEquals(ReadingDialog.Closed, deleting.completeDelete(request, "session", null))
        assertEquals(deleting.copy(phase = DeletePhase.Missing), deleting.completeDelete(request, "session", DeletePhase.Missing))
    }

    @Test fun loadingBlocksOpeningAndSubmissionUntilAnExplicitSnapshot() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        vm.openAdd()
        vm.openEdit(one)
        vm.openDelete(one)
        vm.save(today, "110", Locale.US)
        vm.delete()
        runCurrent()
        assertEquals(ReadingLoadState.LOADING, vm.state.loadState)
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        assertTrue(repo.saves.isEmpty())
        assertTrue(repo.deletes.isEmpty())
        repo.deliver(listOf(one))
        runCurrent()
        vm.openAdd()
        assertEquals(ReadingTarget.Add(today), (vm.state.dialog as ReadingDialog.Editing).target)
    }

    @Test fun dialogsAreExclusiveErrorsBelongToTheirTargetAndOpeningResetsThem() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.openEdit(one)
        val edit = vm.state.dialog as ReadingDialog.Editing
        repo.saveOutcomes.send(Result.success(SaveReadingResult.Invalid(ReadingErrors(value = ReadingError.BELOW_PREVIOUS))))
        vm.save(today, "99", Locale.US); runCurrent()
        assertEquals(edit.copy(phase = SavePhase.Invalid(ReadingErrors(value = ReadingError.BELOW_PREVIOUS))), vm.state.dialog)
        vm.clearDialogError()
        assertEquals(edit, vm.state.dialog)
        vm.openDelete(one)
        assertTrue(vm.state.dialog is ReadingDialog.ConfirmingDeletion)
        repo.deleteOutcomes.send(Result.success(DeleteReadingResult.Missing))
        vm.delete(); runCurrent()
        assertEquals(DeletePhase.Missing, (vm.state.dialog as ReadingDialog.ConfirmingDeletion).phase)
        vm.dismissDialog()
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        vm.openAdd()
        val add = vm.state.dialog as ReadingDialog.Editing
        assertNotEquals(edit.dialogId, add.dialogId)
        assertEquals(SavePhase.Ready, add.phase)
    }

    @Test fun pendingSaveBlocksSwitchingDismissalErrorClearingAndDuplicateMutations() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.selectTab(TrackerTab.HISTORY); vm.selectPeriod(PeriodType.MONTH); vm.navigate(-1)
        val before = vm.state
        vm.openAdd(); vm.save(today, "112.1250", Locale.US); runCurrent()
        val pending = vm.state.dialog
        assertTrue(pending.pending)
        vm.openEdit(one); vm.openDelete(one); vm.openAdd()
        vm.dismissDialog(); vm.clearDialogError()
        vm.save(today, "999", Locale.US); vm.delete(); runCurrent()
        assertEquals(pending, vm.state.dialog)
        assertEquals(1, repo.saves.size)
        assertTrue(repo.deletes.isEmpty())
        assertSame(before.dashboard, vm.state.dashboard)
        repo.saveOutcomes.send(Result.success(SaveReadingResult.Saved(CommittedSnapshot(2, listOf(two, one))))); runCurrent()
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        assertEquals(listOf(two, one), vm.state.readings)
        assertEquals(before.period, vm.state.period)
        assertEquals(before.tab, vm.state.tab)
        assertEquals(dashboard(vm.state.readings, before.period), vm.state.dashboard)
    }

    @Test fun pendingDeletionBlocksOverlapAndSuccessClosesWithoutAConsumableGate() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(two, one)); runCurrent()
        vm.openDelete(two); vm.delete(); runCurrent()
        val pending = vm.state.dialog
        vm.dismissDialog(); vm.openAdd(); vm.delete(); vm.save(today, "1", Locale.US); runCurrent()
        assertEquals(pending, vm.state.dialog)
        assertEquals(listOf("two"), repo.deletes)
        assertTrue(repo.saves.isEmpty())
        repo.deleteOutcomes.send(Result.success(DeleteReadingResult.Deleted(CommittedSnapshot(2, listOf(one))))); runCurrent()
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        assertEquals(listOf(one), vm.state.readings)
        assertNull(vm.state.dashboard.total.cubicMetres)
        assertTrue(vm.state.dashboard.buckets.isEmpty())
        vm.openAdd()
        assertTrue(vm.state.dialog is ReadingDialog.Editing)
    }

    @Test fun failedAddEditDeleteRetainTargetProjectionAndCanRetry() = runTest {
        for (target in listOf<ReadingTarget>(ReadingTarget.Add(today), ReadingTarget.Edit(two))) {
            val repo = ScriptedReadingRepository()
            val vm = TrackerViewModel(repo) { today }
            repo.deliver(listOf(two, one)); runCurrent()
            vm.selectPeriod(PeriodType.MONTH)
            if (target is ReadingTarget.Add) vm.openAdd() else vm.openEdit(two)
            val dialog = vm.state.dialog as ReadingDialog.Editing
            val before = vm.state
            repo.saveOutcomes.send(Result.failure(ReadingStorageException(java.io.IOException("private"))))
            vm.save(today, "115.2500", Locale.US); runCurrent()
            assertEquals(dialog.copy(phase = SavePhase.Failed(ReadingFailure(ReadingFailureCategory.STORAGE_ACCESS, ReadingAction.SAVE))), vm.state.dialog)
            assertEquals(before.readings, vm.state.readings)
            assertSame(before.dashboard, vm.state.dashboard)
            val changed = two.copy(cubicMetres = "115.2500".toBigDecimal())
            repo.saveOutcomes.send(Result.success(SaveReadingResult.Saved(CommittedSnapshot(2, listOf(changed, one)))))
            vm.save(today, "115.2500", Locale.US); runCurrent()
            assertEquals(ReadingDialog.Closed, vm.state.dialog)
            assertEquals(changed.cubicMetres, vm.state.readings.first().cubicMetres)
            vm.openDelete(changed)
            val deletion = vm.state.dialog as ReadingDialog.ConfirmingDeletion
            val projection = vm.state.dashboard
            repo.deleteOutcomes.send(Result.failure(ReadingStorageException(java.io.IOException())))
            vm.delete(); runCurrent()
            assertEquals(deletion.copy(phase = DeletePhase.Failed(ReadingFailure(ReadingFailureCategory.STORAGE_ACCESS, ReadingAction.DELETE))), vm.state.dialog)
            assertSame(projection, vm.state.dashboard)
            repo.deleteOutcomes.send(Result.success(DeleteReadingResult.Deleted(CommittedSnapshot(3, listOf(one)))))
            vm.delete(); runCurrent()
            assertEquals(ReadingDialog.Closed, vm.state.dialog)
            assertEquals(listOf(one), vm.state.readings)
        }
    }

    @Test fun invalidAndMissingResultsLeaveCommittedDataAndProjectionUntouched() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(two, one)); runCurrent()
        val before = vm.state
        for (error in listOf(ReadingErrors(date = ReadingError.DUPLICATE_DATE), ReadingErrors(value = ReadingError.BELOW_PREVIOUS),
            ReadingErrors(value = ReadingError.ABOVE_NEXT), ReadingErrors(value = ReadingError.MISSING_READING))) {
            vm.openEdit(two)
            repo.saveOutcomes.send(Result.success(SaveReadingResult.Invalid(error)))
            vm.save(today, "99", Locale.US); runCurrent()
            assertEquals(SavePhase.Invalid(error), (vm.state.dialog as ReadingDialog.Editing).phase)
            assertEquals(before.readings, vm.state.readings)
            assertSame(before.dashboard, vm.state.dashboard)
        }
        vm.openDelete(two)
        repo.deleteOutcomes.send(Result.success(DeleteReadingResult.Missing))
        vm.delete(); runCurrent()
        assertEquals(DeletePhase.Missing, (vm.state.dialog as ReadingDialog.ConfirmingDeletion).phase)
        assertSame(before.dashboard, vm.state.dashboard)
    }

    @Test fun loadRetryRetainsDialogAndCommittedDataAndBlocksSubmission() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.openEdit(one)
        val before = vm.state
        repo.failObservation(); runCurrent()
        assertEquals(ReadingLoadState.ERROR, vm.state.loadState)
        assertEquals(before.dialog, vm.state.dialog)
        assertSame(before.dashboard, vm.state.dashboard)
        vm.save(today, "101", Locale.US); runCurrent()
        assertTrue(repo.saves.isEmpty())
        vm.retryLoad(); runCurrent()
        assertEquals(ReadingLoadState.LOADING, vm.state.loadState)
        vm.save(today, "101", Locale.US)
        repo.deliver(listOf(one)); runCurrent()
        assertEquals(ReadingLoadState.READY, vm.state.loadState)
        assertEquals(before.dialog, vm.state.dialog)
        assertEquals(2, repo.subscriptions)
    }

    @Test fun navigationAndFreshSessionDefaultsDoNotChangeCommittedReadings() = runTest {
        val repo = ScriptedReadingRepository()
        val store = ViewModelStore()
        val factory = TrackerViewModel.factory(repo)
        try {
            val old = ViewModelProvider(store, factory)[TrackerViewModel::class.java]
            repo.deliver(listOf(two, one)); runCurrent()
            old.navigate(-12); old.selectPeriod(PeriodType.MONTH)
            assertEquals(CalendarPeriod.containing(PeriodType.MONTH, old.today()), old.state.period)
            old.navigate(-1)
            val selected = old.state
            repeat(10) {
                for (tab in listOf(TrackerTab.HISTORY, TrackerTab.HISTORY, TrackerTab.DASHBOARD)) {
                    old.selectTab(tab)
                    assertEquals(selected.period, old.state.period)
                    assertSame(selected.dashboard, old.state.dashboard)
                    assertEquals(selected.readings, old.state.readings)
                }
            }
            old.selectTab(TrackerTab.HISTORY); old.openEdit(two)
            assertSame(old, ViewModelProvider(store, factory)[TrackerViewModel::class.java])
            assertEquals(1, repo.subscriptions)
            val freshRepo = ScriptedReadingRepository()
            val fresh = TrackerViewModel(freshRepo) { today }
            freshRepo.deliver(listOf(two, one)); runCurrent()
            assertEquals(old.state.readings, fresh.state.readings)
            assertNotEquals(old.state.sessionId, fresh.state.sessionId)
            assertEquals(TrackerTab.DASHBOARD, fresh.state.tab)
            assertEquals(CalendarPeriod.containing(PeriodType.WEEK, today), fresh.state.period)
            assertEquals(ReadingDialog.Closed, fresh.state.dialog)
        } finally { store.clear() }
    }

    @Test fun dateProviderIsCapturedForOpeningButReevaluatedAtSubmissionAndPeriodSelection() = runTest {
        var date = LocalDate.parse("2026-10-31")
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { date }
        repo.deliver(emptyList()); runCurrent()
        vm.openAdd()
        date = date.plusDays(1)
        assertEquals(ReadingTarget.Add(date.minusDays(1)), (vm.state.dialog as ReadingDialog.Editing).target)
        vm.selectPeriod(PeriodType.MONTH)
        assertEquals(date, vm.state.period.start)
        vm.save(date, "100.0100", Locale.GERMANY); runCurrent()
        assertEquals(date, repo.saves.single().today)
        assertEquals(ParsedMeter.Value("100.0100".toBigDecimal()), repo.saves.single().meter)
        repo.saveOutcomes.send(Result.success(SaveReadingResult.Invalid(ReadingErrors(date = ReadingError.FUTURE_DATE))))
        runCurrent()
        assertEquals(SavePhase.Invalid(ReadingErrors(date = ReadingError.FUTURE_DATE)), (vm.state.dialog as ReadingDialog.Editing).phase)
    }

    @Test fun defaultDateTracksTimezoneChangesWithoutRecreation() = runTest {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val repo = ScriptedReadingRepository()
            val vm = TrackerViewModel(repo)
            repo.deliver(listOf(one)); runCurrent()
            val easternToday = vm.today()
            vm.selectTab(TrackerTab.HISTORY)
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(easternToday.minusDays(1), vm.today())
            vm.selectPeriod(PeriodType.WEEK)
            assertEquals(CalendarPeriod.containing(PeriodType.WEEK, vm.today()), vm.state.period)
            assertEquals(TrackerTab.HISTORY, vm.state.tab)
            assertEquals(listOf(one), vm.state.readings)
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun newerOtherSessionObservationWinsOverOlderSuccessfulResultAndLateObservation() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.selectTab(TrackerTab.HISTORY); vm.selectPeriod(PeriodType.MONTH); vm.navigate(-1)
        val period = vm.state.period
        vm.openAdd(); vm.save(today, "112.1250", Locale.US); runCurrent()
        val newest = two.copy(cubicMetres = "115.0000".toBigDecimal())
        repo.deliver(listOf(one, newest), 3); runCurrent()
        val projection = vm.state.dashboard
        repo.saveOutcomes.send(Result.success(SaveReadingResult.Saved(CommittedSnapshot(2, listOf(two, one))))); runCurrent()
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        assertEquals(listOf(newest, one), vm.state.readings)
        assertSame(projection, vm.state.dashboard)
        repo.deliver(listOf(one), 1); runCurrent()
        assertSame(projection, vm.state.dashboard)
        assertEquals(period, vm.state.period)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        assertEquals(1, repo.subscriptions)
        vm.openEdit(newest)
        repo.deliver(listOf(two, one), 2); runCurrent()
        assertEquals(SavePhase.Ready, (vm.state.dialog as ReadingDialog.Editing).phase)
    }

    @Test fun observationBeforeResultAndDuplicateUnorderedRowsKeepProjectionIdentityAndWatermark() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.openAdd(); vm.save(today, "112.1250", Locale.US); runCurrent()
        repo.deliver(listOf(two, one), 2); runCurrent()
        val projection = vm.state.dashboard
        repo.saveOutcomes.send(Result.success(SaveReadingResult.Saved(CommittedSnapshot(2, listOf(two, one))))); runCurrent()
        assertEquals(ReadingDialog.Closed, vm.state.dialog)
        assertSame(projection, vm.state.dashboard)
        repo.deliver(listOf(one, two), 5); runCurrent()
        assertSame(projection, vm.state.dashboard)
        repo.deliver(listOf(one), 4); runCurrent()
        assertSame(projection, vm.state.dashboard)
        assertEquals(listOf(two, one), vm.state.readings)
        val scaleChanged = two.copy(cubicMetres = "112.12500".toBigDecimal())
        repo.deliver(listOf(scaleChanged, one), 6); runCurrent()
        assertEquals(5, vm.state.readings.first().cubicMetres.scale())
        assertNotSame(projection, vm.state.dashboard)
        val beforePeriodChange = vm.state.dashboard
        vm.selectPeriod(PeriodType.YEAR)
        assertNotSame(beforePeriodChange, vm.state.dashboard)
        assertEquals(1, repo.subscriptions)
    }

    @Test fun observationFailureDuringOrAfterSaveAndDeleteNeverRelabelsTheCommit() = runTest {
        for (deleting in listOf(false, true)) for (beforeResult in listOf(false, true)) {
            val repo = ScriptedReadingRepository()
            val vm = TrackerViewModel(repo) { today }
            repo.deliver(listOf(one)); runCurrent()
            if (deleting) { vm.openDelete(one); vm.delete() } else { vm.openAdd(); vm.save(today, "112.1250", Locale.US) }
            runCurrent()
            if (beforeResult) { repo.failObservation(); runCurrent() }
            val committed = if (deleting) emptyList() else listOf(two, one)
            if (deleting) repo.deleteOutcomes.send(Result.success(DeleteReadingResult.Deleted(CommittedSnapshot(2, committed))))
            else repo.saveOutcomes.send(Result.success(SaveReadingResult.Saved(CommittedSnapshot(2, committed))))
            runCurrent()
            if (!beforeResult) { repo.failObservation(); runCurrent() }
            assertEquals(ReadingLoadState.ERROR, vm.state.loadState)
            assertEquals(ReadingFailure(ReadingFailureCategory.STORAGE_ACCESS, ReadingAction.LOAD), vm.state.loadFailure)
            assertEquals(ReadingDialog.Closed, vm.state.dialog)
            assertEquals(committed, vm.state.readings)
            vm.openAdd(); vm.openDelete(one); vm.save(today, "999", Locale.US); vm.delete()
            assertEquals(ReadingDialog.Closed, vm.state.dialog)
            assertEquals(if (deleting) 0 else 1, repo.saves.size)
            assertEquals(if (deleting) 1 else 0, repo.deletes.size)
            vm.retryLoad(); runCurrent()
            val projection = vm.state.dashboard
            repo.deliver(committed, 3); runCurrent()
            assertEquals(ReadingLoadState.READY, vm.state.loadState)
            assertNull(vm.state.loadFailure)
            assertSame(projection, vm.state.dashboard)
            assertEquals(2, repo.subscriptions)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test fun retryGenerationRejectsAnOldCollectorsLateDelivery() = runTest {
        val script = ScriptedReadingRepository()
        val collectors = mutableListOf<FlowCollector<CommittedSnapshot>>()
        val events = Channel<Result<CommittedSnapshot>>(Channel.UNLIMITED)
        val repository = object : ReadingRepository by script {
            override fun observeReadings() = object : Flow<CommittedSnapshot> {
                override suspend fun collect(collector: FlowCollector<CommittedSnapshot>) {
                    collectors += collector
                    for (event in events) collector.emit(event.getOrThrow())
                }
            }
        }
        val vm = TrackerViewModel(repository) { today }
        events.send(Result.success(CommittedSnapshot(1, listOf(one)))); runCurrent()
        events.send(Result.failure(ReadingStorageException(java.io.IOException()))); runCurrent()
        vm.retryLoad(); runCurrent()
        events.send(Result.success(CommittedSnapshot(2, listOf(two, one)))); runCurrent()
        val before = vm.state
        collectors.first().emit(CommittedSnapshot(99, emptyList()))
        assertEquals(before, vm.state)
        vm.retryLoad(); runCurrent()
        assertEquals(2, collectors.size)
    }

    @Test fun failedMutationAndObservationRetainIndependentSafeCategoriesWithoutDiscardingTheTarget() = runTest {
        val repo = ScriptedReadingRepository()
        val vm = TrackerViewModel(repo) { today }
        repo.deliver(listOf(one)); runCurrent()
        vm.openEdit(one)
        val target = (vm.state.dialog as ReadingDialog.Editing).target
        val projection = vm.state.dashboard
        vm.save(today, "private raw input", Locale.US); runCurrent()
        repo.failObservation(); runCurrent()
        repo.saveOutcomes.send(Result.failure(ReadingStorageException(
            NumberFormatException("private row and SQL"), ReadingFailureCategory.STORED_DATA,
        ))); runCurrent()
        val dialog = vm.state.dialog as ReadingDialog.Editing
        assertEquals(target, dialog.target)
        assertEquals(ReadingFailure(ReadingFailureCategory.STORED_DATA, ReadingAction.SAVE), (dialog.phase as SavePhase.Failed).failure)
        assertEquals(ReadingFailure(ReadingFailureCategory.STORAGE_ACCESS, ReadingAction.LOAD), vm.state.loadFailure)
        assertSame(projection, vm.state.dashboard)
        assertEquals(listOf(one), vm.state.readings)
        vm.retryLoad(); runCurrent()
        repo.deliver(listOf(one), 2); runCurrent()
        assertEquals(dialog, vm.state.dialog)
        assertEquals(ReadingLoadState.READY, vm.state.loadState)
    }

    @Test fun storedCollectionLoadFailureAndRepeatedRetryRetainDataProjectionAndEitherDialog() = runTest {
        for (deleting in listOf(false, true)) {
            val repo = ScriptedReadingRepository()
            val vm = TrackerViewModel(repo) { today }
            repo.deliver(listOf(two, one)); runCurrent()
            vm.selectTab(TrackerTab.HISTORY)
            vm.selectPeriod(PeriodType.MONTH)
            if (deleting) vm.openDelete(two) else vm.openEdit(two)
            val before = vm.state
            repeat(3) { attempt ->
                if (attempt > 0) {
                    vm.retryLoad(); runCurrent()
                    assertEquals(ReadingLoadState.LOADING, vm.state.loadState)
                }
                repo.failObservation(ReadingStorageException(
                    IllegalArgumentException("Invalid stored reading collection"),
                    ReadingFailureCategory.STORED_DATA,
                ))
                runCurrent()
                assertEquals(ReadingLoadState.ERROR, vm.state.loadState)
                assertEquals(ReadingFailure(ReadingFailureCategory.STORED_DATA, ReadingAction.LOAD), vm.state.loadFailure)
                assertEquals(before.readings, vm.state.readings)
                assertSame(before.dashboard, vm.state.dashboard)
                assertEquals(before.dialog, vm.state.dialog)
                assertEquals(before.tab, vm.state.tab)
                assertEquals(before.period, vm.state.period)
                vm.save(today, "130", Locale.US); vm.delete(); runCurrent()
                assertTrue(repo.saves.isEmpty())
                assertTrue(repo.deletes.isEmpty())
            }
            assertEquals(3, repo.subscriptions)
            vm.retryLoad(); runCurrent()
            repo.deliver(before.readings, 2); runCurrent()
            assertEquals(ReadingLoadState.READY, vm.state.loadState)
            assertNull(vm.state.loadFailure)
            assertEquals(before.dialog, vm.state.dialog)
            assertSame(before.dashboard, vm.state.dashboard)
        }
    }

    @Test fun clearingStoreCancelsPendingRequestsWithoutSynthesizingAFailedPhase() = runTest {
        val repo = ScriptedReadingRepository()
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, TrackerViewModel.factory(repo))[TrackerViewModel::class.java]
        repo.deliver(listOf(one)); runCurrent()
        vm.openAdd(); vm.save(today, "110", Locale.US); runCurrent()
        val pending = vm.state.dialog
        store.clear(); runCurrent()
        assertEquals(pending, vm.state.dialog)
        assertTrue(vm.state.dialog.pending)
    }
}

/** Scripts only: never looks up targets, validates, generates IDs, sorts, or mutates records. */
private class ScriptedReadingRepository : ReadingRepository {
    private val observations = Channel<Result<CommittedSnapshot>>(Channel.UNLIMITED)
    val saveOutcomes = Channel<Result<SaveReadingResult>>(Channel.UNLIMITED)
    val deleteOutcomes = Channel<Result<DeleteReadingResult>>(Channel.UNLIMITED)
    val saves = mutableListOf<SaveReadingIntent>()
    val deletes = mutableListOf<String>()
    var subscriptions = 0

    suspend fun deliver(readings: List<Reading>, revision: Long = 1) = observations.send(Result.success(CommittedSnapshot(revision, readings)))
    suspend fun failObservation(error: ReadingStorageException = ReadingStorageException(java.io.IOException())) =
        observations.send(Result.failure(error))

    override fun observeReadings(): Flow<CommittedSnapshot> = flow {
        subscriptions++
        for (event in observations) emit(event.getOrThrow())
    }

    override suspend fun save(intent: SaveReadingIntent): SaveReadingResult {
        saves += intent
        return saveOutcomes.receive().getOrThrow()
    }

    override suspend fun delete(id: String): DeleteReadingResult {
        deletes += id
        return deleteOutcomes.receive().getOrThrow()
    }
}
