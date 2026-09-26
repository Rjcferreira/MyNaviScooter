package com.mynavisccooter.capture

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.UUID
import java.security.SecureRandom

data class ScannedScooter(val device: BluetoothDevice, val name: String, val rssi: Int,
    val manufacturerData: Map<String, String>, val model: ModelProfile)

/** Serialized diagnostic connection with explicitly selected initial pairing. */
@Suppress("DEPRECATION", "MissingPermission")
@android.annotation.SuppressLint("MissingPermission")
class BleCaptureManager(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onStage(stage: String)
        fun onStatus(message: String)
        fun onDevicesChanged(devices: List<ScannedScooter>)
        fun onCaptureReady(report: CaptureReport)
        fun onPairingAvailable(confirm: () -> Unit, cancel: () -> Unit)
    }
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, ScannedScooter>()
    private val serviceId = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val txId = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val rxId = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val cccdId = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private var gatt: BluetoothGatt? = null
    private var selected: ScannedScooter? = null
    private var scanning = false
    private var phase = "idle"
    private var started = 0L
    private var timeout: Runnable? = null
    private var reported = false
    private var attempted = false
    private var requestHex: String? = null
    private var result: PreCommResult? = null
    private var authAttempted = false
    private var authenticated = false
    private var authNote = "Autenticação de leitura não iniciada."
    private var services: List<ServiceCapture> = emptyList()
    private val events = mutableListOf<String>()
    private val notifications = mutableListOf<String>()
    private val subscribed = mutableListOf<String>()
    private val frames = ProbeFrameBuffer()
    private val credentialStore = EncryptedCredentialStore(context)
    private var pendingStore: EncryptedCredentialStore? = null
    private var sessionCredential: SessionCredentials? = null
    private var pairing = PairingProgress()
    private var pairingWriteAttempted = false
    private var pairingAccepted = false
    private var authCounter = 2
    private var mtu = 23
    private var forcePairing = false

    private fun event(message: String) {
        if (events.size < 200) events += "${SystemClock.elapsedRealtime() - started}ms $message"
    }
    private fun arm(stage: String, milliseconds: Long = 10000L) {
        timeout?.let(main::removeCallbacks)
        phase = stage
        listener.onStage(stage)
        timeout = Runnable { finish("timeout_$stage") }.also { main.postDelayed(it, milliseconds) }
    }
    private fun active(connection: BluetoothGatt, action: () -> Unit) {
        main.post { if (gatt === connection && !reported) action() }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scan: ScanResult) {
            main.post {
                if (!scanning) return@post
                val manufacturer = linkedMapOf<String, String>()
                scan.scanRecord?.manufacturerSpecificData?.let { data ->
                    for (i in 0 until data.size()) manufacturer["%04X".format(data.keyAt(i))] = bytesToHex(data.valueAt(i)) ?: ""
                }
                val name = scan.scanRecord?.deviceName ?: runCatching { scan.device.name }.getOrNull() ?: "Sem nome"
                val model = ModelProfiles.fromAdvertisement(name, manufacturer.entries.joinToString("") { it.key + it.value })
                if (model != ModelProfiles.UNKNOWN || name.startsWith("1K1") || name.contains("segway", true) || name.contains("ninebot", true)) {
                    devices[scan.device.address] = ScannedScooter(scan.device, name, scan.rssi, manufacturer, model)
                    listener.onDevicesChanged(devices.values.toList())
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            main.post { scanning = false; listener.onStatus("Falha no scan BLE: código $errorCode") }
        }
    }
    fun startScan() {
        if (!hasScanPermission() || !hasConnectPermission()) { listener.onStatus("Permissão Bluetooth necessária"); return }
        if (adapter?.isEnabled != true) { listener.onStatus("Ativa o Bluetooth do telemóvel."); return }
        if (scanning) return
        close()
        devices.clear()
        listener.onDevicesChanged(emptyList())
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanning = true
        runCatching { scanner.startScan(scanCallback) }.onFailure {
            scanning = false
            listener.onStatus("Não foi possível iniciar o scan: ${it.javaClass.simpleName}")
        }
        if (scanning) {
            listener.onStatus("A procurar scooters…")
            main.postDelayed({ stopScan() }, 20000L)
        }
    }
    fun stopScan() {
        if (scanning && hasScanPermission()) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
    }
    fun connect(item: ScannedScooter, pairAgain: Boolean = false) {
        if (!hasConnectPermission()) { listener.onStatus("Permissão de ligação Bluetooth necessária"); return }
        close()
        forcePairing = pairAgain
        selected = item
        events.clear(); notifications.clear(); subscribed.clear(); frames.clear()
        services = emptyList(); result = null; requestHex = null
        authAttempted = false; authenticated = false
        sessionCredential = null
        pairing = PairingProgress()
        pairingWriteAttempted = false; pairingAccepted = false; authCounter = 2; mtu = 23
        pendingStore = EncryptedCredentialStore(context, "pending_" + item.device.address.replace(":", ""))
        authNote = "Autenticação de leitura não iniciada."
        attempted = false; reported = false
        started = SystemClock.elapsedRealtime()
        event("build=${BuildConfig.VERSION_NAME} revision=${BuildConfig.REVISION}")
        listener.onStatus("A ligar a ${item.name}…")
        arm("connect", 15000L)
        runCatching {
            gatt = item.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }.onFailure { event("connect_exception=${it.javaClass.simpleName}"); finish("connect_failed") }
    }
    fun close() {
        stopScan()
        main.removeCallbacksAndMessages(null)
        timeout = null
        val old = gatt
        gatt = null
        reported = true
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = active(g) {
            event("connection status=$status state=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) finish("connection_error_$status")
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) finish("disconnected_$phase")
            else if (newState == BluetoothProfile.STATE_CONNECTED && phase == "connect") {
                arm("services")
                val accepted = g.discoverServices()
                event("discoverServices accepted=$accepted")
                if (!accepted) finish("services_rejected")
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = active(g) {
            if (phase != "services") return@active
            event("services status=$status count=${g.services.size}")
            services = g.services.map { it.toCapture() }
            if (status != BluetoothGatt.GATT_SUCCESS) { finish("services_error_$status"); return@active }
            val service = g.getService(serviceId)
            if (service?.getCharacteristic(txId) == null || service.getCharacteristic(rxId) == null) {
                finish("uart_missing"); return@active
            }
            // Official HCI order: MTU -> CCCD -> PRE_COMM.
            arm("mtu")
            val accepted = g.requestMtu(517)
            event("requestMtu requested=517 accepted=$accepted")
            if (!accepted) subscribe(g)
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = active(g) {
            event("mtu value=$mtu status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) this@BleCaptureManager.mtu = mtu
            if (phase == "mtu") subscribe(g)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = active(g) {
            event("cccd callback characteristic=${descriptor.characteristic.uuid} status=$status")
            if (phase != "cccd" || descriptor.uuid != cccdId || descriptor.characteristic.uuid != rxId) return@active
            if (status != BluetoothGatt.GATT_SUCCESS) { finish("cccd_error_$status"); return@active }
            subscribed += rxId.toString()
            sendProbe(g)
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = active(g) {
            event("write callback characteristic=${characteristic.uuid} status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) finish("write_error_$status")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value?.clone() ?: return
            active(g) { receive(g, characteristic.uuid, value) }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.clone()
            active(g) { receive(g, characteristic.uuid, copy) }
        }
    }
    private fun subscribe(g: BluetoothGatt) {
        val characteristic = g.getService(serviceId)?.getCharacteristic(rxId)
        val descriptor = characteristic?.getDescriptor(cccdId)
        if (characteristic == null || descriptor == null) { finish("cccd_missing"); return }
        val accepted = g.setCharacteristicNotification(characteristic, true)
        event("local_subscription accepted=$accepted characteristic=$rxId")
        if (!accepted) { finish("subscription_rejected"); return }
        arm("cccd")
        val writeAccepted = if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            event("cccd enqueue status=$code")
            code == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }
        event("cccd accepted=$writeAccepted")
        if (!writeAccepted) finish("cccd_rejected")
    }
    private fun sendProbe(g: BluetoothGatt) {
        if (attempted) return
        val tx = g.getService(serviceId)?.getCharacteristic(txId)
        if (tx == null || tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
            finish("write_channel_missing"); return
        }
        val frame = Encryption2Probe.buildPreCommFrame(selected!!.name)
        attempted = true
        requestHex = bytesToHex(frame)
        arm("precomm", 15000L)
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            event("precomm enqueue status=$code characteristic=$txId bytes=${frame.size}")
            code == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value = frame
            g.writeCharacteristic(tx)
        }
        event("precomm accepted=$accepted; enqueue is not peer acknowledgement")
        if (!accepted) { finish("precomm_rejected"); return }
        listener.onStatus("À espera da resposta inicial da scooter (15 s). O emparelhamento pelo botão é uma etapa posterior.")
    }
    private fun receive(g: BluetoothGatt, uuid: UUID, value: ByteArray) {
        event("notify characteristic=$uuid bytes=${value.size}")
        if (uuid != rxId || value.isEmpty()) return
        if (notifications.size >= 64) { finish("notification_limit"); return }
        // A pairing response may echo credential material. Do not export raw pairing traffic.
        if (!pairingWriteAttempted) notifications += bytesToHex(value) ?: ""
        for (frame in frames.append(value)) {
            val parsed = Encryption2Probe.parsePreCommFrame(frame, selected!!.name)
            event("frame bytes=${frame.size} precommValid=${parsed != null}")
            if (parsed != null) {
                if (phase != "precomm") {
                    event("duplicate_precomm_ignored")
                    continue
                }
                result = parsed
                beginReadOnlyAuth(g, parsed)
                return
            }
            if (phase == "pairing" || phase == "button") {
                val reply = Encryption2Probe.parsePairingFrame(frame, selected!!.name, result!!.authParameterHex)
                if (reply == null) { event("pairing_frame_invalid"); continue }
                event("pairing_reply index=${reply.index} counter=${reply.counter}")
                when (pairing.accept(reply)) {
                    PairingProgress.Action.WAIT_FOR_BUTTON -> {
                        // Do not extend the bounded wait on repeated status messages.
                        if (phase != "button") arm("button", 45000L)
                        listener.onStatus("A scooter pediu confirmação. Prime uma vez o botão de ligar/desligar. A app continua automaticamente quando receber a autorização.")
                    }
                    PairingProgress.Action.AUTHENTICATE -> {
                        pairingAccepted = true
                        event("pairing_accepted_by_scooter")
                        authCounter = 3
                        sendAuth(g, result!!)
                    }
                    PairingProgress.Action.REJECT -> finish("pairing_rejected")
                    PairingProgress.Action.IGNORE -> event("pairing_duplicate_ignored")
                }
                continue
            }
            if (phase == "auth" && authAttempted) {
                val auth = Encryption2Probe.parseAuthFrame(frame, sessionCredential?.passwordHex ?: "", result?.authParameterHex ?: "")
                if (auth != null) {
                    authenticated = auth.accepted
                    if (auth.accepted) {
                        try {
                            credentialStore.save(sessionCredential!!)
                            pendingStore?.clear()
                        } catch (_: Exception) {
                            authNote = "Autenticado, mas falhou a gravação local. A credencial pendente foi mantida."
                            finish("authenticated_storage_failed")
                            return
                        }
                    }
                    authNote = if (auth.accepted) "AUTH aceite; ainda não foram enviados comandos de leitura." else "AUTH recusado pela scooter."
                    finish(if (auth.accepted) "auth_received" else "auth_rejected")
                    return
                }
            }
        }
    }

    private fun beginReadOnlyAuth(g: BluetoothGatt, pre: PreCommResult) {
        if (pre.reportedSerial != selected?.name || !pre.reportedSerial.orEmpty().matches(Regex("[A-Za-z0-9]{14}"))) {
            finish("serial_mismatch")
            return
        }
        if (mtu < 32) { finish("mtu_too_small_for_handshake"); return }
        val choice = CredentialSelection.choose(pre.reportedSerial!!, credentialStore.load(), pendingStore?.load())
        val credentials = choice?.second
        event("credential_source=${choice?.first ?: "none"}; explicit_repair=$forcePairing")
        if (forcePairing || credentials == null || pre.index == 0) {
            authNote = "Emparelhamento inicial disponível; aguarda a escolha do proprietário."
            arm("pairing_consent", 60000L)
            listener.onPairingAvailable(
                confirm = { if (gatt === g && !reported && phase == "pairing_consent") beginPairing(g, pre) },
                cancel = { if (gatt === g && !reported && phase == "pairing_consent") finish("pairing_cancelled") }
            )
            return
        }
        sessionCredential = credentials
        sendAuth(g, pre)
    }

    private fun beginPairing(g: BluetoothGatt, pre: PreCommResult) {
        val password = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val candidate = SessionCredentials(pre.reportedSerial!!, bytesToHex(password)!!, null)
        try {
            // Persist BEFORE transmission so an interrupted handshake cannot lose the new key.
            credentialStore.load()?.takeIf { it.serialNumber == pre.reportedSerial }?.let {
                EncryptedCredentialStore(context, "saved_archive_" + selected!!.device.address.replace(":", "") + "_" + System.currentTimeMillis()).save(it)
            }
            pendingStore?.load()?.let {
                EncryptedCredentialStore(context, "archive_" + selected!!.device.address.replace(":", "") + "_" + System.currentTimeMillis()).save(it)
            }
            pendingStore!!.save(candidate)
        } catch (_: Exception) {
            finish("pairing_storage_failed")
            return
        }
        sessionCredential = candidate
        val frame = Encryption2Probe.buildPairingFrame(selected!!.name, candidate.passwordHex, pre.authParameterHex)
        arm("pairing", 15000L)
        pairingWriteAttempted = true
        authNote = "Pedido de emparelhamento enviado; aguarda confirmação da scooter."
        listener.onStatus("A pedir emparelhamento à scooter…")
        val tx = g.getService(serviceId)?.getCharacteristic(txId)
        if (tx == null) { finish("pairing_channel_missing"); return }
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value = frame
            g.writeCharacteristic(tx)
        }
        event("set_pwd_enqueued=$accepted; credential and frame redacted")
        if (!accepted) finish("pairing_rejected_by_stack")
    }

    private fun sendAuth(g: BluetoothGatt, pre: PreCommResult) {
        val credentials = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId)
        if (tx == null) { authNote = "Canal Nordic UART indisponível para AUTH."; finish("auth_channel_missing"); return }
        val frame = Encryption2Probe.buildAuthFrame(selected!!.name, credentials.passwordHex, pre.authParameterHex, pre.reportedSerial!!, authCounter)
        authAttempted = true
        authNote = "AUTH enviado. A aguardar confirmação criptográfica da scooter."
        arm("auth", 10000L)
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            event("auth enqueue status=$code characteristic=$txId bytes=${frame.size}")
            code == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value = frame
            g.writeCharacteristic(tx)
        }
        event("auth accepted=$accepted; enqueue is not peer acknowledgement")
        if (!accepted) finish("auth_rejected_by_stack")
    }
    private fun finish(outcome: String) {
        if (reported) return
        reported = true
        if (outcome == "timeout_auth") authNote = "A scooter não respondeu à autenticação. A credencial foi preservada. Podes escolher Emparelhar novamente."
        timeout?.let(main::removeCallbacks)
        timeout = null
        event("complete outcome=$outcome")
        val connection = gatt
        gatt = null
        runCatching { connection?.disconnect() }
        if (connection != null) Handler(Looper.getMainLooper()).postDelayed({ runCatching { connection.close() } }, 300L)
        event("disconnect_requested")
        val item = selected ?: return
        listener.onCaptureReady(CaptureReport(
            CaptureReport.nowUtc(), BuildConfig.VERSION_NAME,
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            item.name, item.device.address, item.rssi, item.manufacturerData, item.model, services,
            ProtocolProbeCapture(attempted, subscribed.toList(), requestHex, notifications.toList(), result,
                authAttempted, authenticated, authNote,
                "PRE_COMM and AUTH diagnostics. SET_PWD only after owner selects pairing. Raw pairing traffic is omitted."),
            mapOf("readOnly" to !pairingWriteAttempted, "configurationWritesPerformed" to false,
                "firmwareFlashed" to false, "pairingWriteAttempted" to pairingWriteAttempted,
                "pairingAcceptedByScooter" to pairingAccepted),
            events.toList(), outcome
        ))
    }
    private fun hasScanPermission() = ContextCompat.checkSelfPermission(context,
        if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    private fun hasConnectPermission() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
