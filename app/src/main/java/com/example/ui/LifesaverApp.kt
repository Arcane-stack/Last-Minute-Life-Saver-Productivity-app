package com.example.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EscalatingReminder
import com.example.data.model.MicroStep
import com.example.data.model.ScheduleBlock
import com.example.data.model.Task
import com.example.data.model.UserProfile
import com.example.ui.theme.*
import com.example.viewmodel.LifesaverViewModel
import java.text.SimpleDateFormat
import java.util.*

fun showDateTimePicker(
    context: android.content.Context,
    initialEpochMillis: Long = System.currentTimeMillis(),
    onDateTimeSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialEpochMillis }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            
            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    onDateTimeSelected(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifesaverApp(viewModel: LifesaverViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setCalendarPermissionSimulated(true)
            Toast.makeText(context, "Google Calendar Connected!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setCalendarPermissionSimulated(false)
            Toast.makeText(context, "Calendar Sync Turned Off. (Real permission denied)", Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationPermissionSimulated(isGranted)
        if (isGranted) {
            Toast.makeText(context, "Alarms & Notifications Enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission is required for background alerts.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndLoadCalendar()
        viewModel.checkNotificationPermission()
    }

    val systemMessage by viewModel.systemMessage.collectAsState()
    LaunchedEffect(systemMessage) {
        systemMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSystemMessage()
        }
    }

    // Edge to edge safe layout
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SleekNavbarBg,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                val tabs = listOf(
                    Triple("Dashboard", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
                    Triple("Tasks", Icons.Default.Assignment, Icons.Outlined.Assignment),
                    Triple("Focus Room", Icons.Default.Timer, Icons.Outlined.Timer),
                    Triple("Analytics", Icons.Default.Analytics, Icons.Outlined.Analytics)
                )

                tabs.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == index) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SleekText,
                            selectedTextColor = SleekText,
                            indicatorColor = SleekSelectedIndicator,
                            unselectedIconColor = SleekMutedText,
                            unselectedTextColor = SleekMutedText
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SleekBackground)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> DashboardTab(viewModel) { currentTab = 2 } // Navigate to Focus Room
                    1 -> TasksTab(viewModel)
                    2 -> FocusRoomTab(viewModel)
                    3 -> AnalyticsTab(
                        viewModel = viewModel,
                        onRequestCalendarPermission = {
                            calendarPermissionLauncher.launch("android.permission.READ_CALENDAR")
                        },
                        onRequestNotificationPermission = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                            } else {
                                viewModel.setNotificationPermissionSimulated(true)
                                Toast.makeText(context, "Notifications Enabled!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- TAB 1: DASHBOARD ---

@Composable
fun DashboardTab(viewModel: LifesaverViewModel, onNavigateToFocus: () -> Unit) {
    val tasks by viewModel.tasksState.collectAsState()
    val scheduleBlocks by viewModel.scheduleBlocksState.collectAsState()
    val userProfile by viewModel.userProfileState.collectAsState()
    val reminders by viewModel.remindersState.collectAsState()
    val isDecisionLoading by viewModel.isDecisionLoading.collectAsState()
    val decisionResult by viewModel.decisionResult.collectAsState()
    val isScheduling by viewModel.isScheduling.collectAsState()

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header matching Design HTML
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "PROACTIVE AI ACTIVE",
                        color = SleekPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "Good afternoon, ${userProfile?.name ?: "Alex"}",
                        color = SleekText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SleekSelectedIndicator),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (userProfile?.name ?: "Alex").firstOrNull()?.toString()?.uppercase() ?: "A",
                        color = SleekRecommendationText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Active Reminders section styled like "Sleek Interface" Critical Alert
        if (reminders.isNotEmpty()) {
            items(reminders) { reminder ->
                val cardBg = when (reminder.level) {
                    "URGENT", "RESCUE" -> SleekRescueBg
                    else -> SleekRecommendationBg
                }
                val textAndIconColor = when (reminder.level) {
                    "URGENT", "RESCUE" -> SleekRescueText
                    else -> SleekRecommendationText
                }
                val borderColor = when (reminder.level) {
                    "URGENT", "RESCUE" -> SleekRescueBorder
                    else -> SleekRecommendationBorder
                }
                val iconCircleBg = when (reminder.level) {
                    "URGENT", "RESCUE" -> SleekRescueIndicatorBg
                    else -> SleekLighterSlate
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(iconCircleBg)
                                .align(Alignment.CenterVertically),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (reminder.level) {
                                    "URGENT", "RESCUE" -> "🚨"
                                    "SUGGESTION" -> "💡"
                                    else -> "🔔"
                                },
                                fontSize = 18.sp
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${reminder.level} ALARM: ${reminder.taskTitle}",
                                color = textAndIconColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reminder.message,
                                color = textAndIconColor.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.dismissReminder(reminder.id) }
                                ) {
                                    Text("Dismiss", color = textAndIconColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                                }
                                if (reminder.level == "URGENT" || reminder.level == "RESCUE" || reminder.level == "SUGGESTION") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.startDeadlineRescueMode(reminder.taskId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(Icons.Default.Healing, contentDescription = "Rescue", modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rescue Mode", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Centerpiece Decision Paralysis button styled beautifully
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Recommendation",
                    color = SleekMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Recommendation Card if active
                decisionResult?.let { result ->
                    val matchingTask = tasks.find { it.id == result.task_id }
                    Card(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .fillMaxWidth()
                            .border(2.dp, SleekRecommendationBorder, RoundedCornerShape(32.dp))
                            .clickable { onNavigateToFocus() },
                        colors = CardDefaults.cardColors(containerColor = SleekRecommendationBg),
                        shape = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = matchingTask?.title ?: "Focus Action Block",
                                color = SleekRecommendationText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Time: ${result.focus_session_minutes} mins • Energy: ${matchingTask?.energyRequired ?: "High Focus"}",
                                color = SleekPrimary,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.reasoning,
                                color = SleekRecommendationText.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { onNavigateToFocus() }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enter Focus Mode", color = SleekPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Main Action Button - What should I do now?
                if (isDecisionLoading) {
                    CircularProgressIndicator(color = SleekPrimary, modifier = Modifier.size(32.dp))
                } else {
                    Button(
                        onClick = { viewModel.computeNextOptimalAction() },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = RoundedCornerShape(50.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("⚡", fontSize = 18.sp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WHAT SHOULD I DO NOW?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "\"AI prioritizes tasks based on deadline urgency, your energy level, and procrastination habits to clear decision paralysis.\"",
                    color = SleekMutedText,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Timeline header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING BLOCKS",
                    color = SleekMutedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (isScheduling) {
                    CircularProgressIndicator(color = SleekPrimary, modifier = Modifier.size(18.dp))
                } else {
                    TextButton(onClick = { viewModel.triggerAutoSchedule() }) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Optimize", modifier = Modifier.size(16.dp), tint = SleekPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Optimize", color = SleekPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Timeline item blocks styled like Design HTML upcoming blocks
        if (scheduleBlocks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SleekCardBg),
                    border = BorderStroke(1.dp, SleekCardBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Empty Schedule",
                            tint = SleekMutedText,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No scheduled blocks yet.",
                            color = SleekText,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI will arrange your tasks dynamically when you add items or tap Optimize.",
                            color = SleekMutedText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(scheduleBlocks) { block ->
                val blockColor = when (block.label) {
                    "Focus Work" -> FocusBlue
                    "Break Activity" -> SuccessGreen
                    "Adaptive Buffer" -> AmberYellow
                    else -> SleekPrimary
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SleekCardBg),
                    border = BorderStroke(1.dp, SleekCardBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored vertical bar left indicator
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(blockColor)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Text details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = block.taskTitle,
                                color = SleekText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val duration = (block.endTime - block.startTime) / (60 * 1000)
                            Text(
                                text = "${block.label} • ${duration}m block",
                                color = SleekMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Right-aligned formatted timing
                        val startTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(block.startTime))
                        Text(
                            text = startTimeStr,
                            color = SleekPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 2: TASKS MANAGER ---

@Composable
fun TasksTab(viewModel: LifesaverViewModel) {
    var rawInputText by remember { mutableStateOf("") }
    var showAddManualDialog by remember { mutableStateOf(false) }
    val tasks by viewModel.tasksState.collectAsState()
    val isAnalyzingTask by viewModel.isAnalyzingTask.collectAsState()

    val context = LocalContext.current

    // Voice intent callback launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { spokenText ->
                rawInputText = spokenText
                viewModel.captureTaskFromVoiceOrText(spokenText)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Task Capture Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, LighterSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI TASK AUTO-CAPTURE",
                        color = RescueOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type or dictate. Gemini translates unstructured inputs into structured sub-steps and metadata automatically.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        label = { Text("Task description or Voice command...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RescueOrange,
                            unfocusedBorderColor = LighterSlate,
                            focusedLabelColor = RescueOrange,
                            unfocusedLabelColor = MutedText
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your task to AI...")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // Fallback if voice search not supported
                                        rawInputText = "Finish quarterly report with slide deck, due in 3 hours"
                                        Toast.makeText(context, "Speech not supported. Load demo transcript instead.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Speak", tint = RescueOrange)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (rawInputText.isNotBlank()) {
                                    viewModel.captureTaskFromVoiceOrText(rawInputText)
                                    rawInputText = ""
                                }
                            },
                            enabled = rawInputText.isNotBlank() && !isAnalyzingTask,
                            colors = ButtonDefaults.buttonColors(containerColor = RescueOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isAnalyzingTask) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Process with Gemini", color = Color.White)
                            }
                        }

                        // Preset shortcuts for demo ease
                        IconButton(
                            onClick = {
                                rawInputText = "Write bio homework research essay due tomorrow afternoon high energy takes 90 mins"
                            },
                            modifier = Modifier.background(LighterSlate, CircleShape)
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = "Load Preset", tint = FocusBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showAddManualDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusBlue),
                        border = BorderStroke(1.dp, FocusBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Manually")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Or Add Task Manually")
                    }
                }
            }
        }

        if (showAddManualDialog) {
            item {
                var title by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }
                var priority by remember { mutableStateOf(5.0) } // Default 5.0
                var estMinutes by remember { mutableStateOf("30") }
                var energy by remember { mutableStateOf("MEDIUM") } // HIGH, MEDIUM, LOW
                
                // Real-time Date & Time state
                var selectedDeadlineMillis by remember { mutableStateOf(System.currentTimeMillis() + 2 * 3600 * 1000) }
                val contextForPicker = LocalContext.current
                val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }

                AlertDialog(
                    onDismissRequest = { showAddManualDialog = false },
                    title = { Text("Add Task Manually", fontWeight = FontWeight.Bold, color = SoftWhite) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Task Title") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RescueOrange,
                                    unfocusedBorderColor = LighterSlate,
                                    focusedLabelColor = RescueOrange,
                                    unfocusedLabelColor = MutedText
                                )
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RescueOrange,
                                    unfocusedBorderColor = LighterSlate,
                                    focusedLabelColor = RescueOrange,
                                    unfocusedLabelColor = MutedText
                                )
                            )
                            
                            // Priority Score selection using Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Task Priority Index:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                    Text(String.format(Locale.US, "%.1f", priority), color = RescueOrange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Slider(
                                    value = priority.toFloat(),
                                    onValueChange = { priority = it.toDouble() },
                                    valueRange = 1f..10f,
                                    steps = 90, // increments of 0.1
                                    colors = SliderDefaults.colors(
                                        thumbColor = RescueOrange,
                                        activeTrackColor = RescueOrange,
                                        inactiveTrackColor = LighterSlate
                                    )
                                )
                            }

                            // Estimated minutes (Number Field)
                            OutlinedTextField(
                                value = estMinutes,
                                onValueChange = { estMinutes = it.filter { char -> char.isDigit() } },
                                label = { Text("Estimated Duration (Minutes)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RescueOrange,
                                    unfocusedBorderColor = LighterSlate,
                                    focusedLabelColor = RescueOrange,
                                    unfocusedLabelColor = MutedText
                                )
                            )

                            // Energy Required drop-down / row of choice
                            Column {
                                Text("Energy Required:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
                                        val isSelected = energy == level
                                        OutlinedButton(
                                            onClick = { energy = level },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) FocusBlue.copy(alpha = 0.15f) else Color.Transparent,
                                                contentColor = if (isSelected) FocusBlue else MutedText
                                            ),
                                            border = BorderStroke(1.dp, if (isSelected) FocusBlue else LighterSlate),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(level, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            // Visual Calendar & Clock deadline selector
                            Column {
                                Text("Task Deadline / Due Date:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(LighterSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .border(1.dp, LighterSlate, RoundedCornerShape(8.dp))
                                        .clickable {
                                            showDateTimePicker(contextForPicker, selectedDeadlineMillis) {
                                                selectedDeadlineMillis = it
                                            }
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dateFormatter.format(Date(selectedDeadlineMillis)),
                                            color = SoftWhite,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Open Calendar",
                                        tint = RescueOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    viewModel.addNewTaskDetailed(
                                        title = title,
                                        description = description,
                                        deadlineMillis = selectedDeadlineMillis,
                                        energyRequired = energy,
                                        priority = priority,
                                        estimatedMinutes = estMinutes.toIntOrNull() ?: 30
                                    )
                                    showAddManualDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RescueOrange)
                        ) {
                            Text("Add Task", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddManualDialog = false }) {
                            Text("Cancel", color = MutedText)
                        }
                    },
                    containerColor = SlateCard
                )
            }
        }

        // Active Tasks Section Title
        item {
            Text(
                text = "YOUR ACTIVE TASKS (${tasks.filter { it.status != "COMPLETED" }.size})",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                letterSpacing = 1.sp
            )
        }

        if (tasks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Task, contentDescription = "No tasks", tint = MutedText, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No tasks active.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(tasks) { task ->
                TaskCard(task, viewModel)
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, viewModel: LifesaverViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val steps = viewModel.getMicroSteps(task)

    val currentEpoch = System.currentTimeMillis()
    val isNearDeadline = task.deadline - currentEpoch < 4 * 3600 * 1000L && task.status != "COMPLETED"
    val isOverdue = task.deadline < currentEpoch && task.status != "COMPLETED"

    val deadlineStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.deadline))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .border(
                1.dp,
                when {
                    task.status == "COMPLETED" -> SuccessGreen.copy(alpha = 0.5f)
                    isOverdue -> SafetyRed
                    isNearDeadline -> RescueOrange
                    else -> LighterSlate
                },
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == "COMPLETED") DeepCharcoal else SlateCard
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = if (task.status == "COMPLETED") MutedText else SoftWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.status == "COMPLETED") TextDecoration.LineThrough else null
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = "Deadline",
                            tint = if (isOverdue) SafetyRed else if (isNearDeadline) RescueOrange else MutedText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Due: $deadlineStr",
                            color = if (isOverdue) SafetyRed else if (isNearDeadline) RescueOrange else MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Checkbox status indicator
                IconButton(
                    onClick = { viewModel.markTaskCompleted(task) },
                    enabled = task.status != "COMPLETED"
                ) {
                    Icon(
                        imageVector = if (task.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Complete",
                        tint = if (task.status == "COMPLETED") SuccessGreen else MutedText
                    )
                }
            }

            // Expanded checklist / details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = LighterSlate, modifier = Modifier.padding(vertical = 6.dp))

                    Text(
                        text = "Description:",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = task.description.ifEmpty { "No details specified." },
                        color = SoftWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est Time: ${task.estimatedTimeMinutes}m",
                            color = FocusBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Priority Index: ${task.priorityScore}",
                            color = RescueOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Energy: ${task.energyRequired}",
                            color = AmberYellow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Checklist
                    if (steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "MICRO-STEPS (AI ANTIDOTE TO PROCRASTINATION):",
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleMicroStepCompletion(task, index) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (step.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = "Step Status",
                                    tint = if (step.isCompleted) SuccessGreen else MutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step.title,
                                    color = if (step.isCompleted) MutedText else SoftWhite,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (step.isCompleted) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Delete Task
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = SafetyRed)
                            }
                            // Edit Task
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit task", tint = FocusBlue)
                            }
                        }

                        // Smart Rescue trigger
                        if (task.status != "COMPLETED") {
                            Button(
                                onClick = { viewModel.startDeadlineRescueMode(task.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = RescueOrange)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = "Rescue", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deadline Rescue", color = Color.White)
                            }
                        }
                    }

                    if (showEditDialog) {
                        var editTitle by remember { mutableStateOf(task.title) }
                        var editDescription by remember { mutableStateOf(task.description) }
                        var editPriority by remember { mutableStateOf(task.priorityScore) }
                        var editEstMinutes by remember { mutableStateOf(task.estimatedTimeMinutes.toString()) }
                        var editEnergy by remember { mutableStateOf(task.energyRequired) }
                        
                        var editDeadlineMillis by remember { mutableStateOf(task.deadline) }
                        val contextForPicker = LocalContext.current
                        val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }

                        AlertDialog(
                            onDismissRequest = { showEditDialog = false },
                            title = { Text("Edit Task Details", fontWeight = FontWeight.Bold, color = SoftWhite) },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editTitle,
                                        onValueChange = { editTitle = it },
                                        label = { Text("Task Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RescueOrange,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = RescueOrange,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )
                                    OutlinedTextField(
                                        value = editDescription,
                                        onValueChange = { editDescription = it },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RescueOrange,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = RescueOrange,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )
                                    
                                    // Priority Score selection using Slider
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Task Priority Index:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.1f", editPriority), color = RescueOrange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Slider(
                                            value = editPriority.toFloat(),
                                            onValueChange = { editPriority = it.toDouble() },
                                            valueRange = 1f..10f,
                                            steps = 90, // increments of 0.1
                                            colors = SliderDefaults.colors(
                                                thumbColor = RescueOrange,
                                                activeTrackColor = RescueOrange,
                                                inactiveTrackColor = LighterSlate
                                            )
                                        )
                                    }

                                    // Estimated minutes
                                    OutlinedTextField(
                                        value = editEstMinutes,
                                        onValueChange = { editEstMinutes = it.filter { char -> char.isDigit() } },
                                        label = { Text("Estimated Duration (Minutes)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RescueOrange,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = RescueOrange,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )

                                    // Energy Required choice
                                    Column {
                                        Text("Energy Required:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
                                                val isSelected = editEnergy == level
                                                OutlinedButton(
                                                    onClick = { editEnergy = level },
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (isSelected) FocusBlue.copy(alpha = 0.15f) else Color.Transparent,
                                                        contentColor = if (isSelected) FocusBlue else MutedText
                                                    ),
                                                    border = BorderStroke(1.dp, if (isSelected) FocusBlue else LighterSlate),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(level, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    // Visual Calendar & Clock deadline selector
                                    Column {
                                        Text("Task Deadline / Due Date:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(LighterSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .border(1.dp, LighterSlate, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    showDateTimePicker(contextForPicker, editDeadlineMillis) {
                                                        editDeadlineMillis = it
                                                    }
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dateFormatter.format(Date(editDeadlineMillis)),
                                                    color = SoftWhite,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Open Calendar",
                                                tint = RescueOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (editTitle.isNotBlank()) {
                                            viewModel.updateTaskDetails(
                                                taskId = task.id,
                                                title = editTitle,
                                                description = editDescription,
                                                priority = editPriority,
                                                estimatedMinutes = editEstMinutes.toIntOrNull() ?: task.estimatedTimeMinutes,
                                                energyRequired = editEnergy,
                                                deadlineMillis = editDeadlineMillis
                                            )
                                            showEditDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RescueOrange)
                                ) {
                                    Text("Save Changes", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditDialog = false }) {
                                    Text("Cancel", color = MutedText)
                                }
                            },
                            containerColor = SlateCard
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 3: FOCUS ROOM (POMODORO ENGINE) ---

@Composable
fun FocusRoomTab(viewModel: LifesaverViewModel) {
    val activeTask by viewModel.activeFocusTask.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val timeLeftSeconds by viewModel.focusTimeLeftSeconds.collectAsState()
    val currentBreak by viewModel.currentBreakActivity.collectAsState()
    val isCompleted by viewModel.isFocusCompleted.collectAsState()

    val minutes = timeLeftSeconds / 60
    val seconds = timeLeftSeconds % 60
    val progress = if (isCompleted) 1f else {
        val totalSecs = (viewModel.decisionResult.value?.focus_session_minutes ?: 25) * 60L
        if (totalSecs > 0) timeLeftSeconds.toFloat() / totalSecs else 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI POMODORO EXECUTION ROOM",
            color = FocusBlue,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, LighterSlate)
        ) {
            Text(
                text = activeTask?.let { "Focus Target: ${it.title}" } ?: "Select a task or let AI pick from Dashboard",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Timer Dial
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background track
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = LighterSlate,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 8.dp.toPx())
                )
            }

            // Progress track
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = if (isTimerRunning) FocusBlue else RescueOrange,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayLarge,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isTimerRunning) "FOCUS" else "PAUSED",
                    color = if (isTimerRunning) SuccessGreen else AmberYellow,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Controls Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.resetFocusTimer() },
                modifier = Modifier.background(LighterSlate, CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset timer", tint = SoftWhite)
            }

            FloatingActionButton(
                onClick = { viewModel.toggleFocusTimer() },
                containerColor = if (isTimerRunning) SafetyRed else SuccessGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Timer"
                )
            }

            IconButton(
                onClick = {
                    if (activeTask != null) {
                        viewModel.markTaskCompleted(activeTask!!)
                    }
                },
                modifier = Modifier.background(LighterSlate, CircleShape)
            ) {
                Icon(Icons.Default.Done, contentDescription = "Mark finished", tint = SuccessGreen)
            }
        }

        // Suggested Break activity
        currentBreak?.let { breakAct ->
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Spa, contentDescription = "Break Suggestion", tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI BREAK RECOMMENDATION",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = breakAct, color = SoftWhite, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// --- TAB 4: ANALYTICS & SETTINGS ---

@Composable
fun AnalyticsTab(
    viewModel: LifesaverViewModel,
    onRequestCalendarPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val tasks by viewModel.tasksState.collectAsState()
    val userProfile by viewModel.userProfileState.collectAsState()

    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()

    val completedTasks = tasks.filter { it.status == "COMPLETED" }
    val incompleteTasks = tasks.filter { it.status != "COMPLETED" }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedEventToImport by remember { mutableStateOf<com.example.data.GoogleCalendarEvent?>(null) }
    var selectedCalendarDate by remember { mutableStateOf(Date()) }
    var currentMonthCalendarState by remember { mutableStateOf(Calendar.getInstance()) }

    val currentEpoch = System.currentTimeMillis()
    val overdueTasks = incompleteTasks.filter { it.deadline < currentEpoch }

    val completionRate = if (tasks.isEmpty()) 0f else completedTasks.size.toFloat() / tasks.size.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "PRODUCTIVITY ANALYTICS ENGINE",
                color = SoftWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Summary Cards Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("COMPLETED", color = MutedText, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${completedTasks.size}",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("ACTIVE TASKS", color = MutedText, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${incompleteTasks.size}",
                            color = FocusBlue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("OVERDUE", color = MutedText, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${overdueTasks.size}",
                            color = SafetyRed,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Progress bar Visualizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, LighterSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI COMPLETION RATE",
                        color = SoftWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rate: ${(completionRate * 100).toInt()}%", color = SoftWhite, style = MaterialTheme.typography.bodySmall)
                        Text("${completedTasks.size} of ${tasks.size} Tasks", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = completionRate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SuccessGreen,
                        trackColor = LighterSlate
                    )
                }
            }
        }

        // Adjustable Energy Profile State
        item {
            Text(
                "CONTEXTUAL ADAPTIVE PROFILE",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                letterSpacing = 1.sp
            )
        }

        userProfile?.let { profile ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, LighterSlate)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Current Energy Level",
                            color = SoftWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Gemini maps matching tasks based on your selections:",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val levels = listOf("HIGH", "MEDIUM", "LOW")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            levels.forEach { level ->
                                val selected = profile.energyLevel == level
                                Button(
                                    onClick = { viewModel.updateUserEnergy(level) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) RescueOrange else LighterSlate
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(level, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Procrastination Index & Delay histories
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, LighterSlate)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Procrastination Index",
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                String.format("%.0f%%", profile.procrastinationScore * 100),
                                color = RescueOrange,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = profile.procrastinationScore.toFloat(),
                            onValueChange = { viewModel.updateUserProcrastinationScore(it.toDouble()) },
                            colors = SliderDefaults.colors(
                                thumbColor = RescueOrange,
                                activeTrackColor = RescueOrange,
                                inactiveTrackColor = LighterSlate
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                profile.procrastinationScore < 0.3 -> "Category: Hyper-focused. Daily schedules prioritize task bundles."
                                profile.procrastinationScore < 0.7 -> "Category: Moderately delay-prone. Schedule buffers automatically added."
                                else -> "Category: High Procrastination. AI splits tasks into microsteps instantly to combat resistance!"
                            },
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Integration Sync details
        item {
            val dateFormat = remember { SimpleDateFormat("EEEE, MMM d 'at' HH:mm", Locale.getDefault()) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, LighterSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "EXTERNAL INTEGRATION CHANNELS",
                        color = FocusBlue,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Google Calendar Integration Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LighterSlate.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, LighterSlate, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = "Calendar",
                                    tint = FocusBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Google Calendar Sync",
                                        color = SoftWhite,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (hasCalendarPermission) "Connected" else "Disconnected",
                                        color = if (hasCalendarPermission) SuccessGreen else MutedText,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Switch(
                                checked = hasCalendarPermission,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onRequestCalendarPermission()
                                    } else {
                                        viewModel.setCalendarPermissionSimulated(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = FocusBlue)
                            )
                        }

                        if (hasCalendarPermission) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = LighterSlate.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Month Selector Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val prev = (currentMonthCalendarState.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                                        currentMonthCalendarState = prev
                                        selectedCalendarDate = prev.apply { set(Calendar.DAY_OF_MONTH, 1) }.time
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month", tint = SoftWhite)
                                }

                                Text(
                                    text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonthCalendarState.time),
                                    color = SoftWhite,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        val next = (currentMonthCalendarState.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                                        currentMonthCalendarState = next
                                        selectedCalendarDate = next.apply { set(Calendar.DAY_OF_MONTH, 1) }.time
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = SoftWhite)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Weekday Headings (S, M, T, W, T, F, S)
                            val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
                            Row(modifier = Modifier.fillMaxWidth()) {
                                weekdays.forEach { dayName ->
                                    Text(
                                        text = dayName,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        color = MutedText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Calculate Days List for the selected month
                            val monthStart = (currentMonthCalendarState.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                            val firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK)
                            val maxDays = currentMonthCalendarState.getActualMaximum(Calendar.DAY_OF_MONTH)

                            val daysList = remember(currentMonthCalendarState) {
                                val list = mutableListOf<Date?>()
                                for (i in 1 until firstDayOfWeek) {
                                    list.add(null)
                                }
                                for (i in 1..maxDays) {
                                    val dayCal = monthStart.clone() as Calendar
                                    dayCal.set(Calendar.DAY_OF_MONTH, i)
                                    list.add(dayCal.time)
                                }
                                while (list.size % 7 != 0) {
                                    list.add(null)
                                }
                                list
                            }

                            // Days Grid in Rows
                            val chunkedDays = daysList.chunked(7)
                            chunkedDays.forEach { week ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    week.forEach { date ->
                                        if (date != null) {
                                            val dayCal = Calendar.getInstance().apply { time = date }
                                            val dayNum = dayCal.get(Calendar.DAY_OF_MONTH)

                                            val isSelected = remember(selectedCalendarDate, date) {
                                                val selCal = Calendar.getInstance().apply { time = selectedCalendarDate }
                                                selCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                                selCal.get(Calendar.MONTH) == dayCal.get(Calendar.MONTH) &&
                                                selCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                            }

                                            val isToday = remember(date) {
                                                val todayCal = Calendar.getInstance()
                                                todayCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                                todayCal.get(Calendar.MONTH) == dayCal.get(Calendar.MONTH) &&
                                                todayCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                            }

                                            val dayHasEvents = remember(calendarEvents, date) {
                                                val dY = dayCal.get(Calendar.YEAR)
                                                val dM = dayCal.get(Calendar.MONTH)
                                                val dD = dayNum
                                                calendarEvents.any { event ->
                                                    val eventCal = Calendar.getInstance().apply { timeInMillis = event.startMillis }
                                                    eventCal.get(Calendar.YEAR) == dY &&
                                                    eventCal.get(Calendar.MONTH) == dM &&
                                                    eventCal.get(Calendar.DAY_OF_MONTH) == dD
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> FocusBlue
                                                            isToday -> FocusBlue.copy(alpha = 0.2f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .border(
                                                        width = if (isToday && !isSelected) 1.dp else 0.dp,
                                                        color = if (isToday && !isSelected) FocusBlue else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        selectedCalendarDate = date
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = dayNum.toString(),
                                                        color = if (isSelected) Color.White else SoftWhite,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    if (dayHasEvents) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) Color.White else AmberYellow)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = LighterSlate.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Selected date details & Add event button
                            val selFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                            val eventsOnSelectedDate = remember(calendarEvents, selectedCalendarDate) {
                                val selCal = Calendar.getInstance().apply { time = selectedCalendarDate }
                                val sY = selCal.get(Calendar.YEAR)
                                val sM = selCal.get(Calendar.MONTH)
                                val sD = selCal.get(Calendar.DAY_OF_MONTH)
                                calendarEvents.filter { event ->
                                    val eventCal = Calendar.getInstance().apply { timeInMillis = event.startMillis }
                                    eventCal.get(Calendar.YEAR) == sY &&
                                    eventCal.get(Calendar.MONTH) == sM &&
                                    eventCal.get(Calendar.DAY_OF_MONTH) == sD
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Events on ${selFormat.format(selectedCalendarDate)}:",
                                    color = MutedText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedButton(
                                    onClick = { showAddEventDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusBlue),
                                    border = BorderStroke(1.dp, FocusBlue),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Event", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Event", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (eventsOnSelectedDate.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(LighterSlate.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No events scheduled for this day.",
                                        color = MutedText,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    eventsOnSelectedDate.forEach { event ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                                            border = BorderStroke(1.dp, LighterSlate.copy(alpha = 0.8f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = event.calendarName ?: "Google Calendar",
                                                            color = FocusBlue,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = event.title,
                                                            color = SoftWhite,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = SimpleDateFormat("EEEE, hh:mm a", Locale.getDefault()).format(Date(event.startMillis)),
                                                            color = AmberYellow,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            selectedEventToImport = event
                                                        },
                                                        modifier = Modifier
                                                            .background(FocusBlue.copy(alpha = 0.1f), CircleShape)
                                                            .size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Add,
                                                            contentDescription = "Import Event",
                                                            tint = FocusBlue,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                if (!event.description.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = event.description,
                                                        color = MutedText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dialog 1: Add Event to Google Calendar Dialog
                    if (showAddEventDialog) {
                        var newEventTitle by remember { mutableStateOf("") }
                        var newEventDesc by remember { mutableStateOf("") }
                        var newEventTime by remember {
                            mutableStateOf(
                                Calendar.getInstance().apply {
                                    time = selectedCalendarDate
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            )
                        }
                        val contextForPicker = LocalContext.current

                        AlertDialog(
                            onDismissRequest = { showAddEventDialog = false },
                            title = { Text("Add Google Calendar Event", fontWeight = FontWeight.Bold, color = SoftWhite) },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newEventTitle,
                                        onValueChange = { newEventTitle = it },
                                        label = { Text("Event Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = FocusBlue,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = FocusBlue,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )
                                    OutlinedTextField(
                                        value = newEventDesc,
                                        onValueChange = { newEventDesc = it },
                                        label = { Text("Event Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = FocusBlue,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = FocusBlue,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )

                                    // Visual Date & Time selector
                                    Column {
                                        Text("Event Date & Time:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(LighterSlate.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .border(1.dp, LighterSlate, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    showDateTimePicker(contextForPicker, newEventTime) {
                                                        newEventTime = it
                                                    }
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dateFormat.format(Date(newEventTime)),
                                                color = SoftWhite,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Select Time",
                                                tint = FocusBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (newEventTitle.isNotBlank()) {
                                            viewModel.addCalendarEvent(newEventTitle, newEventDesc, newEventTime)
                                            showAddEventDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusBlue)
                                ) {
                                    Text("Add to Calendar", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddEventDialog = false }) {
                                    Text("Cancel", color = MutedText)
                                }
                            },
                            containerColor = SlateCard
                        )
                    }

                    // Dialog 2: Customize Imported Task Dialog
                    if (selectedEventToImport != null) {
                        val event = selectedEventToImport!!
                        var importTitle by remember(event.id) { mutableStateOf(event.title) }
                        var importDesc by remember(event.id) { mutableStateOf(event.description ?: "Imported from Google Calendar.") }
                        var importPriority by remember(event.id) { mutableStateOf(6.0) }
                        var importEstMinutes by remember(event.id) { mutableStateOf("30") }
                        var importEnergy by remember(event.id) { mutableStateOf("MEDIUM") }
                        val contextForPicker = LocalContext.current

                        AlertDialog(
                            onDismissRequest = { selectedEventToImport = null },
                            title = { Text("Customize Imported Task", fontWeight = FontWeight.Bold, color = SoftWhite) },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = importTitle,
                                        onValueChange = { importTitle = it },
                                        label = { Text("Task Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = FocusBlue,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = FocusBlue,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )
                                    OutlinedTextField(
                                        value = importDesc,
                                        onValueChange = { importDesc = it },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = FocusBlue,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = FocusBlue,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )
                                    
                                    // Priority Slider
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Task Priority Index:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.1f", importPriority), color = FocusBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Slider(
                                            value = importPriority.toFloat(),
                                            onValueChange = { importPriority = it.toDouble() },
                                            valueRange = 1f..10f,
                                            steps = 90,
                                            colors = SliderDefaults.colors(
                                                thumbColor = FocusBlue,
                                                activeTrackColor = FocusBlue,
                                                inactiveTrackColor = LighterSlate
                                            )
                                        )
                                    }

                                    // Estimated minutes
                                    OutlinedTextField(
                                        value = importEstMinutes,
                                        onValueChange = { importEstMinutes = it.filter { char -> char.isDigit() } },
                                        label = { Text("Estimated Duration (Minutes)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = FocusBlue,
                                            unfocusedBorderColor = LighterSlate,
                                            focusedLabelColor = FocusBlue,
                                            unfocusedLabelColor = MutedText
                                        )
                                    )

                                    // Energy Required choice
                                    Column {
                                        Text("Energy Required:", color = MutedText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
                                                val isSelected = importEnergy == level
                                                OutlinedButton(
                                                    onClick = { importEnergy = level },
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (isSelected) FocusBlue.copy(alpha = 0.15f) else Color.Transparent,
                                                        contentColor = if (isSelected) FocusBlue else MutedText
                                                    ),
                                                    border = BorderStroke(1.dp, if (isSelected) FocusBlue else LighterSlate),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(level, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Due Date: " + dateFormat.format(Date(event.startMillis)),
                                        color = AmberYellow,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (importTitle.isNotBlank()) {
                                            viewModel.importCalendarEventAsTask(
                                                event = event.copy(title = importTitle, description = importDesc),
                                                priority = importPriority,
                                                estimatedMinutes = importEstMinutes.toIntOrNull() ?: 30,
                                                energyRequired = importEnergy
                                            )
                                            selectedEventToImport = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusBlue)
                                ) {
                                    Text("Confirm & Schedule", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { selectedEventToImport = null }) {
                                    Text("Cancel", color = MutedText)
                                }
                            },
                            containerColor = SlateCard
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Background Alarm / WhatsApp-Style Notifications Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LighterSlate.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, LighterSlate, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = "Notifications",
                                    tint = RescueOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "WhatsApp-Style Realtime Alerts",
                                        color = SoftWhite,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (hasNotificationPermission) "Background service active" else "Inactive",
                                        color = if (hasNotificationPermission) SuccessGreen else MutedText,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Switch(
                                checked = hasNotificationPermission,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onRequestNotificationPermission()
                                    } else {
                                        viewModel.setNotificationPermissionSimulated(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = RescueOrange)
                            )
                        }

                        if (hasNotificationPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WhatsApp-style background push alarms are fully active. High priority notifications will show even when app is closed.",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onRequestNotificationPermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = RescueOrange),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Enable Background Push Alerts", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Security Section (End-to-End Encryption)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            Icon(Icons.Default.Lock, contentDescription = "Encryption", tint = MutedText)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("End-to-End Cloud Encryption", color = SoftWhite, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = FocusBlue))
                    }
                }
            }
        }

        // Gemini API Configuration
        item {
            val customApiKey by viewModel.customApiKey.collectAsState()
            var apiKeyInput by remember { mutableStateOf(customApiKey) }
            val isUsingPlaceholder = viewModel.isUsingPlaceholderApiKey()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, if (isUsingPlaceholder) SafetyRed else LighterSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "GEMINI AI API CONFIGURATION",
                            color = if (isUsingPlaceholder) SafetyRed else FocusBlue,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isUsingPlaceholder) SafetyRed.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isUsingPlaceholder) "Key Required" else "Active",
                                color = if (isUsingPlaceholder) SafetyRed else SuccessGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When installed on your physical device, the default development API key is omitted for security. To enable the AI Scheduler, Decision Engine, and Deadline Rescue, please enter your own Gemini API Key below.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key", color = MutedText) },
                        placeholder = { Text("AIzaSy...", color = MutedText.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = SoftWhite)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.updateCustomApiKey(apiKeyInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isUsingPlaceholder) SleekPrimary else SuccessGreen),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save API Key", color = Color.White)
                    }
                }
            }
        }
    }
}
