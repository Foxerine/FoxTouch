package ai.foxtouch.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ai.foxtouch.FoxTouchApp
import ai.foxtouch.MainActivity
import ai.foxtouch.R
import ai.foxtouch.accessibility.OverlayController
import ai.foxtouch.agent.AgentOutput
import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.agent.AgentState
import ai.foxtouch.agent.ApprovalResponse
import ai.foxtouch.agent.CompletionResponse
import ai.foxtouch.agent.PlanApprovalResponse
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.preferences.AgentMode
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.ui.screens.chat.TaskProgress
import ai.foxtouch.di.ApplicationScope
import ai.foxtouch.ui.theme.FoxTouchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Floating overlay service — compact bottom bar design.
 *
 * Shows a thin, draggable status bar at the bottom of the screen during agent execution.
 * Expands inline to show approval cards, plan review, or ask-user prompts.
 * Auto-hides when the user is inside FoxTouch app.
 */
@AndroidEntryPoint
class FloatingBubbleService : Service() {

    @Inject lateinit var agentRunner: AgentRunner
    @Inject lateinit var appSettings: AppSettings
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    private var windowManager: WindowManager? = null
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var isExpanded by mutableStateOf(false)
    private var recentMessages = mutableListOf<String>()
    private var lastMessage by mutableStateOf<String?>(null)
    /** Whether the overlay needs keyboard input (user question or plan review visible). */
    private var needsInput by mutableStateOf(false)
    private var showCompletionSuccess by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stateJob: Job? = null
    private var busyJob: Job? = null
    private var messageJob: Job? = null
    private var foregroundJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "foxtouch_bubble"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
        addPanelView()
        observeAutoExpand()
        observeBusy()
        observeMessages()
        observeForeground()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stateJob?.cancel()
        busyJob?.cancel()
        messageJob?.cancel()
        foregroundJob?.cancel()
        removeOverlayView(panelView)
        panelView = null
        lifecycleOwner.onDestroy()
    }

    /** Auto-expand when agent needs user attention — only when app is NOT in foreground. */
    private fun observeAutoExpand() {
        stateJob = applicationScope.launch {
            agentRunner.state.collect { state ->
                when (state) {
                    is AgentState.WaitingApproval,
                    is AgentState.PlanReview -> mainHandler.post {
                        isExpanded = true
                        updateFocusability(false)
                        if (!FoxTouchApp.ForegroundTracker.isForeground.value) {
                            showPanel()
                        }
                    }
                    is AgentState.ConfirmingCompletion -> mainHandler.post {
                        isExpanded = true
                        updateFocusability(true)
                        if (!FoxTouchApp.ForegroundTracker.isForeground.value) {
                            showPanel()
                        }
                    }
                    is AgentState.AskingUser -> mainHandler.post {
                        isExpanded = true
                        updateFocusability(true)
                        if (!FoxTouchApp.ForegroundTracker.isForeground.value) {
                            showPanel()
                        }
                    }
                    else -> mainHandler.post {
                        updateFocusability(false)
                    }
                }
            }
        }
    }

    /** Toggle FLAG_NOT_FOCUSABLE to allow/disallow keyboard input in the overlay. */
    private fun updateFocusability(focusable: Boolean) {
        if (needsInput == focusable) return
        needsInput = focusable
        val params = panelParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        panelView?.let {
            try { windowManager?.updateViewLayout(it, params) } catch (_: Exception) {}
        }
    }

    /** Show overlay only during task execution — hide when idle. */
    private fun observeBusy() {
        busyJob = applicationScope.launch {
            agentRunner.isBusy.collect { busy ->
                mainHandler.post {
                    if (busy) {
                        recentMessages.clear()
                        lastMessage = null
                        if (!FoxTouchApp.ForegroundTracker.isForeground.value) {
                            showPanel()
                        }
                    } else {
                        mainHandler.postDelayed({
                            if (!agentRunner.isBusy.value) {
                                isExpanded = false
                                hidePanel()
                            }
                        }, 3000)
                    }
                }
            }
        }
    }

    /** Collect assistant text messages for display — keeps last 5 messages. */
    private fun observeMessages() {
        messageJob = applicationScope.launch {
            agentRunner.outputFlow.collect { output ->
                if (output is AgentOutput.Text) {
                    mainHandler.post {
                        recentMessages.add(output.content)
                        if (recentMessages.size > 5) recentMessages.removeAt(0)
                        lastMessage = recentMessages.last()
                    }
                }
            }
        }
    }

    /** Hide overlay when FoxTouch is in the foreground. */
    private fun observeForeground() {
        foregroundJob = applicationScope.launch {
            FoxTouchApp.ForegroundTracker.isForeground.collect { inForeground ->
                mainHandler.post {
                    if (inForeground) {
                        hidePanel()
                    } else if (agentRunner.isBusy.value) {
                        showPanel()
                    }
                }
            }
        }
    }

    private fun showPanel() {
        panelView?.visibility = View.VISIBLE
    }

    private fun hidePanel() {
        updateFocusability(false)
        panelView?.visibility = View.GONE
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    // ── Panel ─────────────────────────────────────────────────────────

    private fun addPanelView() {
        val view = ComposeView(this)
        // params must be declared before setContent so lambdas can capture it
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
        }

        view.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FoxTouchTheme {
                    val state by agentRunner.state.collectAsState()
                    val isBusy by agentRunner.isBusy.collectAsState()
                    val agentMode by appSettings.agentMode.collectAsState(initial = AgentMode.NORMAL)
                    val tasks by agentRunner.sessionTasks.collectAsState()
                    val taskProgress = remember(tasks) {
                        TaskProgress(
                            total = tasks.size,
                            completed = tasks.count { it.status == "completed" },
                            inProgress = tasks.count { it.status == "in_progress" },
                            failed = tasks.count { it.status == "failed" },
                            pending = tasks.count { it.status == "pending" },
                        )
                    }

                    OverlayBar(
                        state = state,
                        isBusy = isBusy,
                        agentMode = agentMode,
                        isExpanded = isExpanded,
                        lastMessage = lastMessage,
                        tasks = tasks,
                        taskProgress = taskProgress,
                        onCycleAgentMode = {
                            val next = when (agentMode) {
                                AgentMode.NORMAL -> AgentMode.PLAN
                                AgentMode.PLAN -> AgentMode.YOLO
                                AgentMode.YOLO -> AgentMode.NORMAL
                            }
                            applicationScope.launch { appSettings.setAgentMode(next) }
                        },
                        onToggleExpand = {
                            isExpanded = !isExpanded
                            if (!isExpanded) updateFocusability(false)
                        },
                        onStop = { agentRunner.cancelCurrentTurn() },
                        onOpenApp = { openMainActivity() },
                        onApprove = { agentRunner.respondToApproval(ApprovalResponse.ALLOW) },
                        onDeny = { agentRunner.respondToApproval(ApprovalResponse.DENY) },
                        onAlwaysAllow = { agentRunner.respondToApproval(ApprovalResponse.ALWAYS_ALLOW) },
                        onPlanApproveNormal = {
                            agentRunner.respondToPlanApproval(PlanApprovalResponse.ApproveNormal())
                        },
                        onPlanApproveYolo = {
                            agentRunner.respondToPlanApproval(PlanApprovalResponse.ApproveYolo())
                        },
                        onPlanReject = {
                            agentRunner.respondToPlanApproval(PlanApprovalResponse.Reject)
                        },
                        onUserAnswer = { answer ->
                            agentRunner.respondToUserQuestion(answer)
                        },
                        showCompletionSuccess = showCompletionSuccess,
                        onCompletionConfirm = {
                            agentRunner.respondToCompletion(CompletionResponse.Confirmed)
                            showCompletionSuccess = true
                            mainHandler.postDelayed({
                                showCompletionSuccess = false
                                isExpanded = false
                                hidePanel()
                            }, 1500)
                        },
                        onCompletionReject = { reason ->
                            agentRunner.respondToCompletion(CompletionResponse.NotDone(reason))
                        },
                        onCompletionDismiss = {
                            agentRunner.respondToCompletion(CompletionResponse.Dismissed)
                        },
                        onDragDelta = { deltaY ->
                            params.y = (params.y + deltaY.toInt()).coerceAtLeast(0)
                            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                        },
                        onDragReset = {
                            params.y = 0
                            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                        },
                    )
                }
            }
            visibility = View.GONE
        }

        panelView = view
        panelParams = params
        windowManager?.addView(view, params)
        OverlayController.register(view)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun removeOverlayView(view: View?) {
        view?.let {
            OverlayController.unregister(it)
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoxTouch Overlay",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps FoxTouch overlay active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoxTouch")
            .setContentText("Tap to open")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
