package com.gbhall.childlock.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gbhall.childlock.R
import com.gbhall.childlock.guard.ForegroundTracker
import com.gbhall.childlock.lock.LockController
import com.gbhall.childlock.lock.LockState
import com.gbhall.childlock.settings.SettingsRepository
import com.gbhall.childlock.ui.MainActivity

/**
 * Quick Settings tile. Tapping it arms the lock with a short delay so the
 * parent can close the shade. It never unlocks: while locked the tile is
 * inert, otherwise a child who found the shade could switch the lock off.
 */
class LockTileService : TileService() {
    private val listener: (LockState) -> Unit = { render(it) }

    override fun onTileAdded() {
        super.onTileAdded()
        SettingsRepository.get(this).tileAdded = true
    }

    override fun onTileRemoved() {
        SettingsRepository.get(this).tileAdded = false
        super.onTileRemoved()
    }

    override fun onStartListening() {
        super.onStartListening()
        LockController.addListener(listener)
        render(LockController.state)
    }

    override fun onStopListening() {
        LockController.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        when (LockController.state) {
            is LockState.Locked -> Unit
            is LockState.Arming -> LockController.unlock() // second tap cancels the countdown
            LockState.Unlocked -> {
                if (!Settings.canDrawOverlays(this)) {
                    openSettingsScreen()
                } else if (!LockController.requestLock(this, ForegroundTracker.lastApp, TILE_ARM_DELAY_MS)) {
                    openSettingsScreen() // the app screen can always start the service
                }
            }
        }
    }

    private fun openSettingsScreen() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: LockState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        val (tileState, subtitle) = when (state) {
            is LockState.Locked -> Tile.STATE_ACTIVE to R.string.tile_locked
            is LockState.Arming -> Tile.STATE_ACTIVE to R.string.tile_arming
            LockState.Unlocked -> Tile.STATE_INACTIVE to R.string.tile_off
        }
        tile.state = tileState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(subtitle)
        tile.updateTile()
    }

    companion object {
        private const val TILE_ARM_DELAY_MS = 2500L
    }
}
