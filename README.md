# dopagaki

X（旧Twitter）と YouTube を 1 画面で同時に表示・操作する Android アプリ。
上ペインで YouTube を再生しながら、下ペインで X のタイムラインをひたすらスクロールできる。

- 仕様は [SPEC.md](SPEC.md) が正
- 常設 UI は境界線ハンドル 1 本だけ: ドラッグ = 比率変更 / ダブルタップ = 1:2 に戻す / 長押しして離す = 再読み込み
- バックジェスチャーは「最後に触ったペイン」に効く

## インストール

[Releases](https://github.com/hatake716/dopagaki/releases) から最新の APK を端末のブラウザでダウンロードしてインストール（サイドロード）。

## ビルド

```
./gradlew assembleDebug
```

main への push ごとに GitHub Actions が APK をビルドして Release に添付する（署名の設定は SPEC.md §7）。
