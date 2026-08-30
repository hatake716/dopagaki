# dopagaki 仕様書 v0.1

作成: 2026-08-30 / ステータス: ドラフト（実装前）
リポジトリ: https://github.com/hatake716/dopagaki

## 1. 概要

- X（旧Twitter）と YouTube を 1 画面で同時に表示・操作する Android アプリ
- 上ペイン = YouTube、下ペイン = X。境界線をドラッグして比率を変えられる
- 縦画面固定。アプリ側の常設ボタンは境界線ハンドル 1 本だけ。それ以外の操作は Android のバックジェスチャーと、各サイト自身の UI に任せる
- 対象端末: Pixel 10a（Android 16）。当面はサイドロード配布で、Google Play 公開は想定しない

### 設計原則

1. アプリ固有の UI は最小限。境界線ハンドル以外の常設ボタンを置かない
2. 「統合コントロール」= 上下ペインにそれぞれツールバーを持たせず、共通操作（戻る・再読み込み）は「最後に触ったペイン」に効く
3. 両ペインは常に同時に動作する。X をスクロールしても YouTube は止まらない
4. 2 サイト間のリンクは適切なペインに振り分ける（X 内の YouTube リンクは上ペインで再生）

## 2. 画面構成

```
┌──────────────────────┐ ← パンチホール分だけ黒い余白
│  YouTube (WebView)   │   デフォルト 1/3
├──────── ▬ ───────────┤ ← 境界線ハンドル（唯一のアプリ UI）
│                      │
│  X (WebView)         │   デフォルト 2/3
│                      │
└──────────────────────┘
```

- ステータスバー・ナビゲーションバーは非表示（イマーシブ）。画面端からのスワイプで一時的に表示
- 背景は黒。アプリテーマはダーク固定（両サイトが「端末のテーマに従う」設定ならダーク表示になる）
- 境界線: 見た目は 3dp のライン + 中央に短いピル。タッチ判定は上下 12dp ずつ広げて計 24dp
- 比率の可動範囲: 上ペイン 15%〜85%
- 比率は端末に保存し、次回起動時に復元する

## 3. 操作体系

| 操作 | どこで | 動作 |
|---|---|---|
| 上下にドラッグ | 境界線 | 比率変更（ドラッグ中もライブで反映） |
| ダブルタップ | 境界線 | 比率を 1:2 に戻す |
| 長押し（800ms 以上動かさず離す） | 境界線 | 最後に触ったペインを再読み込み。Toast で「X を再読み込み」等を表示 |
| バックジェスチャー | システム | 最後に触ったペインで戻る。履歴がなければアプリをバックグラウンドへ |
| タップ / スクロール | 各ペイン | そのペインが「最後に触ったペイン」になる |
| 全画面ボタン | YouTube 内 | 上ペインの中だけで全画面（下の X はそのまま）。バックで解除 |
| ペイン上端から下スワイプ | X ペイン内 | X の上部バーを一時表示（4 秒で自動的に隠れる） |
| ペイン左端から右スワイプ | 各ペイン内 | そのペインの操作メニューを**画面左端に縦表示**（4 秒で自動的に隠れる） |

YouTube は動画の再生開始時、**デフォルトで上ペイン内全画面**に切り替わる（バックまたはプレーヤーの縮小ボタンで解除）。ユーザー操作を起点としない自動再生ではブラウザの制約（transient activation）により入らないことがあり、その場合は全画面ボタンで入る。

X の上部バー（ホームのヘッダー）と両サイトの操作メニュー（X のメインメニュー、YouTube のピボットバー）は**基本非表示**にし、コンテンツを全高で表示する。操作メニューはペイン左端からの右スワイプで**ペイン左端に縦並び**で 4 秒間表示され、その間にタップ操作できる（縦表示なのでコンテンツの縦スペースを奪わない）。

左端はシステムのバックジェスチャーと競合するため、各ペイン左端の**中央 100dp の帯だけ**を `setSystemGestureExclusionRects` でバックジェスチャーから除外してメニュー用に使う（除外はエッジあたり合計 200dp が上限）。帯の外（ペインの上寄り・下寄り）から始めた左端スワイプは従来どおりバックとして働く。

「最後に触ったペイン」の初期値は下（X）。長押しを「離したとき」に発火させるのは、ドラッグ開始前に指を止めただけで再読み込みが走るのを防ぐため。

### リンクの振り分け

- X 内で `youtube.com` / `youtu.be` のリンク → 上ペインで開く
- YouTube 内で `x.com` / `twitter.com` / `t.co` のリンク → 下ペインで開く
- それ以外の http(s) リンクは、タップしたペイン内で開く（外部ブラウザに出さない）
- http(s) 以外のスキーム（`intent://` など、公式アプリへの誘導）は無視する

## 4. 動作要件

- 両ペインは常に描画・実行状態。片方を操作しても他方は一時停止しない
- アプリが前面にある間は画面を消灯させない（`FLAG_KEEP_SCREEN_ON`）
- ログイン状態（Cookie / localStorage）は再起動後も維持
- 各ペインの最後の URL を保存し、次回起動時に復元。初期値: 上 `https://m.youtube.com/`、下 `https://x.com/home`
- キーボード表示時（検索入力など）はキーボード分だけ全体を縮め、入力欄が隠れないようにする
- ディスプレイカットアウト（パンチホール）分は上端に余白を取り、動画にかぶらないようにする
- 端末を傾けても縦のまま。Activity は再生成しない（`configChanges` 指定）

## 5. 技術構成

- Kotlin / 単一 Activity / 従来の View システム（Compose は使わない。WebView を 2 つ保持し続ける用途では View の方が安定）
- レイアウト: `ConstraintLayout` + 水平 `Guideline`（percent = 上ペイン比率）
  - 上 `FrameLayout`: parent top 〜 Guideline
  - 下 `FrameLayout`: Guideline 〜 parent bottom
  - `DividerView`: Guideline 中央に重ねる（z 順は WebView より上）
- 依存: androidx core / appcompat / constraintlayout / webkit のみ。サードパーティ不使用
- minSdk 26 / targetSdk・compileSdk 36 / AGP・Gradle は最新安定版 / Kotlin DSL
- パッケージ名: `io.github.hatake716.dopagaki`
- 設定画面なし。比率と URL の保存は `SharedPreferences`

### ディレクトリ構成（案）

```
dopagaki/
├── SPEC.md                 ← 本書（仕様の正）
├── CLAUDE.md               ← 付録 A
├── .github/workflows/build.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/hatake716/dopagaki/
│       │   ├── MainActivity.kt   # レイアウト・ペイン管理・バック処理・イマーシブ
│       │   ├── PaneWebView.kt    # WebView 共通設定・URL 振り分け・ペイン内全画面
│       │   ├── DividerView.kt    # ドラッグ / ダブルタップ / 長押し
│       │   └── Prefs.kt          # 比率と URL の保存
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/{strings,themes,colors}.xml
└── build.gradle.kts / settings.gradle.kts / gradle.properties / gradle/wrapper/
```

## 6. 実装ノート（つまずきやすい点）

### WebView 共通設定（両ペイン）

- `javaScriptEnabled = true`, `domStorageEnabled = true`, `databaseEnabled = true`
- `mediaPlaybackRequiresUserGesture = false`（自動再生・連続再生のため）
- `setSupportMultipleWindows(false)`。`target=_blank` のリンクを同じ WebView で開かせ、振り分け処理に乗せるため。X の外部リンクが反応しない場合は `true` にして `onCreateWindow` で一時 WebView に URL を拾わせ、振り分けてから破棄する
- `CookieManager`: `setAcceptCookie(true)` + `setAcceptThirdPartyCookies(webView, true)`。`onPause` で `flush()`
- User-Agent: `WebSettings.getDefaultUserAgent()` から `; wv` と `Version/4.0 ` を取り除いた Chrome 相当の文字列にする。X の「サポート外ブラウザ」判定と、Google ログインの WebView 拒否（`disallowed_useragent`）を避けるため
- `shouldOverrideUrlLoading(view, request)`: `request.isForMainFrame` のときだけ判定。t.co のサーバーリダイレクト後の URL でも呼ばれるので、最終 URL のホストで振り分ける
- `doUpdateVisitedHistory`: SPA の pushState でも呼ばれる。ここで最後の URL を保存する
- `onRenderProcessGone`: 未実装だとレンダラクラッシュでアプリごと強制終了する。true を返して WebView を破棄し、Activity を作り直す（URL・比率は保存済みなので復元される）
- `onShowFileChooser`: 未実装だと X の画像・動画添付が無反応になる。`ActivityResultLauncher` でシステムのファイル選択に橋渡しする

### YouTube の全画面をペイン内に閉じ込める

- `WebChromeClient.onShowCustomView(view, callback)`: `view` を上 `FrameLayout` に MATCH_PARENT で追加し、WebView を GONE
- `onHideCustomView()`: view を取り除いて WebView を VISIBLE、`callback.onCustomViewHidden()`
- バック処理では全画面解除を最優先にする
- **全画面中のリロード・遷移対策**: 全画面のままページを読み込み直すと `onHideCustomView` が呼ばれずカスタムビューが取り残されて真っ暗になる。`onPageStarted` と長押しリロードで必ず `exitFullscreen()` する
- 再生開始時の自動全画面: `onPageFinished` で YouTube ペインに JS を注入し、`play` イベント（capture）でプレーヤー要素（`.html5-video-player`）に `requestFullscreen()` を呼ぶ。タップ起点の再生なら transient activation が生きているので通る。サイトの全画面ボタンと同じ custom view 経路に乗るため、ペイン内に閉じ込められる

### 左端の縦メニュー（両ペイン）

- 各サイトの主要ナビゲーション（X: `[data-testid="BottomBar"]` 内の nav、YouTube: `ytm-pivot-bar-renderer`）を CSS で `position: fixed; left: 0; top: 50%` + `flex-direction: column` の縦メニューに変え、`translate(-110%, -50%)` で左端の外に格納する。ペイン左端 40px 内から右へ 24px 以上のスワイプで `<html>` に `dopagaki-menu` クラスを 4 秒付与して滑り込ませる（左端中央の帯はジェスチャー除外済みなのでタッチが届く）
- **X の注意点（実機 DOM で確認）**: BottomBar は transform 付きラッパー `div` に包まれており、transform を持つ祖先は fixed の基準（containing block）になるため、そのままでは viewport 基準の配置ができない。`div:has(> div > [data-testid="BottomBar"])` でラッパーの transform を無効化する。また nav に元の横バー高さの `max-height` が残り縦メニューが途中で切れるため `max-height: none` で解除し、コンテナ収縮でアイコン svg が幅 10px に潰れるため項目 64×56・svg 24×24 に固定して YouTube 側と同寸にする

### X のバー隠蔽（下ペイン）

- `onPageFinished` で X ペインに JS を注入。`<style>` で `[data-testid="BottomBar"]` を `translateY(110%)`、`header[role="banner"]` / `[data-testid="TopNavBar"]` を `translateY(-110%)` に固定（`!important` なので X 自身のスクロール連動表示にも勝つ）。あわせて `position: fixed` にしてレイアウトの流れから外す — transform だけだと退避後に元の場所が余白として残る。表示時はタイムラインに重なる
- ホーム上部のスペース（音声ルーム）カルーセルは `#layers` のオーバーレイに `nav > [data-testid="ScrollSnap-SwipeableList"]` として浮いている（実機 DOM で確認）。`[data-testid="placementTracking"]` を含む nav だけを `display: none` にする — この条件がないと同じ構造の「おすすめ/フォロー中」タブ列 nav まで消える。補助として、スペースリンクを含みツイート（article）でないタイムラインセルも非表示
- debug ビルドは `WebView.setWebContentsDebuggingEnabled(true)` で DevTools 検査可（X の DOM 調査用）
- `touchstart`/`touchmove`（capture, passive）で、ペイン上端 40px 内から 24px 以上下へのスワイプ → `<html>` に `dopagaki-top` クラス、下端 40px 内から上スワイプ → `dopagaki-bottom` クラスを 4 秒間付与して transform を解除する
- X の SPA 遷移では document が生きているので style と リスナーは維持される。X 以外のサイトではセレクタが何にも一致せず無害

### バック処理（統合コントロールの中核）

- `OnBackPressedDispatcher` に callback を 1 つ登録し、次の優先順で処理:
  1. どちらかのペインが全画面中 → 全画面解除（X の Web プレイヤーも全画面になり得るため両ペインを見る）
  2. 最後に触ったペインが `canGoBack()` → `goBack()`
  3. それ以外 → `moveTaskToBack(true)`
- 「最後に触ったペイン」は Activity の `dispatchTouchEvent` で `ACTION_DOWN` の y 座標と Guideline 位置を比べて更新（境界線の帯の中は除外）

### 境界線

- `GestureDetector`: `onScroll` → ドラッグ、`onDoubleTap` → リセット。長押しは `ACTION_UP` 時に「800ms 以上経過かつタッチスロップ内」で自前判定
- ドラッグ中は `Guideline.setGuidelinePercent()` を MOVE ごとに更新。実機でカクつくなら、ドラッグ中はラインだけ動かして `ACTION_UP` で確定する方式に切り替える
- `ACTION_UP` で比率を保存

### イマーシブ表示

- `WindowCompat.setDecorFitsSystemWindows(window, false)`
- `WindowInsetsControllerCompat`: `hide(systemBars())` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
- テーマで `windowLayoutInDisplayCutoutMode = shortEdges`。`displayCutout()` の top インセットを root の上パディングに
- `ime()` の bottom インセットを root の下パディングに（edge-to-edge では `adjustResize` が効かないため手動）
- 実機で X 最下部のタブバーがホームジェスチャーと干渉するなら、`systemGestures()` の bottom 分も下パディングに足す

### Manifest

- `<uses-permission android:name="android.permission.INTERNET" />`
- application: `appCategory="social"`（OS の使用時間集計などでソーシャル扱いにする）
- Activity: `screenOrientation="portrait"`, `configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"`, `exported="true"`
- https のみなので `usesCleartextTraffic` は不要

## 7. ビルド・配布

### GitHub Actions（メイン経路）

main への push ごとに署名済み APK を GitHub Release に添付する。Pixel のブラウザから Releases ページを開いて直接インストールできる。

- `actions/checkout@v5` → `actions/setup-java@v5`（temurin 17）→ `gradle/actions/setup-gradle@v4` → `./gradlew assembleRelease` → `softprops/action-gh-release@v2`（tag: `build-${{ github.run_number }}`、files: `app-release.apk`）
- `permissions: contents: write`
- 署名: 毎回同じ鍵で署名しないと上書きインストールできない。ローカルで一度だけ keystore を作り、base64 で Secrets に登録する
  - `keytool -genkeypair -v -keystore dopagaki.jks -alias dopagaki -keyalg RSA -keysize 2048 -validity 10000`
  - Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
  - CI で `dopagaki.jks` に復元し、`signingConfigs.release` で参照。Secrets 未設定時は debug 署名にフォールバックしてビルドだけは通す（このとき Release への添付はスキップする。使い捨てランナーの debug 鍵はビルドごとに変わり、上書きインストール不能な APK を配布してしまうため）
- minify / shrinkResources は無効（デバッグしやすさ優先）

### ローカルビルド（任意・後回し）

NixOS 側に `flake.nix`（`androidenv.composeAndroidPackages` + JDK 17）を用意し、Tailscale 経由の `adb connect` で Pixel に直接インストールする経路も後で追加してよい。まずは Actions で回す。

## 8. 受け入れテスト（Pixel 10a 実機）

1. 起動 → 上 1/3 に YouTube、下 2/3 に X。ステータスバー・ナビバーは非表示
2. YouTube で動画を再生 → X をスクロール・タップしても映像と音声が続く
3. 境界線ドラッグ → 両ペインがライブで伸縮。再起動後も比率が保持される
4. 境界線ダブルタップ → 1:2 に戻る
5. 境界線長押し → 最後に触ったペインが再読み込みされ、Toast が出る。ドラッグ前に指を止めても再読み込みは走らない
6. X で数回遷移 → バックジェスチャーで X が戻る。YouTube に触ってからバック → YouTube が戻る
7. X 内の YouTube リンク → 上ペインで再生される（X 側は動かない）
8. YouTube の全画面ボタン → 上ペイン内だけ全画面。バックで解除
9. X にログイン → アプリを完全終了して再起動してもログイン状態
10. 端末を横に倒しても縦のまま
11. X の検索欄をタップ → キーボードの分だけ画面が縮み、入力欄が隠れない
12. 5 分放置 → 画面が消灯しない
13. Releases から APK を落として上書きインストールできる（署名一致）

## 9. 既知の制約・スコープ外

- YouTube は m.youtube.com をそのまま表示するため、広告や「アプリで開く」バナーはサイトの仕様どおり出る（バナーの CSS 非表示は将来検討）
- Google アカウントのログインは WebView 内では拒否される場合がある。UA 調整で通ることが多いが、通らなければ YouTube はログアウト状態で使う（X は ID / パスワードでログインすれば問題ない）
- アプリを裏に回すと YouTube は止まる（サイトの仕様）。Picture-in-Picture 化は将来検討
- Google Play 公開は本書の範囲外。公開する場合は YouTube / X の利用規約（サードパーティによる埋め込み表示）の確認が必要
- 広告ブロック、アカウント切替、ペインの上下入替、横画面対応はスコープ外

## 10. 本書で決めた事項（要確認）

依頼内容になかった点を以下のように決めた。変更したければここを書き換えてから実装する。

1. **「統合コントロール」の解釈**: 上下それぞれにツールバーを持たせず、境界線ハンドル 1 本 + システムのバックジェスチャーを「最後に触ったペイン」に効かせる。常設ボタンはゼロ。もし「再生/一時停止・戻る・再読み込み」を並べた 1 行の統合バーが欲しいなら、それは別案として追記する
2. **比率の記憶**: 最後の比率を保存して次回起動時に復元。ダブルタップで 1:2 に戻す（「常に 1:2 で起動」にするなら保存を外すだけ）
3. **履歴がないときのバック**: アプリをバックグラウンドへ（終了はしない）
4. **システムバー**: ステータスバーも含めて非表示。時計・電池が見えないのが不便なら、ステータスバーだけ残す
5. **画面消灯**: 前面にある間は常に消灯しない（再生中だけにするのは WebView から再生状態を取る必要があり、v0.1 ではやらない）
6. **YouTube の初期 URL**: m.youtube.com のトップ。登録チャンネル一覧などにしたければ変更
7. **レイアウトは ConstraintLayout ではなく LinearLayout + weight**（v0.1 実装時の変更）: 開発環境（NixOS）の aapt2 が ConstraintLayout の res-auto 属性（`layout_constraintGuide_percent` 等）をリンクできず、Maven 版 aapt2・constraintlayout 2.1.4/2.2.1 いずれでも再現した。上ペイン weight = 比率、下ペイン weight = 1 − 比率で機能は等価。境界線ハンドルはルート FrameLayout に重ね、`translationY` でペイン境界に同期する。依存から constraintlayout を外した
8. **再生開始時の自動全画面**（2026-08-30 追加要望）: YouTube ペインは動画の再生が始まったらデフォルトで上ペイン内全画面に入る。アプリカテゴリは `social`
9. **X のバーは基本非表示**（2026-08-30 追加要望）: X の上部バー・下部タブバーは CSS 注入で隠し、ペイン内の対応する端からのスワイプで 4 秒間だけ表示する。X の DOM（`data-testid` 等）が変わったら注入セレクタの追従が必要（§9 の制約に準ずる）
10. **X 上部の余白最小化とスペース非表示**（2026-08-30 追加要望）: 隠したバーは `position: fixed` で流れから外して余白を残さない。ホーム上部のスペースカルーセルは CSS で非表示
11. **操作メニューは画面左端に縦表示**（2026-08-30 追加要望）: X のメインメニューと YouTube のピボットバーを左端の縦メニューに変え、基本非表示にした（X の旧・下部タブバー表示を置き換え）。あわせて、全画面中のリロードで上ペインが真っ暗になる不具合を `onPageStarted` での全画面解除で修正
12. **メニューの表示トリガーは左端スワイプ**（2026-08-30 追加要望）: 当初のペイン下端上スワイプを廃止し、ペイン左端からの右スワイプに変更。バックジェスチャーとの競合は、各ペイン左端中央 100dp だけをジェスチャー除外にして両立させる
