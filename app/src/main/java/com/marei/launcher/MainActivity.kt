package com.marei.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var desktopGrid: RecyclerView
    private lateinit var taskbar: View
    private lateinit var taskbarApps: RecyclerView
    private lateinit var startScrim: View
    private lateinit var startMenu: View
    private lateinit var searchField: EditText
    private lateinit var startHeader: TextView
    private lateinit var startToggle: TextView
    private lateinit var startList: RecyclerView
    private lateinit var startEmpty: TextView

    private lateinit var launcherApps: LauncherApps
    private lateinit var repo: AppRepository
    private val worker = Executors.newSingleThreadExecutor()
    private var apps: List<AppEntry> = emptyList()
    private var showAllApps = false

    private val desktopAdapter = AppAdapter(AppAdapter.Mode.TILE, ::launch, ::showAppMenu)
    private val taskbarAdapter = AppAdapter(AppAdapter.Mode.DOCK, ::launch, ::showAppMenu)
    private val startGridAdapter = AppAdapter(AppAdapter.Mode.TILE, ::launch, ::showAppMenu)
    private val startRowsAdapter = AppAdapter(AppAdapter.Mode.ROW, ::launch, ::showAppMenu)

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContentView(R.layout.activity_main)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true      // dark status icons on the light wallpaper
            isAppearanceLightNavigationBars = false // white nav icons on the blue taskbar
        }

        launcherApps = getSystemService(LauncherApps::class.java)
        repo = AppRepository(this, launcherApps)

        bindViews()
        applyInsets()
        wireControls()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeStart()
        })

        launcherApps.registerCallback(packageCallback)
        reloadApps()
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(packageCallback)
        worker.shutdown()
        super.onDestroy()
    }

    // Pressing Home while already home closes the Start menu.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeStart()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (desktopGrid.layoutManager as? GridLayoutManager)?.spanCount = columns()
        refreshStart()
    }

    // ---------- setup ----------

    private fun bindViews() {
        root = findViewById(R.id.root)
        desktopGrid = findViewById(R.id.desktopGrid)
        taskbar = findViewById(R.id.taskbar)
        taskbarApps = findViewById(R.id.taskbarApps)
        startScrim = findViewById(R.id.startScrim)
        startMenu = findViewById(R.id.startMenu)
        searchField = findViewById(R.id.searchField)
        startHeader = findViewById(R.id.startHeader)
        startToggle = findViewById(R.id.startToggle)
        startList = findViewById(R.id.startList)
        startEmpty = findViewById(R.id.startEmpty)

        desktopGrid.layoutManager = GridLayoutManager(this, columns())
        desktopGrid.adapter = desktopAdapter
        taskbarApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        taskbarApps.adapter = taskbarAdapter

        val name = getString(R.string.user_name)
        findViewById<TextView>(R.id.userName).text = name
        findViewById<TextView>(R.id.avatar).text = name.take(1).uppercase()
    }

    private fun applyInsets() {
        val taskbarHeight = resources.getDimensionPixelSize(R.dimen.taskbar_height)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            desktopGrid.updatePadding(
                left = bars.left + dp(8), right = bars.right + dp(8), top = bars.top + dp(20),
            )
            desktopGrid.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = taskbarHeight + bars.bottom
            }
            taskbar.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            startMenu.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bars.top + (resources.displayMetrics.heightPixels * 0.12f).roundToInt()
                bottomMargin = max(taskbarHeight + bars.bottom, ime.bottom) + dp(10)
            }
            insets
        }
    }

    private fun wireControls() {
        findViewById<View>(R.id.startButton).setOnClickListener {
            if (isStartOpen()) closeStart() else openStart(focusSearch = false)
        }
        findViewById<View>(R.id.searchButton).setOnClickListener { openStart(focusSearch = true) }
        startScrim.setOnClickListener { closeStart() }
        startToggle.setOnClickListener {
            showAllApps = !showAllApps
            refreshStart()
        }
        searchField.doAfterTextChanged { refreshStart() }
        searchField.setOnEditorActionListener { v, _, _ ->
            (startList.adapter as? AppAdapter)?.items?.firstOrNull()?.let { launch(v, it) }
            true
        }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            closeStart()
        }
        findViewById<View>(R.id.personalizeButton).setOnClickListener { showPersonalizeMenu(it) }
    }

    // ---------- data ----------

    private fun reloadApps() {
        val style = Prefs.iconStyle(this)
        val size = dp(56)
        worker.execute {
            val list = repo.load(style, size)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = list
                Prefs.ensureDefaults(this, list)
                render()
            }
        }
    }

    private fun pinned(key: String): List<AppEntry> {
        val index = apps.associateBy { it.key }
        return Prefs.list(this, key).mapNotNull { index[it] }
    }

    private fun render() {
        desktopAdapter.submit(pinned(Prefs.DESKTOP))
        taskbarAdapter.submit(pinned(Prefs.TASKBAR))
        refreshStart()
    }

    // ---------- Start menu ----------

    private fun refreshStart() {
        val query = searchField.text.toString().trim()
        when {
            query.isNotEmpty() -> {
                startHeader.setText(R.string.results)
                startToggle.isVisible = false
                val hits = apps.filter { it.label.contains(query, ignoreCase = true) }
                    .sortedBy { !it.label.startsWith(query, ignoreCase = true) }
                showRows(hits, R.string.no_results)
            }
            showAllApps -> {
                startHeader.setText(R.string.all_apps)
                startToggle.isVisible = true
                startToggle.setText(R.string.back)
                showRows(apps, R.string.no_results)
            }
            else -> {
                startHeader.setText(R.string.pinned)
                startToggle.isVisible = true
                startToggle.setText(R.string.all_apps)
                showGrid(pinned(Prefs.START))
            }
        }
    }

    private fun showRows(list: List<AppEntry>, emptyText: Int) {
        if (startList.adapter !== startRowsAdapter) {
            startList.layoutManager = LinearLayoutManager(this)
            startList.adapter = startRowsAdapter
        }
        startRowsAdapter.submit(list)
        startEmpty.setText(emptyText)
        startEmpty.isVisible = list.isEmpty()
    }

    private fun showGrid(list: List<AppEntry>) {
        if (startList.adapter !== startGridAdapter) {
            startList.layoutManager = GridLayoutManager(this, columns())
            startList.adapter = startGridAdapter
        } else {
            (startList.layoutManager as GridLayoutManager).spanCount = columns()
        }
        startGridAdapter.submit(list)
        startEmpty.setText(R.string.start_empty)
        startEmpty.isVisible = list.isEmpty()
    }

    private fun isStartOpen() = startScrim.isVisible

    private fun openStart(focusSearch: Boolean) {
        showAllApps = false
        searchField.setText("")
        refreshStart()
        if (!isStartOpen() || startScrim.alpha < 1f) {
            startScrim.animate().cancel()
            if (!isStartOpen()) startScrim.alpha = 0f
            startScrim.isVisible = true
            startScrim.animate().alpha(1f).setDuration(150).start()
            startMenu.translationY = dp(48).toFloat()
            startMenu.animate().translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        if (focusSearch) {
            searchField.requestFocus()
            WindowCompat.getInsetsController(window, searchField).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun closeStart() {
        if (!isStartOpen()) return
        WindowCompat.getInsetsController(window, searchField).hide(WindowInsetsCompat.Type.ime())
        searchField.clearFocus()
        startScrim.animate().alpha(0f).setDuration(120)
            .withEndAction { startScrim.isVisible = false }.start()
    }

    // ---------- actions ----------

    private fun launch(view: View, app: AppEntry) {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        val options = ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height).toBundle()
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, options)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.open_failed, app.label), Toast.LENGTH_SHORT).show()
            reloadApps()
        }
        closeStart()
    }

    private fun showAppMenu(anchor: View, app: AppEntry) {
        val inStart = Prefs.contains(this, Prefs.START, app.key)
        val onDesktop = Prefs.contains(this, Prefs.DESKTOP, app.key)
        val onTaskbar = Prefs.contains(this, Prefs.TASKBAR, app.key)

        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (inStart) R.string.unpin_start else R.string.pin_start)
            menu.add(0, 2, 1, if (onDesktop) R.string.unpin_desktop else R.string.pin_desktop)
            menu.add(0, 3, 2, if (onTaskbar) R.string.unpin_taskbar else R.string.pin_taskbar)
            menu.add(0, 4, 3, R.string.app_info)
            menu.add(0, 5, 4, R.string.uninstall)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> Prefs.toggle(this@MainActivity, Prefs.START, app.key)
                    2 -> Prefs.toggle(this@MainActivity, Prefs.DESKTOP, app.key)
                    3 -> if (!Prefs.toggle(this@MainActivity, Prefs.TASKBAR, app.key, Prefs.TASKBAR_LIMIT)) {
                        Toast.makeText(this@MainActivity, R.string.taskbar_full, Toast.LENGTH_SHORT).show()
                    }
                    4 -> launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
                    5 -> startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
                    )
                }
                render()
                true
            }
            show()
        }
    }

    private fun showPersonalizeMenu(anchor: View) {
        val style = Prefs.iconStyle(this)
        PopupMenu(this, anchor).apply {
            menu.add(1, 1, 0, R.string.icon_line).isChecked = style == IconStyler.LINE
            menu.add(1, 2, 1, R.string.icon_original).isChecked = style == IconStyler.ORIGINAL
            menu.setGroupCheckable(1, true, true)
            menu.add(0, 3, 2, R.string.home_settings)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { Prefs.setIconStyle(this@MainActivity, IconStyler.LINE); reloadApps() }
                    2 -> { Prefs.setIconStyle(this@MainActivity, IconStyler.ORIGINAL); reloadApps() }
                    3 -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                }
                true
            }
            show()
        }
    }

    // ---------- helpers ----------

    private fun columns() = max(4, resources.configuration.screenWidthDp / 90)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
