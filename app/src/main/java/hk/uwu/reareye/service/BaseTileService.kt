package hk.uwu.reareye.service

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import hk.uwu.reareye.R
import hk.uwu.reareye.utils.RootHelper

abstract class BaseTileService : TileService() {
    companion object {
        private const val TAG = "BaseTileService"
        const val MAIN_DISPLAY_ID = 0
        const val REAR_DISPLAY_ID = 1
        var lastMovedTask: String? = null // Format: "packageName:taskId"
    }

    protected val mainHandler = Handler(Looper.getMainLooper())

    protected abstract fun getTargetDisplayId(): Int

    protected abstract fun getSuccessMessage(): String

    protected abstract fun getFailureMessage(): String

    protected abstract fun getNoAppFoundMessage(): String

    protected abstract fun getDisplayOccupiedMessage(): String

    protected open fun shouldCheckOccupation(): Boolean = true

    protected open fun shouldVerifyPreviousMove(): Boolean = false

    protected abstract fun getSwitchingMessage(): String

    override fun onStartListening() {
        super.onStartListening()

        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = null
            tile.stateDescription = ""
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        switchAppToDisplay()
    }

    protected fun switchAppToDisplay() {
        // Check root access first
        if (!RootHelper.hasRootAccess()) {
            Log.w(TAG, "No root access!")
            showTemporaryFeedback(getString(R.string.toast_need_root_permission))
            showToast(getString(R.string.toast_grant_root_permission))
            return
        }

        val targetDisplayId = getTargetDisplayId()

        // Show switching status
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = getSwitchingMessage()
            tile.stateDescription = ""
            tile.updateTile()
        }

        try {
            // Step 0: Check if target display already has an app running (only for rear display)
            if (shouldCheckOccupation() && lastMovedTask != null && lastMovedTask!!.contains(":")) {
                try {
                    val parts = lastMovedTask!!.split(":")
                    val oldPackageName = parts[0]

                    // Check if old app is still on target display
                    val targetForegroundApp = getForegroundAppOnDisplay(targetDisplayId)
                    if (targetForegroundApp != null && targetForegroundApp == lastMovedTask) {
                        // Target display already has an app running, prohibit operation
                        val oldAppName = getAppName(oldPackageName)

                        // Collapse status bar first so Toast can be shown
                        collapseStatusBar()

                        // Delay showing Toast to ensure status bar is collapsed
                        mainHandler.postDelayed({
                            Toast.makeText(
                                this,
                                getString(R.string.toast_please_switch_back, oldAppName),
                                Toast.LENGTH_LONG
                            ).show()
                        }, 300)

                        showTemporaryFeedback(getDisplayOccupiedMessage())
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check previous app: ${e.message}")
                }
            }

            // Step 1: Get current foreground app on source display
            val sourceDisplayId =
                if (targetDisplayId == REAR_DISPLAY_ID) MAIN_DISPLAY_ID else REAR_DISPLAY_ID
            val currentApp = getForegroundAppOnDisplay(sourceDisplayId)

            // Step 1.5: For switching back to main display, verify the app was previously moved
            if (shouldVerifyPreviousMove() && lastMovedTask != null) {
                if (currentApp != lastMovedTask) {
                    // The current app on rear display is not the one we moved there
                    Log.w(TAG, "Current app on rear display is not the previously moved app")
                    showTemporaryFeedback(getString(R.string.tile_not_previously_moved))
                    return
                }
            }

            if (currentApp != null && currentApp.contains(":")) {
                val parts = currentApp.split(":")
                val packageName = parts[0]
                val taskId = parts[1].toInt()

                // Get app name
                val appName = getAppName(packageName)

                // Step 2: Switch to target display
                val success = moveTaskToDisplay(taskId, targetDisplayId)

                if (success) {
                    // Save last moved task info
                    lastMovedTask = currentApp

                    // Auto collapse status bar
                    Thread {
                        try {
                            collapseStatusBar()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to collapse: ${e.message}")
                        }
                    }.start()

                    // Delay showing Toast to ensure status bar is collapsed
                    mainHandler.postDelayed({
                        Toast.makeText(
                            this,
                            "$appName ${getSuccessMessage()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 300)

                    showTemporaryFeedback(getString(R.string.tile_switched))
                } else {
                    // Collapse status bar first
                    collapseStatusBar()

                    // Delay showing Toast
                    mainHandler.postDelayed({
                        Toast.makeText(
                            this,
                            getFailureMessage(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 300)

                    showTemporaryFeedback(getString(R.string.tile_failed))
                }
            } else {
                Log.w(TAG, "No foreground app found on display $sourceDisplayId")
                showTemporaryFeedback(getNoAppFoundMessage())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching app", e)
            showTemporaryFeedback(getString(R.string.tile_operation_failed))
        }
    }

    protected fun getForegroundAppOnDisplay(displayId: Int): String? {
        return try {
            val output = RootHelper.executeRootCommandOutput("am stack list")
            val lines = output.lines()

            var inTargetDisplay = false
            for (line in lines) {
                if (line.startsWith("RootTask")) {
                    inTargetDisplay = line.contains("displayId=$displayId")
                    continue
                }

                if (inTargetDisplay && line.contains("taskId=") && line.contains("/")) {
                    // Parse:   taskId=1471: com.example.display_switcher/com.example.display_switcher.MainActivity
                    val tidStart = line.indexOf("taskId=") + 7
                    val tidEnd = line.indexOf(':', tidStart)
                    val taskId = line.substring(tidStart, tidEnd).trim()

                    val pkgStart = tidEnd + 2
                    val pkgEnd = line.indexOf('/', pkgStart)
                    val packageName = line.substring(pkgStart, pkgEnd).trim()

                    // Skip launcher
                    if (packageName.contains("launcher") || packageName.contains("miui.home")) {
                        continue
                    }

                    return "$packageName:$taskId"
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app on display $displayId", e)
            null
        }
    }

    protected fun moveTaskToDisplay(taskId: Int, displayId: Int): Boolean {
        return try {
            // Execute service call command with root privileges
            val cmd = "service call activity_task 50 i32 $taskId i32 $displayId"
            RootHelper.executeRootCommandSuccess(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "Error moving task to display", e)
            false
        }
    }

    protected fun collapseStatusBar() {
        try {
            RootHelper.executeRootCommand("cmd statusbar collapse")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collapse status bar: ${e.message}")
        }
    }

    protected fun showTemporaryFeedback(message: String) {
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = message
            tile.stateDescription = ""
            tile.updateTile()
        }

        mainHandler.postDelayed({
            val resetTile = qsTile
            if (resetTile != null) {
                resetTile.state = Tile.STATE_INACTIVE
                resetTile.subtitle = null
                resetTile.stateDescription = ""
                resetTile.updateTile()
            }
        }, 1500)
    }

    protected fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo)
            if (label.isNotEmpty()) {
                label.toString()
            } else {
                packageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app name: ${e.message}")
            packageName
        }
    }

    protected fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
