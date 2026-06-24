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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifesaverApp(viewModel: LifesaverViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

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
                    3 -> AnalyticsTab(viewModel)
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

        // Mock escalation panel for live testing
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SleekCardBg),
                border = BorderStroke(1.dp, SleekCardBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PROACTIVE ALARM SIMULATOR",
                        color = SleekMutedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.triggerMockEscalation("GENTLE") }) {
                            Text("Gentle", color = FocusBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.triggerMockEscalation("SUGGESTION") }) {
                            Text("Suggest", color = AmberYellow, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.triggerMockEscalation("URGENT") }) {
                            Text("Urgent", color = SafetyRed, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.triggerMockEscalation("RESCUE") }) {
                            Text("Rescue", color = SleekPrimary, fontWeight = FontWeight.Bold)
                        }
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
                }
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Delete Task
                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = SafetyRed)
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
fun AnalyticsTab(viewModel: LifesaverViewModel) {
    val tasks by viewModel.tasksState.collectAsState()
    val userProfile by viewModel.userProfileState.collectAsState()

    val completedTasks = tasks.filter { it.status == "COMPLETED" }
    val incompleteTasks = tasks.filter { it.status != "COMPLETED" }

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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            Icon(Icons.Default.Sync, contentDescription = "Calendar", tint = MutedText)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Calendar & Email Sync", color = SoftWhite, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = FocusBlue))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
    }
}
