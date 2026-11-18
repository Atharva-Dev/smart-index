package com.kuriousdragon.smartindex.tools.seeder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.kuriousdragon.smartindex.tools.seeder.role.SmsRoleController
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import java.io.File

/**
 * The exported adb entry point (LLD §3.4): parses the extras, checks grants, runs the SMS-role flow when needed,
 * then hands the command to [SeedService]. `status` and `restore-sms` are answered here.
 */
class SeedActivity : ComponentActivity() {
    private var pending: SeedCommand? = null
    private var pendingExtras: Bundle? = null

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val command = pending
        pending = null
        if (command == null) {
            finish()
        } else if (result.resultCode == Activity.RESULT_OK) {
            startService(command)
        } else if (command.cmd == SeedCmd.WIPE) {
            // LLD §5.2: refusal only skips SMS/MMS; everything else is still wiped.
            SeedLog.warn("wipe-sms-skipped")
            startService(command)
        } else {
            SeedLog.error("sms-role refused")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val extras = intent.extras
        pendingExtras = extras
        val command = SeedCommand.parse(extras.toExtrasMap()).getOrElse {
            SeedLog.error(it.message.orEmpty())
            finish()
            return
        }
        when (command.cmd) {
            SeedCmd.STATUS -> {
                logStatus(this)
                finish()
            }

            SeedCmd.RESTORE_SMS -> {
                logRestoreHint(this)
                startActivity(SmsRoleController(this).defaultAppsSettingsIntent())
                finish()
            }

            SeedCmd.SEED, SeedCmd.WIPE, SeedCmd.FRESH -> {
                if (!grantsOk()) {
                    finish()
                    return
                }
                val wantsRole = command.needsSmsRole || command.cmd == SeedCmd.WIPE
                val role = SmsRoleController(this)
                if (wantsRole && !role.isHeld) {
                    role.rememberPrevious()
                    val request = role.requestIntent()
                    if (request == null) {
                        SeedLog.error("sms-role unavailable")
                        finish()
                        return
                    }
                    pending = command
                    roleRequest.launch(request)
                } else {
                    startService(command)
                }
            }
        }
    }

    private fun startService(@Suppress("UNUSED_PARAMETER") command: SeedCommand) {
        val svc = Intent(this, SeedService::class.java)
        pendingExtras?.let { svc.putExtras(it) }
        startForegroundService(svc)
        finish()
    }

    private fun grantsOk(): Boolean {
        val missing = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val noFiles = !Environment.isExternalStorageManager()
        if (missing.isEmpty() && !noFiles) return true
        SeedLog.error("missing-grants; run:")
        val p = packageName
        missing.forEach { SeedLog.line("adb shell pm grant $p $it") }
        if (noFiles) SeedLog.line("adb shell appops set --uid $p MANAGE_EXTERNAL_STORAGE allow")
        return false
    }

    companion object {
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        fun logStatus(context: Context) {
            val role = SmsRoleController(context)
            SeedLog.line("role held=${role.isHeld} previous=${role.previousPackage.orEmpty()}")
            val counts = Ledger.readAllRuns(File(context.filesDir, "ledger")).groupingBy { it.kind }.eachCount()
            SeedLog.line("ledger " + counts.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" })
        }

        fun logRestoreHint(context: Context) {
            val command = SmsRoleController(context).restoreCommand()
            if (command != null) {
                SeedLog.line("restore-sms run: $command")
            } else {
                SeedLog.line("restore-sms no previous default SMS app is recorded")
            }
        }
    }
}
