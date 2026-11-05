package com.sergiogjoo.iapro.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

class ChatViewModel : ViewModel() {

    val messages = mutableStateListOf(
        Message(
            text = "¡Hola! Soy **IA Pro Avanzada**.\n\nPuedo ayudarte con:\n• Razonamiento complejo\n• Análisis y resúmenes\n• Generación de texto, ideas y código\n• Respuestas profesionales y detalladas\n\nElige un modo abajo y escríbeme lo que necesites.",
            isUser = false
        )
    )

    var currentMode = mutableStateOf(ChatMode.PRO)
    var isLoading = mutableStateOf(false)
    var inputText = mutableStateOf("")

    fun sendMessage() {
        val text = inputText.value.trim()
        if (text.isEmpty() || isLoading.value) return

        messages.add(Message(text = text, isUser = true))
        inputText.value = ""
        isLoading.value = true

        viewModelScope.launch {
            delay(700 + (0..600).random().toLong()) // Simula pensamiento
            val response = generateResponse(text, currentMode.value)
            messages.add(Message(text = response, isUser = false))
            isLoading.value = false
        }
    }

    private fun generateResponse(userText: String, mode: ChatMode): String {
        val lower = userText.lowercase()

        return when (mode) {
            ChatMode.RESUMEN -> {
                "**Resumen ejecutivo:**\n\n${if (userText.length > 120) userText.take(180) + "..." else userText}\n\n• Punto clave 1: Se identifica una necesidad principal clara.\n• Punto clave 2: Existe potencial de optimización.\n• Recomendación: Profundizar en los detalles específicos para una solución más precisa."
            }
            ChatMode.ANALISIS -> {
                "**Análisis estructurado:**\n\n1. **Contexto detectado:** ${userText.take(80)}...\n2. **Fortalezas:** Claridad en la intención del usuario.\n3. **Oportunidades:** Se puede expandir con más datos o ejemplos.\n4. **Riesgos / limitaciones:** Información limitada en el mensaje actual.\n5. **Conclusión:** Recomiendo proporcionar más detalles para un análisis más profundo y accionable."
            }
            ChatMode.CODIGO -> {
                "Aquí tienes una solución de código limpia y comentada:\n\n```kotlin\n// Ejemplo base según tu petición\nfun procesarSolicitud(entrada: String): Map<String, Any> {\n    // Validación\n    if (entrada.isBlank()) return mapOf(\"error\" to \"entrada vacía\")\n    \n    // Lógica principal\n    val resultado = entrada.trim().uppercase()\n    \n    return mapOf(\n        \"original\" to entrada,\n        \"procesado\" to resultado,\n        \"longitud\" to resultado.length\n    )\n}\n\n// Uso\nprintln(procesarSolicitud(\"${userText.take(40)}\"))\n```\n\n¿Quieres que lo adapte a un lenguaje específico (Python, Java, etc.)?"
            }
            ChatMode.CREATIVO -> {
                "**Ideas creativas generadas:**\n\n• Concepto principal: \"$userText\"\n• Variante A: Versión minimalista y elegante\n• Variante B: Enfoque disruptivo e innovador\n• Variante C: Solución práctica y escalable\n\n¿Quieres que desarrolle alguna de estas ideas en detalle?"
            }
            ChatMode.PRO -> {
                when {
                    lower.contains("hola") || lower.contains("buenas") ->
                        "¡Hola! Estoy listo en **Modo Pro**. Puedo ayudarte con razonamiento avanzado, análisis, código, ideas o cualquier tarea compleja. ¿En qué trabajamos hoy?"
                    lower.contains("quién eres") || lower.contains("que eres") || lower.contains("quién eres") ->
                        "Soy **IA Pro Avanzada**, una aplicación nativa de inteligencia artificial diseñada para darte respuestas estructuradas, profesionales y útiles sin necesidad de internet ni APIs de pago. Todo funciona localmente en tu dispositivo."
                    else ->
                        "**Respuesta Pro:**\n\nHe analizado tu consulta: \"$userText\"\n\n**Enfoque recomendado:**\n1. Entender el objetivo principal.\n2. Descomponer el problema en partes manejables.\n3. Proponer una solución clara y accionable.\n\n**Propuesta inicial:**\nPuedo ayudarte a desarrollar esto con más profundidad. Dime si quieres:\n• Un plan paso a paso\n• Código listo para usar\n• Análisis más detallado\n• Versión creativa o formal\n\n¿Cómo quieres continuar?"
                }
            }
        }
    }

    fun setMode(mode: ChatMode) {
        currentMode.value = mode
    }
}
