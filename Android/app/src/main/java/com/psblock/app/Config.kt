package com.psblock.app

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent settings — the Android counterpart of config.json in the Windows build.
 * Backed by SharedPreferences. Holds password (hashed), screen-time state, theme, language,
 * auto-start and the custom blocklist.
 */
object Config {
    private const val PREFS = "psblock_config"

    private const val K_PW_PROTECTED = "password_protected"
    private const val K_PW_HASH = "password_hash"
    private const val K_LIMIT = "screen_time_limit"        // minutes, 0 = unlimited
    private const val K_USED = "screen_time_used"          // seconds used today
    private const val K_DATE = "screen_time_date"
    private const val K_AUTOSTART = "auto_start"
    private const val K_CUSTOM = "custom_blocked_sites"    // newline-separated
    private const val K_BLOCKED_COUNT = "blocked_count"
    private const val K_THEME = "theme"                    // system | light | dark
    private const val K_LANG = "lang"                      // hu | en

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!sp.contains(K_LANG)) {
                val def = if (Locale.getDefault().language == "hu") "hu" else "en"
                sp.edit().putString(K_LANG, def).apply()
            }
            if (!sp.contains(K_AUTOSTART)) sp.edit().putBoolean(K_AUTOSTART, true).apply()
            if (!sp.contains(K_THEME)) sp.edit().putString(K_THEME, "system").apply()
            rolloverDay()
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Reset today's usage if the day changed. */
    fun rolloverDay() {
        if (sp.getString(K_DATE, "") != today()) {
            sp.edit().putInt(K_USED, 0).putString(K_DATE, today()).apply()
        }
    }

    // ── Password ──────────────────────────────────────────────────────────
    fun hashPw(pw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((pw + "cb_v1").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    var passwordProtected: Boolean
        get() = sp.getBoolean(K_PW_PROTECTED, false)
        set(v) = sp.edit().putBoolean(K_PW_PROTECTED, v).apply()

    private var passwordHash: String
        get() = sp.getString(K_PW_HASH, "") ?: ""
        set(v) = sp.edit().putString(K_PW_HASH, v).apply()

    fun checkPassword(pw: String): Boolean =
        !passwordProtected || hashPw(pw) == passwordHash

    /** Sets a new password. If one already exists, [oldPw] must match. Returns error key or null. */
    fun setPassword(newPw: String, oldPw: String?): String? {
        if (passwordProtected) {
            if (oldPw == null || hashPw(oldPw) != passwordHash) return "err_pw_old"
        }
        if (newPw.length < 4) return "err_pw_min"
        passwordHash = hashPw(newPw)
        passwordProtected = true
        return null
    }

    fun removePassword(pw: String): Boolean {
        if (hashPw(pw) != passwordHash) return false
        passwordProtected = false
        passwordHash = ""
        return true
    }

    // ── Screen time ───────────────────────────────────────────────────────
    var screenTimeLimit: Int
        get() = sp.getInt(K_LIMIT, 0)
        set(v) = sp.edit().putInt(K_LIMIT, v.coerceIn(0, 1440)).apply()

    var usedSeconds: Int
        get() { rolloverDay(); return sp.getInt(K_USED, 0) }
        set(v) = sp.edit().putInt(K_USED, maxOf(0, v)).putString(K_DATE, today()).apply()

    /** Remaining seconds today, or -1 for unlimited. */
    fun remaining(): Int {
        if (screenTimeLimit <= 0) return -1
        return maxOf(0, screenTimeLimit * 60 - usedSeconds)
    }

    // ── Misc ──────────────────────────────────────────────────────────────
    var autoStart: Boolean
        get() = sp.getBoolean(K_AUTOSTART, true)
        set(v) = sp.edit().putBoolean(K_AUTOSTART, v).apply()

    var blockedCount: Int
        get() = sp.getInt(K_BLOCKED_COUNT, 0)
        set(v) = sp.edit().putInt(K_BLOCKED_COUNT, v).apply()

    var theme: String
        get() = sp.getString(K_THEME, "system") ?: "system"
        set(v) = sp.edit().putString(K_THEME, v).apply()

    var lang: String
        get() = sp.getString(K_LANG, "hu") ?: "hu"
        set(v) = sp.edit().putString(K_LANG, if (v == "en") "en" else "hu").apply()

    var customBlocked: List<String>
        get() = (sp.getString(K_CUSTOM, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        set(list) {
            val cleaned = list
                .map { it.lowercase().replace(Regex("[\\s\\r\\n]"), "").trim() }
                .filter { it.length in 2..99 }
                .distinct()
            sp.edit().putString(K_CUSTOM, cleaned.joinToString("\n")).apply()
        }

    fun addCustom(site: String): Boolean {
        val v = site.lowercase().replace(Regex("[\\s\\r\\n]"), "").trim()
        if (v.length < 2) return false
        val list = customBlocked.toMutableList()
        if (list.contains(v)) return false
        list.add(v)
        customBlocked = list
        return true
    }

    fun removeCustom(site: String) {
        customBlocked = customBlocked.filter { it != site }
    }
}
