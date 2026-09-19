package com.popovicialinc.gama

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportBundleTest {
    @Test
    fun `support placeholders are replaced left to right`() {
        assertEquals(
            "Device: Samsung Galaxy S24",
            formatSupportText("Device: %s %s", "Samsung", "Galaxy S24")
        )
        assertEquals(
            "Last action: Boot restore -> Vulkan; success=false; unavailable",
            formatSupportText(
                "Last action: %s -> %s; success=%s; %s",
                "Boot restore",
                "Vulkan",
                "false",
                "unavailable"
            )
        )
    }
}
