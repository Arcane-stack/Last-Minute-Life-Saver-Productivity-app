package com.example.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Models ---

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
) {
    fun firstText(): String? {
        return candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    }
}

// --- Moshi parsed Agent Outputs ---

@JsonClass(generateAdapter = true)
data class TaskIntelligenceOutput(
    val title: String,
    val description: String,
    val estimated_time_minutes: Int,
    val energy_required: String, // HIGH, MEDIUM, LOW
    val priority_score: Double, // 1.0 to 10.0
    val micro_steps: List<String>
)

@JsonClass(generateAdapter = true)
data class ScheduledItemOutput(
    val title: String,
    val start_offset_minutes: Int, // Minutes from the reference start time
    val duration_minutes: Int,
    val label: String // "Focus Work", "Break Activity", "Adaptive Buffer"
)

@JsonClass(generateAdapter = true)
data class SchedulingAgentOutput(
    val schedule: List<ScheduledItemOutput>,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class DecisionEngineOutput(
    val task_id: Int,
    val reasoning: String,
    val focus_session_minutes: Int, // AI Decided pomodoro session length (15-90 mins)
    val recommended_break_activity: String
)

@JsonClass(generateAdapter = true)
data class RescueModeOutput(
    val micro_steps: List<String>,
    val emergency_execution_plan: String,
    val updated_schedule: List<ScheduledItemOutput>
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiInstance: Moshi get() = moshi
}
