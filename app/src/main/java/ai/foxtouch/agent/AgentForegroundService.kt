package ai.foxtouch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.foxtouch.MainActivity
import ai.foxtouch.R
import ai.foxtouch.accessibility.TouchAnimationOverlay
import ai.foxtouch.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive while the agent is working.
 *
 * When the agent transitions to a non-Idle state, this service starts and shows
 * a persistent notification with the current status. If the agent requests tool
 * approval, the notification gains Allow / Deny / Always Allow action buttons,
 * allowing the user to approve from any app without returning to FoxTouch.
 *
 * The service automatically stops itself when the agent returns to [AgentState.Idle].
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var agentRunner: AgentRunner
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    private var stateJob: Job? = null
    private var stopSelfJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "foxtouch_agent_status"
        private const val CHANNEL_ID_APPROVAL = "${CHANNEL_ID}_approval"
        private const val NOTIFICATION_ID = 1002

        private const val ACTION_ALLOW = "ai.foxtouch.agent.ALLOW"
        private const val ACTION_DENY = "ai.foxtouch.agent.DENY"
        private const val ACTION_ALWAYS_ALLOW = "ai.foxtouch.agent.ALWAYS_ALLOW"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AgentForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildStatusNotification("Working..."))
        // Keep screen on via overlay window flag — more reliable than wake locks
        // (SCREEN_DIM_WAKE_LOCK is deprecated and ignored by Samsung OneUI)
        TouchAnimationOverlay.setKeepScreenOn(true)
        observeAgentState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ALLOW -> agentRunner.respondToApproval(ApprovalResponse.ALLOW)
            ACTION_DENY -> agentRunner.respondToApproval(ApprovalResponse.DENY)
            ACTION_ALWAYS_ALLOW -> agentRunner.respondToApproval(ApprovalResponse.ALWAYS_ALLOW)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        stopSelfJob?.cancel()
        TouchAnimationOverlay.setKeepScreenOn(false)
        super.onDestroy()
    }

    private fun observeAgentState() {
        stateJob = applicationScope.launch {
            agentRunner.state.collect { state ->
                when (state) {
                    is AgentState.Idle -> {
                        // Debounce stopSelf to avoid killing the service right before
                        // a new turn starts (e.g., rapid successive messages).
                        stopSelfJob?.cancel()
                        stopSelfJob = launch {
                            delay(500)
                            stopSelf()
                        }
                    }
                    is AgentState.Thinking -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Thinking..."))
                    }
                    is AgentState.Acting -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Running: ${state.toolName}"))
                    }
                    is AgentState.WaitingApproval -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildApprovalNotification(state))
                    }
                    is AgentState.PlanReview -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Reviewing plan..."))
                    }
                    is AgentState.AskingUser -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Waiting for your answer..."))
                    }
                    is AgentState.ConfirmingCompletion -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Task complete?"))
                    }
                    is AgentState.Error -> {
                        stopSelfJob?.cancel()
                        updateNotification(buildStatusNotification("Error occurred"))
                    }
                }
            }
        }
    }

    private fun updateNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Agent Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows FoxTouch agent activity while working in other apps"
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_APPROVAL,
                "Tool Approval",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Requests your approval when FoxTouch needs to use a tool"
            },
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildStatusNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoxTouch")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun buildApprovalNotification(state: AgentState.WaitingApproval): Notification {
        val argsPreview = state.args.toString().take(100)

        fun actionIntent(action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                this,
                requestCode,
                Intent(this, AgentForegroundService::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID_APPROVAL)
            .setContentTitle("Approve: ${state.toolName}")
            .setContentText(argsPreview)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Allow", actionIntent(ACTION_ALLOW, 1))
            .addAction(0, "Deny", actionIntent(ACTION_DENY, 2))
            .addAction(0, "Always Allow", actionIntent(ACTION_ALWAYS_ALLOW, 3))
            .build()
    }
}
