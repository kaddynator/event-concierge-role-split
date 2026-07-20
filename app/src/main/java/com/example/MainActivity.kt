package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppRole
import com.example.viewmodel.DialogState
import com.example.viewmodel.EventViewModel
import com.example.viewmodel.findSelfCheckInGuest
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: EventViewModel = viewModel()) {
    val role = viewModel.selectedRole
    if (role == null) {
        RoleGate(onRoleSelected = viewModel::selectRole)
        return
    }
    if (role == AppRole.ATTENDEE && viewModel.activeAutomatedCall != null) {
        AutomatedCallScreen(viewModel)
        return
    }

    val attendeeItems = listOf(
        Triple("Concierge", Icons.Default.AutoAwesome, 0),
        Triple("Schedule", Icons.AutoMirrored.Filled.EventNote, 1),
        Triple("Check In", Icons.Default.HowToReg, 2)
    )
    val organizerItems = listOf(
        Triple("Overview", Icons.Default.SpaceDashboard, 0),
        Triple("Guests", Icons.Default.Groups, 1),
        Triple("Issues", Icons.Default.NotificationImportant, 2)
    )
    val navigationItems = if (role == AppRole.ATTENDEE) attendeeItems else organizerItems

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RoleTopBar(role = role, onSwitchRole = viewModel::requestRoleSwitch)
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navigationItems.forEach { (label, icon, tab) ->
                    NavigationBarItem(
                        selected = viewModel.currentTab == tab,
                        onClick = { viewModel.currentTab = tab },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (role) {
                AppRole.ATTENDEE -> when (viewModel.currentTab) {
                    0 -> HomeScreen(viewModel)
                    1 -> ScheduleScreen(viewModel)
                    else -> AttendeeCheckInScreen(viewModel)
                }
                AppRole.ORGANIZER -> when (viewModel.currentTab) {
                    0 -> OrganizerOverviewScreen(viewModel)
                    1 -> CheckInScreen(viewModel)
                    else -> OrganizerIssuesScreen(viewModel)
                }
            }

            // Global Dialog Controller
            viewModel.activeDialog?.let { dialogState ->
                AlertDialogController(dialogState, viewModel)
            }

            // Global Overlay Sheets
            if (viewModel.showLocationsSheet) {
                LocationsSheet(onDismiss = { viewModel.showLocationsSheet = false })
            }
            if (viewModel.showFoodSheet) {
                FoodSheet(onDismiss = { viewModel.showFoodSheet = false })
            }
            if (viewModel.showHelpSheet) {
                ReportHelpSheet(viewModel = viewModel, onDismiss = { viewModel.showHelpSheet = false })
            }
            if (viewModel.showBriefingSheet) {
                BriefingSheet(viewModel = viewModel, onDismiss = { viewModel.showBriefingSheet = false })
            }
            if (role == AppRole.ATTENDEE) {
                viewModel.incomingAutomatedCall?.let {
                    IncomingAutomatedCallDialog(
                        call = it,
                        onAnswer = { viewModel.answerAutomatedCall(it) },
                        onDecline = { viewModel.declineAutomatedCall(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingAutomatedCallDialog(
    call: AutomatedCall,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.SupportAgent, contentDescription = null) },
        title = { Text("Aurora concierge is calling", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(call.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AI-assisted in-app call for ${call.guestName}. You can decline or answer without sharing sensitive information.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onAnswer, modifier = Modifier.testTag("answer_call_button")) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Answer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline, modifier = Modifier.testTag("decline_call_button")) {
                Icon(Icons.Default.CallEnd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Decline")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AutomatedCallScreen(viewModel: EventViewModel) {
    val call = viewModel.activeAutomatedCall ?: return
    val context = LocalContext.current
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startGuidedCallVoice()
    }

    LaunchedEffect(call.id) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startGuidedCallVoice()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(call.id) {
        onDispose(viewModel::stopGuidedCallVoice)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "AURORA AI CONCIERGE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(42.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(116.dp)
            ) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(28.dp)
                )
            }
            Text(
                call.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                when (viewModel.guidedCallVoiceState) {
                    "connecting" -> "Connecting Gemini voice…"
                    "listening" -> "Gemini is listening"
                    "saving" -> "Saving your response…"
                    "unavailable" -> "Voice unavailable—choose an answer below"
                    else -> "Choose an answer below"
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp)
            ) {
                Text(
                    call.question,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(22.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) {
                call.options.forEach { answer ->
                    Button(
                        onClick = { viewModel.submitAutomatedCallAnswer(answer) },
                        enabled = viewModel.guidedCallVoiceState != "saving",
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        Text(answer, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TextButton(
                onClick = viewModel::endAutomatedCall,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("End call")
            }
        }
    }
    viewModel.activeDialog?.let { AlertDialogController(it, viewModel) }
}

@Composable
private fun RoleTopBar(role: AppRole, onSwitchRole: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (role == AppRole.ATTENDEE) "ATTENDEE MODE" else "ORGANIZER MODE",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (role == AppRole.ATTENDEE) "Your event companion" else "Host operations",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            TextButton(
                onClick = onSwitchRole,
                modifier = Modifier.testTag("switch_role_button")
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Switch role", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RoleGate(onRoleSelected: (AppRole) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AURORA FOUNDATION GALA",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "How are you joining tonight?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Choose one experience for this session.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 30.dp)
        )

        RoleCard(
            title = "I’m attending",
            description = "Concierge, schedule, directions and private self check-in.",
            icon = Icons.Default.Celebration,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 12.dp),
            onClick = { onRoleSelected(AppRole.ATTENDEE) }
        )
        Spacer(Modifier.height(18.dp))
        RoleCard(
            title = "I’m organizing",
            description = "Attendance, guest operations, issues and event briefing.",
            icon = Icons.Default.Badge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(start = 12.dp),
            onClick = { onRoleSelected(AppRole.ORGANIZER) }
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 148.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp).size(28.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun HomeScreen(viewModel: EventViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val latestAnnouncement = viewModel.announcementsList.firstOrNull()
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onVoiceOrbClicked()
        } else {
            viewModel.activeDialog = DialogState.MessageDialog(
                "Microphone permission needed",
                "Allow microphone access to use the live voice concierge."
            )
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Header Area
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "GALA ASSISTANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Welcome, Maya",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = viewModel.cloudStatus,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "MP",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Live Event Pulse Alert Banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Announcement",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EVENT PULSE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = latestAnnouncement?.title ?: "Welcome to the gala",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = latestAnnouncement?.content ?: "Enjoy your evening at the Aurora Foundation Gala!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (!latestAnnouncement?.time.isNullOrBlank()) {
                            Text(
                                text = latestAnnouncement?.time.orEmpty(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Schedule cards (NOW and NEXT)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GALA TIMELINE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // NOW Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "NOW",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "7:00 PM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Opening Remarks",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Grand Ballroom",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // NEXT Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "NEXT",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "7:30 PM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = "Dinner & Auction",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Grand Ballroom",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Orb
        item {
            VoiceOrb(
                state = viewModel.voiceOrbState,
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.onVoiceOrbClicked()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // Typed Concierge Query
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONCIERGE DESK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ask about dress code, parking, locations, schedule, or food options...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.conciergeQuery,
                            onValueChange = { viewModel.onConciergeQueryChanged(it) },
                            placeholder = { Text("Type question...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("concierge_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.submitConciergeQuery()
                                    focusManager.clearFocus()
                                }
                            ),
                            trailingIcon = {
                                if (viewModel.conciergeQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.resetConcierge() }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.submitConciergeQuery()
                                focusManager.clearFocus()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("ask_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                        }
                    }

                    // Answer panel
                    viewModel.conciergeResponse?.let { response ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "CONCIERGE ANSWER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = response,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                if (viewModel.isUnknownResponse) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.requestStaffHelpForUnknown() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("request_staff_help_button")
                                    ) {
                                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Request Staff Help", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        title = "Check In",
                        icon = Icons.Default.HowToReg,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.currentTab = 1 }
                    )
                    QuickActionCard(
                        title = "What's Next",
                        icon = Icons.Default.Event,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.activeDialog = DialogState.MessageDialog(
                                "Dinner & Live Auction",
                                "Up Next at 7:30 PM: Enjoy a gourmet coastal menu and bid on exciting private items to raise critical education funds!"
                            )
                        }
                    )
                    QuickActionCard(
                        title = "Find Place",
                        icon = Icons.Default.Map,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.showLocationsSheet = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        title = "Food Options",
                        icon = Icons.Default.Restaurant,
                        tint = Color(0xFFC2410C),
                        modifier = Modifier.weight(1.5f),
                        onClick = { viewModel.showFoodSheet = true }
                    )
                    QuickActionCard(
                        title = "Get Help",
                        icon = Icons.Default.SupportAgent,
                        tint = Color(0xFF1E3A8A),
                        modifier = Modifier.weight(1.5f),
                        onClick = { viewModel.showHelpSheet = true }
                    )
                }
            }
        }

        // Leave a Note and Wave to Host section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HOST CONNECT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Send note directly to host dashboard or wave to let them know you're here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.noteText,
                        onValueChange = { viewModel.noteText = it },
                        placeholder = { Text("Leave a note for the host...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input"),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.submitGuestNote() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = viewModel.noteText.isNotEmpty(),
                            modifier = Modifier.testTag("submit_note_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Submit Note", fontSize = 13.sp)
                        }

                        // Wave to Host button with success anim feedback
                        Box(contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { viewModel.waveToHost() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("wave_button")
                            ) {
                                Icon(Icons.Default.WavingHand, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wave to Host", fontSize = 13.sp)
                            }
                            
                            // Visual feedback message bubble
                            viewModel.waveConfirmationMessage?.let { waveMsg ->
                                PopupFeedback(waveMsg) {
                                    viewModel.dismissWaveFeedback()
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PopupFeedback(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(1800)
        onDismiss()
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .offset(y = (-45).dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScheduleScreen(viewModel: EventViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("YOUR EVENING", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Gala schedule", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Text("A relaxed guide to what is happening and where.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        }
        items(InMemoryStore.schedule, key = { it.id }) { item ->
            val accent = when {
                item.isCurrent -> MaterialTheme.colorScheme.primaryContainer
                item.isNext -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (item.id % 2 == 0) 12.dp else 0.dp, end = if (item.id % 2 == 0) 0.dp else 12.dp),
                colors = CardDefaults.cardColors(containerColor = accent),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.timeStr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (item.isCurrent || item.isNext) {
                            Text(if (item.isCurrent) "NOW" else "NEXT", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(item.location, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(item.description, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.showLocationsSheet = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Places")
                }
                OutlinedButton(onClick = { viewModel.showFoodSheet = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Restaurant, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Food & access")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun AttendeeCheckInScreen(viewModel: EventViewModel) {
    val query = viewModel.guestSearchQuery
    val guest = findSelfCheckInGuest(query, viewModel.guestsList)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text("PRIVATE CHECK-IN", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Find your invitation", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Enter your full name or the last four digits of your phone. We never display the guest list.",
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.guestSearchQuery = it },
            label = { Text("Full name or last 4 digits") },
            leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("self_check_in_search")
        )

        when {
            query.isBlank() -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, end = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("Your search stays on this device for the current session.", modifier = Modifier.padding(start = 12.dp), lineHeight = 20.sp)
                    }
                }
            }
            guest == null -> {
                Text(
                    "No exact invitation found. Check the spelling or phone digits, or ask the welcome desk.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, start = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(guest.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        Text(
                            if (guest.isCheckedIn) "You are already checked in." else "Invitation found. Confirm to complete check-in.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                        )
                        Button(
                            onClick = { viewModel.initiateCheckIn(guest) },
                            enabled = !guest.isCheckedIn,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text(if (guest.isCheckedIn) "Checked in" else "Confirm check-in")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrganizerOverviewScreen(viewModel: EventViewModel) {
    val context = LocalContext.current
    var guestMenuExpanded by remember { mutableStateOf(false) }
    val checkedIn = viewModel.guestsList.count { it.isCheckedIn }
    val attending = viewModel.guestsList.count { it.rsvpStatus == "Attending" }
    val vipPresent = viewModel.guestsList.count { it.isVip && it.isCheckedIn }
    val vipTotal = viewModel.guestsList.count { it.isVip }
    val openIssues = viewModel.issuesList.count { !it.isResolved }
    val next = InMemoryStore.schedule.firstOrNull { it.isNext }
    val selectedCallGuest = viewModel.guestsList.first { it.id == viewModel.selectedCallGuestId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("ORGANIZER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Tonight at a glance", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("A calm operational summary—not another dashboard.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AUTOMATED CONCIERGE CALL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    Text(
                        "Ask one attendee a short question through an AI-assisted in-app call.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    InMemoryStore.callTemplates.forEach { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.selectedCallTemplateId = template.id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.selectedCallTemplateId == template.id,
                                onClick = { viewModel.selectedCallTemplateId = template.id }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(template.title, fontWeight = FontWeight.Bold)
                                Text(
                                    template.question,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Text(
                        "ATTENDEE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    Box {
                        OutlinedButton(
                            onClick = { guestMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text(selectedCallGuest.name, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = guestMenuExpanded,
                            onDismissRequest = { guestMenuExpanded = false }
                        ) {
                            viewModel.guestsList.forEach { guest ->
                                DropdownMenuItem(
                                    text = { Text(guest.name) },
                                    onClick = {
                                        viewModel.selectedCallGuestId = guest.id
                                        guestMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = viewModel::initiateAutomatedCall,
                        enabled = !viewModel.callSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .padding(top = 12.dp)
                            .testTag("start_automated_call_button")
                    ) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (viewModel.callSending) "Scheduling…" else "Start in-app call")
                    }
                    if (viewModel.automatedCalls.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 14.dp))
                        Text("RECENT CALLS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        viewModel.automatedCalls.take(3).forEach { call ->
                            Text(
                                "${call.guestName} · ${call.title}: " +
                                    if (call.answer.isNotBlank()) call.answer else call.status,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "BROADCAST TO ATTENDEES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    Text(
                        "Send a live Event Pulse update to every attendee using this event.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.broadcastTitle,
                        onValueChange = { viewModel.broadcastTitle = it.take(60) },
                        label = { Text("Short title") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = viewModel.broadcastMessage,
                        onValueChange = { viewModel.broadcastMessage = it.take(280) },
                        label = { Text("Message") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${viewModel.broadcastMessage.length}/280",
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = viewModel::initiateBroadcast,
                            enabled = !viewModel.broadcastSending &&
                                viewModel.broadcastTitle.isNotBlank() &&
                                viewModel.broadcastMessage.isNotBlank(),
                            modifier = Modifier.testTag("send_broadcast_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (viewModel.broadcastSending) "Sending…" else "Send broadcast")
                        }
                    }
                }
            }
        }
        item {
            PaperSummaryCard(
                title = "$checkedIn of $attending guests are here",
                body = "${attending - checkedIn} expected attendees have not checked in yet.",
                icon = Icons.Default.Groups,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        item {
            PaperSummaryCard(
                title = "$vipPresent of $vipTotal VIPs have arrived",
                body = if (vipPresent == vipTotal) "All VIP guests are present." else "Open Guests to review arrivals and confirm check-ins.",
                icon = Icons.Default.Stars,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        item {
            PaperSummaryCard(
                title = next?.title ?: "Program is on schedule",
                body = next?.let { "${it.timeStr} · ${it.location}" } ?: "No upcoming program item.",
                icon = Icons.Default.Event,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        item {
            PaperSummaryCard(
                title = if (openIssues == 1) "1 issue needs attention" else "$openIssues issues need attention",
                body = "Open Issues to review guest notes, waves and operational reports.",
                icon = Icons.Default.NotificationImportant,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        item {
            Button(
                onClick = { viewModel.showBriefingSheet = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("generate_briefing_button"),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Generate organizer briefing", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(
                "GOOGLE WORKSPACE",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                "Use the signed-in Google apps already on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { addGalaToCalendar(context) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add gala to calendar")
                }
                OutlinedButton(
                    onClick = { emailOrganizerUpdate(context, checkedIn, attending, openIssues) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Email organizer update")
                }
                OutlinedButton(
                    onClick = { shareOrganizerBriefing(context, checkedIn, attending, openIssues) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share organizer briefing")
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

private fun addGalaToCalendar(context: Context) {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 18)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, "Aurora Foundation Gala")
        putExtra(CalendarContract.Events.EVENT_LOCATION, "Harbor House, Pier 26, San Francisco")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 5 * 60 * 60 * 1000)
        putExtra(CalendarContract.Events.DESCRIPTION, "Reception 6 PM · Remarks 7 PM · Dinner and auction 7:30 PM · Music 9:30 PM")
    }
    context.startActivity(intent)
}

private fun emailOrganizerUpdate(context: Context, checkedIn: Int, attending: Int, openIssues: Int) {
    val body = "$checkedIn of $attending guests checked in. $openIssues open issues need attention."
    val uri = Uri.parse("mailto:").buildUpon()
        .appendQueryParameter("subject", "Aurora Gala organizer update")
        .appendQueryParameter("body", body)
        .build()
    context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
}

private fun shareOrganizerBriefing(context: Context, checkedIn: Int, attending: Int, openIssues: Int) {
    val briefing = """
        Aurora Foundation Gala
        $checkedIn of $attending guests checked in
        $openIssues open issues
        Next: Dinner & Auction at 7:30 PM, Grand Ballroom
    """.trimIndent()
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, "aurora-gala-briefing.txt").apply { writeText(briefing) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Aurora Gala organizer briefing")
        putExtra(Intent.EXTRA_TEXT, briefing)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share organizer briefing"))
}

@Composable
private fun PaperSummaryCard(
    title: String,
    body: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
fun OrganizerIssuesScreen(viewModel: EventViewModel) {
    val openIssues = viewModel.issuesList
        .filter { !it.isResolved }
        .sortedBy { when (it.priority) { "High" -> 0; "Medium" -> 1; else -> 2 } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("ORGANIZER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Issues & messages", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { viewModel.showHelpSheet = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Report issue")
                }
            }
        }
        if (openIssues.isEmpty()) {
            item { PaperSummaryCard("No open issues", "Operations are clear right now.", Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondaryContainer) }
        } else {
            items(openIssues, key = { it.id }) { issue ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(start = if (issue.priority == "High") 0.dp else 8.dp),
                    colors = CardDefaults.cardColors(containerColor = if (issue.priority == "High") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(issue.priority.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (issue.priority == "High") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text(issue.reportedTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(issue.description, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 10.dp))
                        OutlinedButton(onClick = { viewModel.initiateResolveIssue(issue) }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                            Text("Resolve")
                        }
                    }
                }
            }
        }
        if (viewModel.notesList.isNotEmpty()) {
            item { Text("GUEST NOTES", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.tertiary) }
            items(viewModel.notesList, key = { it.id }) { note ->
                PaperSummaryCard(note.guestName, "“${note.content}” · ${note.timestamp}", Icons.AutoMirrored.Filled.Note, MaterialTheme.colorScheme.tertiaryContainer)
            }
        }
        if (viewModel.wavesList.isNotEmpty()) {
            item { Text("WAVES", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary) }
            items(viewModel.wavesList, key = { it.id }) { wave ->
                PaperSummaryCard("${wave.guestName} waved", wave.timestamp, Icons.Default.WavingHand, MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
fun CheckInScreen(viewModel: EventViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ORGANIZER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Guest roster",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = viewModel.guestSearchQuery,
            onValueChange = { viewModel.guestSearchQuery = it },
            placeholder = { Text("Search by guest name or phone number...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (viewModel.guestSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.guestSearchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("guest_search_bar")
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Guest List
        val filteredGuests = viewModel.guestsList.filter {
            it.name.contains(viewModel.guestSearchQuery, ignoreCase = true) ||
            it.phone.contains(viewModel.guestSearchQuery)
        }

        if (filteredGuests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No guests found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Verify spelling or phone digits.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredGuests, key = { it.id }) { guest ->
                    GuestCheckInCard(guest = guest, onCheckIn = { viewModel.initiateCheckIn(guest) })
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun GuestCheckInCard(guest: Guest, onCheckIn: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = guest.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (guest.isVip) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "VIP",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = guest.phone,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Check-In Action Button
                if (guest.isCheckedIn) {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("checked_in_badge_${guest.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Checked In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onCheckIn,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("check_in_button_${guest.id}")
                    ) {
                        Text("Check In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RSVP STATUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = guest.rsvpStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (guest.rsvpStatus == "Attending") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    )
                }

                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "DIETARY OPTIONS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = guest.dietary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (guest.dietary != "None") Color(0xFFC2410C) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(0.8f)) {
                    Text(
                        text = "ASSIGNMENT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = guest.seat,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun HostScreen(viewModel: EventViewModel) {
    // Math statistics
    val totalGuests = viewModel.guestsList.size
    val checkedInCount = viewModel.guestsList.count { it.isCheckedIn }
    val vipCount = viewModel.guestsList.count { it.isVip }
    val vipCheckedIn = viewModel.guestsList.count { it.isVip && it.isCheckedIn }
    
    val missingVips = viewModel.guestsList.filter { it.isVip && !it.isCheckedIn }
    val openIssues = viewModel.issuesList.filter { !it.isResolved }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COORDINATOR DESK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Host Command",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Button(
                    onClick = { viewModel.showBriefingSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("generate_briefing_button")
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Briefing", fontSize = 13.sp)
                }
            }
        }

        // Live stats counters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCounterCard(
                    title = "Checked In",
                    value = "$checkedInCount / $totalGuests",
                    subtitle = "${if (totalGuests > 0) (checkedInCount * 100 / totalGuests) else 0}% of total",
                    icon = Icons.Default.People,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )

                StatCounterCard(
                    title = "VIPs Present",
                    value = "$vipCheckedIn / $vipCount",
                    subtitle = "${vipCount - vipCheckedIn} missing",
                    icon = Icons.Default.Grade,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                StatCounterCard(
                    title = "Open Issues",
                    value = "${openIssues.size}",
                    subtitle = "Priority pending",
                    icon = Icons.Default.Warning,
                    color = if (openIssues.isNotEmpty()) Color(0xFF962D2D) else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Missing VIPs Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MISSING VIPS (${missingVips.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (missingVips.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "All VIP guests have successfully checked in!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(1.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            missingVips.forEach { vip ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = vip.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Phone: ${vip.phone}  •  Seat: ${vip.seat}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.initiateCheckIn(vip) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Force In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (vip != missingVips.last()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Priority Issues Board
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFF962D2D), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PRIORITY OPEN ISSUES (${openIssues.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF962D2D)
                    )
                }

                if (openIssues.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD1E7DD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No open issues on the board. Everything is operating smoothly!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F321B),
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val sortedIssues = openIssues.sortedWith(compareBy {
                        when (it.priority) {
                            "High" -> 1
                            "Medium" -> 2
                            else -> 3
                        }
                    })

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedIssues.forEach { issue ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = when (issue.priority) {
                                                    "High" -> Color(0xFF962D2D)
                                                    "Medium" -> Color(0xFFC2410C)
                                                    else -> Color(0xFF2C5282)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${issue.priority} Priority",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = issue.reportedTime,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = issue.description,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.initiateResolveIssue(issue) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("resolve_issue_button_${issue.id}")
                                    ) {
                                        Text("Resolve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Guest Notes and Waves log
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GUEST ACTIVITY STREAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (viewModel.notesList.isEmpty() && viewModel.wavesList.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "No guest notes or waves recorded yet.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(1.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Waves list
                            if (viewModel.wavesList.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                    Icon(Icons.Default.WavingHand, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Waves Received (${viewModel.wavesList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                                viewModel.wavesList.forEach { wave ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "👋 ${wave.guestName} waved to Host", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(text = wave.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (viewModel.notesList.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }

                            // Notes list
                            if (viewModel.notesList.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Guest Notes (${viewModel.notesList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                }
                                viewModel.notesList.forEach { note ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = note.guestName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = note.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            text = "\"${note.content}\"",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (note != viewModel.notesList.last()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun StatCounterCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(105.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AlertDialogController(
    state: DialogState,
    viewModel: EventViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.closeDialog() },
        title = {
            Text(
                text = when (state) {
                    is DialogState.CheckInConfirmation -> "Confirm Check-In"
                    is DialogState.ResolveIssueConfirmation -> "Resolve This Issue?"
                    is DialogState.ReportIssueConfirmation -> "Report New Issue?"
                    is DialogState.StaffHelpConfirmation -> "Alert Event Staff?"
                    is DialogState.BroadcastConfirmation -> "Send this broadcast?"
                    is DialogState.AutomatedCallConfirmation -> "Start concierge call?"
                    is DialogState.SwitchRoleConfirmation -> "Switch experience?"
                    is DialogState.MessageDialog -> state.title
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = when (state) {
                    is DialogState.CheckInConfirmation -> "Are you sure you want to check in ${state.guest.name}? This will confirm their arrival and assign ${state.guest.seat}."
                    is DialogState.ResolveIssueConfirmation -> "Are you sure this issue has been resolved: \"${state.issue.description}\"?"
                    is DialogState.ReportIssueConfirmation -> "Are you sure you want to raise a ${state.priority} priority request for \"${state.description}\"?"
                    is DialogState.StaffHelpConfirmation -> "Would you like me to request help from the on-site event staff regarding your question: \"${state.query}\"?"
                    is DialogState.BroadcastConfirmation -> "${state.title}\n\n${state.message}\n\nThis will appear in Event Pulse for all connected attendees."
                    is DialogState.AutomatedCallConfirmation -> "${state.guest.name}\n\n${state.template.question}\n\nThe attendee can answer or decline this AI-assisted in-app call."
                    is DialogState.SwitchRoleConfirmation -> "Leave the current mode and return to attendee or organizer selection?"
                    is DialogState.MessageDialog -> state.message
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("dialog_confirm_button"),
                onClick = {
                    when (state) {
                        is DialogState.CheckInConfirmation -> viewModel.confirmCheckIn(state.guest)
                        is DialogState.ResolveIssueConfirmation -> viewModel.confirmResolveIssue(state.issue)
                        is DialogState.ReportIssueConfirmation -> viewModel.confirmReportIssue(state.description, state.priority)
                        is DialogState.StaffHelpConfirmation -> viewModel.confirmStaffHelp(state.query)
                        is DialogState.BroadcastConfirmation -> viewModel.confirmBroadcast(state.title, state.message)
                        is DialogState.AutomatedCallConfirmation -> viewModel.confirmAutomatedCall(state.guest, state.template)
                        is DialogState.SwitchRoleConfirmation -> viewModel.confirmRoleSwitch()
                        is DialogState.MessageDialog -> viewModel.closeDialog()
                    }
                    if (state !is DialogState.MessageDialog) {
                        viewModel.closeDialog()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (state) {
                        is DialogState.CheckInConfirmation -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    when (state) {
                        is DialogState.MessageDialog -> "OK"
                        is DialogState.BroadcastConfirmation -> "Send"
                        is DialogState.AutomatedCallConfirmation -> "Start call"
                        is DialogState.SwitchRoleConfirmation -> "Switch role"
                        else -> "Confirm"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (state !is DialogState.MessageDialog) {
                TextButton(
                    modifier = Modifier.testTag("dialog_dismiss_button"),
                    onClick = { viewModel.closeDialog() }
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun LocationsSheet(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Harbor House Spaces", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(InMemoryStore.locations) { loc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = loc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = loc.level,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = loc.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Directions: ${loc.directions}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun FoodSheet(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gourmet Dining Menu", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Coastal-Californian Menu curated by Chef Alice Waters for tonight's Gala.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider()
                
                Text("🍽️ WELCOME APPETIZERS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC2410C))
                Text("Artisanal Dungeness crab cakes, baby leek tartlets, and heirloom tomato crostini.", fontSize = 13.sp)

                Text("🥗 FIRST COURSE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC2410C))
                Text("Organic Sonoma greens with toasted pine nuts, goat cheese, and golden fig balsamic vinaigrette.", fontSize = 13.sp)

                Text("🥩 MAIN COURSES (SELECT ONE)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC2410C))
                Text("• Pan-Seared Pacific Sea Bass with lemon herb beurre blanc, wild rice, and organic asparagus.", fontSize = 13.sp)
                Text("• Grass-Fed Angus Ribeye Steak with rosemary truffle jus, garlic mashed potatoes, and roasted carrots.", fontSize = 13.sp)
                Text("• Roasted Butternut Squash Steak with wild mushrooms, quinoa pilaf, and sage cream sauce (Vegan/GF).", fontSize = 13.sp)

                Text("🍰 DESSERT SYMPHONY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC2410C))
                Text("Warm dark chocolate lava cake with raspberry coulis, and classic vanilla bean crème brûlée.", fontSize = 13.sp)

                HorizontalDivider()

                Surface(
                    color = Color(0xFFFDF2E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Safety Note: All culinary preparations are strictly nut-free. Vegan, Halal, Gluten-Free, and Dairy-Free dishes are served at Table 1 and Table 2 respectively upon request.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))) {
                Text("Great", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ReportHelpSheet(viewModel: EventViewModel, onDismiss: () -> Unit) {
    val focusManager = LocalFocusManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF1E3A8A), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Help Request", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Submit a note directly to our on-site team. We will coordinate assistance immediately.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = viewModel.reportDescription,
                    onValueChange = { viewModel.reportDescription = it },
                    placeholder = { Text("What do you need help with? (e.g. Table 3 is missing a fork...)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_issue_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )

                Text("PRIORITY LEVEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("High", "Medium", "Low").forEach { level ->
                        val selected = viewModel.reportPriority == level
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.reportPriority = level },
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = level,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("submit_report_button"),
                onClick = {
                    viewModel.initiateReportIssue()
                    focusManager.clearFocus()
                },
                enabled = viewModel.reportDescription.isNotEmpty()
            ) {
                Text("Report Issue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun BriefingSheet(viewModel: EventViewModel, onDismiss: () -> Unit) {
    // Math calculations
    val totalGuests = viewModel.guestsList.size
    val checkedIn = viewModel.guestsList.count { it.isCheckedIn }
    val vipTotal = viewModel.guestsList.count { it.isVip }
    val vipCheckedIn = viewModel.guestsList.count { it.isVip && it.isCheckedIn }
    val openIssues = viewModel.issuesList.filter { !it.isResolved }
    
    // Dietary summary
    val dietSummary = viewModel.guestsList.filter { it.dietary != "None" }.groupBy { it.dietary }.mapValues { it.value.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gala Executive Briefing", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Automated briefing report generated for Harbor House coordinators.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text("📊 REGISTRATION STATUS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text("• Total Checked-In: $checkedIn out of $totalGuests guests (${if (totalGuests > 0) (checkedIn * 100 / totalGuests) else 0}%)", fontSize = 13.sp)
                Text("• VIP Presence: $vipCheckedIn present, ${vipTotal - vipCheckedIn} missing.", fontSize = 13.sp)

                Text("🚨 VIP ACTION ALERT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                val missingVipNames = viewModel.guestsList.filter { it.isVip && !it.isCheckedIn }.map { it.name }
                if (missingVipNames.isEmpty()) {
                    Text("• All VIPs checked-in successfully! No alerts.", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("• MISSING VIPS: ${missingVipNames.joinToString(", ")}", fontSize = 13.sp, color = Color(0xFF962D2D), fontWeight = FontWeight.Bold)
                }

                Text("🥗 DIETARY REQUESTS COUNT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (dietSummary.isEmpty()) {
                    Text("• No specific dietary requests filed.", fontSize = 13.sp)
                } else {
                    dietSummary.forEach { (diet, count) ->
                        Text("• $diet: $count guest(s)", fontSize = 13.sp)
                    }
                }

                Text("⚠️ ACTIVE ISSUES SUMMARY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (openIssues.isEmpty()) {
                    Text("• All reported guest issues have been successfully resolved!", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("• ${openIssues.size} pending issues on the board. (High Priority: ${openIssues.count { it.priority == "High" }}, Medium Priority: ${openIssues.count { it.priority == "Medium" }})", fontSize = 13.sp, color = Color(0xFF962D2D))
                }

                HorizontalDivider()
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Report generated on July 19, 2026. State is locked to active memory cache.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Briefing", fontWeight = FontWeight.Bold)
            }
        }
    )
}
