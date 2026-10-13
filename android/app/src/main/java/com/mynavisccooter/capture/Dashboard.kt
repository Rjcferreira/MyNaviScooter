package com.mynavisccooter.capture

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/** Native dashboard: unavailable telemetry is never represented as measured data. */
class Dashboard(private val context: Context) {
    private val ink = Color.rgb(4, 16, 27)
    private val panel = Color.rgb(12, 29, 43)
    private val muted = Color.rgb(153, 177, 197)
    private val orange = Color.rgb(255, 143, 39)
    private val white = Color.rgb(240, 247, 253)
    lateinit var status: TextView
    lateinit var devices: LinearLayout
    lateinit var export: Button
    lateinit var recover: Button
    lateinit var scan: Button
    lateinit var badge: TextView
    lateinit var applyProfile: Button
    lateinit var restoreProfile: Button
    lateinit var deepScan: Button
    lateinit var live: Button
    private lateinit var capabilityNotice: TextView
    private lateinit var speedDial: SpeedDial
    private lateinit var batteryValue: TextView
    private lateinit var rangeValue: TextView
    private lateinit var telemetryDetail: TextView
    private lateinit var steps: TextView
    private lateinit var selected: TextView
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun background(color: Int, border: Int = Color.rgb(29, 53, 71)) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(22).toFloat(); setStroke(dp(1), border)
    }
    private fun label(value: String, size: Float = 14f, color: Int = white) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5))
    }
    private fun card() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = background(panel)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }
    fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) ink else white)
        background = background(if (primary) orange else panel, if (primary) orange else Color.rgb(43, 69, 88))
        minHeight = dp(52); setPadding(dp(16), dp(10), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(8) }
        setOnClickListener { action() }
    }
    fun create(connectionOnly: Boolean = false, onScan: () -> Unit, onRecover: () -> Unit, onDeepScan: () -> Unit, onApply: () -> Unit, onRestore: () -> Unit, onExport: () -> Unit, onSettings: () -> Unit, onLive: () -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(24)); setBackgroundColor(ink)
        }
        val brand = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(label("MyNavi", 28f).apply { setTypeface(null, Typeface.BOLD) })
        brand.addView(label("Scooter", 28f, orange).apply { setTypeface(null, Typeface.BOLD) })
        root.addView(brand)
        val menu = label("⋮", 30f).apply {
            gravity = Gravity.CENTER
            contentDescription = "Atalhos das funções"
            isClickable = true
        }
        brand.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        brand.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(label("MAIS LIBERDADE EM CADA TRAJETO", 10f, muted).apply { letterSpacing = .18f })
        badge = label("BLUETOOTH  ·  Desligado", 12f, muted)
        root.addView(badge)
        root.addView(label(if (connectionOnly) "Liga a tua scooter" else "Boa viagem!", 30f).apply { setTypeface(null, Typeface.BOLD); setPadding(0, dp(22), 0, 0) })
        selected = label(if (connectionOnly) "Vamos acompanhar cada passo da ligação." else "Scooter ligada e pronta.", 15f, muted)
        root.addView(selected)
        if (!connectionOnly) {
            speedDial = SpeedDial(context)
            root.addView(speedDial, LinearLayout.LayoutParams(-1, dp(265)))
            val stats = card()
            val statsRow = LinearLayout(context)
            listOf("— %" to "BATERIA", "— km" to "AUTONOMIA").forEachIndexed { index, (value, caption) ->
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val valueView = label(value, 25f).apply { setTypeface(null, Typeface.BOLD) }
                if (index == 0) batteryValue = valueView else rangeValue = valueView
                column.addView(valueView); column.addView(label(caption, 10f, muted))
                statsRow.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            }
            stats.addView(statsRow)
            telemetryDetail = label("Telemetria ainda indisponível", 12f, muted)
            stats.addView(telemetryDetail); root.addView(stats)
        }
        val connection = card()
        connection.addView(label("Ligar por Bluetooth", 20f).apply { setTypeface(null, Typeface.BOLD) })
        steps = label("1  Ligar    ·    2  Emparelhar    ·    3  Autenticar", 12f, muted)
        connection.addView(steps)
        status = label("Liga a scooter e mantém o telemóvel por perto.", 14f, muted)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        connection.addView(status)
        scan = button("Procurar scooter", true, onScan)
        connection.addView(scan)
        connection.addView(label("SCOOTERS ENCONTRADAS", 10f, muted).apply {
            letterSpacing = .13f
            setPadding(0, dp(12), 0, dp(6))
        })
        devices = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        connection.addView(devices)
        recover = button("Emparelhar novamente", false, onRecover).apply { visibility = View.GONE }
        connection.addView(recover)
        connection.visibility = if (connectionOnly) View.VISIBLE else View.GONE
        root.addView(connection)
        if (connectionOnly) {
            menu.setOnClickListener { onSettings() }
            root.addView(label("A app só abrirá as funções depois de confirmar a autenticação e proteger o estado inicial.", 12f, muted))
            return ScrollView(context).apply { isFillViewport = true; setBackgroundColor(ink); addView(root) }
        }
        val diagnostics = card()
        diagnostics.addView(label("DIAGNÓSTICO AVANÇADO", 11f, muted).apply { letterSpacing = .13f })
        diagnostics.addView(label("Scanner profundo ZT3", 20f).apply { setTypeface(null, Typeface.BOLD) })
        diagnostics.addView(label("Inventário só de leitura dos registos documentados da VCU, MCU, BLE e BMS. Não é um dump de firmware.", 13f, muted))
        deepScan = button("Ler informação completa conhecida", false, onDeepScan).apply { visibility = View.GONE }
        diagnostics.addView(deepScan)
        root.addView(diagnostics)
        val profile = card()
        profile.addView(label("PERFIS DE CONDUÇÃO", 11f, muted).apply { letterSpacing = .13f })
        profile.addView(label("Original", 22f).apply { setTypeface(null, Typeface.BOLD) })
        profile.addView(label("Eco  ·  Drive  ·  Sport", 16f, orange))
        profile.addView(label("O estado inicial é guardado antes de qualquer alteração feita pela app.", 13f, muted))
        profile.addView(label("Teste limitado: apenas registos de velocidade 0x47/0x48", 15f, muted))
        capabilityNotice = label("A verificar compatibilidade da VCU…", 13f, orange)
        profile.addView(capabilityNotice)
        applyProfile = button("Aplicar Drive 25 / Sport 30", true, onApply).apply { visibility = View.GONE }
        restoreProfile = button("Voltar ao estado inicial", false, onRestore).apply { visibility = View.GONE }
        profile.addView(applyProfile); profile.addView(restoreProfile)
        root.addView(profile)
        export = button("Exportar diagnóstico", false, onExport).apply { isEnabled = false; alpha = .45f }
        root.addView(export)
        root.addView(button("Configurações e diagnóstico", false, onSettings))
        root.addView(label("MyNaviScooter  /  ${BuildConfig.VERSION_NAME}\nNavegação GPS em desenvolvimento", 11f, muted))
        val names = listOf("Telemetria", "GPS", "Diagnóstico", "Perfis", "Relatórios")
        val telemetry = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        live = button("Iniciar telemetria contínua", true, onLive)
        telemetry.addView(live)
        listOf<View>(speedDial, statsView()).forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            telemetry.addView(view)
        }
        val gps = card().apply {
            addView(label("Navegação GPS", 24f, orange))
            addView(label("Mapa, trajetos e alertas da comunidade em desenvolvimento.", 15f, muted))
        }
        val reports = card().apply {
            (export.parent as? ViewGroup)?.removeView(export)
            addView(label("Relatórios", 24f))
            addView(export)
            addView(button("Configurações e diagnóstico", false, onSettings))
        }
        val contents = listOf<View>(telemetry, gps, diagnostics, profile, reports)
        contents.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        // Keep the shared header and connection status above all function pages.
        while (root.childCount > 6) root.removeViewAt(root.childCount - 1)
        val title = label(names.first(), 22f, orange)
        root.addView(title)
        root.addView(label("Desliza para mudar de função", 12f, muted))
        val pager = ViewPager2(context)
        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = contents.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val frame = holder.itemView as FrameLayout
                frame.removeAllViews()
                val content = contents[position]
                (content.parent as? ViewGroup)?.removeView(content)
                frame.addView(ScrollView(context).apply { addView(content) })
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { title.text = "${names[position]} · ${position + 1}/${names.size}" }
        })
        root.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        menu.setOnClickListener {
            PopupMenu(context, menu).apply {
                names.forEachIndexed { index, name -> this.menu.add(0, index, index, name) }
                this.menu.add(0, 100, 100, "Configurações")
                setOnMenuItemClickListener { item ->
                    if (item.itemId == 100) onSettings() else pager.setCurrentItem(item.itemId, true)
                    true
                }
                show()
            }
        }
        return root
    }
    private fun statsView(): View = batteryValue.parent.parent as View
    fun select(name: String) { selected.text = name }
    fun showMutationCapability(capability: MutationCapability, controlsVisible: Boolean) {
        capabilityNotice.text = "${capability.title}\n${capability.detail}"
        applyProfile.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        restoreProfile.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        applyProfile.isEnabled = capability.allowed
        restoreProfile.isEnabled = capability.allowed
        applyProfile.alpha = if (capability.allowed) 1f else .45f
        restoreProfile.alpha = if (capability.allowed) 1f else .45f
    }
    fun showTelemetry(value: TelemetrySnapshot) {
        speedDial.speed = value.speedKmh
        batteryValue.text = value.batteryPercent?.let { "$it %" } ?: "— %"
        rangeValue.text = value.rangeKm?.let { "%.1f km".format(it) } ?: "— km"
        telemetryDetail.text = buildList {
            value.voltageV?.let { add("%.2f V".format(it)) }
            value.currentA?.let { add("%+.2f A".format(it)) }
            value.powerW?.let { add("%+.0f W".format(it)) }
            value.batteryTempC?.let { add("Bateria %.0f °C".format(it)) }
            value.motorTempC?.let { add("Motor %.1f °C".format(it)) }
            if (value.cellVoltagesMv.isNotEmpty()) add("${value.cellVoltagesMv.size} células · Δ ${value.cellVoltagesMv.max() - value.cellVoltagesMv.min()} mV")
        }.joinToString("  ·  ").ifBlank { "Leitura parcial; consulta o relatório exportado." }
    }
    fun scooterResult(item: ScannedScooter, action: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = background(Color.rgb(9, 24, 37), Color.rgb(35, 61, 80))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = "${item.model.name}, ${item.name}, sinal ${signalBars(item.rssi)} de 5"
            layoutParams = LinearLayout.LayoutParams(-1, dp(92)).apply {
                topMargin = dp(5); bottomMargin = dp(5)
            }
            setOnClickListener { action() }
        }
        row.addView(ImageView(context).apply {
            setImageResource(modelImage(item))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(76), dp(68)).apply { rightMargin = dp(12) })
        val identity = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(label(item.model.name, 15f).apply {
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        identity.addView(label(item.name, 12f, muted).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        identity.addView(label("${item.rssi} dBm", 11f, muted))
        row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(SignalStrengthView(context).apply { rssi = item.rssi }, LinearLayout.LayoutParams(dp(62), dp(46)).apply {
            leftMargin = dp(8)
        })
        return row
    }
    private fun signalBars(rssi: Int) = when {
        rssi >= -55 -> 5
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }
    private fun modelImage(item: ScannedScooter): Int {
        val identity = "${item.name} ${item.model.name}".lowercase()
        return when {
            identity.contains("zt3") -> R.drawable.scooter_zt3
            identity.contains("gt3") -> R.drawable.scooter_gt3
            identity.contains("f3") -> R.drawable.scooter_f3
            identity.contains("g3") -> R.drawable.scooter_g3
            else -> R.drawable.scooter_unknown
        }
    }
    fun stage(stage: String) {
        val state = when (stage) {
            "connect", "services", "mtu", "cccd", "precomm" -> "1  A ligar  →  2  Emparelhar  →  3  Autenticar"
            "pairing_consent", "pairing", "button" -> "1  Ligado  →  2  A emparelhar  →  3  Autenticar"
            "auth" -> "1  Ligado  →  2  Credencial local  →  3  A autenticar"
            "backup" -> "1  Ligado  →  2  Autenticado  →  3  A guardar estado inicial"
            "deep_scan" -> "1  Backup protegido  →  2  Autenticado  →  3  Scanner profundo"
            "write", "verify_write" -> "1  Ligado  →  2  Estado protegido  →  3  A aplicar e confirmar"
            else -> "Sessão terminada · consulta o resultado abaixo"
        }
        steps.text = state
        badge.text = if (stage == "finished") "BLUETOOTH  ·  Desligado" else "BLUETOOTH  ·  Ligação em curso"
        scan.isEnabled = stage == "finished"
        scan.alpha = if (scan.isEnabled) 1f else .45f
    }
}

class SignalStrengthView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var rssi: Int = -100
        set(value) {
            field = value
            contentDescription = "Sinal ${bars(value)} de 5"
            invalidate()
        }
    private fun bars(value: Int) = when {
        value >= -55 -> 5
        value >= -65 -> 4
        value >= -75 -> 3
        value >= -85 -> 2
        else -> 1
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val active = bars(rssi)
        val activeColor = when (active) {
            1 -> Color.rgb(239, 76, 73)
            2, 3 -> Color.rgb(244, 190, 52)
            else -> Color.rgb(47, 218, 123)
        }
        val gap = width * .055f
        val barWidth = (width - gap * 6) / 5f
        val bottom = height * .82f
        for (index in 0 until 5) {
            val barHeight = height * (.20f + index * .13f)
            val left = gap + index * (barWidth + gap)
            paint.color = if (index < active) activeColor else Color.rgb(39, 59, 74)
            canvas.drawRoundRect(left, bottom - barHeight, left + barWidth, bottom, barWidth * .45f, barWidth * .45f, paint)
        }
    }
}

class SpeedDial(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var speed: Float? = null
        set(value) { field = value; contentDescription = value?.let { "Velocidade %.1f quilómetros por hora".format(it) } ?: "Velocidade indisponível"; invalidate() }
    init { contentDescription = "Velocidade indisponível. Sem telemetria da scooter."; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width.toFloat(), height * 1.14f)
        val cx = width / 2f; val cy = height * .53f; val radius = size * .43f
        val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(69, 41, 23); paint.strokeWidth = size * .055f
        canvas.drawArc(arc, 145f, 250f, false, paint)
        paint.color = Color.rgb(255, 148, 40); paint.strokeWidth = size * .024f
        canvas.drawArc(arc, 145f, 250f, false, paint)
        paint.strokeWidth = size * .006f; paint.color = Color.rgb(68, 84, 99)
        for (i in 0..40) {
            val angle = Math.toRadians((145 + i * 6.25).toDouble())
            val outer = radius * .89f; val inner = radius * if (i % 5 == 0) .76f else .82f
            canvas.drawLine(cx + outer * kotlin.math.cos(angle).toFloat(), cy + outer * kotlin.math.sin(angle).toFloat(),
                cx + inner * kotlin.math.cos(angle).toFloat(), cy + inner * kotlin.math.sin(angle).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(151, 175, 196); paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = size * .038f; canvas.drawText("VELOCIDADE", cx, cy - size * .13f, paint)
        paint.color = Color.WHITE; paint.textSize = size * .24f; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText(speed?.let { "%.1f".format(it) } ?: "—", cx, cy + size * .08f, paint)
        paint.color = Color.rgb(151, 175, 196); paint.textSize = size * .055f
        canvas.drawText("km/h", cx, cy + size * .18f, paint)
        paint.textSize = size * .033f; canvas.drawText(if (speed == null) "À ESPERA DE DADOS" else "LEITURA DA SCOOTER", cx, cy + size * .34f, paint)
    }
}
