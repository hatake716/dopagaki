package io.github.hatake716.dopagaki

import android.content.ActivityNotFoundException
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * レイアウト・ペイン管理・バック処理・イマーシブ表示（SPEC.md §2, §3, §6）。
 * 上ペイン = YouTube、下ペイン = X。常設 UI は境界線ハンドルのみ。
 * ペインの比率は LinearLayout の weight（上 = ratio、下 = 1 - ratio）で表現する。
 */
class MainActivity : AppCompatActivity(), PaneWebView.Listener, DividerView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var paneTopContainer: FrameLayout
    private lateinit var paneBottomContainer: FrameLayout
    private lateinit var webYoutube: PaneWebView
    private lateinit var webX: PaneWebView
    private lateinit var divider: DividerView

    private var topRatio = Prefs.DEFAULT_RATIO

    /** 「最後に触ったペイン」。初期値は下（X）。バックと長押しリロードが効く先 */
    private var lastTouched = Pane.X

    private var recreatingAfterRenderGone = false

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileChooserCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
            fileChooserCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ダーク固定。WebView 内の prefers-color-scheme もダークになる（SPEC.md §2）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)

        // debug ビルドでは WebView を Chrome DevTools (chrome://inspect / CDP) から
        // 検査できるようにする。X の DOM 調査用
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        prefs = Prefs(this)
        topRatio = prefs.topRatio.coerceIn(Prefs.MIN_RATIO, Prefs.MAX_RATIO)

        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        paneTopContainer = findViewById(R.id.paneTopContainer)
        paneBottomContainer = findViewById(R.id.paneBottomContainer)
        webYoutube = findViewById(R.id.webYoutube)
        webX = findViewById(R.id.webX)
        divider = findViewById(R.id.divider)

        setUpWindow()
        setUpPanes()
        setUpBackHandling()

        applyRatio(topRatio)
        divider.listener = this
        // 境界線ハンドルを常にペインの境界に重ねる。divider は paneTopContainer より後に
        // レイアウトされるため、初回は divider 側のリスナーで height 確定後に同期される
        paneTopContainer.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) syncDividerPosition()
        }
        divider.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) syncDividerPosition()
        }
    }

    /** イマーシブ・カットアウト・キーボードのインセット処理（SPEC.md §6） */
    private fun setUpWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(0, cutoutTop, 0, imeBottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setUpPanes() {
        webYoutube.setup(Pane.YOUTUBE, this)
        webX.setup(Pane.X, this)
        webYoutube.loadUrl(prefs.youtubeUrl)
        webX.loadUrl(prefs.xUrl)
    }

    /**
     * バックは 1) 全画面解除 2) 最後に触ったペインで戻る 3) バックグラウンドへ、の優先順
     * （SPEC.md §6）
     */
    private fun setUpBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        webYoutube.isFullscreen -> webYoutube.exitFullscreen()
                        webX.isFullscreen -> webX.exitFullscreen()
                        paneView(lastTouched).canGoBack() -> paneView(lastTouched).goBack()
                        else -> moveTaskToBack(true)
                    }
                }
            },
        )
    }

    private fun paneView(pane: Pane): PaneWebView =
        if (pane == Pane.YOUTUBE) webYoutube else webX

    private fun applyRatio(ratio: Float) {
        (paneTopContainer.layoutParams as LinearLayout.LayoutParams).weight = ratio
        (paneBottomContainer.layoutParams as LinearLayout.LayoutParams).weight = 1f - ratio
        paneTopContainer.parent.requestLayout()
    }

    private fun syncDividerPosition() {
        divider.translationY = paneTopContainer.bottom - divider.height / 2f
    }

    /** ACTION_DOWN の位置で「最後に触ったペイン」を更新。境界線の帯の中は除外（SPEC.md §6） */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val location = IntArray(2)
            divider.getLocationOnScreen(location)
            val dividerTop = location[1]
            when {
                ev.rawY < dividerTop -> lastTouched = Pane.YOUTUBE
                ev.rawY > dividerTop + divider.height -> lastTouched = Pane.X
                // 帯の中は変更しない
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---- PaneWebView.Listener ----

    override fun onOpenInOther(target: Pane, url: String) {
        paneView(target).loadUrl(url)
    }

    override fun onUrlChanged(pane: Pane, url: String) {
        when (pane) {
            Pane.YOUTUBE -> prefs.youtubeUrl = url
            Pane.X -> prefs.xUrl = url
        }
    }

    override fun onRenderProcessGone(pane: Pane) {
        // 両ペインが同じレンダラを共有していると 2 回呼ばれるため 1 回だけ処理する。
        // 死んだレンダラの WebView は再利用できないので、破棄して Activity ごと作り直す。
        // 比率と各ペインの URL は保存済みなので表示はほぼ復元される
        if (recreatingAfterRenderGone) return
        recreatingAfterRenderGone = true
        (webYoutube.parent as? ViewGroup)?.removeView(webYoutube)
        (webX.parent as? ViewGroup)?.removeView(webX)
        webYoutube.destroy()
        webX.destroy()
        recreate()
    }

    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = filePathCallback
        return try {
            fileChooserLauncher.launch(fileChooserParams.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileChooserCallback = null
            false
        }
    }

    // ---- DividerView.Listener ----

    override fun onDragTo(rawY: Float) {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val contentTop = location[1] + root.paddingTop
        val contentHeight = root.height - root.paddingTop - root.paddingBottom
        if (contentHeight <= 0) return
        topRatio = ((rawY - contentTop) / contentHeight)
            .coerceIn(Prefs.MIN_RATIO, Prefs.MAX_RATIO)
        applyRatio(topRatio)
    }

    override fun onDragEnd() {
        prefs.topRatio = topRatio
    }

    override fun onReset() {
        topRatio = Prefs.DEFAULT_RATIO
        applyRatio(topRatio)
        prefs.topRatio = topRatio
    }

    override fun onLongPressReload() {
        // 全画面のままリロードするとカスタムビューが取り残されるため先に解除する
        paneView(lastTouched).exitFullscreen()
        paneView(lastTouched).reload()
        val message = if (lastTouched == Pane.YOUTUBE) R.string.reload_youtube else R.string.reload_x
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        // ログイン状態の維持のため Cookie を書き出す（SPEC.md §6）
        CookieManager.getInstance().flush()
    }
}
