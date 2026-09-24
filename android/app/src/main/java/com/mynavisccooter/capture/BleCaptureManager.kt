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
    private var readQueue: MutableList<BluetoothGattCharacteristic> = mutableListOf()
    private val readValues = linkedMapOf<String, ByteArray>()

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
            val model = ModelProfiles.fromAdvertisement(name, data.values.firstOrNull() ?: "")
            if (model != ModelProfiles.UNKNOWN || name.contains("segway", true) || name.contains("ninebot", true)) {
                devices[device.address] = ScannedScooter(device, name, result.rssi, data, model)
                listener.onDevicesChanged(devices.values.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onStatus("Falha no scan BLE: código $errorCode")
        }
    }

    fun startScan() {
        if (!hasScanPermission()) {
            listener.onStatus("Permissão Bluetooth necessária")
            return
        }
        devices.clear()
        listener.onDevicesChanged(emptyList())
        scanner?.startScan(scanCallback)
        listener.onStatus("A procurar ZT3/F3/GT3…")
    }

    fun stopScan() {
        if (hasScanPermission()) scanner?.stopScan(scanCallback)
    }

    fun connect(item: ScannedScooter) {
        if (!hasConnectPermission()) {
            listener.onStatus("Permissão de ligação Bluetooth necessária")
            return
        }
        stopScan()
        selected = item
        readValues.clear()
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
            listener.onStatus("Serviços encontrados: ${gatt.services.size}. Leituras seguras: ${readQueue.size}")
            readNext(gatt)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) readValues[characteristic.uuid.toString()] = characteristic.value ?: byteArrayOf()
            readNext(gatt)
        }
    }

    private fun readNext(gatt: BluetoothGatt) {
        val next = readQueue.removeFirstOrNull()
        if (next == null) {
            val item = selected ?: return
            val reportServices = gatt.services.map { service ->
                ServiceCapture(service.uuid.toString(), service.characteristics.map { c ->
                    val value = readValues[c.uuid.toString()]
                    CharacteristicCapture(c.uuid.toString(), c.propertyLabel(), bytesToHex(value), bytesToSafeText(value))
                })
            }
            listener.onCaptureReady(CaptureReport(
                capturedAtUtc = CaptureReport.nowUtc(),
                appVersion = "0.1.0",
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceName = item.name,
                deviceAddress = item.device.address,
                rssi = item.rssi,
                manufacturerData = item.manufacturerData,
                model = item.model,
                services = reportServices,
                safety = mapOf("readOnly" to true, "writesPerformed" to false, "firmwareFlashed" to false)
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
