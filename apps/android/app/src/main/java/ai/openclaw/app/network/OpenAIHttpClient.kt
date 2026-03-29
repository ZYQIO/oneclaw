package ai.openclaw.app.network

import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.OkHttpClient

internal fun ipv4FirstDns(delegate: Dns = Dns.SYSTEM): Dns =
  Dns { hostname ->
    val resolved = delegate.lookup(hostname)
    if (resolved.size < 2) return@Dns resolved
    val ipv4 = ArrayList<InetAddress>(resolved.size)
    val other = ArrayList<InetAddress>(resolved.size)
    resolved.forEach { address ->
      if (address is Inet4Address) {
        ipv4 += address
      } else {
        other += address
      }
    }
    if (ipv4.isEmpty() || other.isEmpty()) {
      resolved
    } else {
      ipv4 + other
    }
  }

internal fun buildOpenAIHttpClient(
  builder: OkHttpClient.Builder = OkHttpClient.Builder(),
  dns: Dns = Dns.SYSTEM,
): OkHttpClient =
  builder
    .dns(ipv4FirstDns(dns))
    .build()
