package ai.foxtouch.accessibility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Manages MediaProjection-based screen capture as a fallback for apps that
 * block [android.accessibilityservice.AccessibilityService.takeScreenshot]
 * (e.g., WeChat 8.0.52+).
 *
 * Lifecycle:
 * 1. User grants permission via [MediaProjectionManager.createScreenCaptureIntent()]
 * 2. [init] is called with the result code and data from the Activity result
 * 3. [captureFrame] captures a single screenshot as Bitmap
 * 4. [release] frees resources when no longer needed
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"
    private const val VIRTUAL_DISPLAY_NAME = "FoxTouchCapture"
    private const val CAPTURE_TIMEOUT_MS = 5000L

    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    /** Whether the user has granted MediaProjection permission this session. */
    val isAuthorized: Boolean get() = resultData != null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Store the permission result for later use.
     * Called from the Activity after the user grants screen capture permission.
     */
    fun init(context: Context, resultCode: Int, data: Intent) {
        this.projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        this.resultCode = resultCode
        this.resultData = data
        Log.d(TAG, "MediaProjection permission stored, resultCode=$resultCode")
    }

    /**
     * Capture a single frame from the screen as a Bitmap.
     *
     * Creates a MediaProjection + VirtualDisplay + ImageReader, captures one
     * frame, then tears down the VirtualDisplay. The MediaProjection itself
     * is kept alive for efficiency.
     *
     * Includes a timeout to avoid hanging if the image callback never fires.
     */
    suspend fun captureFrame(context: Context): Bitmap {
        val pm = projectionManager
            ?: throw IllegalStateException("ScreenCaptureManager not initialized. Call init() first.")
        val data = resultData
            ?: throw IllegalStateException("MediaProjection permission not granted.")

        // Create or reuse MediaProjection
        val projection = mediaProjection ?: try {
            Log.d(TAG, "Creating new MediaProjection")
            pm.getMediaProjection(resultCode, data).also {
                mediaProjection = it
                it.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped by system")
                        mediaProjection = null
                    }
                }, mainHandler)
                Log.d(TAG, "MediaProjection created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaProjection", e)
            // Token might be stale — clear it so isAuthorized returns false
            resultData = null
            throw IllegalStateException("MediaProjection token expired. Please re-authorize in Settings.", e)
        }

        return OverlayController.withOverlaysHidden {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                captureFrameInternal(context, projection)
            }
        }
    }

    private suspend fun captureFrameInternal(
        context: Context,
        projection: MediaProjection,
    ): Bitmap = suspendCancellableCoroutine { cont ->
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = wm.maximumWindowMetrics.bounds

        val width = metrics.width()
        val height = metrics.height()
        val density = context.resources.displayMetrics.densityDpi
        Log.d(TAG, "Capturing frame: ${width}x${height} @${density}dpi")

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        cont.invokeOnCancellation {
            Log.w(TAG, "Capture cancelled/timed out")
            mainHandler.post {
                virtualDisplay?.release()
                imageReader.close()
            }
        }

        imageReader.setOnImageAvailableListener({ reader ->
            if (!cont.isActive) return@setOnImageAvailableListener
            val image = reader.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "acquireLatestImage returned null, waiting for next frame")
                return@setOnImageAvailableListener
            }

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop off the row padding
                val cropped = if (rowPadding > 0) {
                    Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }

                Log.d(TAG, "Frame captured: ${cropped.width}x${cropped.height}")
                cont.resume(cropped)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process captured frame", e)
                cont.resume(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            } finally {
                image.close()
                virtualDisplay?.release()
                imageReader.close()
            }
        }, mainHandler)

        try {
            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null, mainHandler,
            )
            Log.d(TAG, "VirtualDisplay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            imageReader.close()
            mediaProjection = null
            resultData = null
            cont.resume(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }

    /** Release the MediaProjection and all resources. */
    fun release() {
        mediaProjection?.stop()
        mediaProjection = null
        resultData = null
        resultCode = Activity.RESULT_CANCELED
        Log.d(TAG, "MediaProjection released")
    }
}
