package io.github.hatake716.dopagaki

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
                // SPA 遷移でも注入を打ち直す（スクリプト側の document 同一性ガードで冪等）
                injectSiteUi(view)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // 全画面（カスタムビュー）表示中にリロード・遷移すると onHideCustomView が
                // 呼ばれずビューが取り残されて真っ暗になるため、読み込み開始時に必ず解除する
                exitFullscreen()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // m.youtube.com は初回ロード後に document.open() で文書を書き換えることがあり、
                // その時点で注入済みのリスナー・要素・style は全て消える（window のフラグだけ
                // 残る）。document 同一性ガードで冪等にし、遅延再注入で書き換え後も復元する
                injectSiteUi(view)
                view.postDelayed({ injectSiteUi(view) }, 1200)
                view.postDelayed({ injectSiteUi(view) }, 3500)
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

    /** サイト UI 調整の JS を注入する。スクリプト側の document 同一性ガードで冪等 */
    private fun injectSiteUi(view: WebView) {
        when (pane) {
            // 自動全画面 + ピボットバー縦メニュー + 引っ張って更新（SPEC.md §10.8, §10.11, §10.14）
            Pane.YOUTUBE -> view.evaluateJavascript(YOUTUBE_UI_JS, null)
            // バー非表示 + メインメニュー縦メニュー + ピル非表示（SPEC.md §10.9-§10.12, §10.15）
            Pane.X -> view.evaluateJavascript(X_BARS_JS, null)
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
         * YouTube ペイン用:
         * 1) 動画の play イベントでプレーヤー要素に requestFullscreen を呼ぶ。
         *    タップ起点の再生なら transient activation 内なので通り、サイトの全画面
         *    ボタンと同じ onShowCustomView 経路（＝ペイン内全画面）に乗る。
         * 2) ピボットバー（ホーム/ショート等）を画面左端の縦メニューに変え、既定で隠す。
         *    ペイン下端からの上スワイプで 4 秒表示（実機 DOM で検証済み）。
         * 3) ホーム / フィードでは X 同様に「下に引っ張って更新」できるようにする。
         *    ページ先頭（スクロール済みの祖先がない）で 110px 以上ほぼ垂直に引くと
         *    リロード。/watch やショートでは無効。インジケーターは引っ張り量に追従する。
         */
        private val YOUTUBE_UI_JS = """
            (function() {
              // document.open() による書き換え後は document が別物になるので、
              // window ではなく document の同一性で再実行を判定する
              if (window.__dopagakiYtDoc === document) return;
              window.__dopagakiYtDoc = document;
              var css =
                'ytm-pivot-bar-renderer{position:fixed !important;left:0 !important;right:auto !important;top:50% !important;bottom:auto !important;width:auto !important;height:auto !important;flex-direction:column !important;transform:translate(-110%,-50%) !important;transition:transform .25s ease !important;z-index:2147483000 !important;border-radius:0 14px 14px 0 !important;overflow:hidden !important;}' +
                'html.dopagaki-menu ytm-pivot-bar-renderer{transform:translate(0,-50%) !important;}' +
                'ytm-pivot-bar-item-renderer{flex:0 0 auto !important;width:64px !important;height:56px !important;}' +
                '#dopagaki-ptr{position:fixed;top:6px;left:50%;margin-left:-19px;width:38px;height:38px;border-radius:50%;background:#17171d;border:1px solid #2c2c34;z-index:2147483001;display:flex;align-items:center;justify-content:center;transform:translateY(-52px);pointer-events:none;}' +
                '#dopagaki-ptr svg{width:18px;height:18px;}' +
                '#dopagaki-ptr.spin svg{animation:dopagaki-rot .7s linear infinite;}' +
                '@keyframes dopagaki-rot{to{transform:rotate(360deg)}}';
              var style = document.createElement('style');
              style.textContent = css;
              document.documentElement.appendChild(style);
              var ptr = document.createElement('div');
              ptr.id = 'dopagaki-ptr';
              ptr.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="#FF0033" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12,4 v13 M6,11 l6,6 6,-6"/></svg>';
              document.documentElement.appendChild(ptr);
              var tryFs = function() {
                if (document.fullscreenElement) return;
                var p = document.querySelector('.html5-video-player')
                     || document.getElementById('movie_player')
                     || document.querySelector('video');
                if (p && p.requestFullscreen) {
                  p.requestFullscreen().catch(function() {});
                }
              };
              document.addEventListener('play', function(ev) {
                // 視聴ページのみ。ホームのミュート自動プレビューまで全画面化しない
                if (location.pathname !== '/watch') return;
                if (ev.target && ev.target.tagName === 'VIDEO') {
                  setTimeout(tryFs, 0);
                }
              }, true);
              var menuTimer = null;
              var reveal = function() {
                document.documentElement.classList.add('dopagaki-menu');
                clearTimeout(menuTimer);
                menuTimer = setTimeout(function() {
                  document.documentElement.classList.remove('dopagaki-menu');
                }, 4000);
              };
              var startX = null, edge = false;
              document.addEventListener('touchstart', function(ev) {
                var t = ev.touches[0];
                startX = t.clientX;
                edge = t.clientX < 40;
              }, { capture: true, passive: true });
              document.addEventListener('touchmove', function(ev) {
                if (!edge) return;
                if (ev.touches[0].clientX - startX > 24) { reveal(); edge = false; }
              }, { capture: true, passive: true });
              // ---- 下に引っ張って更新（ホーム / フィードのみ） ----
              var PULL = 110;
              var pullEligible = function(target) {
                var p = location.pathname;
                if (p !== '/' && p.indexOf('/feed') !== 0) return false;
                var el = target;
                while (el && el !== document.documentElement) {
                  if (el.scrollTop > 0) return false;
                  el = el.parentElement;
                }
                return (document.scrollingElement || document.documentElement).scrollTop <= 0;
              };
              var ptrStartX = 0, ptrStartY = 0, ptrArmed = false, ptrFired = false;
              var ptrReset = function() {
                ptrArmed = false;
                if (!ptrFired) {
                  ptr.style.transition = 'transform .2s ease';
                  ptr.style.transform = 'translateY(-52px)';
                }
              };
              document.addEventListener('touchstart', function(ev) {
                if (ptrFired) return;
                if (ev.touches.length !== 1) { ptrArmed = false; return; }
                var t = ev.touches[0];
                ptrArmed = pullEligible(ev.target);
                ptrStartX = t.clientX;
                ptrStartY = t.clientY;
              }, { capture: true, passive: true });
              document.addEventListener('touchmove', function(ev) {
                if (!ptrArmed || ptrFired || ev.touches.length !== 1) return;
                var t = ev.touches[0];
                var dx = t.clientX - ptrStartX, dy = t.clientY - ptrStartY;
                // 動き出し数 px の横ぶれで捨てないよう、方向確定は少し動いてから
                if (Math.abs(dx) < 10 && dy < 10) return;
                if (dy < 0 || Math.abs(dx) > 48 || Math.abs(dx) > dy) { ptrReset(); return; }
                ptr.style.transition = 'none';
                var pull = Math.min(dy, 140);
                ptr.style.transform = 'translateY(' + (pull * 0.9 - 52) + 'px) rotate(' + (pull * 1.6) + 'deg)';
                if (dy > PULL) {
                  ptrFired = true;
                  ptrArmed = false;
                  ptr.classList.add('spin');
                  ptr.style.transition = 'transform .15s ease';
                  ptr.style.transform = 'translateY(8px)';
                  setTimeout(function() { location.reload(); }, 180);
                }
              }, { capture: true, passive: true });
              document.addEventListener('touchend', ptrReset, { capture: true, passive: true });
              document.addEventListener('touchcancel', ptrReset, { capture: true, passive: true });
            })();
        """.trimIndent()

        /**
         * X ペイン用。上部バーは既定で隠しペイン上端からの下スワイプで 4 秒表示。
         * メインメニュー（旧下部タブバー）は画面左端の縦メニューに変えて既定で隠し、
         * ペイン下端からの上スワイプで 4 秒表示する。!important のため X 自身の
         * スクロール連動表示にも勝ち、position:fixed で流れから外すので余白も残らない。
         * 注意（実機 DOM で検証済み）:
         * - BottomBar は transform 付きラッパーに包まれており、それが fixed の基準に
         *   なってしまうため、ラッパーの transform を無効化する
         * - nav には元の横バー高さの max-height が掛かっており、解除しないと縦メニューが
         *   途中で切れる。項目は 64x56・アイコンは 24x24 に固定（YouTube 側と同寸）
         * - スペースカルーセルは #layers 内の nav > ScrollSnap-SwipeableList +
         *   placementTracking（この条件で「おすすめ/フォロー中」タブ列を巻き添えにしない）
         * - 新着通知ピル「〜さんがポストしました / 新しいポストを表示」は
         *   [data-testid="pillLabel"]（実機 DOM で確認）。閲覧の邪魔なので常に隠す
         * セレクタが X の DOM 変更で効かなくなっても表示自体は壊れない。
         */
        private val X_BARS_JS = """
            (function() {
              if (window.__dopagakiXDoc === document) return;
              window.__dopagakiXDoc = document;
              var css =
                'header[role="banner"],[data-testid="TopNavBar"]{position:fixed !important;top:0 !important;left:0 !important;right:0 !important;z-index:2147483000 !important;transform:translateY(-110%) !important;transition:transform .25s ease !important;}' +
                'html.dopagaki-top header[role="banner"],html.dopagaki-top [data-testid="TopNavBar"]{transform:none !important;}' +
                'div:has(> div > [data-testid="BottomBar"]){transform:none !important;transition:none !important;}' +
                'html [data-testid="BottomBar"][data-testid][data-testid]{position:fixed !important;left:0 !important;right:auto !important;top:50% !important;bottom:auto !important;width:auto !important;height:auto !important;transform:translate(-110%,-50%) !important;transition:transform .25s ease !important;z-index:2147483000 !important;border-radius:0 14px 14px 0 !important;overflow:hidden !important;}' +
                'html.dopagaki-menu [data-testid="BottomBar"][data-testid][data-testid]{transform:translate(0,-50%) !important;}' +
                'html [data-testid="BottomBar"]>div{height:auto !important;width:auto !important;flex:0 0 auto !important;min-height:0 !important;}' +
                'html [data-testid="BottomBar"] nav[aria-label]{flex-direction:column !important;width:auto !important;height:auto !important;max-height:none !important;min-height:0 !important;flex:0 0 auto !important;}' +
                'html [data-testid="BottomBar"] nav[aria-label]>*{flex:0 0 auto !important;width:64px !important;height:56px !important;padding:0 !important;}' +
                'html [data-testid="BottomBar"] nav[aria-label] svg{width:24px !important;height:24px !important;}' +
                '[data-testid="cellInnerDiv"]:has(a[href*="/i/spaces"]):not(:has(article)){display:none !important;}' +
                '#layers nav:has([data-testid="ScrollSnap-SwipeableList"]):has([data-testid="placementTracking"]){display:none !important;}' +
                '[data-testid="pillLabel"]{display:none !important;}' +
                'div[role="status"]:has([data-testid="pillLabel"]){display:none !important;}';
              var style = document.createElement('style');
              style.textContent = css;
              document.documentElement.appendChild(style);
              var timers = {};
              var reveal = function(cls) {
                document.documentElement.classList.add(cls);
                clearTimeout(timers[cls]);
                timers[cls] = setTimeout(function() {
                  document.documentElement.classList.remove(cls);
                }, 4000);
              };
              var startX = null, startY = null, edge = null;
              document.addEventListener('touchstart', function(ev) {
                var t = ev.touches[0];
                startX = t.clientX;
                startY = t.clientY;
                edge = t.clientY < 40 ? 'top'
                     : t.clientX < 40 ? 'left'
                     : null;
              }, { capture: true, passive: true });
              document.addEventListener('touchmove', function(ev) {
                if (edge === null) return;
                var t = ev.touches[0];
                if (edge === 'top' && t.clientY - startY > 24) { reveal('dopagaki-top'); edge = null; }
                else if (edge === 'left' && t.clientX - startX > 24) { reveal('dopagaki-menu'); edge = null; }
              }, { capture: true, passive: true });
            })();
        """.trimIndent()

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
