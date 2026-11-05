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
    val imageBase64: String? = null,
    val id: Long = System.currentTimeMillis()
)

enum class ChatMode(val label: String) {
    PRO("Pro"),
    ANALISIS("Análisis"),
    RESUMEN("Resumen"),
    CREATIVO("Creativo"),
    CODIGO("Código"),
    APP_WEB("App Web"),
    APP_ANDROID("App Android"),
    CORREGIR("Corregir errores"),
    LOGO("Logo / Imagen")
}

val Context.dataStore by preferencesDataStore(name = "settings")

class ChatViewModel : ViewModel() {

    val messages = mutableStateListOf(
        Message(
            text = "IA Pro — Generador de Apps + Corrección de Errores\n\nModos nuevos:\n• **App Web** → genera HTML/CSS/JS completo\n• **App Android** → genera estructura Kotlin + Compose\n• **Corregir errores** → pega tu código y el error, lo arreglo\n\nTambién: Chat, Logos e Imágenes.",
            isUser = false
        )
    )

    var currentMode = mutableStateOf(ChatMode.PRO)
    var isLoading = mutableStateOf(false)
    var inputText = mutableStateOf("")
    var apiKey = mutableStateOf("")
    var showSettings = mutableStateOf(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
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
            messages.add(Message("API Key guardada.", false))
        }
    }

    fun sendMessage(context: Context) {
        val text = inputText.value.trim()
        if (text.isEmpty() || isLoading.value) return

        messages.add(Message(text = text, isUser = true))
        inputText.value = ""
        isLoading.value = true

        viewModelScope.launch {
            val result = if (apiKey.value.isNotBlank()) {
                when (currentMode.value) {
                    ChatMode.LOGO -> generateImage(text)
                    else -> callGeminiText(text, currentMode.value)
                }
            } else {
                delay(800)
                Pair(generateLocalResponse(text, currentMode.value), null)
            }
            messages.add(Message(text = result.first, isUser = false, imageBase64 = result.second))
            isLoading.value = false
        }
    }

    private suspend fun generateImage(prompt: String): Pair<String, String?> {
        return try {
            val requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": "Generate a high quality logo or image: $prompt. Clean, professional, modern design. Transparent or simple background preferred for logos."}]
                  }],
                  "generationConfig": {
                    "responseModalities": ["IMAGE"],
                    "imageConfig": {
                      "aspectRatio": "1:1"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=${apiKey.value}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Pair("Error: respuesta vacía", null)

            if (!response.isSuccessful) {
                return Pair("Error imagen (${response.code}): ${body.take(300)}", null)
            }

            val geminiResp = json.decodeFromString<GeminiImageResponse>(body)
            val inlineData = geminiResp.candidates?.firstOrNull()?.content?.parts
                ?.firstOrNull { it.inlineData != null }?.inlineData

            if (inlineData?.data != null) {
                Pair("Logo / Imagen generada:", inlineData.data)
            } else {
                Pair("No se pudo generar la imagen. Prueba otro prompt.", null)
            }

        } catch (e: Exception) {
            Pair("Error generando imagen: ${e.message}", null)
        }
    }

    private suspend fun callGeminiText(userText: String, mode: ChatMode): Pair<String, String?> {
        return try {
            val systemPrompt = when (mode) {
                ChatMode.APP_WEB -> """
                    Eres un experto desarrollador web. Genera una aplicación web COMPLETA y funcional en un solo archivo HTML (con CSS y JS embebidos).
                    Debe ser moderna, responsive y lista para abrir en el navegador.
                    Incluye comentarios claros y estructura profesional.
                    Responde ÚNICAMENTE con el código HTML completo.
                """.trimIndent()

                ChatMode.APP_ANDROID -> """
                    Eres un experto desarrollador Android. Genera el código principal de una aplicación Android completa usando Kotlin + Jetpack Compose.
                    Incluye:
                    - MainActivity.kt
                    - Pantalla principal funcional
                    - Estructura clara
                    - Comentarios útiles
                    Da el código de forma organizada y listo para copiar a un proyecto.
                """.trimIndent()

                ChatMode.CORREGIR -> """
                    Eres un experto en debugging. El usuario te va a pegar código (web o Android) junto con el error.
                    Analiza el error, explica qué lo causa y proporciona el código corregido.
                    Sé claro y directo.
                """.trimIndent()

                ChatMode.CODIGO -> "Proporciona código limpio, comentado y listo para usar. Explica brevemente."
                ChatMode.PRO -> "Eres IA Pro Avanzada. Da respuestas detalladas, estructuradas y profesionales."
                ChatMode.ANALISIS -> "Analiza el texto de forma estructurada con puntos claros."
                ChatMode.RESUMEN -> "Haz un resumen ejecutivo claro y conciso."
                ChatMode.CREATIVO -> "Genera ideas creativas y variantes."
                else -> "Responde de forma útil y profesional."
            }

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": "$systemPrompt\n\nSolicitud del usuario:\n$userText"}]
                  }],
                  "generationConfig": {
                    "temperature": 0.6,
                    "maxOutputTokens": 8192
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey.value}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Pair("Error: respuesta vacía", null)

            if (!response.isSuccessful) {
                return Pair("Error API (${response.code}): ${body.take(200)}", null)
            }

            val geminiResponse = json.decodeFromString<GeminiResponse>(body)
            val text = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No se pudo obtener respuesta."
            Pair(text, null)

        } catch (e: Exception) {
            Pair("Error de conexión: ${e.message}", null)
        }
    }

    private fun generateLocalResponse(userText: String, mode: ChatMode): String {
        return when (mode) {
            ChatMode.APP_WEB, ChatMode.APP_ANDROID, ChatMode.CORREGIR ->
                "Modo avanzado: Necesitas tu API Key de Gemini para generar aplicaciones o corregir errores."
            ChatMode.LOGO ->
                "Modo Logo: Agrega tu API Key para generar imágenes."
            else -> "Agrega tu API Key para respuestas reales de Gemini."
        }
    }

    fun setMode(mode: ChatMode) {
        currentMode.value = mode
    }
}

@Serializable
data class GeminiResponse(val candidates: List<Candidate>? = null)
@Serializable
data class GeminiImageResponse(val candidates: List<ImageCandidate>? = null)
@Serializable
data class Candidate(val content: Content? = null)
@Serializable
data class ImageCandidate(val content: ImageContent? = null)
@Serializable
data class Content(val parts: List<Part>? = null)
@Serializable
data class ImageContent(val parts: List<ImagePart>? = null)
@Serializable
data class Part(val text: String? = null)
@Serializable
data class ImagePart(val text: String? = null, val inlineData: InlineData? = null)
@Serializable
data class InlineData(val mimeType: String? = null, val data: String? = null)
