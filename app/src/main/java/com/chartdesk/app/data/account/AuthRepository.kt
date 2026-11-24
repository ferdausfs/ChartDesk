package com.chartdesk.app.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "chartdesk_account")

data class AccountState(
    val email: String? = null,
    val signedIn: Boolean = false,
)

/**
 * Local account. Passwords are stored as PBKDF2-HMAC-SHA256 hashes with a random salt; the plain password never
 * touches disk. Cloud sync uses the exported bundle until the Worker sync endpoints are deployed.
 */
@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val email = stringPreferencesKey("email")
        val salt = stringPreferencesKey("salt")
        val hash = stringPreferencesKey("hash")
        val signedIn = booleanPreferencesKey("signed_in")
    }

    val account: Flow<AccountState> = context.accountStore.data.map { prefs ->
        AccountState(email = prefs[Keys.email], signedIn = prefs[Keys.signedIn] ?: false)
    }

    suspend fun register(email: String, password: String): Result<Unit> {
        val clean = email.trim().lowercase()
        if (!clean.contains("@") || clean.length < 5) return Result.failure(IllegalArgumentException("Enter a valid email"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Password needs 6+ characters"))
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(password, salt)
        context.accountStore.edit {
            it[Keys.email] = clean
            it[Keys.salt] = Base64.getEncoder().encodeToString(salt)
            it[Keys.hash] = hash
            it[Keys.signedIn] = true
        }
        return Result.success(Unit)
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        val clean = email.trim().lowercase()
        val current = context.accountStore.data.first()
        val savedEmail = current[Keys.email]
        val salt = current[Keys.salt]
        val hash = current[Keys.hash]
        if (savedEmail == null || salt == null || hash == null) {
            return Result.failure(IllegalStateException("Create an account first"))
        }
        if (savedEmail != clean) return Result.failure(IllegalArgumentException("Unknown email"))
        val valid = PasswordHasher.verify(password, Base64.getDecoder().decode(salt), hash)
        if (!valid) return Result.failure(IllegalArgumentException("Wrong password"))
        context.accountStore.edit { it[Keys.signedIn] = true }
        return Result.success(Unit)
    }

    suspend fun signOut() {
        context.accountStore.edit { it[Keys.signedIn] = false }
    }

}
