package com.popovicialinc.gama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererSwitchStateMachineTest {
    @Test
    fun `successful transaction reaches complete only after verification`() {
        val machine = RendererSwitchStateMachine(
            RendererState.RENDERER_VULKAN,
            RendererState.RENDERER_OPENGL
        )
        machine.request()
        machine.applying()
        machine.verifying()
        machine.verified()
        machine.complete()
        assertEquals(RendererSwitchStateMachine.Phase.COMPLETE, machine.phase)
        assertEquals(null, machine.failure)
    }

    @Test
    fun `failure preserves explicit failed terminal state`() {
        val machine = RendererSwitchStateMachine("Vulkan", "OpenGL")
        machine.request()
        machine.applying()
        machine.fail("backend disappeared")
        assertEquals(RendererSwitchStateMachine.Phase.FAILED, machine.phase)
        assertEquals("backend disappeared", machine.failure)
    }

    @Test
    fun `cannot complete before verification`() {
        val machine = RendererSwitchStateMachine("Vulkan", "OpenGL")
        machine.request()
        machine.applying()
        assertThrows(IllegalStateException::class.java) { machine.complete() }
        assertTrue(machine.phase != RendererSwitchStateMachine.Phase.COMPLETE)
    }
}
