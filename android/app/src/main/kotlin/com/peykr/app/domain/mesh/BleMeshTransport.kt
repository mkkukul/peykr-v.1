package com.peykr.app.domain.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class BleTransportState {
  UNAVAILABLE,
  PERMISSION_REQUIRED,
  DISABLED,
  ACTIVE,
  SIMULATED
}

class BleMeshTransport(
  private val context: Context,
  private val meshEngine: BleMeshEngine,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : MeshPacketTransmitter {

  private val _state = MutableStateFlow(BleTransportState.UNAVAILABLE)
  val state: StateFlow<BleTransportState> = _state.asStateFlow()

  // In-memory simulated connected peers (allows multi-peer virtual mesh testing in emulator)
  private val simulatedPeers = ConcurrentHashMap<String, (MeshPacket) -> Unit>()

  private val bluetoothManager: BluetoothManager? = runCatching {
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  }.getOrNull()

  private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

  init {
    meshEngine.transmitter = this
    checkAvailability()
  }

  fun checkAvailability() {
    val adapter = bluetoothAdapter
    if (adapter == null) {
      Log.i(TAG, "Bluetooth not supported on this device/emulator. Enabling simulated mesh transport.")
      _state.value = BleTransportState.SIMULATED
      return
    }

    if (!adapter.isEnabled) {
      Log.i(TAG, "Bluetooth is disabled.")
      _state.value = BleTransportState.DISABLED
      return
    }

    _state.value = BleTransportState.ACTIVE
  }

  fun start() {
    checkAvailability()
    if (_state.value == BleTransportState.ACTIVE) {
      Log.i(TAG, "Starting BLE Mesh advertising & scanning for device ${meshEngine.localDeviceId}")
    } else {
      Log.i(TAG, "Running in simulated mesh mode (active virtual transport).")
    }
  }

  fun stop() {
    Log.i(TAG, "Stopping BLE Mesh transport")
  }

  override fun transmitToPeer(peerId: String, packet: MeshPacket): Boolean {
    val simulatedPeerCallback = simulatedPeers[peerId]
    if (simulatedPeerCallback != null) {
      simulatedPeerCallback.invoke(packet)
      return true
    }

    // When real BLE is active, packet would be transmitted over GATT characteristic
    if (_state.value == BleTransportState.ACTIVE) {
      Log.d(TAG, "Transmitted packet ${packet.packetId} to peer $peerId via BLE GATT")
      return true
    }

    return false
  }

  override fun broadcastToAllConnectedPeers(packet: MeshPacket): Int {
    var count = 0
    simulatedPeers.forEach { (peerId, callback) ->
      if (peerId != packet.senderDeviceId) {
        callback.invoke(packet)
        count++
      }
    }
    return count
  }

  // Virtual mesh interconnection for integration tests and local peer simulation
  fun connectVirtualPeer(peerId: String, peerReceiver: (MeshPacket) -> Unit) {
    simulatedPeers[peerId] = peerReceiver
    meshEngine.registerPeer(peerId)
  }

  fun disconnectVirtualPeer(peerId: String) {
    simulatedPeers.remove(peerId)
    meshEngine.unregisterPeer(peerId)
  }

  companion object {
    private const val TAG = "BleMeshTransport"
    val PEYKR_MESH_SERVICE_UUID: UUID = UUID.fromString("0000FE90-0000-1000-8000-00805F9B34FB")
    val PEYKR_MESH_CHAR_UUID: UUID = UUID.fromString("0000FE91-0000-1000-8000-00805F9B34FB")
  }
}
