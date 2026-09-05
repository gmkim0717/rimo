package com.rimo.player.ui.update

import app.cash.turbine.test
import com.rimo.player.domain.update.UpdateState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePromptGateTest {

    private val ready2 = UpdateState.ReadyToInstall(2, "0.2.0", File("2.apk"))
    private val ready3 = UpdateState.ReadyToInstall(3, "0.3.0", File("3.apk"))

    @Test
    fun `emits once when ready and not yet prompted`() = runTest {
        val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val gate = UpdatePromptGate(state)

        gate.prompt.test {
            expectNoEvents()
            state.value = UpdateState.Checking
            expectNoEvents()
            state.value = ready2
            assertEquals(ready2, awaitItem())
            gate.markPrompted()
            expectNoEvents()
        }
    }

    @Test
    fun `after prompting even a newer ready version is suppressed this process`() = runTest {
        val state = MutableStateFlow<UpdateState>(ready2)
        val gate = UpdatePromptGate(state)

        gate.prompt.test {
            assertEquals(ready2, awaitItem())
            gate.markPrompted()
            state.value = ready3
            expectNoEvents()
        }
    }

    @Test
    fun `playback holds the prompt back until it ends`() = runTest {
        val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val gate = UpdatePromptGate(state)
        gate.setPlaybackActive(true)

        gate.prompt.test {
            state.value = ready2
            expectNoEvents()
            gate.setPlaybackActive(false)
            assertEquals(ready2, awaitItem())
        }
    }

    @Test
    fun `ready state that goes away emits nothing further`() = runTest {
        val state = MutableStateFlow<UpdateState>(ready2)
        val gate = UpdatePromptGate(state)

        gate.prompt.test {
            assertEquals(ready2, awaitItem())
            state.value = UpdateState.Idle
            expectNoEvents()
        }
    }
}
