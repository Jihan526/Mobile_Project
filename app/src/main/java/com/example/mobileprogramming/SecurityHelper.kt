package com.example.mobileprogramming

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurityHelper {
    private const val PREF_FILE_NAME = "secure_user_prefs"
    
    private const val KEY_CURRENT_USER = "current_user" // Holds current logged in username, or "guest"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_NICKNAME = "user_nickname"
    private const val KEY_REGISTERED_USERS = "registered_users" // format: "username:password:nickname|..."
    
    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return try {
            EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(PREF_FILE_NAME)
                } else {
                    context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun getCurrentUser(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_CURRENT_USER, "guest") ?: "guest"
    }

    fun isLoggedIn(context: Context): Boolean {
        return getEncryptedPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(context: Context, loggedIn: Boolean, username: String, nickname: String, grade: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            putString(KEY_CURRENT_USER, username)
            putString(KEY_NICKNAME + "_" + username, nickname)
            putString("user_grade_" + username, grade)
            apply()
        }
    }

    fun getNickname(context: Context): String {
        val username = getCurrentUser(context)
        if (username == "guest") return "게스트"
        return getEncryptedPrefs(context).getString(KEY_NICKNAME + "_" + username, "사용자") ?: "사용자"
    }

    fun getCreationCount(context: Context): Int {
        val username = getCurrentUser(context)
        return getEncryptedPrefs(context).getInt("creation_count_" + username, 0)
    }

    fun incrementCreationCount(context: Context) {
        val prefs = getEncryptedPrefs(context)
        val username = getCurrentUser(context)
        val key = "creation_count_" + username
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    fun getUserGrade(context: Context): String {
        val username = getCurrentUser(context)
        if (username == "guest") return "비회원"
        return getEncryptedPrefs(context).getString("user_grade_" + username, "일반회원") ?: "일반회원"
    }

    fun setUserGrade(context: Context, grade: String) {
        val prefs = getEncryptedPrefs(context)
        val username = getCurrentUser(context)
        prefs.edit().putString("user_grade_" + username, grade).apply()
    }

    fun isLimitExceeded(context: Context): Boolean {
        val grade = getUserGrade(context)
        val count = getCreationCount(context)
        return when (grade) {
            "비회원" -> count >= 2
            "일반회원" -> count >= 6
            else -> false // Premium is unlimited
        }
    }
    
    fun getMaxLimit(context: Context): Int {
        return when (getUserGrade(context)) {
            "비회원" -> 2
            "일반회원" -> 6
            else -> Integer.MAX_VALUE
        }
    }

    fun logout(context: Context) {
        getEncryptedPrefs(context).edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            putString(KEY_CURRENT_USER, "guest")
            apply()
        }
    }

    fun withdrawUser(context: Context, username: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val rawUsers = prefs.getString(KEY_REGISTERED_USERS, "") ?: ""
        if (rawUsers.isEmpty()) return false
        
        val usersList = rawUsers.split("|")
        val updatedList = mutableListOf<String>()
        var found = false
        for (user in usersList) {
            val parts = user.split(":")
            if (parts.size >= 3 && parts[0] == username) {
                found = true
                continue
            }
            updatedList.add(user)
        }
        
        if (!found) return false
        
        val updatedUsers = updatedList.joinToString("|")
        prefs.edit().apply {
            putString(KEY_REGISTERED_USERS, updatedUsers)
            remove(KEY_NICKNAME + "_" + username)
            remove("user_grade_" + username)
            remove("creation_count_" + username)
            apply()
        }
        
        logout(context)
        return true
    }

    // Simple registration helper: registers a new user (stores in a pipe-separated string in SharedPreferences)
    fun registerUser(context: Context, usernameInput: String, passwordInput: String, nicknameInput: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val rawUsers = prefs.getString(KEY_REGISTERED_USERS, "") ?: ""
        
        // Split by '|' to check existing users
        val usersList = if (rawUsers.isNotEmpty()) rawUsers.split("|") else emptyList()
        for (user in usersList) {
            val parts = user.split(":")
            if (parts.size >= 3 && parts[0] == usernameInput) {
                return false // User already exists
            }
        }

        // Add the new user
        val separator = if (rawUsers.isEmpty()) "" else "|"
        val updatedUsers = rawUsers + separator + "$usernameInput:$passwordInput:$nicknameInput"
        prefs.edit().putString(KEY_REGISTERED_USERS, updatedUsers).apply()
        return true
    }

    // Simple authentication helper
    fun authenticateUser(context: Context, usernameInput: String, passwordInput: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val rawUsers = prefs.getString(KEY_REGISTERED_USERS, "") ?: ""
        val usersList = if (rawUsers.isNotEmpty()) rawUsers.split("|") else emptyList()
        for (user in usersList) {
            val parts = user.split(":")
            if (parts.size >= 3 && parts[0] == usernameInput && parts[1] == passwordInput) {
                // Log in
                setLoggedIn(context, true, usernameInput, parts[2], "일반회원")
                return true
            }
        }
        return false
    }
}
