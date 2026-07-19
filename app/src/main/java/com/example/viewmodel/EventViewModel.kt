package com.example.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class DialogState {
    data class CheckInConfirmation(val guest: Guest) : DialogState()
    data class ResolveIssueConfirmation(val issue: OpenIssue) : DialogState()
    data class ReportIssueConfirmation(val description: String, val priority: String) : DialogState()
    data class StaffHelpConfirmation(val query: String) : DialogState()
    data class BroadcastConfirmation(val title: String, val message: String) : DialogState()
    data object SwitchRoleConfirmation : DialogState()
    data class MessageDialog(val title: String, val message: String) : DialogState()
}

enum class AppRole { ATTENDEE, ORGANIZER }

fun findSelfCheckInGuest(query: String, guests: List<Guest>): Guest? {
    val trimmed = query.trim()
    val digits = trimmed.filter(Char::isDigit)
    if (trimmed.isBlank()) return null

    return guests.firstOrNull { guest ->
        guest.name.equals(trimmed, ignoreCase = true) ||
            (digits.length >= 4 && guest.phone.filter(Char::isDigit).endsWith(digits))
    }
}

fun validateBroadcast(title: String, message: String): String? = when {
    title.isBlank() -> "Add a short title."
    message.isBlank() -> "Add a message for attendees."
    title.trim().length > 60 -> "Keep the title under 60 characters."
    message.trim().length > 280 -> "Keep the message under 280 characters."
    else -> null
}

class EventViewModel : ViewModel() {
    var cloudStatus by mutableStateOf("Connecting")
    val announcementsList = mutableStateListOf<Announcement>().apply {
        addAll(InMemoryStore.announcements)
    }
    private var stopBroadcastListener: (() -> Unit)? = null

    init {
        CloudServices.connect { result ->
            cloudStatus = if (result.isSuccess) "Cloud connected" else "Offline mode"
            if (result.isSuccess) {
                stopBroadcastListener = CloudServices.observeBroadcasts(
                    onUpdate = { broadcasts ->
                        if (broadcasts.isNotEmpty()) {
                            announcementsList.clear()
                            announcementsList.addAll(broadcasts)
                        }
                    },
                    onError = { }
                )
            }
        }
    }

    var selectedRole by mutableStateOf<AppRole?>(null)
    var currentTab by mutableStateOf(0)

    fun selectRole(role: AppRole) {
        selectedRole = role
        currentTab = 0
        guestSearchQuery = ""
        recordRole(role)
    }

    fun requestRoleSwitch() {
        activeDialog = DialogState.SwitchRoleConfirmation
    }

    fun confirmRoleSwitch() {
        viewModelScope.launch {
            runCatching { CloudServices.stopVoice() }
            selectedRole = null
            currentTab = 0
            guestSearchQuery = ""
            activeDialog = null
            resetConcierge()
        }
    }
    
    // Concierge Search State
    var conciergeQuery by mutableStateOf("")
    var conciergeResponse by mutableStateOf<String?>(null)
    var isUnknownResponse by mutableStateOf(false)
    
    // Voice states: "idle", "listening", "thinking", "speaking"
    var voiceOrbState by mutableStateOf("idle")
    
    // Note to Host state
    var noteText by mutableStateOf("")
    var waveConfirmationMessage by mutableStateOf<String?>(null)
    
    // Check-in search queries
    var guestSearchQuery by mutableStateOf("")
    
    // Sheets states
    var showLocationsSheet by mutableStateOf(false)
    var showFoodSheet by mutableStateOf(false)
    var showHelpSheet by mutableStateOf(false)
    var showBriefingSheet by mutableStateOf(false)
    
    // Dynamic lists (from InMemoryStore)
    val guestsList = InMemoryStore.guests
    val issuesList = InMemoryStore.issues
    val notesList = InMemoryStore.guestNotes
    val wavesList = InMemoryStore.guestWaves
    
    // Active Dialog State
    var activeDialog by mutableStateOf<DialogState?>(null)
    
    // Help Sheet inputs
    var reportDescription by mutableStateOf("")
    var reportPriority by mutableStateOf("Medium")

    var broadcastTitle by mutableStateOf("")
    var broadcastMessage by mutableStateOf("")
    var broadcastSending by mutableStateOf(false)

    fun onConciergeQueryChanged(newQuery: String) {
        conciergeQuery = newQuery
    }
    
    fun submitConciergeQuery() {
        val query = conciergeQuery.trim()
        if (query.isEmpty()) return
        
        voiceOrbState = "thinking"
        viewModelScope.launch {
            val fallback = InMemoryStore.getDeterministicAnswer(query)
            val answer = runCatching { CloudServices.ask(query) }
                .getOrNull()
                .takeUnless { it.isNullOrBlank() }
                ?: fallback
            isUnknownResponse = answer == "UNKNOWN"
            conciergeResponse = if (isUnknownResponse) {
                "I don’t have that information. Would you like me to request help from the event staff?"
            } else {
                answer
            }
            voiceOrbState = "speaking"
        }
    }
    
    fun resetConcierge() {
        conciergeQuery = ""
        conciergeResponse = null
        isUnknownResponse = false
        voiceOrbState = "idle"
    }
    
    fun onVoiceOrbClicked() {
        if (voiceOrbState == "thinking") return
        voiceOrbState = "thinking"
        viewModelScope.launch {
            runCatching { CloudServices.toggleVoice() }
                .onSuccess { voiceOrbState = if (it) "listening" else "idle" }
                .onFailure {
                    voiceOrbState = "idle"
                    activeDialog = DialogState.MessageDialog(
                        "Voice unavailable",
                        it.message ?: "Check the network and try again."
                    )
                }
        }
    }

    fun recordRole(role: AppRole) {
        CloudServices.save(
            "activity",
            "role-${System.currentTimeMillis()}",
            mapOf("action" to "role_selected", "role" to role.name.lowercase())
        )
    }

    private fun saveIssue(issue: OpenIssue) {
        CloudServices.save(
            "issues",
            issue.id,
            mapOf(
                "description" to issue.description,
                "priority" to issue.priority,
                "reportedTime" to issue.reportedTime
            )
        )
    }

    private fun saveCheckIn(guest: Guest) {
        CloudServices.save(
            "checkins",
            guest.id,
            mapOf("guestName" to guest.name, "seat" to guest.seat)
        )
    }

    private fun saveMessage(collection: String, id: String, guestName: String, message: String = "") {
        CloudServices.save(
            collection,
            id,
            mapOf("guestName" to guestName, "message" to message)
        )
    }

    fun setVoiceOrb(state: String) {
        voiceOrbState = state
    }

    fun requestStaffHelpForUnknown() {
        val query = conciergeQuery
        activeDialog = DialogState.StaffHelpConfirmation(query)
    }

    fun initiateBroadcast() {
        validateBroadcast(broadcastTitle, broadcastMessage)?.let {
            activeDialog = DialogState.MessageDialog("Broadcast incomplete", it)
            return
        }
        activeDialog = DialogState.BroadcastConfirmation(
            broadcastTitle.trim(),
            broadcastMessage.trim()
        )
    }

    fun confirmBroadcast(title: String, message: String) {
        broadcastSending = true
        CloudServices.sendBroadcast(title, message) { result ->
            broadcastSending = false
            if (result.isSuccess) {
                broadcastTitle = ""
                broadcastMessage = ""
                activeDialog = DialogState.MessageDialog(
                    "Broadcast sent",
                    "Attendees will see this message in Event Pulse."
                )
            } else {
                activeDialog = DialogState.MessageDialog(
                    "Broadcast failed",
                    result.exceptionOrNull()?.message ?: "Check the connection and try again."
                )
            }
        }
    }

    fun confirmStaffHelp(query: String) {
        // Create an issue automatically
        val newIssue = OpenIssue(
            id = "I${100 + issuesList.size + 1}",
            description = "Guest Maya Patel: Inquiry about '$query'",
            priority = "High",
            reportedTime = "12:44 PM"
        )
        issuesList.add(newIssue)
        saveIssue(newIssue)
        activeDialog = DialogState.MessageDialog(
            "Request Sent",
            "I've alerted the event staff regarding: '$query'. Someone will assist you shortly."
        )
        resetConcierge()
    }
    
    fun submitGuestNote() {
        val text = noteText.trim()
        if (text.isEmpty()) return
        
        val newNote = GuestNote(
            id = UUID.randomUUID().toString(),
            guestName = "Maya Patel",
            content = text,
            timestamp = "12:44 PM"
        )
        notesList.add(newNote)
        saveMessage("notes", newNote.id, newNote.guestName, newNote.content)
        noteText = ""
        activeDialog = DialogState.MessageDialog(
            "Note Left",
            "Your note has been securely delivered to the host panel."
        )
    }
    
    fun waveToHost() {
        val newWave = GuestWave(
            id = UUID.randomUUID().toString(),
            guestName = "Maya Patel",
            timestamp = "12:44 PM"
        )
        wavesList.add(newWave)
        saveMessage("waves", newWave.id, newWave.guestName)
        waveConfirmationMessage = "Waved to host!"
        // Auto reset wave visual feedback after an action or show message
    }
    
    fun initiateCheckIn(guest: Guest) {
        if (guest.isCheckedIn) {
            activeDialog = DialogState.MessageDialog(
                "Already Checked In",
                "${guest.name} is already checked in to the gala."
            )
            return
        }
        activeDialog = DialogState.CheckInConfirmation(guest)
    }
    
    fun confirmCheckIn(guest: Guest) {
        val foundGuest = guestsList.find { it.id == guest.id }
        if (foundGuest != null) {
            foundGuest.isCheckedIn = true
            // Also update in mutable list to trigger compose recomposition
            val index = guestsList.indexOf(foundGuest)
            if (index != -1) {
                guestsList[index] = foundGuest.copy(isCheckedIn = true)
            }
        }
        saveCheckIn(guest)
        activeDialog = DialogState.MessageDialog(
            "Check-In Successful",
            "${guest.name} is now checked in. Table assignment: ${guest.seat}."
        )
    }
    
    fun initiateResolveIssue(issue: OpenIssue) {
        activeDialog = DialogState.ResolveIssueConfirmation(issue)
    }
    
    fun confirmResolveIssue(issue: OpenIssue) {
        issuesList.removeIf { it.id == issue.id }
        CloudServices.delete("issues", issue.id)
        activeDialog = DialogState.MessageDialog(
            "Issue Resolved",
            "The issue '${issue.description}' has been cleared from the board."
        )
    }
    
    fun initiateReportIssue() {
        val desc = reportDescription.trim()
        if (desc.isEmpty()) return
        activeDialog = DialogState.ReportIssueConfirmation(desc, reportPriority)
    }
    
    fun confirmReportIssue(description: String, priority: String) {
        val newIssue = OpenIssue(
            id = "I${100 + issuesList.size + 1}",
            description = description,
            priority = priority,
            reportedTime = "12:44 PM"
        )
        issuesList.add(newIssue)
        saveIssue(newIssue)
        reportDescription = ""
        reportPriority = "Medium"
        showHelpSheet = false
        activeDialog = DialogState.MessageDialog(
            "Report Filed",
            "Thank you. A $priority priority issue has been raised with the host console."
        )
    }
    
    fun closeDialog() {
        activeDialog = null
    }
    
    fun dismissWaveFeedback() {
        waveConfirmationMessage = null
    }

    override fun onCleared() {
        stopBroadcastListener?.invoke()
        super.onCleared()
    }
}
