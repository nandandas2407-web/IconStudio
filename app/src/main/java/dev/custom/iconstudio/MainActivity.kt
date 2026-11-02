package dev.custom.iconstudio

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private class App(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo) {
        val id get() = "$pkg/$cls"
    }
    private class Holder(val iv: ImageView, val tv: TextView)

    private val apps = ArrayList<App>()
    private val selected = LinkedHashSet<String>()
    private val cache = LruCache<String, Bitmap>(500)
    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private var style = IconStyle.LIQUID
    private var glass = 60
    private val chips = ArrayList<TextView>()
    private lateinit var grid: GridView
    private lateinit var glassRow: View
    private lateinit var status: TextView
    private lateinit var pinBtn: TextView

    // pin queue
    private val queue = ArrayDeque<App>()
    private var gen = 0
    private val pinReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { advance() }
    }

    companion object {
        const val ACTION_PINNED = "dev.custom.iconstudio.PINNED"
        const val HINT = "Tap apps to select · long-press to pin one"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("p", MODE_PRIVATE)
        style = try { IconStyle.valueOf(sp.getString("style", "LIQUID")!!) } catch (e: Exception) { IconStyle.LIQUID }
        glass = sp.getInt("glass", 60)
        buildUi()
        val f = IntentFilter(ACTION_PINNED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pinReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(pinReceiver, f)
    }

    override fun onResume() { super.onResume(); loadApps() }

    override fun onDestroy() {
        try { unregisterReceiver(pinReceiver) } catch (_: Exception) { }
        pool.shutdown()
        super.onDestroy()
    }

    private fun key(a: App) = a.id + "|" + style.name + "|" + (if (style == IconStyle.LIQUID) glass else 0)

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101114.toInt())
            fitsSystemWindows = true
        }

        // style chips
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in IconStyle.values()) {
            val t = chip(s.label) {
                style = s
                getSharedPreferences("p", MODE_PRIVATE).edit().putString("style", s.name).apply()
                chips.forEachIndexed { i, tv -> paintChip(tv, IconStyle.values()[i] == style) }
                glassRow.visibility = if (style == IconStyle.LIQUID) View.VISIBLE else View.GONE
                (grid.adapter as BaseAdapter).notifyDataSetChanged()
            }
            paintChip(t, s == style); chips.add(t); row.addView(t)
        }
        scroll.addView(row)

        // glass thickness slider (Liquid Glass only)
        val label = TextView(this).apply { text = "Glass depth"; setTextColor(0xFFB8BCC8.toInt()); textSize = 12f }
        val seek = SeekBar(this).apply {
            max = 100; progress = glass
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    glass = sb?.progress ?: glass
                    getSharedPreferences("p", MODE_PRIVATE).edit().putInt("glass", glass).apply()
                    (grid.adapter as BaseAdapter).notifyDataSetChanged()
                }
            })
        }
        glassRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(4))
            addView(label)
            addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
            visibility = if (style == IconStyle.LIQUID) View.VISIBLE else View.GONE
        }

        // app grid
        grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(96)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(10)
            setPadding(dp(8), dp(8), dp(8), dp(16))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
        }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val v = cv ?: makeCell()
                val h = v.tag as Holder
                val a = apps[pos]
                h.tv.text = a.label
                v.background = if (a.id in selected) GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(0x33FFFFFF); setStroke(dp(2), 0xAAFFFFFF.toInt())
                } else null
                val k = key(a)
                h.iv.tag = k
                val cached = cache.get(k)
                if (cached != null) h.iv.setImageBitmap(cached) else {
                    h.iv.setImageDrawable(null)
                    val st = style; val gl = glass / 100f
                    pool.execute {
                        try {
                            val bmp = IconRenderer.render(a.ri.loadIcon(packageManager), st, gl)
                            cache.put(k, bmp)
                            ui.post { if (h.iv.tag == k) h.iv.setImageBitmap(bmp) }
                        } catch (_: Throwable) { }
                    }
                }
                return v
            }
        }
        grid.setOnItemClickListener { _, _, p, _ ->
            val id = apps[p].id
            if (!selected.add(id)) selected.remove(id)
            refreshSelection()
        }
        grid.setOnItemLongClickListener { _, _, p, _ ->
            startPin(listOf(apps[p])); true
        }

        // bottom bar
        status = TextView(this).apply {
            text = HINT; setTextColor(0xFFB8BCC8.toInt()); textSize = 12f
            setPadding(dp(16), dp(6), dp(16), 0)
        }
        val all = chip("Select all") { selected.clear(); selected.addAll(apps.map { it.id }); refreshSelection() }
        val none = chip("Clear") { selected.clear(); refreshSelection() }
        val upd = chip("Update pinned") { updatePinned() }
        pinBtn = chip("Pin (0)") {
            val list = apps.filter { it.id in selected }
            if (list.isEmpty()) toast("Select some apps first") else startPin(list)
        }
        listOf(all, none, upd).forEach { paintChip(it, false) }
        paintChip(pinBtn, true)
        val bottomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(8), dp(6), dp(8), dp(8))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(all); addView(none); addView(upd); addView(pinBtn)
            })
        }

        root.addView(scroll)
        root.addView(glassRow)
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status)
        root.addView(bottomScroll)
        setContentView(root)
    }

    private fun refreshSelection() {
        pinBtn.text = "Pin (${selected.size})"
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun makeCell(): View {
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(6))
            addView(iv, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            tag = Holder(iv, tv)
        }
    }

    private fun chip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 13f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
    }

    private fun paintChip(t: TextView, selected: Boolean) {
        t.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        t.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat(); setColor(if (selected) Color.WHITE else 0x33FFFFFF)
        }
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(packageManager).toString(), it) }
            .sortedBy { it.label.lowercase() }
        apps.clear(); apps.addAll(list)
        selected.retainAll(list.map { it.id }.toSet())
        refreshSelection()
    }

    // ---------- pinning ----------

    private fun launchIntent(a: App) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(a.pkg, a.cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun startPin(list: List<App>) {
        queue.clear(); queue.addAll(list)
        gen++
        pinNext()
    }

    private fun advance() { gen++; pinNext() }

    private fun pinNext() {
        val a = queue.removeFirstOrNull()
        if (a == null) { status.text = HINT; return }
        status.text = "Pinning ${a.label}: confirm the prompt (${queue.size} more)"
        val st = style; val gl = glass / 100f
        pool.execute {
            val bmp = try { IconRenderer.render(a.ri.loadIcon(packageManager), st, gl) } catch (t: Throwable) { null }
            ui.post {
                if (bmp == null) { advance(); return@post }
                val waiting = requestPin(a, bmp)
                val g = gen
                ui.postDelayed({ if (g == gen) advance() }, if (waiting) 12000L else 600L)
            }
        }
    }

    /** returns true if we're waiting on the launcher's confirmation callback */
    private fun requestPin(a: App, bmp: Bitmap): Boolean {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm != null && sm.isRequestPinShortcutSupported) {
            val info = ShortcutInfo.Builder(this, a.id)
                .setShortLabel(a.label).setLongLabel(a.label)
                .setIcon(Icon.createWithBitmap(bmp))
                .setIntent(launchIntent(a)).build()
            val cb = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_PINNED).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ).intentSender
            return try { sm.requestPinShortcut(info, cb) } catch (e: Exception) { toast("Pin failed: ${e.message}"); false }
        }
        // legacy fallback for launchers without pin support
        sendBroadcast(Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent(a))
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, a.label)
            .putExtra(Intent.EXTRA_SHORTCUT_ICON, bmp))
        return false
    }

    /** Re-renders every shortcut this app has already pinned, using the current style. */
    private fun updatePinned() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        val pinned = try { sm.pinnedShortcuts } catch (e: Exception) { emptyList() }
        if (pinned.isEmpty()) { toast("No pinned icons from this app yet"); return }
        val snapshot = apps.toList()
        val st = style; val gl = glass / 100f
        status.text = "Updating ${pinned.size} pinned icons…"
        pool.execute {
            val upd = pinned.mapNotNull { si ->
                val a = snapshot.firstOrNull { it.id == si.id } ?: return@mapNotNull null
                val bmp = try { IconRenderer.render(a.ri.loadIcon(packageManager), st, gl) } catch (t: Throwable) { return@mapNotNull null }
                ShortcutInfo.Builder(this, si.id).setIcon(Icon.createWithBitmap(bmp)).build()
            }
            val ok = try { sm.updateShortcuts(upd) } catch (e: Exception) { false }
            ui.post { status.text = HINT; toast(if (ok) "Updated ${upd.size} icons" else "Update failed") }
        }
    }
}
