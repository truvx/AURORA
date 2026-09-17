package dev.aurora.player.data.player

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The media session is how the lock screen, notification, Bluetooth buttons, and Android
 * Auto reach the player. The service is exported with a MediaSessionService intent filter,
 * so the system can bind it at any time - but nothing in the app starts it, so a failure in
 * onCreate is invisible during normal use.
 */
@RunWith(AndroidJUnit4::class)
class AuroraMediaSessionServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun serviceBindsAndBuildsItsSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // MediaSessionService only returns a binder for its own action, which is how the
        // system and external controllers reach it.
        val intent = Intent(context, AuroraMediaSessionService::class.java).apply {
            action = "androidx.media3.session.MediaSessionService"
        }

        // Binding runs onCreate. If it throws, every external transport control is dead.
        val binder = serviceRule.bindService(intent)
        assertNotNull("service failed to bind; onCreate threw", binder)
    }
}
