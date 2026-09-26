package com.mynavisccooter.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity(), BleCaptureManager.Listener {
    private lateinit var dashboard: Dashboard
    private lateinit var ble: BleCaptureManager
    private lateinit var credentials: EncryptedCredentialStore
    private var pairingDialog: AlertDialog? = null
    private var latestReport: CaptureReport? = null
    private var selected: ScannedScooter? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        credentials = EncryptedCredentialStore(this)
        ble = BleCaptureManager(this, this)
        dashboard = Dashboard(this)
        val view = dashboard.create(
            onScan = { if (permissionsReady()) { dashboard.recover.visibility = View.GONE; ble.startScan() } else requestPermissions() },
            onRecover = { selected?.let { connect(it, true) } },
            onExport = { exportReport() }, onSettings = { settings() }
        )
        val container = FrameLayout(this).apply { setBackgroundColor(0xFF04101B.toInt()); addView(view) }
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun connect(item: ScannedScooter, repair: Boolean = false) {
        if (busy) return
        selected = item
        dashboard.select(item.name)
        dashboard.recover.visibility = View.GONE
        dashboard.devices.removeAllViews()
        latestReport = null
        dashboard.export.isEnabled = false
        dashboard.export.alpha = .45f
        busy = true
        ble.connect(item, repair)
    }

    override fun onStage(stage: String) { runOnUiThread { dashboard.stage(stage) } }
    override fun onStatus(message: String) { runOnUiThread { dashboard.status.text = message } }
    override fun onDevicesChanged(devices: List<ScannedScooter>) {
        runOnUiThread {
            if (busy) return@runOnUiThread
            dashboard.devices.removeAllViews()
            devices.forEach { item ->
                dashboard.devices.addView(dashboard.button("${item.name}\n${item.model.name} · ${item.rssi} dBm") { connect(item) })
            }
        }
    }

    override fun onCaptureReady(report: CaptureReport) {
        runOnUiThread {
            latestReport = report
            busy = false
            pairingDialog?.dismiss(); pairingDialog = null
            dashboard.stage("finished")
            val ok = report.protocolProbe?.authenticated == true
            dashboard.status.text = when {
                report.outcome == "authenticated_storage_failed" -> "A scooter autenticou, mas a gravação local falhou. Credencial pendente preservada."
                ok -> "Autenticação confirmada. Credencial guardada. A sessão de diagnóstico terminou; os dados de condução ainda não estão disponíveis."
                report.outcome == "timeout_auth" -> "Sem resposta à autenticação. A credencial pode estar desatualizada. Podes emparelhar novamente; as credenciais anteriores serão preservadas."
                report.outcome == "pairing_rejected" -> "A scooter recusou o emparelhamento. Exporta o diagnóstico para análise."
                report.outcome == "timeout_button" -> "Não recebemos a confirmação do botão dentro do prazo. Credencial pendente preservada."
                report.outcome == "timeout_pairing" -> "A scooter não respondeu ao pedido de emparelhamento. Exporta o diagnóstico para análise."
                report.outcome == "pairing_cancelled" -> "Emparelhamento cancelado."
                else -> "Ligação não concluída (${report.outcome}). Podes exportar o diagnóstico."
            }
            dashboard.recover.visibility = if (!ok && selected != null) View.VISIBLE else View.GONE
            dashboard.export.isEnabled = true; dashboard.export.alpha = 1f
        }
    }

    override fun onPairingAvailable(confirm: () -> Unit, cancel: () -> Unit) {
        runOnUiThread {
            pairingDialog?.dismiss()
            pairingDialog = AlertDialog.Builder(this)
                .setTitle("Emparelhar esta scooter?")
                .setMessage("Será enviada uma nova credencial Bluetooth. Pode ser necessário voltar a emparelhar a app oficial. As credenciais locais anteriores serão preservadas.\n\nPrime o botão da scooter apenas quando a app pedir. A confirmação é automática.")
                .setPositiveButton("Emparelhar") { _, _ -> confirm() }
                .setNegativeButton("Cancelar") { _, _ -> cancel() }
                .setOnCancelListener { cancel() }.show()
        }
    }

    private fun settings() {
        AlertDialog.Builder(this).setTitle("Configurações e diagnóstico")
            .setItems(arrayOf("Ver diagnóstico desta sessão", "Importar credencial (avançado)", "Sobre esta versão")) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this).setTitle("Diagnóstico").setMessage(latestReport?.let {
                        "Resultado: ${it.outcome}\n\n${it.protocolProbe?.authenticationNote}\n\n" + it.diagnosticEvents.joinToString("\n")
                    } ?: "Ainda não existe uma captura nesta sessão.").setPositiveButton("Fechar", null).show()
                    1 -> if (!busy) credentialDialog() else Toast.makeText(this, "Aguarda o fim da ligação.", Toast.LENGTH_SHORT).show()
                    2 -> AlertDialog.Builder(this).setTitle("MyNaviScooter")
                        .setMessage("${BuildConfig.VERSION_NAME} · ${BuildConfig.REVISION.take(8)}\n\nEmparelhamento experimental. Perfis, backup restaurável, telemetria e GPS ainda não disponíveis.")
                        .setPositiveButton("Fechar", null).show()
                }
            }.show()
    }

    private fun credentialDialog() {
        val input = EditText(this).apply { hint = "segway://credentials?sn=…&pwd=…"; inputType = 129 }
        AlertDialog.Builder(this).setTitle("Importar credencial").setView(input)
            .setMessage("Apenas para credenciais que já possuas. Guardada cifrada neste telemóvel.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar") { _, _ ->
                CredentialParser.parse(input.text.toString()).onSuccess { value ->
                    runCatching { credentials.save(value) }.onSuccess {
                        Toast.makeText(this, "Credencial guardada", Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(this, "Não foi possível guardar", Toast.LENGTH_LONG).show() }
                }.onFailure { Toast.makeText(this, "Credencial inválida", Toast.LENGTH_LONG).show() }
            }.show()
    }

    private fun exportReport() {
        if (latestReport == null) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "mynavi-diagnostico.json")
        }, 42)
    }

    @Deprecated("Legacy document result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val report = latestReport ?: return
        runCatching { checkNotNull(contentResolver.openOutputStream(uri)).use { it.write(report.toJson().toByteArray()) } }
            .onSuccess { Toast.makeText(this, "Diagnóstico guardado", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Não foi possível guardar o ficheiro", Toast.LENGTH_LONG).show() }
    }

    private fun permissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun permissionsReady() = permissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    private fun requestPermissions() { ActivityCompat.requestPermissions(this, permissions(), 41) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && permissionsReady()) ble.startScan()
        else dashboard.status.text = "Permite o acesso Bluetooth para procurar a scooter."
    }

    override fun onDestroy() { pairingDialog?.dismiss(); ble.close(); super.onDestroy() }
}
