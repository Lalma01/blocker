package com.psblock.app

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Settings — Android port of settings.html (theme, language, password, screen time, auto-start, blocklist). */
class SettingsActivity : AppCompatActivity() {

    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.closeSettingsBtn).setOnClickListener { finish() }

        // Password gate: block the form until unlocked, exactly like the lock overlay.
        if (Config.passwordProtected) showLockGate() else { unlocked = true; bind() }
    }

    private fun showLockGate() {
        findViewById<View>(R.id.settingsContent).visibility = View.GONE
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lock_h2)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.lock_btn, null)
            .setNegativeButton(R.string.close_btn) { _, _ -> finish() }
            .show()
            .also { dlg ->
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (Config.checkPassword(input.text.toString())) {
                        unlocked = true; dlg.dismiss()
                        findViewById<View>(R.id.settingsContent).visibility = View.VISIBLE
                        bind()
                    } else {
                        Toast.makeText(this, R.string.lock_wrong, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun bind() {
        bindTheme()
        bindLang()
        bindPassword()
        bindScreenTime()
        bindAutoStart()
        bindBlocklist()
    }

    private fun bindTheme() {
        val sp = findViewById<Spinner>(R.id.themeSpinner)
        sp.adapter = ArrayAdapter.createFromResource(this, R.array.theme_options, android.R.layout.simple_spinner_dropdown_item)
        sp.setSelection(when (Config.theme) { "light" -> 1; "dark" -> 2; else -> 0 })
        sp.onItemSelected { pos ->
            val v = when (pos) { 1 -> "light"; 2 -> "dark"; else -> "system" }
            if (v != Config.theme) { Config.theme = v; PsBlockApp.applyTheme(v); recreate() }
        }
    }

    private fun bindLang() {
        val sp = findViewById<Spinner>(R.id.langSpinner)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Magyar", "English"))
        sp.setSelection(if (Config.lang == "en") 1 else 0)
        sp.onItemSelected { pos ->
            val v = if (pos == 1) "en" else "hu"
            if (v != Config.lang) { Config.lang = v; recreate() }
        }
    }

    private fun bindPassword() {
        val newPw = findViewById<EditText>(R.id.newPw)
        val confirmPw = findViewById<EditText>(R.id.confirmPw)
        val removeBtn = findViewById<Button>(R.id.removePwBtn)
        removeBtn.visibility = if (Config.passwordProtected) View.VISIBLE else View.GONE

        findViewById<Button>(R.id.setPwBtn).setOnClickListener {
            val p1 = newPw.text.toString(); val p2 = confirmPw.text.toString()
            if (p1.length < 4) { toast(R.string.pw_min_msg); return@setOnClickListener }
            if (p1 != p2) { toast(R.string.pw_nomatch_msg); return@setOnClickListener }
            if (Config.passwordProtected) {
                promptPassword(R.string.pw_prompt_old) { old ->
                    applySetPassword(p1, old, newPw, confirmPw, removeBtn)
                }
            } else applySetPassword(p1, null, newPw, confirmPw, removeBtn)
        }

        removeBtn.setOnClickListener {
            promptPassword(R.string.pw_del_prompt) { pw ->
                if (Config.removePassword(pw)) {
                    toast(R.string.pw_deleted); removeBtn.visibility = View.GONE
                    DevicePolicy.relax(this)   // allow uninstall again
                } else toast(R.string.lock_wrong)
            }
        }
    }

    private fun applySetPassword(p1: String, old: String?, newPw: EditText, confirmPw: EditText, removeBtn: Button) {
        val err = Config.setPassword(p1, old)
        if (err == null) {
            toast(R.string.pw_set_ok); newPw.text.clear(); confirmPw.text.clear()
            removeBtn.visibility = View.VISIBLE
            DevicePolicy.apply(this)   // block uninstall now that protection is on
            promptEnableAdmin()
        } else toast(resources.getIdentifier(err, "string", packageName).let { if (it != 0) it else R.string.pw_err })
    }

    private fun bindScreenTime() {
        val limit = findViewById<EditText>(R.id.screenLimit)
        limit.setText(Config.screenTimeLimit.toString())
        findViewById<Button>(R.id.saveTimeBtn).setOnClickListener {
            val v = limit.text.toString().toIntOrNull() ?: 0
            if (Config.passwordProtected) {
                promptPassword(R.string.time_prompt_pw) { pw ->
                    if (Config.checkPassword(pw)) { Config.screenTimeLimit = v; toast(R.string.msg_saved) }
                    else toast(R.string.err_pw_wrong)
                }
            } else { Config.screenTimeLimit = v; toast(R.string.msg_saved) }
        }
    }

    private fun bindAutoStart() {
        val sw = findViewById<SwitchCompat>(R.id.autoStartSwitch)
        sw.isChecked = Config.autoStart
        findViewById<Button>(R.id.saveAutoBtn).setOnClickListener {
            Config.autoStart = sw.isChecked
            toast(R.string.msg_saved)
        }
    }

    private fun bindBlocklist() {
        val input = findViewById<EditText>(R.id.newSite)
        findViewById<Button>(R.id.addSiteBtn).setOnClickListener {
            val v = input.text.toString().trim().lowercase()
            if (v.isEmpty()) return@setOnClickListener
            if (Config.addCustom(v)) { input.text.clear(); renderTags(); toast(R.string.block_added) }
            else toast(R.string.block_exists)
        }
        renderTags()
    }

    private fun renderTags() {
        val container = findViewById<LinearLayout>(R.id.tagList)
        container.removeAllViews()
        for (site in Config.customBlocked) {
            val row = layoutInflater.inflate(R.layout.item_tag, container, false)
            row.findViewById<TextView>(R.id.tagText).text = site
            row.findViewById<View>(R.id.tagRemove).setOnClickListener {
                Config.removeCustom(site); renderTags()
            }
            container.addView(row)
        }
    }

    private fun promptPassword(msgRes: Int, onOk: (String) -> Unit) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        MaterialAlertDialogBuilder(this)
            .setTitle(msgRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(R.string.st_lock_cancel, null)
            .show()
    }

    /** Offer to enable device admin (uninstall friction) when not already owner/admin. */
    private fun promptEnableAdmin() {
        if (DevicePolicy.isOwner(this) || DevicePolicy.isAdminActive(this)) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DevicePolicy.admin(this))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_disable_warning))
        try { startActivity(intent) } catch (_: Exception) {}
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun Spinner.onItemSelected(cb: (Int) -> Unit) {
        var first = true
        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                cb(pos)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }
}
