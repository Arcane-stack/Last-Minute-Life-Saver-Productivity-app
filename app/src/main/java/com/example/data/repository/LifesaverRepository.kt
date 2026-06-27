package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.dao.EscalatingReminderDao
import com.example.data.dao.ScheduleBlockDao
import com.example.data.dao.TaskDao
import com.example.data.dao.UserProfileDao
import com.example.data.model.EscalatingReminder
import com.example.data.model.MicroStep
import com.example.data.model.ScheduleBlock
import com.example.data.model.Task
import com.example.data.model.UserProfile
import com.example.network.Content
import com.example.network.DecisionEngineOutput
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.Part
import com.example.network.RescueModeOutput
import com.example.network.RetrofitClient
import com.example.network.SchedulingAgentOutput
import com.example.network.TaskIntelligenceOutput
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LifesaverRepository(
    private val context: android.content.Context,
    private val taskDao: TaskDao,
    private val scheduleBlockDao: ScheduleBlockDao,
    private val userProfileDao: UserProfileDao,
    private val reminderDao: EscalatingReminderDao
) {
    private val tag = "LifesaverRepository"

    // --- Local DB Streams ---
    val allTasksFlow: Flow<List<Task>> = taskDao.getAllTasksFlow()
    val allScheduleBlocksFlow: Flow<List<ScheduleBlock>> = scheduleBlockDao.getAllScheduleBlocksFlow()
    val userProfileFlow: Flow<UserProfile?> = userProfileDao.getUserProfileFlow()
    val activeRemindersFlow: Flow<List<EscalatingReminder>> = reminderDao.getActiveRemindersFlow()

    // --- Basic CRUD Operations ---
    suspend fun insertTask(task: Task): Long = withContext(Dispatchers.IO) {
        val id = taskDao.insertTask(task)
        try {
            val createdTask = task.copy(id = id.toInt())
            com.example.notification.AlarmScheduler.scheduleAlarmsForTask(context, createdTask)
        } catch (e: Exception) {
            Log.e(tag, "Failed to schedule alarm for inserted task", e)
        }
        id
    }

    suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
        try {
            if (task.status == "COMPLETED") {
                com.example.notification.AlarmScheduler.cancelAlarmsForTask(context, task.id)
            } else {
                com.example.notification.AlarmScheduler.scheduleAlarmsForTask(context, task)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update alarm status for task", e)
        }
    }

    suspend fun deleteTaskById(id: Int) = withContext(Dispatchers.IO) {
        taskDao.deleteTaskById(id)
        try {
            com.example.notification.AlarmScheduler.cancelAlarmsForTask(context, id)
        } catch (e: Exception) {
            Log.e(tag, "Failed to cancel alarm for deleted task", e)
        }
    }

    suspend fun getTaskById(id: Int): Task? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(id)
    }

    suspend fun insertUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insertUserProfile(profile)
    }

    suspend fun getUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getUserProfile()
    }

    suspend fun insertReminder(reminder: EscalatingReminder) = withContext(Dispatchers.IO) {
        reminderDao.insertReminder(reminder)
        try {
            com.example.notification.NotificationHelper.showNotification(
                context,
                "${reminder.level} Alarm: ${reminder.taskTitle}",
                reminder.message,
                reminder.taskId
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to show system notification", e)
        }
    }

    suspend fun dismissReminder(id: Int) = withContext(Dispatchers.IO) {
        reminderDao.dismissReminder(id)
    }

    suspend fun clearAllReminders() = withContext(Dispatchers.IO) {
        reminderDao.clearAllReminders()
    }

    // --- Helper to get API Key safely ---
    private fun getApiKey(): String {
        try {
            val sharedPrefs = context.getSharedPreferences("lifesaver_prefs", android.content.Context.MODE_PRIVATE)
            val customKey = sharedPrefs.getString("gemini_api_key", null)
            if (!customKey.isNullOrEmpty()) {
                return customKey
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading custom API key", e)
        }
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            Log.e(tag, "Gemini API Key is missing or placeholder!")
        }
        return key
    }

        // --- 1. Task Intelligence Agent ---
    // Converts unstructured text/voice input into a fully structured Task
    suspend fun analyzeAndCaptureTask(rawInput: String, defaultDeadlineOffsetHours: Int = 24): Task? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API Key is missing. Please configure your custom API Key in the Analytics / Settings tab.")
        }

        val currentEpoch = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(currentEpoch))

        val prompt = """
            You are the Task Intelligence Agent for the "Last-Minute Life Saver" application.
            Your job is to analyze the following raw text or transcribed voice command describing a task, and transform it into a highly structured JSON object.
            
            Current Time reference: $formattedDate (Epoch: $currentEpoch)
            
            Raw Input: "$rawInput"
            
            Please refine the task title to be clear and motivating, clarify the description, estimate the required time in minutes, assign a recommended energy required level (HIGH, MEDIUM, LOW), assign a priority score between 1.0 and 10.0 based on deadlines or importance, and break down this task into 3-5 concrete micro_steps (bite-sized actions) to combat procrastination.
            
            Return a JSON object matching this schema:
            {
              "title": "Refined title",
              "description": "Clarified description",
              "estimated_time_minutes": 30,
              "energy_required": "HIGH", // HIGH, MEDIUM, LOW
              "priority_score": 7.5, // 1.0 to 10.0
              "micro_steps": ["Step 1", "Step 2", "Step 3"]
            }
            Ensure the output is valid JSON and nothing else.
        """.trimIndent()

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )
            val response = executeWithRetry { RetrofitClient.service.generateContent(apiKey, request) }
            val jsonText = response.firstText() ?: throw Exception("Empty response received from Gemini API.")

            val adapter = RetrofitClient.moshiInstance.adapter(TaskIntelligenceOutput::class.java)
            val output = adapter.fromJson(jsonText) ?: throw Exception("Failed to parse Task Intelligence JSON.")

            // Calculate a default deadline if none was mentioned, or guess based on parsed info
            val calculatedDeadline = currentEpoch + (defaultDeadlineOffsetHours * 3600 * 1000)

            // Serialize microsteps to JSON format
            val microStepsList = output.micro_steps.map { MicroStep(title = it, isCompleted = false) }
            val stepsAdapter = RetrofitClient.moshiInstance.adapter<List<MicroStep>>(
                Types.newParameterizedType(List::class.java, MicroStep::class.java)
            )
            val stepsJson = stepsAdapter.toJson(microStepsList)

            return@withContext Task(
                title = output.title,
                description = output.description,
                deadline = calculatedDeadline,
                priorityScore = output.priority_score,
                estimatedTimeMinutes = output.estimated_time_minutes,
                energyRequired = output.energy_required,
                microStepsJson = stepsJson
            )
        } catch (e: Exception) {
            Log.e(tag, "Error in Task Intelligence Agent", e)
            handleApiException(e)
        }
    }

        // --- 2. Scheduling Agent ---
    // Automatically schedules all active tasks into optimized time blocks
    suspend fun generateDailySchedule(): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API Key is missing. Please configure your custom API Key in the Analytics / Settings tab.")
        }

        val profile = getUserProfile() ?: UserProfile()
        val activeTasks = taskDao.getActiveTasks()

        if (activeTasks.isEmpty()) {
            scheduleBlockDao.clearAllBlocks()
            return@withContext "No active tasks to schedule!"
        }

        val currentEpoch = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(currentEpoch))

        val tasksDescription = activeTasks.joinToString("\n") { task ->
            "- ID: ${task.id}, Title: '${task.title}', Priority Score: ${task.priorityScore}, Est Time: ${task.estimatedTimeMinutes}m, Energy Required: ${task.energyRequired}, Deadline: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.deadline))}"
        }

        val prompt = """
            You are the Scheduling Agent for "Last-Minute Life Saver".
            Your task is to take the list of active user tasks, combine them with the user's profile and current time, and generate a concrete, optimized time-blocked schedule for the day.
            
            Current Time reference: $formattedDate (Epoch: $currentEpoch)
            
            User Profile:
            - Energy level: ${profile.energyLevel}
            - Preferred working hours: ${profile.workingHoursStart}:00 to ${profile.workingHoursEnd}:00
            - Procrastination index: ${profile.procrastinationScore}
            
            Active Tasks:
            $tasksDescription
            
            Rules:
            1. Create a sequential daily schedule. Assign each task a 'start_offset_minutes' representing how many minutes from NOW (Epoch: $currentEpoch) the task should start.
            2. Match HIGH energy tasks with times when the user is most productive (based on high energy levels).
            3. Do not overlap schedule blocks.
            4. Include a few 'Break Activity' (e.g., 5-15 mins) and 'Adaptive Buffer' (e.g. 10 mins) blocks between tasks to resolve procrastination and handle delays.
            5. Ensure tasks with urgent deadlines (closest deadlines) are scheduled first.
            
            Output a JSON response matching this schema:
            {
              "schedule": [
                {
                  "title": "Task title or Break description",
                  "start_offset_minutes": 0, // start immediately
                  "duration_minutes": 30,
                  "label": "Focus Work" // "Focus Work", "Break Activity", "Adaptive Buffer"
                }
              ],
              "explanation": "Brief context-aware explanation of why this schedule is optimized for your deadlines and energy level."
            }
        """.trimIndent()

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.3f
                )
            )
            val response = executeWithRetry { RetrofitClient.service.generateContent(apiKey, request) }
            val jsonText = response.firstText() ?: throw Exception("Empty response received from Gemini API during scheduling.")

            val adapter = RetrofitClient.moshiInstance.adapter(SchedulingAgentOutput::class.java)
            val output = adapter.fromJson(jsonText) ?: throw Exception("Failed to parse daily schedule JSON.")

            // Save the newly generated schedule to DB
            scheduleBlockDao.clearAllBlocks()

            val newBlocks = output.schedule.map { item ->
                val startTime = currentEpoch + (item.start_offset_minutes * 60 * 1000L)
                val endTime = startTime + (item.duration_minutes * 60 * 1000L)
                // Find matching taskId if title is similar to any active task
                val taskId = activeTasks.find { it.title.equals(item.title, ignoreCase = true) }?.id

                ScheduleBlock(
                    taskId = taskId,
                    taskTitle = item.title,
                    startTime = startTime,
                    endTime = endTime,
                    label = item.label
                )
            }

            scheduleBlockDao.insertBlocks(newBlocks)
            return@withContext output.explanation
        } catch (e: Exception) {
            Log.e(tag, "Error in Scheduling Agent", e)
            handleApiException(e)
        }
    }

    // --- 3. Decision Engine ("What should I do now?") ---
    // Decides the absolute best single task to perform right now with clear mathematical scoring explanation
    suspend fun selectBestNextTask(): DecisionEngineOutput? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API Key is missing. Please configure your custom API Key in the Analytics / Settings tab.")
        }

        val activeTasks = taskDao.getActiveTasks()
        if (activeTasks.isEmpty()) return@withContext null

        val profile = getUserProfile() ?: UserProfile()
        val currentEpoch = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(currentEpoch))

        val tasksDescription = activeTasks.joinToString("\n") { task ->
            "ID: ${task.id}, Title: '${task.title}', Priority Score: ${task.priorityScore}, Est Time: ${task.estimatedTimeMinutes}m, Energy Required: ${task.energyRequired}, Deadline Epoch: ${task.deadline}"
        }

        val prompt = """
            You are the Decision Engine for "Last-Minute Life Saver".
            Your job is to run a scoring algorithm on the active tasks to select the single absolute best task for the user to perform right now.
            
            Current Time reference: $formattedDate (Epoch: $currentEpoch)
            
            User Profile:
            - Current Energy Level: ${profile.energyLevel}
            - Procrastination Index: ${profile.procrastinationScore}
            
            Active Tasks:
            $tasksDescription
            
            Mathematical Decision Scoring Formula:
            Priority Score = (Deadline Urgency * 0.4) + (Task Importance/Priority Score * 0.3) + (User Energy Match * 0.2) + (Procrastination History * 0.1)
            
            Where:
            - Deadline Urgency is highest for tasks with deadlines closest to the current time.
            - User Energy Match is highest when task's 'Energy Required' matches current energy level: '${profile.energyLevel}'.
            - Procrastination History weight helps users start smaller or more critical items to break procrastination loops.
            
            Analyze these items, select the single best task, and output:
            - 'task_id': The ID of the chosen task.
            - 'reasoning': A precise, motivating, conversational explanation (with the score breakdown) explaining why this is the highest priority item to save their schedule.
            - 'focus_session_minutes': Decided duration for the Pomodoro session (15-90 mins based on estimated time and user procrastination score).
            - 'recommended_break_activity': A micro break idea (e.g. 'Do 10 jumping jacks', 'Stare out window for 2 minutes', 'Quick stretch').
            
            Output a JSON response matching this schema:
            {
              "task_id": 1,
              "reasoning": "Selected because its deadline is in 2 hours and aligns perfectly with your HIGH energy levels...",
              "focus_session_minutes": 25,
              "recommended_break_activity": "Take a 5-minute deep breathing exercise"
            }
            Ensure the response is raw valid JSON only.
        """.trimIndent()

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )
            val response = executeWithRetry { RetrofitClient.service.generateContent(apiKey, request) }
            val jsonText = response.firstText() ?: throw Exception("Empty response received from Gemini API during task selection.")

            val adapter = RetrofitClient.moshiInstance.adapter(DecisionEngineOutput::class.java)
            return@withContext adapter.fromJson(jsonText) ?: throw Exception("Failed to parse Decision Engine JSON.")
        } catch (e: Exception) {
            Log.e(tag, "Error in Decision Engine Agent", e)
            handleApiException(e)
        }
    }

    // --- 4. Smart Deadline Rescue Mode ---
    // If a deadline is close and incomplete, break task into micro-steps, generate emergency execution plan, and reorganize schedule
    suspend fun activateDeadlineRescueMode(taskId: Int): RescueModeOutput? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API Key is missing. Please configure your custom API Key in the Analytics / Settings tab.")
        }

        val task = taskDao.getTaskById(taskId) ?: throw Exception("Task with ID $taskId not found.")
        val profile = getUserProfile() ?: UserProfile()
        val currentEpoch = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(currentEpoch))

        val prompt = """
            EMERGENCY DEADBAND RESCUE INITIATION!
            The task '${task.title}' has an imminent deadline: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.deadline))}.
            Description: '${task.description}'
            Status is currently incomplete, and the deadline is fast approaching!
            
            You are the Rescue Intelligence Engine for "Last-Minute Life Saver".
            Your task is to:
            1. Break this task down into 4-6 hyper-tactical, tiny micro-steps.
            2. Generate a highly motivating, hour-by-hour emergency execution plan.
            3. Generate an updated daily schedule starting from NOW (Epoch: $currentEpoch) containing these micro steps and necessary buffers so the user can complete it.
            
            Current Time reference: $formattedDate (Epoch: $currentEpoch)
            
            Output a JSON response matching this schema:
            {
              "micro_steps": ["Step 1: Open document and write outline", "Step 2: draft intro...", ...],
              "emergency_execution_plan": "Emergency Plan: We are going to attack this in 2 sessions. Focus first on outline, then on content...",
              "updated_schedule": [
                {
                  "title": "Rescue Step 1",
                  "start_offset_minutes": 0,
                  "duration_minutes": 15,
                  "label": "Focus Work"
                }
              ]
            }
            Ensure the response is raw valid JSON only.
        """.trimIndent()

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )
            val response = executeWithRetry { RetrofitClient.service.generateContent(apiKey, request) }
            val jsonText = response.firstText() ?: throw Exception("Empty response received from Gemini API during rescue mode.")

            val adapter = RetrofitClient.moshiInstance.adapter(RescueModeOutput::class.java)
            val output = adapter.fromJson(jsonText) ?: throw Exception("Failed to parse Rescue Mode JSON.")

            // 1. Update the Task with new microsteps and mark emergency generated
            val microStepsList = output.micro_steps.map { MicroStep(title = it, isCompleted = false) }
            val stepsAdapter = RetrofitClient.moshiInstance.adapter<List<MicroStep>>(
                Types.newParameterizedType(List::class.java, MicroStep::class.java)
            )
            val stepsJson = stepsAdapter.toJson(microStepsList)

            val updatedTask = task.copy(
                microStepsJson = stepsJson,
                isEmergencyPlanGenerated = true,
                status = "IN_PROGRESS"
            )
            taskDao.updateTask(updatedTask)

            // 2. Re-arrange the schedule blocks based on rescue schedule
            scheduleBlockDao.clearAllBlocks()
            val newBlocks = output.updated_schedule.map { item ->
                val startTime = currentEpoch + (item.start_offset_minutes * 60 * 1000L)
                val endTime = startTime + (item.duration_minutes * 60 * 1000L)
                ScheduleBlock(
                    taskId = taskId,
                    taskTitle = item.title,
                    startTime = startTime,
                    endTime = endTime,
                    label = item.label
                )
            }
            scheduleBlockDao.insertBlocks(newBlocks)

            // 3. Insert a critical notification reminder in the DB
            val rescueReminder = EscalatingReminder(
                taskId = taskId,
                taskTitle = task.title,
                message = "RESCUE PLAN ACTIVATED: ${output.emergency_execution_plan.take(120)}...",
                level = "RESCUE",
                timestamp = currentEpoch
            )
            reminderDao.insertReminder(rescueReminder)

            return@withContext output
        } catch (e: Exception) {
            Log.e(tag, "Error in Emergency Rescue Mode", e)
            handleApiException(e)
        }
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 2000L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMillis
        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                val isRateLimit = (e is retrofit2.HttpException && e.code() == 429) ||
                        (e.message?.contains("429") == true)

                if (isRateLimit && attempt < maxRetries) {
                    Log.w(tag, "Rate limit hit (429). Retrying in ${currentDelay}ms (Attempt $attempt/$maxRetries)...")
                    delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff
                } else {
                    throw e
                }
            }
        }
        throw Exception("Failed after maximum retries")
    }

    private fun handleApiException(e: Exception): Nothing {
        if (e is retrofit2.HttpException) {
            val code = e.code()
            if (code == 429) {
                throw Exception("API Rate Limit Exceeded (HTTP 429). The free-tier Gemini API is limited to 15 requests per minute. Please wait 10-15 seconds and try again.")
            } else if (code == 400) {
                throw Exception("Bad Request (HTTP 400) from Gemini API. The prompt format or model configuration might be unsupported.")
            } else if (code == 403) {
                throw Exception("Access Forbidden (HTTP 403). Your Gemini API key is invalid, lacks permissions, or has expired. Please check your key in the Settings/Analytics tab.")
            } else if (code == 404) {
                throw Exception("Endpoint Not Found (HTTP 404). The requested model or API version is unavailable.")
            } else {
                throw Exception("Gemini API Error (HTTP $code): ${e.message()}")
            }
        }
        throw e
    }
}
