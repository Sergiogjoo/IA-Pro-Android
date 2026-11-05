package com.sergiogjoo.iapro.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sergiogjoo.iapro.data.AppDatabase
import com.sergiogjoo.iapro.data.ChatEntity
import com.sergiogjoo.iapro.data.MessageEntity
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
            text = "IA Pro Premium\n\nHistorial guardado automáticamente.\nTema claro/oscuro disponible en Ajustes.\n\nModos: Apps Web, Android, Corregir errores, Logos...",
            isUser = false
        )
    )

    var currentMode = mutableStateOf(ChatMode.PRO)
    var isLoading = mutableStateOf(false)
    var inputText = mutableStateOf("")
    var apiKey = mutableStateOf("")
    var showSettings = mutableStateOf(false)
    var darkTheme = mutableStateOf(true)
    var currentChatId = mutableStateOf(0L)

    private lateinit var database: AppDatabase
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun initDatabase(context: Context) {
        database = AppDatabase.getDatabase(context)
        viewModelScope.launch {
            // Cargar tema
            val isDark = context.dataStore.data.map { it[booleanPreferencesKey("dark_theme")] ?: true }.first()
            darkTheme.value = isDark

            // Cargar o crear chat
            val chats = database.chatDao().getAllChats().first()
            if (chats.isEmpty()) {
                val id = database.chatDao().insertChat(ChatEntity(title = "Nuevo chat"))
                currentChatId.value = id
            } else {
                currentChatId.value = chats.first().id
                loadMessages(chats.first().id)
            }
        }
    }

    private suspend fun loadMessages(chatId: Long) {
        val msgs = database.chatDao().getMessages(chatId).first()
        messages.clear()
        if (msgs.isEmpty()) {
            messages.add(Message("Nuevo chat iniciado.", false))
        } else {
            msgs.forEach {
                messages.add(Message(it.text, it.isUser, it.imageBase64, it.id))
            }
        }
    }

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
        }
    }

    fun toggleTheme(context: Context) {
        viewModelScope.launch {
            val newValue = !darkTheme.value
            darkTheme.value = newValue
            context.dataStore.edit { it[booleanPreferencesKey("dark_theme")] = newValue }
        }
    }

    fun sendMessage(context: Context) {
        val text = inputText.value.trim()
        if (text.isEmpty() || isLoading.value) return

        val userMsg = Message(text = text, isUser = true)
        messages.add(userMsg)
        inputText.value = ""
        isLoading.value = true

        viewModelScope.launch {
            // Guardar mensaje usuario
            database.chatDao().insertMessage(
                MessageEntity(chatId = currentChatId.value, text = text, isUser = true)
            )

            val result = if (apiKey.value.isNotBlank()) {
                when (currentMode.value) {
                    ChatMode.LOGO -> generateImage(text)
                    else -> callGeminiText(text, currentMode.value)
                }
            } else {
                delay(800)
                Pair("Agrega tu API Key en Ajustes para usar Gemini.", null)
            }

            val botMsg = Message(text = result.first, isUser = false, imageBase64 = result.second)
            messages.add(botMsg)

            // Guardar respuesta
            database.chatDao().insertMessage(
                MessageEntity(
                    chatId = currentChatId.value,
                    text = result.first,
                    isUser = false,
                    imageBase64 = result.second
                )
            )
            isLoading.value = false
        }
    }

    private suspend fun generateImage(prompt: String): Pair<String, String?> {
        return try {
            val requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": "Generate a high quality logo or image: $prompt. Clean, professional, modern design."}]
                  }],
                  "generationConfig": {
                    "responseModalities": ["IMAGE"],
                    "imageConfig": { "aspectRatio": "1:1" }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=${apiKey.value}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Pair("Error", null)
            if (!response.isSuccessful) return Pair("Error imagen", null)

            val geminiResp = json.decodeFromString<GeminiImageResponse>(body)
            val data = geminiResp.candidates?.firstOrNull()?.content?.parts
                ?.firstOrNull { it.inlineData != null }?.inlineData?.data
            if (data != null) Pair("Imagen generada:", data) else Pair("No se pudo generar.", null)
        } catch (e: Exception) {
            Pair("Error: ${e.message}", null)
        }
    }

    private suspend fun callGeminiText(userText: String, mode: ChatMode): Pair<String, String?> {
        return try {
            val systemPrompt = when (mode) {
                ChatMode.APP_WEB -> "Genera una aplicación web COMPLETA en un solo archivo HTML (CSS y JS embebidos). Moderna y funcional."
                ChatMode.APP_ANDROID -> "Genera código Android completo con Kotlin + Jetpack Compose. Estructura clara y funcional."
                ChatMode.CORREGIR -> "Analiza el error, explica la causa y da el código corregido."
                ChatMode.CODIGO -> "Proporciona código limpio y comentado."
                else -> "Responde de forma profesional y útil."
            }

            val requestBody = """
                {
                  "contents": [{"parts": [{"text": "$systemPrompt\n\n$userText"}]}],
                  "generationConfig": {"temperature": 0.6, "maxOutputTokens": 8192}
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey.value}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Pair("Error", null)
            if (!response.isSuccessful) return Pair("Error API", null)

            val resp = json.decodeFromString<GeminiResponse>(body)
            val text = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Sin respuesta"
            Pair(text, null)
        } catch (e: Exception) {
            Pair("Error: ${e.message}", null)
        }
    }

    fun setMode(mode: ChatMode) {
        currentMode.value = mode
    }
}

@Serializable data class GeminiResponse(val candidates: List<Candidate>? = null)
@Serializable data class GeminiImageResponse(val candidates: List<ImageCandidate>? = null)
@Serializable data class Candidate(val content: Content? = null)
@Serializable data class ImageCandidate(val content: ImageContent? = null)
@Serializable data class Content(val parts: List<Part>? = null)
@Serializable data class ImageContent(val parts: List<ImagePart>? = null)
@Serializable data class Part(val text: String? = null)
@Serializable data class ImagePart(val text: String? = null, val inlineData: InlineData? = null)
@Serializable data class InlineData(val mimeType: String? = null, val data: String? = null)
