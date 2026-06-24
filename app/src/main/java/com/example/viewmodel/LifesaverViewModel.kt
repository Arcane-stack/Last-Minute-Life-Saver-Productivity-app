package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.EscalatingReminder
import com.example.data.model.MicroStep
import com.example.data.model.ScheduleBlock
import com.example.data.model.Task
import com.example.data.model.UserProfile
import com.example.data.repository.LifesaverRepository
import com.example.network.DecisionEngineOutput
import com.example.network.RescueModeOutput
import com.example.network.RetrofitClient
import com.squareup.moshi.Types
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class LifesaverViewModel(
    application: Application,
    private val repository: LifesaverRepository
) : AndroidViewModel(application) {

    // --- Core Database Streams ---
    val tasksState: StateFlow<List<Task>> = repository.allTasksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scheduleBlocksState: StateFlow<List<ScheduleBlock>> = repository.allScheduleBlocksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userProfileState: StateFlow<UserProfile?> = repository.userProfileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val remindersState: StateFlow<List<EscalatingReminder>> = repository.activeRemindersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Loading & Async States ---
    private val _isAnalyzingTask = MutableStateFlow(false)
    val isAnalyzingTask = _isAnalyzingTask.asStateFlow()

    private val _isScheduling = MutableStateFlow(false)
    val isScheduling = _isScheduling.asStateFlow()

    private val _isDecisionLoading = MutableStateFlow(false)
    val isDecisionLoading = _isDecisionLoading.asStateFlow()

    private val _isRescueLoading = MutableStateFlow(false)
    val isRescueLoading = _isRescueLoading.asStateFlow()

    private val _systemMessage = MutableStateFlow<String?>(null)
    val systemMessage = _systemMessage.asStateFlow()

    // --- AI Agent outputs ---
    private val _decisionResult = MutableStateFlow<DecisionEngineOutput?>(null)
    val decisionResult = _decisionResult.asStateFlow()

    private val _rescueResult = MutableStateFlow<RescueModeOutput?>(null)
    val rescueResult = _rescueResult.asStateFlow()

    // --- Pomodoro Focus Timer State ---
    private val _activeFocusTask = MutableStateFlow<Task?>(null)
    val activeFocusTask = _activeFocusTask.asStateFlow()

    private val _focusTimeLeftSeconds = MutableStateFlow(1500L) // 25 minutes default
    val focusTimeLeftSeconds = _focusTimeLeftSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private val _isFocusCompleted = MutableStateFlow(false)
    val isFocusCompleted = _isFocusCompleted.asStateFlow()

    private val _currentBreakActivity = MutableStateFlow<String?>(null)
    val currentBreakActivity = _currentBreakActivity.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Initialize User Profile if not existing
        viewModelScope.launch {
            val current = repository.getUserProfile()
            if (current == null) {
                repository.insertUserProfile(UserProfile())
            }
        }
    }

    fun clearSystemMessage() {
        _systemMessage.value = null
    }

    // --- Task Actions ---

    fun addNewTask(
        title: String,
        description: String,
        deadlineDaysFromNow: Int,
        energyRequired: String,
        priority: Double,
        estimatedMinutes: Int
    ) {
        viewModelScope.launch {
            val deadlineTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, deadlineDaysFromNow)
            }.timeInMillis

            val newTask = Task(
                title = title,
                description = description,
                deadline = deadlineTime,
                priorityScore = priority,
                estimatedTimeMinutes = estimatedMinutes,
                energyRequired = energyRequired,
                microStepsJson = "[]"
            )
            repository.insertTask(newTask)
            _systemMessage.value = "Task '$title' added."
            
            // Auto schedule daily assistant whenever a task is added
            triggerAutoSchedule()
        }
    }

    fun captureTaskFromVoiceOrText(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _isAnalyzingTask.value = true
            try {
                val parsedTask = repository.analyzeAndCaptureTask(input)
                if (parsedTask != null) {
                    repository.insertTask(parsedTask)
                    _systemMessage.value = "AI Task Captured: '${parsedTask.title}' with ${getMicroSteps(parsedTask).size} micro-steps!"
                    triggerAutoSchedule()
                } else {
                    _systemMessage.value = "AI Task capture failed. Using fallback entry."
                    // Fallback to a basic title
                    val fallbackTask = Task(
                        title = input.take(40),
                        description = input,
                        deadline = System.currentTimeMillis() + 24 * 3600 * 1000L
                    )
                    repository.insertTask(fallbackTask)
                }
            } catch (e: Exception) {
                _systemMessage.value = "Error capturing task: ${e.message}"
            } finally {
                _isAnalyzingTask.value = false
            }
        }
    }

    fun toggleMicroStepCompletion(task: Task, index: Int) {
        viewModelScope.launch {
            val steps = getMicroSteps(task).toMutableList()
            if (index in steps.indices) {
                val step = steps[index]
                steps[index] = step.copy(isCompleted = !step.isCompleted)

                // Re-serialize
                val stepsAdapter = RetrofitClient.moshiInstance.adapter<List<MicroStep>>(
                    Types.newParameterizedType(List::class.java, MicroStep::class.java)
                )
                val updatedJson = stepsAdapter.toJson(steps)
                val updatedTask = task.copy(microStepsJson = updatedJson)
                repository.updateTask(updatedTask)
            }
        }
    }

    fun getMicroSteps(task: Task): List<MicroStep> {
        return try {
            val stepsAdapter = RetrofitClient.moshiInstance.adapter<List<MicroStep>>(
                Types.newParameterizedType(List::class.java, MicroStep::class.java)
            )
            stepsAdapter.fromJson(task.microStepsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
            _systemMessage.value = "Task deleted"
            triggerAutoSchedule()
        }
    }

    fun markTaskCompleted(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(status = "COMPLETED")
            repository.updateTask(updated)

            // Update user profile statistics
            val profile = repository.getUserProfile() ?: UserProfile()
            val updatedProfile = profile.copy(
                totalTasksCompleted = profile.totalTasksCompleted + 1
            )
            repository.insertUserProfile(updatedProfile)
            _systemMessage.value = "Task completed! Great job saving your schedule!"
            
            // Re-schedule daily routine to remove completed item
            triggerAutoSchedule()
        }
    }

    // --- User Profile / Context Actions ---

    fun updateUserEnergy(energy: String) {
        viewModelScope.launch {
            val profile = repository.getUserProfile() ?: UserProfile()
            repository.insertUserProfile(profile.copy(energyLevel = energy))
            _systemMessage.value = "Energy set to $energy"
            
            // Auto schedule on change of context
            triggerAutoSchedule()
        }
    }

    fun updateUserProcrastinationScore(score: Double) {
        viewModelScope.launch {
            val profile = repository.getUserProfile() ?: UserProfile()
            repository.insertUserProfile(profile.copy(procrastinationScore = score))
        }
    }

    // --- AI Scheduling Agent ---

    fun triggerAutoSchedule() {
        viewModelScope.launch {
            _isScheduling.value = true
            try {
                val explanation = repository.generateDailySchedule()
                if (explanation != null) {
                    _systemMessage.value = "Schedule Optimized! $explanation"
                }
            } catch (e: Exception) {
                _systemMessage.value = "Scheduling error: ${e.message}"
            } finally {
                _isScheduling.value = false
            }
        }
    }

    // --- AI Decision Engine ("What should I do now?") ---

    fun computeNextOptimalAction() {
        viewModelScope.launch {
            _isDecisionLoading.value = true
            try {
                val result = repository.selectBestNextTask()
                if (result != null) {
                    _decisionResult.value = result
                    
                    // Automatically prep focus mode timers
                    val task = tasksState.value.find { it.id == result.task_id }
                    if (task != null) {
                        _activeFocusTask.value = task
                        _focusTimeLeftSeconds.value = result.focus_session_minutes * 60L
                        _currentBreakActivity.value = result.recommended_break_activity
                        _isFocusCompleted.value = false
                    }
                } else {
                    _decisionResult.value = null
                    _systemMessage.value = "No pending tasks found. Set some tasks first!"
                }
            } catch (e: Exception) {
                _systemMessage.value = "Decision engine error: ${e.message}"
            } finally {
                _isDecisionLoading.value = false
            }
        }
    }

    // --- Smart Deadline Rescue Mode ---

    fun startDeadlineRescueMode(taskId: Int) {
        viewModelScope.launch {
            _isRescueLoading.value = true
            try {
                val result = repository.activateDeadlineRescueMode(taskId)
                if (result != null) {
                    _rescueResult.value = result
                    _systemMessage.value = "RESCUE MODE ACTIVATED! Emergency plan generated for task."
                    
                    // Auto select the newly updated rescue tasks for active display
                    val updatedTask = repository.getTaskById(taskId)
                    if (updatedTask != null) {
                        _activeFocusTask.value = updatedTask
                    }
                } else {
                    _systemMessage.value = "Failed to launch Rescue Mode. Please verify connection and try again."
                }
            } catch (e: Exception) {
                _systemMessage.value = "Rescue Mode error: ${e.message}"
            } finally {
                _isRescueLoading.value = false
            }
        }
    }

    // --- Focus Mode Timer Engine ---

    fun toggleFocusTimer() {
        if (_isTimerRunning.value) {
            pauseFocusTimer()
        } else {
            startFocusTimer()
        }
    }

    private fun startFocusTimer() {
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_focusTimeLeftSeconds.value > 0 && _isTimerRunning.value) {
                delay(1000)
                _focusTimeLeftSeconds.value -= 1
            }
            if (_focusTimeLeftSeconds.value == 0L) {
                onTimerCompleted()
            }
        }
    }

    private fun pauseFocusTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetFocusTimer() {
        pauseFocusTimer()
        val minutes = _decisionResult.value?.focus_session_minutes ?: 25
        _focusTimeLeftSeconds.value = minutes * 60L
        _isFocusCompleted.value = false
    }

    private fun onTimerCompleted() {
        _isTimerRunning.value = false
        _isFocusCompleted.value = true
        _systemMessage.value = "Focus Session Completed! Time for your recommended break: ${_currentBreakActivity.value ?: "Relax for 5 minutes"}"
        
        // Boost completed count and update analytics
        _activeFocusTask.value?.let { task ->
            viewModelScope.launch {
                // Mark some micro steps completed automatically or increment stats
                val steps = getMicroSteps(task)
                val firstIncomplete = steps.indexOfFirst { !it.isCompleted }
                if (firstIncomplete != -1) {
                    toggleMicroStepCompletion(task, firstIncomplete)
                } else {
                    markTaskCompleted(task)
                }
            }
        }
    }

    // --- Escalating Reminder Alerts ---

    fun dismissReminder(id: Int) {
        viewModelScope.launch {
            repository.dismissReminder(id)
        }
    }

    fun triggerMockEscalation(level: String) {
        viewModelScope.launch {
            val tasks = tasksState.value.filter { it.status != "COMPLETED" }
            if (tasks.isEmpty()) {
                _systemMessage.value = "No active tasks to trigger reminder escalation."
                return@launch
            }
            val firstTask = tasks.first()
            val message = when (level) {
                "GENTLE" -> "Gentle suggestion: '${firstTask.title}' has a deadline coming up."
                "SUGGESTION" -> "Helpful Recommendation: Start working on '${firstTask.title}' now to stay aligned with your energy schedule."
                "URGENT" -> "⚠️ URGENT WARNING: Deadline for '${firstTask.title}' is imminent! Immediate action is recommended."
                else -> "🚨 RESCUE PLAN ACTIVATED: Schedule restructured to rescue incomplete deadline for '${firstTask.title}'."
            }

            val reminder = EscalatingReminder(
                taskId = firstTask.id,
                taskTitle = firstTask.title,
                message = message,
                level = level,
                timestamp = System.currentTimeMillis()
            )
            repository.insertReminder(reminder)
            _systemMessage.value = "Triggered $level alert!"

            if (level == "RESCUE") {
                startDeadlineRescueMode(firstTask.id)
            }
        }
    }
}

class LifesaverViewModelFactory(
    private val application: Application,
    private val repository: LifesaverRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LifesaverViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LifesaverViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
