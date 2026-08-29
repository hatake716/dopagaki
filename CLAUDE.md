# dopagaki

- 仕様は SPEC.md が正。仕様を変える場合は SPEC.md を先に更新してからコードを変える
- 決めたこと・試して駄目だったことは SPEC.md の §10 か §9 に追記して残す
- Kotlin / View システム / サードパーティ依存なし（SPEC.md §5）
- 実機テストは人間が Pixel 10a で行う。adb は叩かず、SPEC.md §8 のどの項目を確認してほしいかを伝える
- ビルドは GitHub Actions（SPEC.md §7）。push 前に `./gradlew assembleDebug` が通ることを確認する
