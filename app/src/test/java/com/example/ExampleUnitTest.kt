package com.example

import com.example.data.Guest
import com.example.viewmodel.findSelfCheckInGuest
import com.example.viewmodel.validateBroadcast
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  private val guests = listOf(
    Guest("1", "Maya Patel", "510-555-0199", true, "Attending", "None", false, "Table 1")
  )

  @Test
  fun selfCheckInRequiresExactNameOrLastFourDigits() {
    assertNull(findSelfCheckInGuest("Maya", guests))
    assertEquals("Maya Patel", findSelfCheckInGuest("maya patel", guests)?.name)
    assertEquals("Maya Patel", findSelfCheckInGuest("0199", guests)?.name)
    assertNull(findSelfCheckInGuest("199", guests))
  }

  @Test
  fun broadcastRequiresTitleAndMessage() {
    assertEquals("Add a short title.", validateBroadcast("", "Dinner is ready."))
    assertEquals("Add a message for attendees.", validateBroadcast("Dinner", ""))
  }

  @Test
  fun broadcastEnforcesCharacterLimits() {
    assertEquals(
      "Keep the title under 60 characters.",
      validateBroadcast("a".repeat(61), "Message")
    )
    assertEquals(
      "Keep the message under 280 characters.",
      validateBroadcast("Title", "a".repeat(281))
    )
    assertNull(validateBroadcast("Dinner", "Dinner is served in the garden."))
  }
}
