package com.openfy.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openfy.android.R
import com.openfy.android.data.PlaylistDto
import com.openfy.android.ui.theme.OpenfyAccent
import com.openfy.android.ui.theme.OpenfyAccent2
import com.openfy.android.ui.theme.OpenfyInk
import com.openfy.android.ui.theme.OpenfyPanel
import com.openfy.android.ui.theme.OpenfyPanelHigh
import com.openfy.android.ui.theme.OpenfyTextHigh
import com.openfy.android.ui.theme.OpenfyTextLow

@Composable
fun OpenfyApp(viewModel: AuthViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> LoadingScreen()
            state.session == null -> LoginScreen(state, viewModel)
            else -> HomeScreen(state, viewModel)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("openfy", color = OpenfyAccent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = OpenfyAccent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text("Sincronizando sua conta…", color = OpenfyTextLow, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LoginScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to OpenfyPanel,
                    0.55f to OpenfyInk,
                    1f to OpenfyInk,
                )
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.openfy_logo),
                contentDescription = "Openfy",
                modifier = Modifier.size(96.dp),
            )
            Text("openfy", color = OpenfyAccent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "Sua música, no PC e no celular.",
                color = OpenfyTextLow,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (creating) "Criar conta" else "Entrar na conta",
                        color = OpenfyTextHigh,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "A conta sincroniza playlists, curtidas, histórico e recap. O modo local continua disponível no PC.",
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )

                    if (creating) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Nome de exibição") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (creating) viewModel.signUp(email, password, displayName)
                            else viewModel.signIn(email, password)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OpenfyInk,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (creating) "Criar conta" else "Entrar")
                    }

                    TextButton(
                        onClick = {
                            creating = !creating
                            viewModel.clearFeedback()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (creating) "Já tenho conta" else "Criar uma conta",
                            color = OpenfyAccent2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Sem login, o Openfy do PC continua tocando sua biblioteca local.",
                color = OpenfyTextLow.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun HomeScreen(state: AuthUiState, viewModel: AuthViewModel) {
    val session = state.session ?: return
    var newPlaylistOpen by remember { mutableStateOf(false) }
    val profile = state.profile
    val title = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: session.email.substringBefore("@").ifBlank { "Openfy" }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("openfy", color = OpenfyAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Olá, $title",
                        color = OpenfyTextHigh,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    profile?.handle?.takeIf { it.isNotBlank() }?.let {
                        Text("@$it", color = OpenfyTextLow, fontSize = 11.sp)
                    }
                }
                TextButton(onClick = { viewModel.signOut() }) {
                    Text("Sair", color = OpenfyTextLow)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OpenfyPanelHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(9.dp)
                                .background(OpenfyAccent, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Conta sincronizada",
                            color = OpenfyTextHigh,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        session.email.ifBlank { "Conta Openfy" },
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                    )
                    Text(
                        "Dispositivo: ${state.deviceName}",
                        color = OpenfyTextLow,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.refreshLibrary() },
                            enabled = !state.busy,
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = OpenfyInk,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Sincronizar")
                        }
                        TextButton(onClick = { newPlaylistOpen = true }, enabled = !state.busy) {
                            Text("Nova playlist", color = OpenfyAccent2)
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        state.message?.let { message ->
            item {
                Text(message, color = OpenfyAccent, fontSize = 12.sp)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Suas playlists",
                    color = OpenfyTextHigh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.playlists.size}",
                    color = OpenfyTextLow,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.playlists.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Nenhuma playlist sincronizada ainda.",
                            color = OpenfyTextHigh,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Crie uma aqui ou importe no PC; ela aparece nos dois aparelhos.",
                            color = OpenfyTextLow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        } else {
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist)
            }
        }

        item {
            Text(
                "Player, busca, downloads e colaboração chegam nas próximas etapas.",
                color = OpenfyTextLow.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )
        }
    }

    if (newPlaylistOpen) {
        NewPlaylistDialog(
            onDismiss = { newPlaylistOpen = false },
            onCreate = { name ->
                newPlaylistOpen = false
                viewModel.createPlaylist(name)
            },
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpenfyPanel),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp)
                    .background(OpenfyPanelHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("♫", color = OpenfyAccent2, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = OpenfyTextHigh,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Sincronizada com a conta",
                    color = OpenfyTextLow,
                    fontSize = 11.sp,
                )
            }
            Text("›", color = OpenfyTextLow, fontSize = 20.sp)
        }
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpenfyPanelHigh,
        title = { Text("Nova playlist", color = OpenfyTextHigh) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) {
                Text("Criar", color = OpenfyAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OpenfyTextLow)
            }
        },
    )
}
