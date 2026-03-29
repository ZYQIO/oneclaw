package ai.openclaw.app.network

import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAIHttpClientTest {
  @Test
  fun ipv4FirstDns_movesIpv4AddressesAheadOfIpv6() {
    val ipv6a = InetAddress.getByName("2001:db8::1")
    val ipv4a = InetAddress.getByName("203.0.113.10")
    val ipv6b = InetAddress.getByName("2001:db8::2")
    val ipv4b = InetAddress.getByName("203.0.113.11")
    val dns = ipv4FirstDns(Dns { listOf(ipv6a, ipv4a, ipv6b, ipv4b) })

    val resolved = dns.lookup("chatgpt.com")

    assertEquals(listOf(ipv4a, ipv4b, ipv6a, ipv6b), resolved)
  }

  @Test
  fun ipv4FirstDns_keepsOriginalOrderWhenOnlyOneAddressFamilyExists() {
    val ipv4a = InetAddress.getByName("203.0.113.10")
    val ipv4b = InetAddress.getByName("203.0.113.11")
    val dns = ipv4FirstDns(Dns { listOf(ipv4a, ipv4b) })

    val resolved = dns.lookup("auth.openai.com")

    assertEquals(listOf(ipv4a, ipv4b), resolved)
  }

  @Test
  fun buildOpenAIHttpClient_wrapsProvidedDns() {
    val ipv6 = InetAddress.getByName("2001:db8::1")
    val ipv4 = InetAddress.getByName("203.0.113.10")
    val delegate = Dns { listOf(ipv6, ipv4) }
    val client =
      buildOpenAIHttpClient(
        dns = delegate,
      )

    val resolved = client.dns.lookup("chatgpt.com")

    assertEquals(listOf(ipv4, ipv6), resolved)
  }
}
