package com.kuriousdragon.smartindex.tools.seeder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.gen.Generator
import com.kuriousdragon.smartindex.tools.seeder.media.MediaPhases
import com.kuriousdragon.smartindex.tools.seeder.media.MediaStoreWriter
import com.kuriousdragon.smartindex.tools.seeder.media.SparseFiles
import com.kuriousdragon.smartindex.tools.seeder.role.SeederPrefs
import com.kuriousdragon.smartindex.tools.seeder.role.SmsRoleController
import com.kuriousdragon.smartindex.tools.seeder.tel.CallLogWriter
import com.kuriousdragon.smartindex.tools.seeder.tel.ContactsWriter
import com.kuriousdragon.smartindex.tools.seeder.tel.FreshWriter
import com.kuriousdragon.smartindex.tools.seeder.tel.MmsWriter
import com.kuriousdragon.smartindex.tools.seeder.tel.SmsWriter
import com.kuriousdragon.smartindex.tools.seeder.tel.TelPhases
import com.kuriousdragon.smartindex.tools.seeder.tel.ThreadResolver
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import com.kuriousdragon.smartindex.tools.seeder.wipe.Wiper
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground (dataSync) service running a [SeedRun] or a wipe, with a progress notification (LLD §3.4, §5). */
class SeedService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            progressNotification("Starting"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val command = SeedCommand.parse(intent?.extras.toExtrasMap()).getOrElse {
            SeedLog.error(it.message.orEmpty())
            finish(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                execute(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SeedLog.error("run ${e.javaClass.simpleName}")
            } finally {
                finish(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun execute(command: SeedCommand) {
        val ledgerDir = File(filesDir, "ledger")
        when (command.cmd) {
            SeedCmd.WIPE -> {
                val role = SmsRoleController(this)
                Wiper(
                    resolver = contentResolver,
                    ledgerDir = ledgerDir,
                    cacheDir = File(filesDir, "templates"),
                    keepSmsIds = SeederPrefs(this).keepSmsIds,
                    smsRoleHeld = role.isHeld,
                    ownPackage = packageName,
                ).wipe()
                SeedActivity.logRestoreHint(this)
            }

            SeedCmd.SEED -> {
                if (freeBytes() < MIN_FREE_BYTES) {
                    SeedLog.error("low-space")
                    return
                }
                val ledger = Ledger(ledgerDir, "run-" + UUID.randomUUID().toString().take(8))
                val role = SmsRoleController(this)
                val plan = Generator(
                    command.profile,
                    command.seed,
                    command.anchor,
                    ZoneId.systemDefault(),
                    command.only.mapTo(HashSet()) { it.key },
                ).plan()
                val threads = ThreadResolver { Telephony.Threads.getOrCreateThreadId(this, it) }
                val tel = TelPhases.build(
                    plan = plan,
                    contacts = ContactsWriter(contentResolver),
                    calls = CallLogWriter(contentResolver),
                    sms = SmsWriter(contentResolver, threads),
                    mms = MmsWriter(contentResolver, threads),
                    isSmsRoleHeld = { role.isHeld },
                )
                val phases = (tel + MediaPhases.build(plan, mediaWriter(ledger, command, plan.zone)))
                    .filter { phaseSelected(it.name, command) }
                SeedRun(
                    ctx = SeedContext(command, ledger, plan, File(filesDir, "seed-manifest.json")),
                    phases = phases,
                    onRoleReleaseNow = { releaseRoleNow(role) },
                ).run()
            }

            SeedCmd.FRESH -> {
                val type = command.freshType
                val token = command.freshToken
                if (type == null || token == null) {
                    SeedLog.error("bad-arg fresh")
                    return
                }
                val ledger = Ledger(ledgerDir, "fresh-" + UUID.randomUUID().toString().take(8))
                FreshWriter(
                    contentResolver,
                    ThreadResolver { Telephony.Threads.getOrCreateThreadId(this, it) },
                    media = mediaWriter(ledger, command, ZoneId.systemDefault()),
                ).write(type, token, ledger)
                ledger.flush()
            }

            SeedCmd.STATUS, SeedCmd.RESTORE_SMS -> Unit // handled by SeedActivity
        }
    }

    private fun mediaWriter(ledger: Ledger, command: SeedCommand, zone: ZoneId) =
        MediaStoreWriter(this, ledger, SparseFiles(command.sparse, ::freeBytes), zone)

    private fun phaseSelected(name: String, command: SeedCommand): Boolean = when (name) {
        "CONTACTS" -> SeedKind.CONTACTS in command.only
        "CALLS" -> SeedKind.CALLS in command.only
        "SMS" -> SeedKind.SMS in command.only && !command.skipSms
        "MMS" -> SeedKind.MMS in command.only && !command.skipSms
        "PHOTOS" -> SeedKind.PHOTOS in command.only
        "VIDEOS" -> SeedKind.VIDEOS in command.only
        "DOCUMENTS" -> SeedKind.DOCUMENTS in command.only
        "OTHERS" -> SeedKind.OTHERS in command.only
        "RECORDINGS" -> SeedKind.RECORDINGS in command.only
        else -> true
    }

    private fun releaseRoleNow(role: SmsRoleController) {
        if (!role.isHeld) return
        val previous = role.previousPackage ?: return
        SeedLog.line("role release-now previous=$previous")
        SeedLog.line(role.restoreCommand().orEmpty())
        val open = PendingIntent.getActivity(
            this,
            0,
            role.defaultAppsSettingsIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(previous, 0)).toString()
        }.getOrDefault(previous)
        getSystemService(NotificationManager::class.java).notify(
            RELEASE_NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Restore $label as default SMS app")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun finish(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes

    private fun progressNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Seeder progress", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SIDX Seeder")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "seeder-progress"
        const val NOTIFICATION_ID = 1
        const val RELEASE_NOTIFICATION_ID = 2
        const val MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024
    }
}
