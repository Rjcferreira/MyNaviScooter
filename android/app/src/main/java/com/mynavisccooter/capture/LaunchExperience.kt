package com.mynavisccooter.capture

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Versioned launch and legal experience. It never touches BLE state. */
class LaunchExperience(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("mynavi_launch", Context.MODE_PRIVATE)
    private val ink = Color.rgb(4, 16, 27)
    private val panel = Color.rgb(12, 29, 43)
    private val white = Color.rgb(240, 247, 253)
    private val muted = Color.rgb(153, 177, 197)
    private val orange = Color.rgb(255, 143, 39)
    private val green = Color.rgb(69, 238, 140)
    private val legalVersion = 1

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = white) = TextView(context).apply {
        this.text = value; textSize = size; setTextColor(color)
    }
    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT, radius: Int = 24) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }
    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(if (primary) ink else white)
        background = rounded(if (primary) orange else panel, if (primary) orange else Color.rgb(43, 69, 88))
        minHeight = dp(56); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    fun show(host: FrameLayout, onReady: () -> Unit) {
        val splash = FrameLayout(context).apply { setBackgroundColor(ink) }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(44), dp(24), dp(32))
        }
        val brand = LinearLayout(context).apply { gravity = Gravity.CENTER }
        brand.addView(text("MyNavi", 30f).apply { setTypeface(null, Typeface.BOLD) })
        brand.addView(text("Scooter", 30f, orange).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(brand)
        content.addView(text("A TUA SCOOTER. O TEU CAMINHO.", 10f, muted).apply {
            letterSpacing = .2f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })
        val scooter = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; alpha = 0f }
        content.addView(scooter, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(20) })
        val model = text("F3", 25f).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) }
        content.addView(model)
        content.addView(text("FAMÍLIA X3", 11f, muted).apply { letterSpacing = .22f; gravity = Gravity.CENTER })
        val loading = text("A preparar uma ligação segura…", 13f, muted).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(8))
        }
        content.addView(loading)
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(orange)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(26, 47, 64))
        }
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply { leftMargin = dp(22); rightMargin = dp(22) })
        splash.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.removeAllViews(); host.addView(splash)

        val sequence = listOf(
            R.drawable.scooter_f3 to "F3",
            R.drawable.scooter_g3 to "MAX G3",
            R.drawable.scooter_zt3 to "ZT3 PRO",
            R.drawable.scooter_gt3 to "GT3"
        )
        val introDuration = 20_000L
        val frames = List(2) { sequence }.flatten()
        frames.forEachIndexed { index, (image, name) ->
            handler.postDelayed({
                scooter.setImageResource(image); model.text = name
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(scooter, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(scooter, View.TRANSLATION_X, dp(26).toFloat(), 0f)
                    ); duration = 260; start()
                }
            }, index * (introDuration / frames.size))
        }
        for (step in 1..100) handler.postDelayed({
            progress.progress = step
            loading.text = when {
                step < 30 -> "A preparar a experiência…"
                step < 65 -> "A carregar perfis da família X3…"
                step < 90 -> "A preparar Bluetooth e segurança…"
                else -> "Tudo pronto."
            }
        }, introDuration * step / 100)
        handler.postDelayed({
            if (prefs.getInt("legal_version", 0) >= legalVersion) onReady()
            else showTerms(host, onReady)
        }, introDuration + 250L)
    }

    private fun showTerms(host: FrameLayout, onReady: () -> Unit) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(ink)
        }
        root.addView(text("Antes de começar", 30f).apply { setTypeface(null, Typeface.BOLD) })
        root.addView(text("TERMOS DE UTILIZAÇÃO  ·  1 DE 2", 10f, orange).apply { letterSpacing = .16f; setPadding(0, dp(8), 0, dp(22)) })
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel, Color.rgb(29, 53, 71))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        card.addView(text("Utilização responsável", 21f).apply { setTypeface(null, Typeface.BOLD) })
        card.addView(text("A MyNaviScooter permite consultar dados técnicos e, apenas quando explicitamente suportado, solicitar alterações à scooter.", 15f, muted).apply { setPadding(0, dp(12), 0, dp(12)) })
        card.addView(text("• Confirma sempre o modelo e o estado da scooter.\n\n• Não utilizes o telemóvel enquanto conduzes.\n\n• Faz testes apenas num local privado, seguro e sem trânsito.\n\n• Mantém um estado inicial verificável antes de qualquer alteração.", 15f))
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        root.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(button("Continuar", true) { showLiability(host, onReady) })
        host.removeAllViews(); host.addView(root)
    }

    private fun showLiability(host: FrameLayout, onReady: () -> Unit) {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(28)); setBackgroundColor(ink)
        }
        body.addView(text("Segurança e responsabilidade", 28f).apply { setTypeface(null, Typeface.BOLD) })
        body.addView(text("AVISO IMPORTANTE  ·  2 DE 2", 10f, orange).apply { letterSpacing = .16f; setPadding(0, dp(8), 0, dp(20)) })
        val warning = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel, orange)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        warning.addView(text("Alterações podem afetar a homologação", 20f, orange).apply { setTypeface(null, Typeface.BOLD) })
        warning.addView(text("Alterar velocidade, região, firmware ou outros parâmetros pode anular a garantia e tornar a scooter não autorizada para circulação em via pública. As regras variam por país.", 15f).apply { setPadding(0, dp(12), 0, dp(12)) })
        warning.addView(text("A aplicação não substitui as instruções do fabricante, inspeção técnica nem aconselhamento jurídico. O utilizador é responsável pela legalidade, segurança e consequências das alterações que confirmar.", 15f, muted))
        body.addView(warning)
        val check = CheckBox(context).apply {
            text = "Li, compreendi e aceito continuar."; setTextColor(white); textSize = 15f
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(0, dp(18), 0, dp(6))
        }
        body.addView(check)
        val accept = button("Aceitar e entrar", true) {
            prefs.edit().putInt("legal_version", legalVersion).apply(); onReady()
        }.apply { isEnabled = false; alpha = .45f }
        check.setOnCheckedChangeListener { _, checked -> accept.isEnabled = checked; accept.alpha = if (checked) 1f else .45f }
        body.addView(accept)
        body.addView(button("Voltar", false) { showTerms(host, onReady) })
        host.removeAllViews(); host.addView(ScrollView(context).apply { isFillViewport = true; addView(body) })
    }
}
