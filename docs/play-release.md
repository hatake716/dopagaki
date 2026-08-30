# Google Play 公開チェックリスト

dopagaki を Google Play で公開するための準備状況と残作業。2026-08-30 時点。

## 準備済み（リポジトリ側）

| 項目 | 状態 |
|---|---|
| targetSdk / compileSdk 36 | ✅ Play のターゲット API 要件を満たす |
| App Bundle (.aab) のビルド | ✅ `./gradlew bundleRelease`。CI でも署名設定時に生成・Release 添付 |
| 権限が INTERNET のみ | ✅ データセーフティ・審査上もっともシンプルな構成 |
| アプリカテゴリ social 宣言 | ✅ Manifest の `appCategory` |
| プライバシーポリシー | ✅ [docs/privacy-policy.md](privacy-policy.md)（日英）。GitHub Pages で公開して URL を使う |
| ストア掲載文の下書き | ✅ [docs/play/listing.md](play/listing.md)（名称 / 短い説明 / 詳しい説明 / データセーフティ回答案） |
| アイコン 512×512 / フィーチャーグラフィック 1024×500 | ✅ [docs/play/](play/) |
| サードパーティ SDK なし | ✅ 収集データゼロで申告できる |

## 残作業（ユーザーの操作が必要）

順番どおりに進めるのがおすすめ。

1. **Play Console デベロッパーアカウント作成**（$25、一度だけ）
   - 個人アカウントの場合、本人確認と「後述のクローズドテスト要件」が課される
2. **アップロード鍵の作成と Secrets 登録**
   - 既存の `dopagaki.jks`（SPEC.md §7 の手順で作成）をそのまま Play のアップロード鍵として使える
   - Play App Signing に登録（Console の指示に従うだけ。アプリ署名鍵は Google 管理になる）
   - CI の Secrets（`KEYSTORE_BASE64` など 4 つ）が未登録ならここで登録
3. **Console でアプリを作成**し、[listing.md](play/listing.md) の内容を貼り付け
   - 実機スクリーンショットを最低 2 枚撮影してアップロード（モックアップ不可。ログイン情報や個人のタイムラインが写り込まない画面で）
4. **データセーフティ・コンテンツレーティング・対象年齢**の質問票に回答（回答案は listing.md）
5. **内部テスト → クローズドテスト**
   - ⚠️ 2023-11 以降に作成された**個人**デベロッパーアカウントは、製品版公開の前に「クローズドテストでテスター 12 人以上・14 日間継続」が必須
   - AAB は CI の Release からダウンロードするか `./gradlew bundleRelease` で生成（`app/build/outputs/bundle/release/app-release.aab`）
6. **製品版公開の申請**

## 審査リスクと対策（重要）

- **非公式クライアント/WebView ラッパーの扱い**: 本アプリは x.com / youtube.com を WebView 表示する。Play の「最小限の機能」ポリシーでは単純な Web ラッパーが拒否されることがあるが、dopagaki には 2 ペイン同時表示・統合コントロール等の独自機能があるため、説明文でそれを明確に打ち出す
- **商標**: アプリ名・アイコン・スクリーンショットの装飾に X / YouTube のロゴや名称を使わない（掲載文には「非公式であり無関係」の明記済み）。ストア用アセットは中立デザインにしてある
- **利用規約**: X / YouTube の ToS はサードパーティによる表示改変・非公式クライアントに制限があり、Play 審査とは別にサービス側からの措置（表示ブロック等）の可能性はゼロではない。SPEC.md §9 の従来からの注意事項どおり
- **UGC ポリシー**: ブラウザ型として申告すれば UGC アプリ本体の要件（通報機能など）は課されないのが通例だが、審査官によっては照会が来ることがある

## バージョン運用

- `versionCode` は公開のたびに +1（Play は同じ versionCode を受け付けない）
- `versionName` は SemVer 風（0.2.0 → 0.3.0 …）
- リリースノートは Console の「このリリースの新機能」に日本語で記載
