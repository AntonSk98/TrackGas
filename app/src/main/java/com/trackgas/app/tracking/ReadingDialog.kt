package com.trackgas.app.tracking

import java.time.LocalDate

sealed interface ReadingTarget {
    data class Add(val openingDate: LocalDate) : ReadingTarget
    data class Edit(val reading: Reading) : ReadingTarget
}

sealed interface SavePhase {
    data object Ready : SavePhase
    data class Pending(val requestId: String) : SavePhase
    data class Invalid(val errors: ReadingErrors) : SavePhase
    data class Failed(val failure: ReadingFailure) : SavePhase
}

sealed interface DeletePhase {
    data object Ready : DeletePhase
    data class Pending(val requestId: String) : DeletePhase
    data object Missing : DeletePhase
    data object Protected : DeletePhase
    data class Failed(val failure: ReadingFailure) : DeletePhase
}

sealed interface ReplacementPhase {
    data object Ready : ReplacementPhase
    data class Pending(val requestId: String) : ReplacementPhase
    data class Unavailable(val error: ReplacementError) : ReplacementPhase
    data class Failed(val failure: ReadingFailure) : ReplacementPhase
}

sealed interface ReadingDialog {
    data object Closed : ReadingDialog
    data class Editing(val dialogId: String, val target: ReadingTarget, val phase: SavePhase = SavePhase.Ready) : ReadingDialog
    data class ConfirmingDeletion(val dialogId: String, val reading: Reading, val phase: DeletePhase = DeletePhase.Ready) : ReadingDialog
    data class Replacing(val dialogId: String, val target: ReplacementTarget, val phase: ReplacementPhase = ReplacementPhase.Ready) : ReadingDialog
}

val ReadingDialog.pending: Boolean
    get() = when (this) {
        ReadingDialog.Closed -> false
        is ReadingDialog.Editing -> phase is SavePhase.Pending
        is ReadingDialog.ConfirmingDeletion -> phase is DeletePhase.Pending
        is ReadingDialog.Replacing -> phase is ReplacementPhase.Pending
    }

data class ReadingRequest(val sessionId: String, val dialogId: String, val requestId: String)

internal fun ReadingDialog.completeSave(request: ReadingRequest, sessionId: String, phase: SavePhase?): ReadingDialog =
    if (sessionId == request.sessionId && this is ReadingDialog.Editing &&
        dialogId == request.dialogId && this.phase == SavePhase.Pending(request.requestId)
    ) {
        if (phase == null) ReadingDialog.Closed else copy(phase = phase)
    } else this

internal fun ReadingDialog.completeDelete(request: ReadingRequest, sessionId: String, phase: DeletePhase?): ReadingDialog =
    if (sessionId == request.sessionId && this is ReadingDialog.ConfirmingDeletion &&
        dialogId == request.dialogId && this.phase == DeletePhase.Pending(request.requestId)
    ) {
        if (phase == null) ReadingDialog.Closed else copy(phase = phase)
    } else this

internal fun ReadingDialog.completeReplacement(request: ReadingRequest, sessionId: String, phase: ReplacementPhase?): ReadingDialog =
    if (sessionId == request.sessionId && this is ReadingDialog.Replacing &&
        dialogId == request.dialogId && this.phase == ReplacementPhase.Pending(request.requestId)
    ) {
        if (phase == null) ReadingDialog.Closed else copy(phase = phase)
    } else this
