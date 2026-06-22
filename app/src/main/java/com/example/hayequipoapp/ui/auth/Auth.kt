package com.example.hayequipoapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ─── LoginScreen ─────────────────────────────────────────
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.hayequipoapp.ui.theme.GreenField
import kotlinx.coroutines.launch

import com.example.hayequipoapp.data.session.SessionManager


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val playerRepository: PlayerRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    init {
        loadExistingPlayer()
    }

    private fun loadExistingPlayer() {
        val firebaseUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val player = playerRepository.getPlayerByUid(firebaseUser.uid)
                if (player != null) {
                    sessionManager.setPlayer(player)
                }
            } catch (e: Exception) {
                    Log.e("AuthViewModel", "Error loading existing player",e)
            }
        }
    }

    private val _authState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val authState = _authState.asStateFlow()

    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = UiState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: throw Exception("Usuario nulo")
                // Crear perfil de jugador si no existe
                val existing = playerRepository.getPlayerByUid(user.uid)
                if (existing == null) {
                    val newPlayer = Player(
                        uid      = user.uid,
                        name     = user.displayName ?: "",
                        email    = user.email ?: "",
                        photoUrl = user.photoUrl?.toString() ?: ""
                    )
                    playerRepository.createPlayer(newPlayer)
                    sessionManager.setPlayer(existing ?: newPlayer)
                } else {
                    sessionManager.setPlayer(existing)
                }
                _authState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Error al iniciar sesión")
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = UiState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val user = auth.currentUser ?: return@launch
                val player = playerRepository.getPlayerByUid(user.uid)
                if (player != null) sessionManager.setPlayer(player)
                _authState.value = UiState.Success(Unit)

            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Error al iniciar sesión")
            }
        }
    }

    fun registerWithEmail(email: String, password: String, name: String) {
        viewModelScope.launch {
            _authState.value = UiState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("Usuario nulo")
                val newPlayer = Player(uid = user.uid, name = name, email = email)
                playerRepository.createPlayer(newPlayer)
                sessionManager.setPlayer(newPlayer)
                _authState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Error al registrarse")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        sessionManager.clear()
    }
}

// Función helper para obtener la Activity desde cualquier Context
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val state   by viewModel.authState.collectAsState()

    var email            by remember { mutableStateOf("") }
    var password         by remember { mutableStateOf("") }
    var isRegisterMode   by remember { mutableStateOf(false) }
    var name             by remember { mutableStateOf("") }
    var googleError      by remember { mutableStateOf("") }
    var isGoogleLoading  by remember { mutableStateOf(false) }

    val webClientId = remember {
        try {
            val resId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            if (resId != 0) {
                val id = context.getString(resId)
                Log.d("GOOGLE_AUTH", "webClientId OK: $id")
                id
            } else {
                Log.e("GOOGLE_AUTH", "default_web_client_id no encontrado")
                null
            }
        } catch (e: Exception) {
            Log.e("GOOGLE_AUTH", "Error obteniendo webClientId: ${e.message}")
            null
        }
    }

    LaunchedEffect(state) {
        if (state is UiState.Success) onLoginSuccess()
    }

    fun signInWithGoogle() {
        val activity = context.findActivity()
        if (activity == null) {
            googleError = "No se pudo obtener la Activity"
            Log.e("GOOGLE_AUTH", "findActivity() devolvió null")
            return
        }
        if (webClientId == null) {
            googleError = "webClientId no disponible"
            return
        }

        scope.launch {
            isGoogleLoading = true
            googleError = ""
            try {
                Log.d("GOOGLE_AUTH", "Iniciando Credential Manager...")
                val credentialManager = CredentialManager.create(context)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                Log.d("GOOGLE_AUTH", "Llamando getCredential con Activity: ${activity.javaClass.simpleName}")
                val result = credentialManager.getCredential(
                    request = request,
                    context = activity           // ← Activity real, no Context genérico
                )

                val credential = result.credential
                Log.d("GOOGLE_AUTH", "Credential type: ${credential.type}")

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    Log.d("GOOGLE_AUTH", "Token obtenido, enviando a Firebase...")
                    viewModel.signInWithGoogle(googleCredential.idToken)
                } else {
                    googleError = "Tipo de credencial inesperado: ${credential.type}"
                    Log.e("GOOGLE_AUTH", googleError)
                }

            } catch (e: GetCredentialCancellationException) {
                Log.w("GOOGLE_AUTH", "Usuario canceló")
                // No mostrar error si el usuario cancela
            } catch (e: GetCredentialException) {
                Log.e("GOOGLE_AUTH", "GetCredentialException: ${e.type} - ${e.message}")
                googleError = "Error: ${e.message}"
            } catch (e: Exception) {
                Log.e("GOOGLE_AUTH", "Exception inesperada: ${e.javaClass.simpleName} - ${e.message}")
                googleError = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                isGoogleLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "HayEquipo",
            style = MaterialTheme.typography.displayLarge,
            color = GreenField
        )
        Text(
            text  = "Organizá tu partido",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Nombre") },
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            modifier      = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = password,
            onValueChange        = { password = it },
            label                = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        if (state is UiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isRegisterMode) viewModel.registerWithEmail(email, password, name)
                    else                viewModel.signInWithEmail(email, password)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Crear cuenta" else "Entrar con email")
            }

            if (webClientId != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        "  o  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick  = { signInWithGoogle() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !isGoogleLoading
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando...")
                    } else {
                        Text("Continuar con Google")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                Text(if (isRegisterMode) "Ya tengo cuenta" else "Crear cuenta nueva")
            }
        }

        val errorMsg = when {
            state is UiState.Error   -> (state as UiState.Error).message
            googleError.isNotBlank() -> googleError
            else -> ""
        }
        if (errorMsg.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
