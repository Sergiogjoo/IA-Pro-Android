package com.sergiogjoo.iapro.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sergiogjoo.iapro.ui.theme.Muted
import com.sergiogjoo.iapro.ui.theme.Primary
import com.sergiogjoo.iapro.ui.theme.Surface
import com.sergiogjoo.iapro.viewmodel.ChatMode
import com.sergiogjoo.iapro.viewmodel.ChatViewModel
import com.sergiogjoo.iapro.viewmodel.Message
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadApiKey(context)
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(snackbarMessage)
            snackbarMessage = ""
        }
    }

    if (viewModel.showSettings.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showSettings.value = false },
            title = { Text("Configuración Premium") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("API Key de Gemini (gratis)", fontWeight = FontWeight.Medium)
                    Text("Consíguela en aistudio.google.com/apikey", fontSize = 12.sp, color = Muted)
                    OutlinedTextField(
                        value = viewModel.apiKey.value,
                        onValueChange = { viewModel.apiKey.value = it },
                        label = { Text("Pega tu API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.saveApiKey(context, viewModel.apiKey.value) }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSettings.value = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Premium
            Surface(
                color = Surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "IA Pro",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            "Premium • Apps + Logos + Corrección",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                    IconButton(onClick = { viewModel.showSettings.value = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Primary)
                    }
                }
            }

            // Chat
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(viewModel.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onSave = { msg ->
                            val saved = saveMessage(context, msg)
                            snackbarMessage = if (saved) "Guardado correctamente" else "No se pudo guardar"
                        }
                    )
                }

                if (viewModel.isLoading.value) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            TypingIndicator()
                        }
                    }
                }
            }

            // Modos
            Surface(
                color = Surface.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 90.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChatMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = viewModel.currentMode.value == mode,
                                    onClick = { viewModel.setMode(mode) },
                                    label = { Text(mode.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF2A2A35)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.inputText.value,
                    onValueChange = { viewModel.inputText.value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe tu app, logo o pega código...", color = Muted) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFF3A3A45),
                        focusedContainerColor = Color(0xFF12121A),
                        unfocusedContainerColor = Color(0xFF12121A)
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage(context) })
                )

                FloatingActionButton(
                    onClick = { viewModel.sendMessage(context) },
                    containerColor = Primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, onSave: (Message) -> Unit) {
    val isUser = message.isUser
    val isCode = message.text.contains("```") ||
            message.text.contains("<!DOCTYPE") ||
            message.text.contains("<html") ||
            message.text.contains("package ") ||
            message.text.contains("fun ") ||
            message.text.length > 400

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Primary else Color(0xFF1E1E28),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "IA Pro",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (message.text.isNotBlank() || message.imageBase64 != null) {
                            IconButton(
                                onClick = { onSave(message) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Guardar",
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = if (isCode) 12.5.sp else 15.sp,
                        lineHeight = if (isCode) 17.sp else 21.sp,
                        fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default
                    )
                }

                message.imageBase64?.let { base64 ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Imagen generada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF0F0F13), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .background(Color(0xFF1E1E28), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Primary, RoundedCornerShape(50))
            )
        }
    }
}

private fun saveMessage(context: Context, message: Message): Boolean {
    return try {
        if (message.imageBase64 != null) {
            saveImage(context, message.imageBase64)
        } else {
            val extension = when {
                message.text.contains("<!DOCTYPE") || message.text.contains("<html") -> "html"
                message.text.contains("package ") || message.text.contains("fun ") -> "kt"
                else -> "txt"
            }
            val fileName = "IA_Pro_${System.currentTimeMillis()}.$extension"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(message.text.toByteArray()) }
            true
        }
    } catch (e: Exception) {
        false
    }
}

private fun saveImage(context: Context, base64: String): Boolean {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val fileName = "IA_Pro_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IA_Pro")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(bytes)
                }
                true
            } ?: false
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val folder = File(picturesDir, "IA_Pro")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            true
        }
    } catch (e: Exception) {
        false
    }
}
