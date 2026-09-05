package com.rimo.player

import android.app.Application
import com.rimo.player.domain.update.UpdateCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RimoApp : Application() {

    @Inject
    lateinit var updateCoordinator: UpdateCoordinator

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget on the app scope; must never delay the first frame.
        updateCoordinator.start()
    }
}
