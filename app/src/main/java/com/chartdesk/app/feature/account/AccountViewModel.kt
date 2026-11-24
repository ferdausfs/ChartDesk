package com.chartdesk.app.feature.account

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.data.account.AccountState
import com.chartdesk.app.data.account.AuthRepository
import com.chartdesk.app.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val sync: SyncManager,
) : ViewModel() {

    val account: StateFlow<AccountState> = auth.account.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountState(),
    )

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            auth.register(email, password)
                .onSuccess { _ui.value = AccountUiState(message = "Account created") }
                .onFailure { _ui.value = AccountUiState(error = it.message) }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            auth.login(email, password)
                .onSuccess { _ui.value = AccountUiState(message = "Signed in") }
                .onFailure { _ui.value = AccountUiState(error = it.message) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _ui.value = AccountUiState(message = "Signed out")
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, error = null)
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            runCatching {
                val payload = sync.exportJson()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
                        ?: error("Could not open the target file")
                }
            }.onSuccess { _ui.value = AccountUiState(message = "Backup written") }
                .onFailure { _ui.value = AccountUiState(error = it.message ?: "Export failed") }
        }
    }

    fun import(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the file")
                }
                sync.importJson(raw).getOrThrow()
            }.onSuccess { count -> _ui.value = AccountUiState(message = "Imported $count rows") }
                .onFailure { _ui.value = AccountUiState(error = it.message ?: "Import failed") }
        }
    }
}
