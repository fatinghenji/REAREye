package hk.uwu.reareye.service

import android.os.Build
import androidx.annotation.RequiresApi
import hk.uwu.reareye.R

@RequiresApi(Build.VERSION_CODES.R)
class SwitchToMainTileService : BaseTileService() {

    override fun getTargetDisplayId(): Int = MAIN_DISPLAY_ID

    override fun getSuccessMessage(): String = getString(R.string.toast_cast_to_main)

    override fun getFailureMessage(): String = getString(R.string.toast_switch_failed)

    override fun getNoAppFoundMessage(): String = getString(R.string.tile_no_app_found)

    override fun getDisplayOccupiedMessage(): String =
        getString(R.string.tile_main_display_occupied)

    override fun getSwitchingMessage(): String = getString(R.string.tile_switching)

    override fun shouldCheckOccupation(): Boolean = false

    override fun shouldVerifyPreviousMove(): Boolean = true
}
