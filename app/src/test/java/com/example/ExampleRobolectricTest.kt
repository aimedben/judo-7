package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Judo Seddouk", appName)
  }

  @Test
  fun `douchette input with carriage return is cleaned`() {
    val rawFromScanner = "JUDO-SED-001\r\n"
    val clean = rawFromScanner.replace("\r", "").replace("\n", "").trim()
    assertEquals("JUDO-SED-001", clean)
  }

  @Test
  fun `sync report error message correctly retained on failure`() {
    val report = com.example.data.sync.SyncReport(
      isSuccess = false,
      itemsSent = 0,
      errorMessage = "Aucune connexion Internet disponible sur la tablette. Les données restent conservées localement.",
      pendingRemaining = 3
    )
    assertEquals(false, report.isSuccess)
    assertEquals(3, report.pendingRemaining)
    org.junit.Assert.assertNotNull(report.errorMessage)
  }

  @Test
  fun `launch MainActivity does not crash`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    org.junit.Assert.assertNotNull(controller.get())
  }
}
