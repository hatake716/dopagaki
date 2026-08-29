package io.github.hatake716.dopagaki

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/** どちらのサイトを担当するペインか */
enum class Pane { YOUTUBE, X }

/**
 * WebView 共通設定・URL 振り分け・ペイン内全画面（SPEC.md §6）。
 * 全画面のカスタムビューは自分の親 FrameLayout に MATCH_PARENT で重ねる。
 */
class PaneWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    interface Listener {
        /** このペイン以外で開くべき URL が踏まれた */
        fun onOpenInOther(target: Pane, url: String)

        /** メインフレームの URL が変わった（SPA の pushState 含む）。永続化に使う */
        fun onUrlChanged(pane: Pane, url: String)

        /** レンダラプロセスが死んだ。この WebView は再利用できないので作り直しが必要 */
        fun onRenderProcessGone(pane: Pane)

        /** ファイル選択（X の画像・動画添付など）。処理したら true */
        fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams,
        ): Boolean
    }

    lateinit var pane: Pane
        private set
    private lateinit var listener: Listener

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /** YouTube の全画面表示中か（バック処理で最優先に解除する。SPEC.md §6） */
    val isFullscreen: Boolean
        get() = customView != null

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(pane: Pane, listener: Listener) {
        this.pane = pane
        this.listener = listener

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 自動再生・連続再生のため（SPEC.md §6）
            mediaPlaybackRequiresUserGesture = false
            // target=_blank も同じ WebView で開かせ、振り分け処理に乗せる（SPEC.md §6）
            setSupportMultipleWindows(false)
            // 「; wv」と「Version/4.0 」を除いた Chrome 相当の UA にする。
            // X のサポート外ブラウザ判定と Google の disallowed_useragent を避ける（SPEC.md §6）
            userAgentString = WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@PaneWebView, true)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url
                val scheme = url.scheme?.lowercase()
                // intent:// など公式アプリへの誘導は無視する（SPEC.md §3）
                if (scheme != "http" && scheme != "https") return true
                val host = url.host?.lowercase() ?: return false
                val target = paneForHost(host)
                return if (target != null && target != pane) {
                    listener.onOpenInOther(target, url.toString())
                    true
                } else {
                    false
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                    listener.onUrlChanged(pane, url)
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                // false を返すとアプリごと強制終了するため、ここで引き取って作り直す
                listener.onRenderProcessGone(pane)
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                (parent as? ViewGroup)?.addView(
                    view,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this@PaneWebView.visibility = GONE
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = listener.onShowFileChooser(filePathCallback, fileChooserParams)
        }
    }

    /** 全画面カスタムビューを取り除いて元の WebView 表示に戻す */
    fun exitFullscreen() {
        val view = customView ?: return
        customView = null
        (view.parent as? ViewGroup)?.removeView(view)
        visibility = VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    companion object {
        /**
         * URL のホストからあるべきペインを返す。どちらでもなければ null。
         * t.co はサーバーリダイレクトなので、X ペイン内では自ペイン扱いのままロードさせ、
         * リダイレクト先の URL で改めて振り分けられる（SPEC.md §6）。
         */
        fun paneForHost(host: String): Pane? = when {
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> Pane.YOUTUBE
            host == "x.com" || host.endsWith(".x.com") ||
                host == "twitter.com" || host.endsWith(".twitter.com") ||
                host == "t.co" -> Pane.X
            else -> null
        }
    }
}
