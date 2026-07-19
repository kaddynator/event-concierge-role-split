package com.example

import com.example.data.Guest
import com.example.viewmodel.findSelfCheckInGuest
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
}
