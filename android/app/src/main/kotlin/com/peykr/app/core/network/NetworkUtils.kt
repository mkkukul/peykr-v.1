package com.peykr.app.core.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

  fun getLocalIpAddress(): String? {
    return try {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      val candidateList = mutableListOf<String>()

      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        for (addr in intf.inetAddresses) {
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val ip = addr.hostAddress ?: continue
            // Prefer Wi-Fi / LAN subnets
            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
              candidateList.add(0, ip) // higher priority
            } else if (!ip.startsWith("127.")) {
              candidateList.add(ip)
            }
          }
        }
      }
      candidateList.firstOrNull()
    } catch (_: Throwable) {
      null
    }
  }
}
