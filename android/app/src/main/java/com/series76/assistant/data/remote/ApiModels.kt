package com.series76.assistant.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class CurrentUser(
    val id: String,
    val email: String,
    @SerialName("is_developer") val isDeveloper: Boolean,
)

@Serializable
data class ApiErrorBody(val message: String)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String,
    val date: String,
    val preview: String? = null,
)

/** A catalog row: enough to complete and build part numbers in the app, without the ratings. */
@Serializable
data class CatalogEntry(
    @SerialName("part_number") val partNumber: String,
    val enclosure: String,
    @SerialName("power_supply") val powerSupply: String,
    @SerialName("max_torque_nm") val maxTorqueNm: Int?,
)

/** One page of a list. Passing nextCursor back fetches the following page; it is null on the last one. */
@Serializable
data class Page<T>(val items: List<T>, @SerialName("next_cursor") val nextCursor: String? = null)

/** The backend rejects a longer query with a 422, so the app stops the draft here instead. */
const val MAX_QUERY_LENGTH = 2000

@Serializable
data class ConversationRequest(
    @SerialName("session_id") val sessionId: String? = null,
    val query: String,
)

@Serializable
data class ConversationResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("message_id") val messageId: String,
    val title: String,
    val answer: String,
    val outcome: String,
    val products: List<ActuatorData>,
    val alternatives: List<Candidate>,
    val suggestions: List<String>,
    val matches: List<String> = emptyList(),
    @SerialName("follow_ups") val followUps: List<String> = emptyList(),
    val requirements: Requirements,
)

@Serializable
data class MessageView(
    val id: String,
    val role: String,
    val content: String,
    val outcome: String? = null,
    val products: List<ActuatorData> = emptyList(),
    val alternatives: List<Candidate> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val matches: List<String> = emptyList(),
    @SerialName("follow_ups") val followUps: List<String> = emptyList(),
    val requirements: Requirements? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Rating(
    val service: String,
    @SerialName("torque_in_lb") val torqueInLb: Int,
    @SerialName("torque_nm") val torqueNm: Int,
    @SerialName("torque_ft_lb") val torqueFtLb: Double,
    @SerialName("duty_pct") val dutyPct: Int,
)

@Serializable
data class Electrical(
    val frequency: String,
    @SerialName("speed_sec") val speedSec: Int,
    val fla: Double,
    val lra: Double,
)

@Serializable
data class ActuatorData(
    @SerialName("part_number") val partNumber: String,
    val enclosure: String,
    @SerialName("power_supply") val powerSupply: String,
    val service: String,
    @SerialName("not_available") val notAvailable: Boolean,
    @SerialName("not_csa_certified") val notCsaCertified: Boolean,
    @SerialName("cycles_per_hour") val cyclesPerHour: Int? = null,
    @SerialName("starts_per_hour") val startsPerHour: Int? = null,
    @SerialName("motor_watts") val motorWatts: Int? = null,
    val ratings: List<Rating>,
    val electricals: List<Electrical>,
)

@Serializable
data class UnmetRequirement(
    val requirement: String,
    val required: JsonPrimitive,
    val actual: JsonPrimitive? = null,
)

@Serializable
data class Candidate(val actuator: ActuatorData, val unmet: List<UnmetRequirement>)

@Serializable
data class TorqueRequirement(val value: Double, val unit: String)

@Serializable
data class Requirements(
    val enclosure: String? = null,
    @SerialName("power_supply") val powerSupply: String? = null,
    val service: String? = null,
    val frequency: String? = null,
    @SerialName("min_torque") val minTorque: TorqueRequirement? = null,
    @SerialName("min_duty_pct") val minDutyPct: Int? = null,
    @SerialName("max_speed_sec") val maxSpeedSec: Int? = null,
    @SerialName("max_fla") val maxFla: Double? = null,
    @SerialName("max_lra") val maxLra: Double? = null,
    @SerialName("max_motor_watts") val maxMotorWatts: Int? = null,
    @SerialName("min_cycles_per_hour") val minCyclesPerHour: Int? = null,
    @SerialName("min_starts_per_hour") val minStartsPerHour: Int? = null,
    @SerialName("csa_certified") val csaCertified: Boolean = false,
)

@Serializable
data class TranscriptionResponse(val text: String)

@Serializable
data class MessageDebug(
    @SerialName("message_id") val messageId: String,
    val trace: TurnTrace,
)

@Serializable
data class TurnTrace(
    val model: String,
    @SerialName("prompt_version") val promptVersion: String,
    val tools: List<ToolTrace>,
    @SerialName("model_calls") val modelCalls: List<ModelCallTrace>,
    val validation: ValidationTrace,
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
    @SerialName("cost_usd") val costUsd: Double? = null,
    @SerialName("latency_ms") val latencyMs: Int,
)

@Serializable
data class ToolTrace(
    val name: String,
    val parameters: JsonElement,
    val result: JsonElement,
    @SerialName("latency_ms") val latencyMs: Int,
)

@Serializable
data class ModelCallTrace(
    val model: String? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("cost_usd") val costUsd: Double? = null,
    @SerialName("latency_ms") val latencyMs: Int,
)

@Serializable
data class ValidationTrace(
    val rejected: List<String> = emptyList(),
    val retried: Boolean = false,
    @SerialName("used_template") val usedTemplate: Boolean = false,
)
