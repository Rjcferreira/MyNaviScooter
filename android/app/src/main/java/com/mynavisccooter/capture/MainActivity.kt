package com.mynavisccooter.capture

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), BleCaptureManager.Listener {
    private lateinit var status: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var exportButton: Button
    private var latestReport: CaptureReport? = null
    private lateinit var ble: BleCaptureManager
    private lateinit var credentials: EncryptedCredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentials = EncryptedCredentialStore(this)
        ble = BleCaptureManager(this, this)
        buildUi()
        requestBluetoothPermissionsIfNeeded()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 24)
            setBackgroundColor(0xFF07131E.toInt())
        }
        fun text(value: String, size: Float, color: Int = 0xFFEAF4FF.toInt()): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
        }
        root.addView(text("MyNaviScooter", 28f, 0xFFFF8128.toInt()))
        root.addView(text("${BuildConfig.VERSION_NAME} · ${BuildConfig.REVISION.take(8)}", 12f))
        root.addView(text("Captura segura ZT3 / F3 / GT3", 16f, 0xFFA8BCD0.toInt()))
        root.addView(text("Primeira fase: apenas leitura. Nenhuma configuração, firmware ou limite de velocidade será alterado.", 14f, 0xFFB8C9D8.toInt()))

        val scan = Button(this).apply { text = "Procurar scooters"; setOnClickListener {
            latestReport = null
            exportButton.isEnabled = false
            ble.startScan()
        } }
        root.addView(scan, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })

        val credentialButton = Button(this).apply {
            text = if (credentials.hasCredentials()) "Credencial guardada · importar outra" else "Importar credencial de sessão"
            setOnClickListener { showCredentialDialog() }
        }
        root.addView(credentialButton)

        status = text("Pronto. Liga a ZT3 e toca em Procurar scooters.", 14f, 0xFF6DE7A0.toInt())
        root.addView(status)

        root.addView(text("Dispositivos encontrados", 18f))
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(deviceList) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        exportButton = Button(this).apply {
            text = "Exportar captura para análise"
            isEnabled = false
            setOnClickListener { exportReport() }
        }
        root.addView(exportButton)
        root.addView(text("Exporta um diagnóstico, não um backup restaurável. Envia apenas pedidos BLE de diagnóstico.", 12f, 0xFF7893AA.toInt()))
        setContentView(root)
    }

    override fun onDevicesChanged(devices: List<ScannedScooter>) {
        runOnUiThread {
            deviceList.removeAllViews()
            if (devices.isEmpty()) {
                val empty = TextView(this).apply { text = "Ainda não encontrei uma scooter compatível."; setTextColor(0xFFA8BCD0.toInt()); setPadding(0, 12, 0, 12) }
                deviceList.addView(empty)
            }
            devices.forEach { item ->
                val button = Button(this).apply {
                    text = "${item.name}  •  RSSI ${item.rssi}\n${item.model.name}"
                    setOnClickListener {
                        latestReport = null
                        exportButton.isEnabled = false
                        ble.connect(item)
                    }
                }
                deviceList.addView(button)
            }
        }
    }

    override fun onStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    override fun onCaptureReady(report: CaptureReport) {
        latestReport = report
        runOnUiThread {
            status.text = "Resultado: ${report.outcome}. Exporta o diagnóstico com o registo de todas as etapas."
            exportButton.isEnabled = true
            Toast.makeText(this, "Diagnóstico pronto", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportReport() {
        val report = latestReport ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "mynavi-${report.model.name.replace(" ", "-").lowercase()}-capture.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun showCredentialDialog() {
        val input = EditText(this).apply {
            hint = "segway://credentials?sn=...&pwd=..."
            setSingleLine(false)
            minLines = 3
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Credencial da tua scooter")
            .setMessage("Cola uma URI de credenciais obtida legitimamente para a tua scooter. A app valida e guarda-a cifrada apenas neste telemóvel. Nunca a envies por chat ou para o GitHub. Só será usada para AUTH de leitura.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val result = CredentialParser.parse(input.text.toString())
                result.onSuccess {
                    credentials.save(it)
                    status.text = "Credencial guardada localmente para ${it.serialNumber}. Autenticação BLE ainda não foi iniciada."
                    Toast.makeText(this, "Credencial guardada de forma cifrada", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Credencial inválida: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    @Deprecated("Activity result API kept minimal for the first capture build")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            val report = latestReport ?: return
            contentResolver.openOutputStream(data.data!!)?.use { it.write(report.toJson().toByteArray()) }
            Toast.makeText(this, "Captura exportada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST)
    }

    override fun onDestroy() {
        ble.close()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSIONS_REQUEST = 41
        private const val EXPORT_REQUEST = 42
    }
}
