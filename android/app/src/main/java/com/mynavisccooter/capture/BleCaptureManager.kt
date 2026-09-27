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
enum class RequestedOperation { CAPTURE, APPLY_25_30, RESTORE_INITIAL }

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
    private var pairingCounter = 2
    private var pairingAttempts = 0
    private var pairingRetry: Runnable? = null
    private var authAttempts = 0
    private var authRetry: Runnable? = null
    private var mtu = 23
    private var forcePairing = false
    private var pairingReconnects = 0
    private val initialState = mutableListOf<RegisterCapture>()
    private var backupIndex = 0
    private var readCounter = 4
    private val baselineStore = BaselineStore(context)
    private var requestedOperation = RequestedOperation.CAPTURE
    private var writeQueue = mutableListOf<RegisterCapture>()
    private var writeIndex = 0
    private var configurationWritesPerformed = false
    private var verifyWriteIndex = 0

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
    fun connect(item: ScannedScooter, pairAgain: Boolean = false, operation: RequestedOperation = RequestedOperation.CAPTURE) {
        if (!hasConnectPermission()) { listener.onStatus("Permissão de ligação Bluetooth necessária"); return }
        close()
        forcePairing = pairAgain
        requestedOperation = operation
        selected = item
        events.clear(); notifications.clear(); subscribed.clear(); frames.clear(); initialState.clear()
        services = emptyList(); result = null; requestHex = null
        authAttempted = false; authenticated = false
        sessionCredential = null
        pairing = PairingProgress()
        pairingWriteAttempted = false; pairingAccepted = false
        authCounter = 2; pairingCounter = 2; pairingAttempts = 0; pairingRetry = null
        authAttempts = 0; authRetry = null; mtu = 23
        configurationWritesPerformed = false
        backupIndex = 0; readCounter = 4
        verifyWriteIndex = 0; writeQueue.clear(); writeIndex = 0
        pendingStore = EncryptedCredentialStore(context, "pending_" + item.device.address.replace(":", ""))
        authNote = "Autenticação de leitura não iniciada."
        attempted = false; reported = false
        pairingReconnects = 0
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
        pairingRetry?.let(main::removeCallbacks)
        pairingRetry = null
        authRetry?.let(main::removeCallbacks)
        authRetry = null
        val old = gatt
        gatt = null
        reported = true
        runCatching { old?.disconnect() }
        runCatching { old?.close() }
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = active(g) {
            event("connection status=$status state=$newState")
            if (status == 19 && newState == BluetoothProfile.STATE_DISCONNECTED &&
                phase == "auth" && pairingWriteAttempted && pairingReconnects == 0) {
                restartAfterPairing(g)
            }
            else if (status != BluetoothGatt.GATT_SUCCESS) finish("connection_error_$status")
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
                val reply = Encryption2Probe.parsePairingFrame(
                    frame, selected!!.name, result!!.authParameterHex, sessionCredential?.passwordHex
                )
                if (reply == null) {
                    val wireCounter = if (frame.size >= 2) {
                        ((frame[frame.lastIndex - 1].toInt() and 0xFF) shl 8) or
                            (frame.last().toInt() and 0xFF)
                    } else -1
                    // A zero-payload 13-byte reply contains no credential or
                    // user data. Preserve it for protocol diagnosis if an
                    // unknown firmware variant still fails both validated keys.
                    val safeFrame = if (frame.size == 13) bytesToHex(frame) else "redacted"
                    event("pairing_frame_invalid bytes=${frame.size} counter=$wireCounter frame=$safeFrame")
                    continue
                }
                event("pairing_reply index=${reply.index} counter=${reply.counter}")
                // NinebotCrypto uses one shared counter stream. After TX=5 and
                // RX=6 the internal iterator becomes 6; encrypting the next
                // frame increments it to TX=7. Do not skip to RX+2.
                pairingCounter = reply.counter + 1
                when (pairing.accept(reply)) {
                    PairingProgress.Action.WAIT_FOR_BUTTON -> {
                        event("pairing_random_received_waiting_for_button")
                        authNote = "Chave temporária recebida; aguarda confirmação física no botão da scooter."
                        listener.onStatus("A scooter recebeu a chave. Prime uma vez o botão de ligar/desligar; a app continuará a tentar até receber a confirmação física.")
                    }
                    PairingProgress.Action.AUTHENTICATE -> {
                        pairingRetry?.let(main::removeCallbacks)
                        pairingRetry = null
                        event("pairing_random_acknowledged")
                        authCounter = NinebotCounter.nextTxAfterReply(reply.counter)
                        listener.onStatus("Chave aceite. A concluir a autorização física da scooter…")
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
                    authRetry?.let(main::removeCallbacks)
                    authRetry = null
                    authenticated = auth.accepted
                    if (auth.accepted) {
                        pairingAccepted = pairingWriteAttempted
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
                    if (auth.accepted) {
                        readCounter = NinebotCounter.nextTxAfterReply(auth.counter)
                        beginInitialStateRead(g)
                    } else finish("auth_rejected")
                    return
                }
            }
            if (phase == "backup") {
                val credential = sessionCredential ?: continue
                val reply = Encryption2Probe.parseReadRegisterFrame(frame, credential.passwordHex, result?.authParameterHex ?: "") ?: continue
                val expected = Zt3BackupPlan.required.getOrNull(backupIndex) ?: continue
                if (reply.device != expected.device || reply.register != expected.register) {
                    event("backup_unexpected device=${reply.device} register=${reply.register} counter=${reply.counter}")
                    continue
                }
                initialState += RegisterCapture(reply.device, reply.register, expected.name, expected.length, reply.valueHex)
                event("backup_reply name=${expected.name} bytes=${reply.valueHex.length / 2} counter=${reply.counter}")
                backupIndex += 1
                readCounter = NinebotCounter.nextTxAfterReply(reply.counter)
                if (backupIndex == Zt3BackupPlan.required.size) {
                    completeBaselineOrWrite(g)
                } else sendNextBackupRead(g)
                return
            }
            if (phase == "write") {
                val credential = sessionCredential ?: continue
                val reply = Encryption2Probe.parseWriteRegisterFrame(frame, credential.passwordHex, result?.authParameterHex ?: "") ?: continue
                val expected = writeQueue.getOrNull(writeIndex) ?: continue
                if (reply.device != expected.device || reply.register != expected.register || !reply.accepted) {
                    finish("profile_write_rejected"); return
                }
                event("profile_write_ack name=${expected.name} counter=${reply.counter}")
                writeIndex += 1; readCounter = NinebotCounter.nextTxAfterReply(reply.counter)
                if (writeIndex == writeQueue.size) {
                    verifyWriteIndex = 0
                    arm("verify_write", 8000L)
                    listener.onStatus("Escrita aceite. A reler os dois limites para confirmar…")
                    main.postDelayed({ if (gatt === g && !reported && phase == "verify_write") sendNextWriteVerification(g) }, 300L)
                } else sendNextWrite(g)
                return
            }
            if (phase == "verify_write") {
                val credential = sessionCredential ?: continue
                val reply = Encryption2Probe.parseReadRegisterFrame(frame, credential.passwordHex, result?.authParameterHex ?: "") ?: continue
                val expected = writeQueue.getOrNull(verifyWriteIndex) ?: continue
                if (reply.device != expected.device || reply.register != expected.register) continue
                val matches = reply.valueHex.equals(expected.valueHex, true)
                event("profile_verify name=${expected.name} expected=${expected.valueHex} actual=${reply.valueHex} matches=$matches counter=${reply.counter}")
                if (!matches) { finish("profile_write_not_persisted"); return }
                verifyWriteIndex += 1; readCounter = NinebotCounter.nextTxAfterReply(reply.counter)
                if (verifyWriteIndex == writeQueue.size) finish("profile_write_verified") else sendNextWriteVerification(g)
                return
            }
        }
    }

    private fun completeBaselineOrWrite(g: BluetoothGatt) {
        val serial = result?.reportedSerial ?: run { finish("baseline_serial_missing"); return }
        val captured = InitialBaseline(serial, initialState.toList())
        if (!baselineStore.saveFirst(captured)) { finish("baseline_store_failed"); return }
        val baseline = baselineStore.load()
        if (baseline == null || baseline.serial != serial || baseline.registers.size != Zt3BackupPlan.required.size) {
            finish("baseline_verify_failed"); return
        }
        if (requestedOperation == RequestedOperation.CAPTURE) {
            authNote = "AUTH aceite; estado inicial cifrado e verificado localmente."
            finish("initial_state_saved"); return
        }
        val reg47 = baseline.register(0x16, 0x47) ?: run { finish("baseline_speed_missing"); return }
        val reg48 = baseline.register(0x16, 0x48) ?: run { finish("baseline_speed_missing"); return }
        val germanZt3 = serial.startsWith("1K1D", ignoreCase = true)
        val taintedByReviewTest = germanZt3 && (
            reg47.valueHex.equals("0F19", true) ||
            reg48.valueHex.equals("1E14", true) || reg48.valueHex.equals("1E1E", true) ||
            reg48.valueHex.equals("1414", true)
        )
        if (requestedOperation == RequestedOperation.APPLY_25_30 && germanZt3) {
            event("profile_write_blocked reason=german_region_rejected_sport_30")
            finish("region_change_required")
            return
        }
        writeQueue = if (requestedOperation == RequestedOperation.RESTORE_INITIAL && taintedByReviewTest) {
            event("baseline_recovery source=verified_pre_write_report reg47=0F14 reg48=1416")
            mutableListOf(reg47.copy(valueHex = "0F14"), reg48.copy(valueHex = "1416"))
        } else if (requestedOperation == RequestedOperation.RESTORE_INITIAL) mutableListOf(reg47, reg48) else mutableListOf(
            reg47.copy(valueHex = reg47.valueHex.take(2) + "19"),
            // Verified X3 wire format is [0x14, Sport]. Do not derive byte 0
            // from a baseline that may contain the review.47/.48 test artifact.
            reg48.copy(valueHex = "141E")
        )
        writeIndex = 0; arm("write", 10000L)
        listener.onStatus(if (requestedOperation == RequestedOperation.RESTORE_INITIAL) "A restaurar os limites guardados…" else "A aplicar apenas Drive 25 e Sport 30…")
        sendNextWrite(g)
    }

    private fun sendNextWrite(g: BluetoothGatt) {
        val item = writeQueue.getOrNull(writeIndex) ?: return
        val credential = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId) ?: run { finish("write_channel_missing"); return }
        val frame = Encryption2Probe.buildWriteRegisterFrame(credential.passwordHex, result!!.authParameterHex,
            item.device, item.register, item.valueHex, readCounter)
        val accepted = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(tx, frame,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; tx.value = frame; g.writeCharacteristic(tx)
        }
        event("profile_write name=${item.name} value=${item.valueHex} counter=$readCounter accepted=$accepted")
        if (!accepted) finish("profile_write_stack_rejected") else configurationWritesPerformed = true
    }

    private fun sendNextWriteVerification(g: BluetoothGatt) {
        val item = writeQueue.getOrNull(verifyWriteIndex) ?: return
        val credential = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId) ?: run { finish("verify_channel_missing"); return }
        val frame = Encryption2Probe.buildReadRegisterFrame(credential.passwordHex, result!!.authParameterHex,
            item.device, item.register, item.expectedBytes, readCounter)
        val accepted = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(tx, frame,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; tx.value = frame; g.writeCharacteristic(tx)
        }
        event("profile_verify_read name=${item.name} counter=$readCounter accepted=$accepted")
        if (!accepted) finish("verify_read_rejected")
    }

    private fun beginInitialStateRead(g: BluetoothGatt) {
        initialState.clear(); backupIndex = 0
        arm("backup", 20000L)
        listener.onStatus("Autenticada. A guardar o estado inicial da scooter — apenas leitura, sem alterações…")
        sendNextBackupRead(g)
    }

    private fun sendNextBackupRead(g: BluetoothGatt) {
        val spec = Zt3BackupPlan.required.getOrNull(backupIndex) ?: return
        val credential = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId) ?: run { finish("backup_channel_missing"); return }
        val frame = Encryption2Probe.buildReadRegisterFrame(credential.passwordHex, result!!.authParameterHex,
            spec.device, spec.register, spec.length, readCounter)
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; tx.value = frame; g.writeCharacteristic(tx)
        }
        event("backup_read name=${spec.name} device=${spec.device} register=${spec.register} counter=$readCounter accepted=$accepted")
        if (!accepted) finish("backup_read_rejected")
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
        arm("button", 30000L)
        pairingWriteAttempted = true
        authNote = "Chave temporária enviada; aguarda confirmação física no botão da scooter."
        listener.onStatus("Prime agora uma vez o botão de ligar/desligar da scooter. Mantém a scooter ligada e próxima; a app continuará automaticamente.")
        sendPairingAttempt(g, pre)
    }

    private fun sendPairingAttempt(g: BluetoothGatt, pre: PreCommResult) {
        if (reported || phase != "button") return
        val credentials = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId)
        if (tx == null) { finish("pairing_channel_missing"); return }
        val frame = Encryption2Probe.buildPairingFrame(
            selected!!.name, credentials.passwordHex, pre.authParameterHex, pairingCounter
        )
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value = frame
            g.writeCharacteristic(tx)
        }
        pairingAttempts += 1
        event("pairing_enqueued=$accepted attempt=$pairingAttempts counter=$pairingCounter; credential and frame redacted")
        if (!accepted) { finish("pairing_rejected_by_stack"); return }
        pairingCounter += 1
        pairingRetry = Runnable {
            if (gatt === g && !reported && phase == "button") sendPairingAttempt(g, pre)
        }.also { main.postDelayed(it, 1000L) }
    }

    private fun sendAuth(g: BluetoothGatt, pre: PreCommResult) {
        val credentials = sessionCredential ?: return
        val tx = g.getService(serviceId)?.getCharacteristic(txId)
        if (tx == null) { authNote = "Canal Nordic UART indisponível para AUTH."; finish("auth_channel_missing"); return }
        val frame = Encryption2Probe.buildAuthFrame(selected!!.name, credentials.passwordHex, pre.authParameterHex, pre.reportedSerial!!, authCounter)
        authAttempted = true
        authNote = "AUTH enviado. A aguardar confirmação criptográfica da scooter."
        if (phase != "auth") arm("auth", 10000L)
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeCharacteristic(tx, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            event("auth enqueue status=$code characteristic=$txId bytes=${frame.size}")
            code == BluetoothStatusCodes.SUCCESS
        } else {
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value = frame
            g.writeCharacteristic(tx)
        }
        authAttempts += 1
        event("auth accepted=$accepted attempt=$authAttempts counter=$authCounter; enqueue is not peer acknowledgement")
        if (!accepted) { finish("auth_rejected_by_stack"); return }
        authCounter += 1
        // This ZT3 firmware completes fresh pairing only after a repeated
        // 0x5D, then restarts BLE with status 19. Keep the retry bounded; the
        // transport restart is handled once by restartAfterPairing().
        if (authAttempts < 4) {
            authRetry = Runnable {
                if (gatt === g && !reported && phase == "auth") sendAuth(g, pre)
            }.also { main.postDelayed(it, 1000L) }
        }
    }

    private fun restartAfterPairing(old: BluetoothGatt) {
        pairingReconnects += 1
        event("pairing_transport_restart status=19; reconnect=$pairingReconnects")
        timeout?.let(main::removeCallbacks)
        pairingRetry?.let(main::removeCallbacks)
        authRetry?.let(main::removeCallbacks)
        timeout = null; pairingRetry = null; authRetry = null
        gatt = null
        runCatching { old.close() }

        // A ZT3 may restart its BLE session after accepting the app random.
        // Keep the encrypted pending credential, but reset all per-connection
        // crypto/GATT state and verify it through a fresh PRE_COMM -> AUTH.
        frames.clear(); subscribed.clear(); notifications.clear()
        services = emptyList(); result = null; requestHex = null
        attempted = false; authAttempted = false; authenticated = false
        authCounter = 2; authAttempts = 0; pairingCounter = 2; pairingAttempts = 0; mtu = 23
        pairing = PairingProgress()
        authNote = "A scooter reiniciou a sessão Bluetooth após o emparelhamento; a verificar a nova credencial."
        listener.onStatus("A scooter reiniciou o Bluetooth. A reconectar e verificar o emparelhamento…")
        val item = selected ?: run { finish("pairing_restart_missing_device"); return }
        arm("connect", 15000L)
        main.postDelayed({
            if (!reported && phase == "connect" && pairingReconnects == 1) {
                runCatching {
                    gatt = item.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                }.onFailure {
                    event("pairing_reconnect_exception=${it.javaClass.simpleName}")
                    finish("pairing_reconnect_failed")
                }
            }
        }, 1200L)
    }
    private fun finish(outcome: String) {
        if (reported) return
        reported = true
        if (outcome == "timeout_auth") authNote = "A scooter não respondeu à autenticação. A credencial foi preservada. Podes escolher Emparelhar novamente."
        timeout?.let(main::removeCallbacks)
        timeout = null
        pairingRetry?.let(main::removeCallbacks)
        pairingRetry = null
        authRetry?.let(main::removeCallbacks)
        authRetry = null
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
            initialState.toList(),
            mapOf("readOnly" to (!pairingWriteAttempted && !configurationWritesPerformed), "configurationWritesPerformed" to configurationWritesPerformed,
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
