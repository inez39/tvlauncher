package com.inez.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class Item(val title: String, val image: String?, val onClick: () -> Unit)

class MainActivity : Activity() {

    // ---- cores (paleta neutra) ----
    private val BG = 0xFFF4EEE6.toInt()
    private val TEXT = 0xFF4A3F3A.toInt()
    private val MUTED = 0xFF6F625A.toInt()
    private val ROSE = 0xFFD9A7A0.toInt()
    private val ROSE_TEXT = 0xFF3E2C29.toInt()
    private val ROSE_FOCUS = 0xFFB9756B.toInt()
    private val BEIGE = 0xFFEADFD2.toInt()
    private val BEIGE_BORDER = 0xFFD8CBBB.toInt()
    private val CARD_TEXT = 0xFF3E3430.toInt()
    private val PAPER = 0xFFFBF8F3.toInt()
    private val SUN = 0xFFC98F5E.toInt()

    private val appleBgs = intArrayOf(0xFFCBD5C4.toInt(), 0xFFE8CBC5.toInt(), 0xFFE5D6BE.toInt(), 0xFFCBD7DC.toInt(), 0xFFDACFDB.toInt())
    private val ytBgs = intArrayOf(0xFFE9D3C4.toInt(), 0xFFD3DCCB.toInt(), 0xFFE4D9E2.toInt(), 0xFFD5DDE2.toInt(), 0xFFEBDFC6.toInt())
    private val peppaBgs = intArrayOf(0xFFEFCFD3.toInt(), 0xFFE9D9C4.toInt(), 0xFFD3DFD0.toInt(), 0xFFD6DDE6.toInt(), 0xFFE6D3E0.toInt())
    private val agendaBgs = intArrayOf(0xFFE8CBC5.toInt(), 0xFFCBD5C4.toInt(), 0xFFE5D6BE.toInt())

    private var scale = 1f
    private val exec = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private val bitmaps = LruCache<String, Bitmap>(40)

    private lateinit var dateText: TextView
    private lateinit var tempText: TextView
    private lateinit var forecastText: TextView
    private lateinit var weatherIcon: WeatherIconView
    private lateinit var appleRow: LinearLayout
    private lateinit var ytRow: LinearLayout
    private lateinit var peppaRow: LinearLayout
    private lateinit var agendaRow: LinearLayout
    private var firstFocus: View? = null

    private val tick = object : Runnable {
        override fun run() {
            updateDate()
            ui.postDelayed(this, 30_000)
        }
    }
    private val refresh = object : Runnable {
        override fun run() {
            loadAll()
            ui.postDelayed(this, 30 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scale = resources.displayMetrics.widthPixels / 1280f
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(buildUi())
        firstFocus?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(tick); ui.removeCallbacks(refresh)
        tick.run()
        refresh.run()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick); ui.removeCallbacks(refresh)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Num ecrã inicial o botão "voltar" não faz nada.
    }

    // ---------------------------------------------------------------- UI

    private fun px(v: Number): Int = (v.toFloat() * scale).toInt()
    private fun pxf(v: Number): Float = v.toFloat() * scale

    private fun label(text: String, size: Number, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size))
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(color: Int, radius: Number, stroke: Int = 0, strokeW: Number = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = pxf(radius)
            if (strokeW.toFloat() > 0f) setStroke(px(strokeW), stroke)
        }

    private fun focusable(v: View, radius: Number, onClick: () -> Unit) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnClickListener { onClick() }
        val ring = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = pxf(radius)
            setStroke(px(4), ROSE_FOCUS)
        }
        v.setOnFocusChangeListener { _, has ->
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
            v.translationZ = if (has) pxf(8) else 0f
            v.foreground = if (has) ring else null
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(px(40), px(28), px(40), px(24))
        }

        // ---- linha de cima: botões + relógio/tempo ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvNos = bigButton("TV NOS", IconView.KIND_TV, ROSE, ROSE_TEXT, ROSE, 26f) { openHdmi(3) }
        buttons.addView(tvNos, buttonLp(190))
        firstFocus = tvNos
        buttons.addView(bigButton("HDMI 2", IconView.KIND_HDMI, BEIGE, TEXT, BEIGE_BORDER, 22f) { openHdmi(2) }, buttonLp(150))
        buttons.addView(bigButton("HDMI 1", IconView.KIND_HDMI, BEIGE, TEXT, BEIGE_BORDER, 22f) { openHdmi(1) }, buttonLp(150))
        buttons.addView(bigButton("Jogos", IconView.KIND_GAME, BEIGE, TEXT, BEIGE_BORDER, 22f) { openGames() }, buttonLp(150))
        top.addView(buttons)

        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = WeatherIconView(this)
        right.addView(weatherIcon, LinearLayout.LayoutParams(px(70), px(70)).apply { rightMargin = px(20) })

        val clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(60))
            setTextColor(TEXT)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        dateText = label("", 19, MUTED)
        clockCol.addView(clock)
        clockCol.addView(dateText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        right.addView(clockCol)

        val divider = View(this).apply { setBackgroundColor(BEIGE_BORDER) }
        right.addView(divider, LinearLayout.LayoutParams(px(2), px(64)).apply { leftMargin = px(20); rightMargin = px(20) })

        val tempCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tempText = label("--°", 46, TEXT, true)
        tempCol.addView(tempText)
        tempCol.addView(label("Coimbra", 18, MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        right.addView(tempCol)
        top.addView(right)
        root.addView(top)

        forecastText = label("", 17, MUTED).apply { gravity = Gravity.END }
        root.addView(forecastText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })

        // ---- linhas de conteúdo ----
        appleRow = rowOfPlaceholders(appleBgs, 226, 76)
        ytRow = rowOfPlaceholders(ytBgs, 226, 76)
        peppaRow = rowOfPlaceholders(peppaBgs, 226, 76)
        agendaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        root.addView(section("Novidades Apple TV+", appleRow))
        root.addView(section("YouTube em destaque", ytRow))
        root.addView(section("Desenhos animados: Peppa Pig (novos em português)", peppaRow))
        root.addView(section("Agenda de hoje", agendaRow))

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // ---- barra de apps ----
        val apps = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(name: String, action: () -> Unit) {
            val t = label(name, 17, TEXT, true).apply {
                background = rounded(BEIGE, 14)
                setPadding(px(20), px(10), px(20), px(10))
            }
            focusable(t, 14, action)
            apps.addView(t, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = px(12) })
        }
        chip("Netflix") { openApp("com.netflix.ninja") }
        chip("YouTube") { openApp("com.google.android.youtube.tv") }
        chip("Apple TV") { openApp("com.apple.atve.androidtv.appletv") }
        chip("Prime Video") { openApp("com.amazon.amazonvideo.livingroom") }
        chip("Todas as apps") { showAllApps() }
        root.addView(apps)

        return root
    }

    private fun buttonLp(w: Int) = LinearLayout.LayoutParams(px(w), px(90)).apply { rightMargin = px(14) }

    private fun bigButton(txt: String, icon: Int, bg: Int, fg: Int, border: Int, size: Float, onClick: () -> Unit): View {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(bg, 22, border, 2)
        }
        b.addView(IconView(this, icon, fg), LinearLayout.LayoutParams(px(32), px(32)))
        b.addView(label(txt, size, fg, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(6) })
        focusable(b, 22, onClick)
        return b
    }

    private fun section(title: String, row: View): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(title, 21, TEXT, true))
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        col.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) }
        return col
    }

    private fun rowOfPlaceholders(bgs: IntArray, w: Int, h: Int): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until 5) {
            row.addView(makeCard(w, h, bgs[i], "A carregar…", null) {}, cardLp(w, h))
        }
        return row
    }

    private fun cardLp(w: Int, h: Int) = LinearLayout.LayoutParams(px(w), px(h)).apply { rightMargin = px(14) }

    private fun makeCard(w: Int, h: Int, bg: Int, title: String, imageUrl: String?, onClick: () -> Unit): View {
        val radius = pxf(18)
        val card = FrameLayout(this).apply {
            background = rounded(bg, 18)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
        if (imageUrl != null) {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(iv, FrameLayout.LayoutParams(-1, -1))
            loadImage(imageUrl, iv)
        }
        val t = label(title, 15, CARD_TEXT, true).apply {
            if (imageUrl != null) {
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setBackgroundColor(0xCCFBF8F3.toInt())
            } else {
                maxLines = 3
            }
            setPadding(px(12), px(6), px(12), px(6))
        }
        card.addView(t, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        focusable(card, 18, onClick)
        return card
    }

    private fun fillRow(row: LinearLayout, items: List<Item>, bgs: IntArray, emptyMsg: String) {
        runOnUiThread {
            row.removeAllViews()
            val list = if (items.isEmpty()) listOf(Item(emptyMsg, null) {}) else items
            list.take(5).forEachIndexed { i, it ->
                row.addView(makeCard(226, 76, bgs[i % bgs.size], it.title, it.image, it.onClick), cardLp(226, 76))
            }
        }
    }

    private fun updateDate() {
        val s = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "PT")).format(Date())
        dateText.text = s.replaceFirstChar { it.uppercase() }
    }

    // ------------------------------------------------------------ dados

    private fun loadAll() {
        loadWeather()
        loadApple()
        loadYoutube()
        loadPeppa()
        loadAgenda()
    }

    private fun httpGet(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun loadImage(url: String, iv: ImageView) {
        val cached = bitmaps.get(url)
        if (cached != null) { iv.setImageBitmap(cached); return }
        exec.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    bitmaps.put(url, bmp)
                    runOnUiThread { iv.setImageBitmap(bmp) }
                }
            } catch (e: Exception) { /* fica a cor de fundo */ }
        }
    }

    private fun loadWeather() = exec.execute {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=40.2111&longitude=-8.4292" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
            "&timezone=Europe%2FLisbon&forecast_days=4"
        val body = httpGet(url) ?: return@execute
        try {
            val j = JSONObject(body)
            val cur = j.getJSONObject("current")
            val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
            val code = cur.getInt("weather_code")
            val night = cur.getInt("is_day") == 0
            val daily = j.getJSONObject("daily")
            val days = daily.getJSONArray("time")
            val max = daily.getJSONArray("temperature_2m_max")
            val min = daily.getJSONArray("temperature_2m_min")
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt = SimpleDateFormat("EEE", Locale("pt", "PT"))
            val parts = ArrayList<String>()
            for (i in 1 until days.length()) {
                val d = parse.parse(days.getString(i))!!
                val name = dayFmt.format(d).replace(".", "").replaceFirstChar { it.uppercase() }
                parts.add("$name ${Math.round(max.getDouble(i))}° / ${Math.round(min.getDouble(i))}°")
            }
            runOnUiThread {
                tempText.text = "$temp°"
                weatherIcon.night = night
                weatherIcon.kind = WeatherIconView.kindFor(code)
                forecastText.text = parts.joinToString("      ")
            }
        } catch (e: Exception) { /* mantém o que estava */ }
    }

    private data class Tmdb(val date: String, val title: String, val image: String?)

    private fun loadApple() = exec.execute {
        val key = BuildConfig.TMDB_KEY
        if (key.isEmpty()) {
            fillRow(appleRow, emptyList(), appleBgs, "Falta a chave TMDB")
            return@execute
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val all = ArrayList<Tmdb>()
        val configs = listOf(
            arrayOf("movie", "primary_release_date", "release_date", "title"),
            arrayOf("tv", "first_air_date", "first_air_date", "name")
        )
        for (cfg in configs) {
            val url = "https://api.themoviedb.org/3/discover/${cfg[0]}?api_key=$key&language=pt-PT" +
                "&watch_region=PT&with_watch_providers=350&with_watch_monetization_types=flatrate" +
                "&sort_by=${cfg[1]}.desc&${cfg[1]}.lte=$today&page=1"
            val body = httpGet(url) ?: continue
            try {
                val arr = JSONObject(body).getJSONArray("results")
                for (i in 0 until minOf(5, arr.length())) {
                    val o = arr.getJSONObject(i)
                    val path = o.optString("backdrop_path").ifEmpty { o.optString("poster_path") }
                    all.add(
                        Tmdb(
                            o.optString(cfg[2]),
                            o.optString(cfg[3]),
                            if (path.isEmpty() || path == "null") null else "https://image.tmdb.org/t/p/w500$path"
                        )
                    )
                }
            } catch (e: Exception) { /* ignora */ }
        }
        val items = all.sortedByDescending { it.date }.take(5).map {
            Item(it.title, it.image) { openApp("com.apple.atve.androidtv.appletv") }
        }
        fillRow(appleRow, items, appleBgs, "Sem ligação ao TMDB")
    }

    private fun youtubeItems(url: String, idOf: (JSONObject) -> String): List<Item> {
        val body = httpGet(url) ?: return emptyList()
        return try {
            val arr = JSONObject(body).getJSONArray("items")
            (0 until minOf(5, arr.length())).map { i ->
                val o = arr.getJSONObject(i)
                val sn = o.getJSONObject("snippet")
                val th = sn.getJSONObject("thumbnails")
                val img = (th.optJSONObject("medium") ?: th.optJSONObject("default"))?.optString("url")
                val id = idOf(o)
                Item(sn.optString("title"), img) { openYoutube(id) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadYoutube() = exec.execute {
        val key = BuildConfig.YT_KEY
        if (key.isEmpty()) {
            fillRow(ytRow, emptyList(), ytBgs, "Falta a chave YouTube")
            return@execute
        }
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular" +
            "&regionCode=PT&maxResults=5&key=$key"
        fillRow(ytRow, youtubeItems(url) { it.getString("id") }, ytBgs, "Sem ligação ao YouTube")
    }

    private fun loadPeppa() = exec.execute {
        val key = BuildConfig.YT_KEY
        if (key.isEmpty()) {
            fillRow(peppaRow, emptyList(), peppaBgs, "Falta a chave YouTube")
            return@execute
        }
        val q = URLEncoder.encode("Peppa Pig português", "UTF-8")
        var url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&order=date" +
            "&q=$q&relevanceLanguage=pt&regionCode=PT&maxResults=5&key=$key"
        if (BuildConfig.PEPPA_CHANNEL_ID.isNotEmpty()) url += "&channelId=${BuildConfig.PEPPA_CHANNEL_ID}"
        val items = youtubeItems(url) { it.getJSONObject("id").getString("videoId") }
        fillRow(peppaRow, items, peppaBgs, "Sem ligação ao YouTube")
    }

    private fun loadAgenda() = exec.execute {
        val ics = BuildConfig.CAL_ICS_URL
        if (ics.isEmpty()) {
            showAgenda(emptyList(), "Falta ligar a agenda")
            return@execute
        }
        val body = httpGet(ics)
        if (body == null) {
            showAgenda(emptyList(), "Sem ligação à agenda")
            return@execute
        }
        showAgenda(IcsParser.eventsForToday(body).take(3), "Sem compromissos hoje")
    }

    private fun showAgenda(events: List<AgendaEvent>, emptyMsg: String) = runOnUiThread {
        agendaRow.removeAllViews()
        val list = if (events.isEmpty()) listOf(AgendaEvent(0, "Hoje", emptyMsg)) else events
        list.forEachIndexed { i, e ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(PAPER, 18, 0xFFE8DDD0.toInt(), 2)
                setPadding(px(16), 0, px(16), 0)
            }
            val pill = label(e.time, 16, CARD_TEXT, true).apply {
                background = rounded(agendaBgs[i % agendaBgs.size], 999)
                setPadding(px(12), px(6), px(12), px(6))
            }
            box.addView(pill)
            val title = label(e.title, 18, TEXT, true).apply {
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            box.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = px(14) })
            agendaRow.addView(box, LinearLayout.LayoutParams(px(372), px(52)).apply { rightMargin = px(14) })
        }
    }

    // ----------------------------------------------------------- ações

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun openHdmi(port: Int) {
        try {
            val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            val inputs = tim.tvInputList
            fun names(i: TvInputInfo): String =
                (i.loadLabel(this)?.toString() ?: "") + " " + (i.loadCustomLabel(this)?.toString() ?: "")
            val rx = Regex("hdmi\\s*$port\\b", RegexOption.IGNORE_CASE)
            val match = inputs.firstOrNull { it.type == TvInputInfo.TYPE_HDMI && rx.containsMatchIn(names(it)) }
            if (match != null) {
                startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(match.id)))
                return
            }
            toast("Entrada HDMI $port não encontrada. Entradas: " + inputs.joinToString { names(it).trim() })
        } catch (e: Exception) {
            toast("Não foi possível abrir o HDMI $port")
        }
    }

    private fun openApp(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            startActivity(i)
        } else {
            toast("App não instalada")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (e: Exception) { /* sem Play Store */ }
        }
    }

    private fun openGames() {
        val pkgs = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
        for (p in pkgs) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p)
            if (i != null) { startActivity(i); return }
        }
        toast("Instala o RetroArch na Play Store")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.retroarch")))
        } catch (e: Exception) { /* sem Play Store */ }
    }

    private fun openYoutube(id: String) {
        val uri = Uri.parse("https://www.youtube.com/watch?v=$id")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv"))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                toast("YouTube não encontrado")
            }
        }
    }

    private fun showAllApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val q2 = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = (pm.queryIntentActivities(q, 0) + pm.queryIntentActivities(q2, 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val labels = list.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Todas as apps")
            .setItems(labels) { _, which ->
                val ai = list[which].activityInfo
                val i = pm.getLeanbackLaunchIntentForPackage(ai.packageName) ?: pm.getLaunchIntentForPackage(ai.packageName)
                if (i != null) startActivity(i)
            }
            .show()
    }
}
