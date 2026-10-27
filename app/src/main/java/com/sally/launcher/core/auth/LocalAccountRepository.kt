package com.sally.launcher.core.auth

import android.content.Context
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.OfflineAccountProfile

/** Persists local profile names only. Offline identities never contain or persist access tokens. */
object LocalAccountRepository {
    private const val PREFS = "sally_local_accounts"
    private const val KEY_NAMES = "usernames"
    private const val KEY_SELECTED = "selected_username"

    fun list(context: Context): List<OfflineAccountProfile> = preferences(context)
        .getStringSet(KEY_NAMES, emptySet())
        .orEmpty()
        .mapNotNull { name -> runCatching { OfflineAccountProfile.create(name) }.getOrNull() }
        .distinctBy { it.username.lowercase() }
        .sortedBy { it.username.lowercase() }

    fun add(context: Context, rawName: String): OfflineAccountProfile {
        val candidate = OfflineAccountProfile.create(rawName)
        val existing = list(context).firstOrNull { it.username.equals(candidate.username, ignoreCase = true) }
        val selected = existing ?: candidate
        val names = list(context).map { it.username }.toMutableSet().apply { add(selected.username) }
        preferences(context).edit()
            .putStringSet(KEY_NAMES, names)
            .putString(KEY_SELECTED, selected.username)
            .apply()
        MinecraftLaunchSessionStore.installLocalAccount(selected.toSession())
        return selected
    }

    fun activate(context: Context, rawName: String): OfflineAccountProfile {
        val selected = list(context).firstOrNull { it.username.equals(rawName.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("That local account no longer exists.")
        preferences(context).edit().putString(KEY_SELECTED, selected.username).apply()
        MinecraftLaunchSessionStore.installLocalAccount(selected.toSession())
        return selected
    }

    fun remove(context: Context, rawName: String) {
        val currentNames = list(context).map { it.username }
        val match = currentNames.firstOrNull { it.equals(rawName.trim(), ignoreCase = true) } ?: return
        val prefs = preferences(context)
        prefs.edit().putStringSet(KEY_NAMES, currentNames.filterNot { it.equals(match, ignoreCase = true) }.toSet()).apply()
        if (prefs.getString(KEY_SELECTED, null).equals(match, ignoreCase = true)) {
            prefs.edit().remove(KEY_SELECTED).apply()
        }
        val active = MinecraftLaunchSessionStore.current()
        if (active?.accountType == MinecraftAccountType.LOCAL && active.username.equals(match, ignoreCase = true)) {
            MinecraftLaunchSessionStore.clear()
        }
    }

    /** Called after successful Microsoft auth so a stale local selection is not restored on next process start. */
    fun clearLocalSelectionForMicrosoft(context: Context) {
        preferences(context).edit().remove(KEY_SELECTED).apply()
    }

    /** Restores a saved local identity after process recreation, but never overrides a live Microsoft session. */
    fun restoreSelectedSession(context: Context) {
        if (MinecraftLaunchSessionStore.current() != null) return
        val selected = preferences(context).getString(KEY_SELECTED, null) ?: return
        val account = list(context).firstOrNull { it.username.equals(selected, ignoreCase = true) } ?: return
        runCatching { MinecraftLaunchSessionStore.installLocalAccount(account.toSession()) }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
