package com.sergiogjoo.iapro.ui

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.loadApiKey(context)
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    if (viewModel.showSettings.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showSettings.value = false },
            title = { Text("API Key de Gemini") },
            text = {
                Column {
                    Text("Obtén una key gratis en:\naistudio.google.com/apikey", fontSize = 13.sp, color = Muted)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.apiKey.value,
                        onValueChange = { viewModel.apiKey.value = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveApiKey(context, viewModel.apiKey.value) }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSettings.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "IA Pro + Gemini + Logos",
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            },
            actions = {
                IconButton(onClick = { viewModel.showSettings.value = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (viewModel.isLoading.value) {
                item { TypingIndicator() }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChatMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewModel.currentMode.value == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = { Text(mode.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = viewModel.inputText.value,
                onValueChange = { viewModel.inputText.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe tu logo o mensaje...", color = Muted) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color(0xFF2A2A35),
                    focusedContainerColor = Color(0xFF12121A),
                    unfocusedContainerColor = Color(0xFF12121A)
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage(context) })
            )

            FloatingActionButton(
                onClick = { viewModel.sendMessage(context) },
                containerColor = Primary,
                contentColor = Color.White,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Primary else Surface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Text("IA Pro", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (message.text.isNotBlank()) {
                    Text(text = message.text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
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
                                .height(220.dp)
                                .background(Color.White, RoundedCornerShape(12.dp)),
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
            .background(Surface, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(modifier = Modifier.size(8.dp).background(Primary, RoundedCornerShape(50)))
        }
    }
}
