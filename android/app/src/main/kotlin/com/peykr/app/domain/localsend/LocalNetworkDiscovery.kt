package com.peykr.app.domain.localsend

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.peykr.app.domain.transfer.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class LocalNetworkDiscovery(
  private val context: Context? = null,
  private val localDeviceId: String,
  private val localDisplayName: String,
  private val localPort: Int,
  private val localFingerprint: String,
) {

  private val nsdManager: NsdManager? = runCatching {
    context?.getSystemService(Context.NSD_SERVICE) as? NsdManager
  }.getOrNull()

  private val devicesMap = ConcurrentHashMap<String, DiscoveredDevice>()
  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null

  fun start() {
    registerService()
    startDiscovery()
  }

  fun stop() {
    stopDiscovery()
    unregisterService()
  }

  private fun registerService() {
    val nsd = nsdManager ?: return
    try {
      val serviceInfo = NsdServiceInfo().apply {
        serviceType = SERVICE_TYPE
        serviceName = "Peykr-${localDeviceId.take(8)}"
        port = localPort
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          setAttribute("id", localDeviceId)
          setAttribute("name", localDisplayName)
          setAttribute("model", Build.MODEL)
          setAttribute("fp", localFingerprint)
        }
      }

      registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
          Log.i(TAG, "LocalSend service registered: ${info.serviceName} on port $localPort")
        }
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
          Log.w(TAG, "LocalSend service registration failed: $errorCode")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) {
          Log.i(TAG, "LocalSend service unregistered: ${info.serviceName}")
        }
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
          Log.w(TAG, "LocalSend service unregistration failed: $errorCode")
        }
      }

      nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register NSD service: ${e.message}")
    }
  }

  private fun startDiscovery() {
    val nsd = nsdManager ?: return
    try {
      discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          Log.w(TAG, "NSD discovery failed to start: $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          Log.w(TAG, "NSD discovery failed to stop: $errorCode")
        }
        override fun onDiscoveryStarted(serviceType: String) {
          Log.i(TAG, "NSD discovery started for $serviceType")
        }
        override fun onDiscoveryStopped(serviceType: String) {
          Log.i(TAG, "NSD discovery stopped for $serviceType")
        }
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
          Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
          if (serviceInfo.serviceName.contains(localDeviceId.take(8))) {
            return // Ignore own service
          }
          resolveService(serviceInfo)
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
          Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
          devicesMap.remove(serviceInfo.serviceName)
          _discoveredDevices.value = devicesMap.values.toList()
        }
      }

      nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start NSD discovery: ${e.message}")
    }
  }

  private fun resolveService(serviceInfo: NsdServiceInfo) {
    val nsd = nsdManager ?: return
    try {
      nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
          Log.w(TAG, "Failed to resolve NSD service ${info.serviceName}: $errorCode")
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
          val host = info.host?.hostAddress ?: return
          val port = info.port

          var deviceId = info.serviceName
          var displayName = "Peykr Cihazı"
          var model = "Android"
          var fingerprint = ""

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.attributes["id"]?.let { deviceId = String(it, StandardCharsets.UTF_8) }
            info.attributes["name"]?.let { displayName = String(it, StandardCharsets.UTF_8) }
            info.attributes["model"]?.let { model = String(it, StandardCharsets.UTF_8) }
            info.attributes["fp"]?.let { fingerprint = String(it, StandardCharsets.UTF_8) }
          }

          if (deviceId == localDeviceId) return

          val device = DiscoveredDevice(
            deviceId = deviceId,
            displayName = displayName,
            deviceModel = model,
            ipAddress = host,
            port = port,
            fingerprint = fingerprint,
            lastSeenEpochMillis = System.currentTimeMillis()
          )

          devicesMap[info.serviceName] = device
          _discoveredDevices.value = devicesMap.values.toList()
          Log.i(TAG, "Resolved Peykr device on LAN: $displayName ($host:$port)")
        }
      })
    } catch (e: Exception) {
      Log.e(TAG, "Exception resolving service: ${e.message}")
    }
  }

  private fun stopDiscovery() {
    discoveryListener?.let {
      runCatching { nsdManager?.stopServiceDiscovery(it) }
      discoveryListener = null
    }
  }

  private fun unregisterService() {
    registrationListener?.let {
      runCatching { nsdManager?.unregisterService(it) }
      registrationListener = null
    }
  }

  // Virtual device discovery helper for testing and offline environments
  fun addVirtualDiscoveredDevice(device: DiscoveredDevice) {
    devicesMap[device.deviceId] = device
    _discoveredDevices.value = devicesMap.values.toList()
  }

  fun removeVirtualDiscoveredDevice(deviceId: String) {
    devicesMap.remove(deviceId)
    _discoveredDevices.value = devicesMap.values.toList()
  }

  companion object {
    private const val TAG = "LocalNetworkDiscovery"
    const val SERVICE_TYPE = "_peykr._tcp."
  }
}
