package com.mynavisccooter.capture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

data class ScannedScooter(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
    val manufacturerData: Map<String, String>,
    val model: ModelProfile
)

class BleCaptureManager(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(message: String)
        fun onDevicesChanged(devices: List<ScannedScooter>)
        fun onCaptureReady(report: CaptureReport)
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private val devices = linkedMapOf<String, ScannedScooter>()
    private var gatt: BluetoothGatt? = null
    private var selected: ScannedScooter? = null
    private var scanning = false
    private var readQueue: MutableList<BluetoothGattCharacteristic> = mutableListOf()
    private val readValues = linkedMapOf<String, ByteArray>()
    private val notificationQueue: MutableList<BluetoothGattCharacteristic> = mutableListOf()
    private val notificationCharacteristics = mutableListOf<String>()
    private val rawNotificationHex = mutableListOf<String>()
    private var notificationBuffer = ByteArray(0)
    private var preComm: PreCommResult? = null
    private var probeRequestHex: String? = null
    private var probeAttempted = false
    private var reportScheduled = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val customNinebotService = UUID.fromString("6e400001-0000-0000-006e-696e65626f74")
    private val nordicUartService = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val manufacturer = result.scanRecord?.manufacturerSpecificData
            val data = linkedMapOf<String, String>()
            if (manufacturer != null) {
                for (i in 0 until manufacturer.size()) {
                    val key = "%04X".format(manufacturer.keyAt(i))
                    bytesToHex(manufacturer.valueAt(i))?.let { data[key] = it }
                }
            }
            val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "Sem nome"
            val manufacturerFingerprint = data.entries.joinToString("") { it.key + it.value }
            val model = ModelProfiles.fromAdvertisement(name, manufacturerFingerprint)
            val x3Signature = manufacturerFingerprint.contains("4E430100020000FC", ignoreCase = true) ||
                manufacturerFingerprint.contains("434E0100020000FC", ignoreCase = true)
            val serialLikeName = name.matches(Regex("1K1[A-Z0-9]{6,}"))
            if (model != ModelProfiles.UNKNOWN || x3Signature || serialLikeName || name.contains("segway", true) || name.contains("ninebot", true)) {
                devices[device.address] = ScannedScooter(device, name, result.rssi, data, model)
                listener.onDevicesChanged(devices.values.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onStatus("Falha no scan BLE: código $errorCode")
        }
    }

    fun startScan() {
        if (!hasScanPermission()) {
            listener.onStatus("Permissão Bluetooth necessária")
            return
        }
        if (scanning) {
            listener.onStatus("Scan BLE já está ativo. Aguarda alguns segundos…")
            return
        }
        devices.clear()
        listener.onDevicesChanged(emptyList())
        scanner?.startScan(scanCallback)
        scanning = true
        listener.onStatus("A procurar ZT3/F3/GT3…")
    }

    fun stopScan() {
        if (scanning && hasScanPermission()) scanner?.stopScan(scanCallback)
        scanning = false
    }

    fun connect(item: ScannedScooter) {
        if (!hasConnectPermission()) {
            listener.onStatus("Permissão de ligação Bluetooth necessária")
            return
        }
        stopScan()
        selected = item
        readValues.clear()
        notificationQueue.clear()
        notificationCharacteristics.clear()
        rawNotificationHex.clear()
        notificationBuffer = ByteArray(0)
        preComm = null
        probeRequestHex = null
        probeAttempted = false
        reportScheduled = false
        listener.onStatus("A ligar a ${item.name} (modo somente leitura)…")
        gatt?.close()
        gatt = item.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun close() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Ligado. A descobrir serviços…")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                listener.onStatus("Bluetooth desligado da scooter")
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Falha BLE: estado $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Não foi possível descobrir os serviços: $status")
                return
            }
            readQueue = gatt.services.flatMap { it.characteristics }
                .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }
                .toMutableList()
            notificationQueue += gatt.services
                .flatMap { it.characteristics }
                .filter { characteristic ->
                    val canNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val canIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    (canNotify || canIndicate) && characteristic.getDescriptor(cccdUuid) != null
                }
                .distinctBy { it.uuid }
            listener.onStatus("Serviços encontrados: ${gatt.services.size}. Leituras: ${readQueue.size}; notificações: ${notificationQueue.size}")
            enableNextNotification(gatt)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) readValues[characteristic.uuid.toString()] = characteristic.value ?: byteArrayOf()
            readNext(gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            recordNotification(characteristic, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            recordNotification(characteristic, value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onStatus("Não foi possível ativar uma notificação BLE: $status")
            }
            enableNextNotification(gatt)
        }
    }

    private fun enableNextNotification(gatt: BluetoothGatt) {
        val characteristic = notificationQueue.removeFirstOrNull()
        if (characteristic == null) {
            sendPreCommProbe(gatt)
            return
        }

        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!enabled) listener.onStatus("Aviso: subscrição BLE recusada para ${characteristic.uuid}")
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            enableNextNotification(gatt)
            return
        }
        notificationCharacteristics += characteristic.uuid.toString()
        descriptor.value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        if (!gatt.writeDescriptor(descriptor)) {
            listener.onStatus("Aviso: não foi possível escrever o descritor BLE ${characteristic.uuid}")
            enableNextNotification(gatt)
        }
    }

    private fun sendPreCommProbe(gatt: BluetoothGatt) {
        val item = selected
        val characteristic = gatt.getService(customNinebotService)?.characteristics
            ?.firstOrNull { it.uuid.toString().endsWith("0002-0000-0000-006e-696e65626f74") }
        if (item == null || characteristic == null || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
            listener.onStatus("Probe de protocolo não disponível; a terminar captura somente leitura…")
            readNext(gatt)
            return
        }

        val frame = Encryption2Probe.buildPreCommFrame(item.name)
        probeRequestHex = bytesToHex(frame)
        probeAttempted = true
        listener.onStatus("A recolher resposta de diagnóstico BLE (sem alterar configurações)…")
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = frame
        if (!gatt.writeCharacteristic(characteristic)) {
            listener.onStatus("Probe de protocolo recusado; a terminar captura…")
            readNext(gatt)
            return
        }
        // A resposta chega por notify/indicate. Dá tempo para a receber antes de exportar.
        mainHandler.postDelayed({
            if (preComm == null) {
                val fallback = gatt.getService(nordicUartService)?.characteristics
                    ?.firstOrNull { it.uuid.toString().endsWith("0002-b5a3-f393-e0a9-e50e24dcca9e") }
                if (fallback != null && fallback.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    val fallbackFrame = Encryption2Probe.buildPreCommFrame(item.name)
                    probeRequestHex = bytesToHex(fallbackFrame)
                    fallback.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    fallback.value = fallbackFrame
                    listener.onStatus("Sem resposta no canal custom; a testar o canal BLE compatível…")
                    gatt.writeCharacteristic(fallback)
                    mainHandler.postDelayed({ readNext(gatt) }, 700L)
                } else {
                    readNext(gatt)
                }
            } else {
                readNext(gatt)
            }
        }, 700L)
    }

    private fun recordNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (value.isEmpty()) return
        notificationCharacteristics += characteristic.uuid.toString()
        rawNotificationHex += bytesToHex(value) ?: ""
        notificationBuffer += value
        val item = selected ?: return
        while (true) {
            val start = notificationBuffer.indexOfFrameHeader()
            if (start < 0) {
                notificationBuffer = notificationBuffer.takeLast(2).toByteArray()
                return
            }
            if (start > 0) notificationBuffer = notificationBuffer.copyOfRange(start, notificationBuffer.size)
            if (notificationBuffer.size < 3) return
            val length = notificationBuffer[2].toInt() and 0xFF
            val total = length + 13
            if (total < 13 || total > 4096) {
                notificationBuffer = notificationBuffer.copyOfRange(2, notificationBuffer.size)
                continue
            }
            if (notificationBuffer.size < total) return
            val frame = notificationBuffer.copyOfRange(0, total)
            notificationBuffer = notificationBuffer.copyOfRange(total, notificationBuffer.size)
            Encryption2Probe.parsePreCommFrame(frame, item.name)?.let { preComm = it }
        }
    }

    private fun readNext(gatt: BluetoothGatt) {
        val next = readQueue.removeFirstOrNull()
        if (next == null) {
            if (!probeAttempted && !reportScheduled) {
                reportScheduled = true
            }
            val item = selected ?: return
            val reportServices = gatt.services.map { service ->
                ServiceCapture(service.uuid.toString(), service.characteristics.map { c ->
                    val value = readValues[c.uuid.toString()]
                    CharacteristicCapture(c.uuid.toString(), c.propertyLabel(), bytesToHex(value), bytesToSafeText(value))
                })
            }
            listener.onCaptureReady(CaptureReport(
                capturedAtUtc = CaptureReport.nowUtc(),
                appVersion = "0.1.3",
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceName = item.name,
                deviceAddress = item.device.address,
                rssi = item.rssi,
                manufacturerData = item.manufacturerData,
                model = item.model,
                services = reportServices,
                protocolProbe = ProtocolProbeCapture(
                    attempted = probeAttempted,
                    notificationCharacteristics = notificationCharacteristics.distinct(),
                    requestHex = probeRequestHex,
                    rawNotificationHex = rawNotificationHex.toList(),
                    preComm = preComm,
                    note = if (probeAttempted) {
                        "Only notification subscriptions and an unauthenticated PRE_COMM diagnostic probe were used; no profile, speed, credential, or firmware change was requested."
                    } else {
                        "PRE_COMM diagnostic probe was not available on the discovered GATT characteristics."
                    }
                ),
                safety = mapOf(
                    "readOnly" to true,
                    "configurationWritesPerformed" to false,
                    "firmwareFlashed" to false
                )
            ))
            return
        }
        gatt.readCharacteristic(next)
    }

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

private fun ByteArray.indexOfFrameHeader(): Int {
    for (i in 0 until size - 1) {
        if (this[i] == 0x5A.toByte() && this[i + 1] == 0xA5.toByte()) return i
    }
    return -1
}
