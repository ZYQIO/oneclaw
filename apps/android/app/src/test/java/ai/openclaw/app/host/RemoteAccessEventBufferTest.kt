package ai.openclaw.app.host

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAccessEventBufferTest {
  @Test
  fun snapshotAfter_trimsOldEventsAndRespectsCursor() {
    val buffer = RemoteAccessEventBuffer(maxEvents = 2)

    buffer.append(event = "chat", payloadJson = """{"state":"one"}""", timestampMs = 1)
    val second = buffer.append(event = "chat", payloadJson = """{"state":"two"}""", timestampMs = 2)
    val third = buffer.append(event = "agent", payloadJson = """{"state":"three"}""", timestampMs = 3)

    val snapshot = buffer.snapshotAfter(cursor = 0, limit = 10)

    assertEquals(listOf(second, third), snapshot)
    assertTrue(buffer.hasEventsAfter(second.id))
    assertFalse(buffer.hasEventsAfter(third.id))
  }

  @Test
  fun awaitAfter_returnsWhenNewEventArrives() {
    val buffer = RemoteAccessEventBuffer(maxEvents = 4)
    val producer =
      Thread {
        Thread.sleep(60)
        buffer.append(event = "chat", payloadJson = """{"state":"delta"}""", timestampMs = 42)
      }

    producer.start()
    val events =
      runBlocking {
        buffer.awaitAfter(cursor = 0, limit = 10, waitMs = 1_000)
      }
    producer.join()

    assertEquals(1, events.size)
    assertEquals("chat", events.first().event)
    assertEquals(42, events.first().timestampMs)
  }
}
