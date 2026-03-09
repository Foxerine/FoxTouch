# FoxTouch Batch Issues Tracker

## Code Changes (All Complete)

- [x] #1 - Task completion timing: Mark tasks complete immediately, not batched
  - `SystemPrompts.kt`: Added "CRITICAL: Mark each task as 'completed' IMMEDIATELY" instruction
  - `AgentRunner.kt`: Added `buildTaskReminder()` injecting task status before each LLM call

- [x] #2 - Permission bypass: Click/type_text bypassing permission control
  - `AgentRunner.kt`: Fixed YOLO mode persistence - "Allow All" no longer saves to DataStore, only sets session-local variable

- [x] #3 - Tool display text: Show friendly text instead of raw tool names
  - `ToolDisplayInfo.kt`: Updated formatArgs for all tools (e.g., wait shows "等待 3.0 秒")
  - `AgentRunner.kt`: Acting state now uses `ToolDisplayRegistry.formatArgs()` for displayText
  - `ChatScreen.kt`: AgentStatusIndicator uses `state.displayText`

- [x] #4 - Streaming config location: Move from Network to Agent section
  - `SettingsScreen.kt`: Moved streaming toggle to Agent section

- [x] #5 - Per-tool permission settings: Settings dialog was placeholder
  - `SettingsScreen.kt`: Replaced placeholder AlertDialog with ModalBottomSheet containing ToolPermissionSettings
  - `SettingsViewModel.kt`: Added permissionStore parameter

- [x] #6 - Plan review checkbox bug: Toggling refreshes plan content
  - `ChatScreen.kt`: Used no-key `remember` for saveAsSkill, `stablePlanContent` captures once

- [x] #7 - Plan review UX: Show input field directly, keyboard pushes sheet
  - `ChatScreen.kt`: Always-visible OutlinedTextField, added `.imePadding()`, removed button-to-field toggle

- [x] #8 - Auto-expand task panel: Tasks list should expand when tasks exist
  - `TaskProgressPanel.kt`: Both panels default expanded=true, LaunchedEffect re-expands on new tasks

- [x] #10 - Launch app approval: Show app icon
  - `ApprovalSheet.kt`: Shows app icon via AndroidView+ImageView, app name, and package name for launch_app

- [x] #12 - WeChat blank screenshots: MediaProjection fallback implemented
  - **Root cause**: WeChat v8.0.52+ blocks third-party AccessibilityService.takeScreenshot() (returns all-white)
  - **Solution**: MediaProjection API as per-app fallback (system compositor level, apps can't block it)
  - New files:
    - `ScreenCaptureManager.kt`: Singleton managing MediaProjection lifecycle + single-frame capture
    - `ScreenCaptureService.kt`: Foreground service holding MediaProjection (Android 14+ requirement)
  - Modified files:
    - `AccessibilityBridge.kt`: `captureScreenshotBase64()` checks per-app mode, routes to MediaProjection when configured
    - `ReadScreenTool.kt`: Passes `AppSettings` to `captureScreenshotBase64()` for per-app mode lookup
    - `AppSettings.kt`: Added `ScreenshotMode` enum, per-app screenshot mode get/set, `getMediaProjectionApps()`
    - `SettingsScreen.kt`: "Enhanced Screenshot" UI with app picker, authorization status, per-app list
    - `SettingsViewModel.kt`: Added `mediaProjectionApps` Flow, `setScreenshotMode()`
    - `MainActivity.kt`: `mediaProjectionLauncher` for system permission dialog, `requestScreenCapturePermission()`
    - `AndroidManifest.xml`: Added `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission, registered `ScreenCaptureService`

- [x] #13 - Stale tasks: LLM doesn't auto-clear completed tasks
  - `AgentRunner.kt`: Task status injected as system message before each LLM iteration
  - `SystemPrompts.kt`: Added stale task cleanup guidance

- [x] #15 - Animation visibility: Click/swipe animations on light/dark backgrounds
  - `TouchAnimationOverlay.kt`: Added shadow paints (black, lower alpha) behind all ripple and swipe elements

- [x] #18 - Voice input false positive: Shows "install Google" even when installed
  - `SpeechRecognizer.kt`: Removed `isRecognitionAvailable()` pre-check, tries on-device first, falls back gracefully

- [x] #20 - Language switch animation: Black screen during recreate()
  - `MainActivity.kt`: Added crossfade animation with 200ms fade-out before recreate

## Optimization (Investigation Complete, Follow-up Actions Recommended)

- [ ] #9 - APK size (73MB debug)
  - **Finding**: 73MB is expected for debug build. Release with R8 minification should be 25-35MB.
  - **Top contributors**: Ktor+OkHttp (8-12MB), Compose (8-12MB), OpenAI SDK (4-7MB), Material Icons Extended (2-4MB)
  - **TODO actions**:
    1. Build release APK to verify smaller size
    2. Consider removing `material-icons-extended` (use explicit icon imports instead)
    3. Consider replacing OpenAI SDK with lighter Ktor-only client
    4. Use AAB (Android App Bundle) for Play Store distribution
    5. `shrinkResources true` already enabled in release build config
