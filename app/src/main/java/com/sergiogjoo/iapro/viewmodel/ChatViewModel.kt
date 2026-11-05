package com.sergiogjoo.iapro.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class Message(
    val text: String,
    val isUser: Boolean,
    val id: Long = System.currentTimeMillis()
)

enum class ChatMode(val label: String) {
    PRO("Pro"),
    ANALISIS("Análisis"),
    RESUMEN("Resumen"),
    CREATIVO("Creativo"),
    CODIGO("Código")
}

val Context.dataStore by preferencesDataStore(name = "settings")

class ChatViewModel : ViewModel() {

    val messages = mutableStateListOf(
        Message(
            text = "¡Hola! Soy **IA Pro Avanzada** con integración real de Gemini.\n\nPara usar respuestas reales:\n1. Ve a Ajustes (icono arriba)\n2. Pega tu API Key de Google Gemini (gratis)\n3. Empieza a chatear\n\nSi no pones key, uso el modo local de respaldo.",
            isUser = false
        )
    )

    var currentMode = mutableStateOf(ChatMode.PRO)
    var isLoading = mutableStateOf(false)
    var inputText = mutableStateOf("")
    var apiKey = mutableStateOf("")
    var showSettings = mutableStateOf(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadApiKey(context: Context) {
        viewModelScope.launch {
            val key = context.dataStore.data.map { it[stringPreferencesKey("gemini_api_key")] ?: "" }.first()
            apiKey.value = key
        }
    }

    fun saveApiKey(context: Context, key: String) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("gemini_api_key")] = key.trim()
            }
            apiKey.value = key.trim()
            showSettings.value = false
            messages.add(Message("API Key guardada. Ahora las respuestas son reales con Gemini.", false))
        }
    }

    fun sendMessage(context: Context) {
        val text = inputText.value.trim()
        if (text.isEmpty() || isLoading.value) return

        messages.add(Message(text = text, isUser = true))
        inputText.value = ""
        isLoading.value = true

        viewModelScope.launch {
            val response = if (apiKey.value.isNotBlank()) {
                callGemini(text, currentMode.value)
            } else {
                delay(800)
                generateLocalResponse(text, currentMode.value)
            }
            messages.add(Message(text = response, isUser = false))
            isLoading.value = false
        }
    }

    private suspend fun callGemini(userText: String, mode: ChatMode): String {
        return try {
            val systemPrompt = when (mode) {
                ChatMode.PRO -> "Eres IA Pro Avanzada. Da respuestas detalladas, estructuradas y profesionales."
                ChatMode.ANALISIS -> "Analiza el texto de forma estructurada con puntos claros."
                ChatMode.RESUMEN -> "Haz un resumen ejecutivo claro y conciso."
                ChatMode.CREATIVO -> "Genera ideas creativas y variantes."
                ChatMode.CODIGO -> "Proporciona código limpio, comentado y listo para usar."
            }

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": "$systemPrompt\n\nUsuario: $userText"}]
                  }],
                  "generationConfig": {
                    "temperature": 0.7,
                    "maxOutputTokens": 2048
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey.value}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return "Error: respuesta vacía"

            if (!response.isSuccessful) {
                return "Error API (${response.code}): ${body.take(200)}"
            }

            val geminiResponse = json.decodeFromString<GeminiResponse>(body)
            geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No se pudo obtener respuesta de Gemini."

        } catch (e: Exception) {
            "Error de conexión: ${e.message}\n\nVerifica tu API Key y conexión a internet."
        }
    }

    private fun generateLocalResponse(userText: String, mode: ChatMode): String {
        // Fallback local (igual que antes)
        return when (mode) {
            ChatMode.PRO -> "**Respuesta local (sin API Key):**\n\nHe analizado: \"$userText\"\n\nPara respuestas reales con Gemini, introduce tu API Key en Ajustes."
            else -> "Modo local activo. Agrega tu API Key de Gemini para respuestas reales."
        }
    }

    fun setMode(mode: ChatMode) {
        currentMode.value = mode
    }
}

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>? = null
)

@Serializable
data class Part(
    val text: String? = null
)
