# フォーク独自の拡張機能変更まとめ

この文書は、新規スレッドへの引き継ぎ用に、ローカルのGit差分と実装を調査した結果をまとめたものです。

## 比較範囲

- 調査日：2026-10-24
- リポジトリ：`Midra429/keiyoushi-extensions-ja`
- 対象：`main` の `4c95ea5594762064df602609627e4c3a3e505435`
- 追記：2026-10-24のビビビコミック改修（上記コミット以降の変更）を含む。
- 比較元：上記マージで取り込まれた上流の `d64b4deac0205e823f296a3485f4ba759dc5e3cf`
- 上流：`keiyoushi/extensions-source`

上流の現在の最新版との比較ではなく、取り込み済み上流に対するフォーク独自の差分です。初回調査は上記コミットまでを対象とし、今回はビビビコミックの改修と配布設定・ドキュメントの更新を追記しています。

再確認する場合：

```sh
git diff --stat d64b4deac0205e823f296a3485f4ba759dc5e3cf 4c95ea5594762064df602609627e4c3a3e505435
git diff d64b4deac0205e823f296a3485f4ba759dc5e3cf 4c95ea5594762064df602609627e4c3a3e505435 -- src/ja lib-multisrc/gigaviewer lib/coverbackground
# 今回のビビビコミック改修を含む差分
git diff 4c95ea5594762064df602609627e4c3a3e505435 HEAD -- src/ja/bibibicomic lib-multisrc/comiciviewer store.json .github/workflows/personal_store.yml README.md README-JA.md docs/fork-changes.md
```

## 対象の拡張機能

**既存6拡張の改修＋1拡張の新規追加**。DMMとFANZAを別ソースとして数えると、**7拡張・8ソース**です。[store.json](../store.json) の配布対象と一致します。

| 拡張機能 | モジュール | 主な変更 |
| --- | --- | --- |
| DMMブックス／FANZAブックス | [src/ja/dmm](../src/ja/dmm) | 表示・設定・エラーの日本語化。「購入済み」フィルターを追加し、内部JSON APIから本棚を取得。一般・成人向けの振り分けと重複除去に対応。通常一覧・検索を1ページ30件にして読み込みを軽量化。 |
| ブックウォーカー | [src/ja/bookwalkerjp](../src/ja/bookwalkerjp) | 日本語化。購入済み一覧の取得条件・書籍IDを修正。シリーズに属さない単独書籍の詳細・章・購入済みボタンの取得に対応。「無料の章を非表示にする」設定を追加。表紙は詳細表示の一覧から取得し、URLを推測する処理を削除。 |
| 少年ジャンプ＋ | [src/ja/shonenjumpplus](../src/ja/shonenjumpplus) | 表示名・設定・メッセージ・単行本の章表記を日本語化。エピソードを1回100件取得する指定を追加。 |
| となりのヤングジャンプ | [src/ja/tonarinoyoungjump](../src/ja/tonarinoyoungjump) | 少年ジャンプ＋と同様の日本語化と、エピソードの100件取得に対応。 |
| カドコミ | [src/ja/kadocomi](../src/ja/kadocomi) | 拡張名・検索フィルター・設定・非公開時のメッセージを日本語化。詳細情報の「Next Update」を「次回更新」に変更。 |
| ワンパンマン（ＯＮＥ） | [src/ja/onepunchman](../src/ja/onepunchman) | 新規追加。ＯＮＥ公式サイトから話一覧とページ画像を取得。作品検索、更新日の取得、サイトURLからの作品表示に対応。 |
| ビビビコミック | [src/ja/bibibicomic](../src/ja/bibibicomic) | 表示名・設定・検索フィルター・ログイン／購入案内を日本語化。ランキング・一覧・検索・詳細の表紙に共通処理を適用。既存パッケージ名・ソースIDを維持して個人ストアの配布対象に追加。 |

上記に加えて、全7拡張に以下の共通表紙処理を導入しています。

## 共通する変更

### 日本語表示への統一

既存6拡張で、拡張側が生成する名前・設定・検索案内・エラー・章の接頭辞を日本語化しています。例として、`(Preview)` は `（試し読み）`、`(Volume)` は `（単行本）` に変更しています。

作品名や説明文などのサイト由来の内容、アプリ本体・Android・ネットワークライブラリが生成する文言は、それぞれの提供元に従います。

### 全7拡張で表紙の見た目を統一

新設した共通ライブラリ [lib/coverbackground](../lib/coverbackground/README.md) を利用しています。

- 元画像を切り抜かず、縦横比を保って2:3の表紙枠に収める。
- 余白を同じ画像のぼかし背景で埋め、明るさに応じた暗さ調整と穏やかな周辺の陰影を加える。
- 最大600×900ピクセルのPNGで出力する。
- 整数ピクセルに位置を合わせ、不要な補間による前景のぼけを抑える。
- 追加のネットワーク要求や外部画像加工サービスは使わない。

実装は `CoverBackgroundInterceptor` と `withCoverBackground()` を共有しています。加工対象としてマークした表紙URLだけを処理し、本文ページ画像は対象外です。既存ライブラリの作品に反映するには、メタデータの更新が必要です。

加工後の画像は詳細画面・保存・共有などでも使われます。アプリ側が正方形など別の比率で表示する場合は、加工後の表紙がさらに切り抜かれることがあります。

### 既存ライブラリとの紐付けを維持

既存拡張のパッケージ名とソースIDを維持しています。ソース名を変更したDMM／FANZA、ブックウォーカー、少年ジャンプ＋、となりのヤングジャンプ、ビビビコミックでは、従来のIDを `source {}` に明示指定しています。

カドコミは元からソース名が日本語なので、名前に関しては拡張名の変更が中心です。

### 購入済み作品へのアクセスを改善

DMM／FANZAとブックウォーカーでは、購入済みフィルターを通常検索より優先し、HTTPキャッシュを使わず購入一覧を取得します。ログインが必要な場合の案内も日本語化しています。

- DMM／FANZA：`/ajax/bff/library/` のJSON APIを使用。ショップ種別で絞り込み、シリーズIDで重複を除去。
- ブックウォーカー：購入一覧APIにシリーズ表示を明示。シリーズ情報がない場合は書籍UUIDを保持し、単独書籍として扱う。

## 個別に押さえておく変更

### ビビビコミックの日本語化と表紙処理

- 拡張名・ソース名を「Bibibi Comic」から「ビビビコミック」に変更。
- 従来のソースID `3726193166490568923` を明示指定。上流の[配布インデックス](https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json)および既存名からのID計算結果と照合済み。
- 「ロックされた章を表示する」「ログインが必要な章を表示する」と、その説明・ログイン／購入案内・検索の「絞り込み」を日本語化。設定キーと初期値（いずれも有効）は維持。
- ランキング、更新順・カテゴリ一覧、キーワード検索、作品詳細のすべてで表紙URLに加工マーカーを付与。
- 表紙処理を本文画像の復号処理（`ImageInterceptor`）より前に配置。本文ページの`#scramble=`は既存の復号処理で扱う。
- 拡張の`versionCode`は0から1、共通テーマの`baseVersionCode`は1から2に更新し、生成バージョンは`1.6.3`。

### ブックウォーカーの無料章フィルター

「無料の章を非表示にする」は初期状態で有効です。

- 未購入で「無料で読む」がある章を非表示にする。
- タイトルに括弧付きの無料版・試し読み版表記がある別版を非表示にする。
- 無料版表記のない通常版の購入済み巻は、無料公開中でも残す。
- シリーズ巻一覧、単独書籍、「話・連載」に適用する。
- 設定変更後は章一覧の更新が必要。

単独書籍ではシリーズ名が空の場合に書籍名を使用し、購入済みの閲覧ボタンも取得できるよう修正しています。

### 一覧・章取得の調整

- DMM／FANZA：通常一覧・検索に `limit=30` を指定。
- 少年ジャンプ＋／となりのヤングジャンプ：エピソード取得に `limit=100` を指定。単行本の取得には適用しない。
- ブックウォーカー：人気・新着で `detail=1` の一覧を使用し、サイトが提供する表紙URLを取得。推測による高解像度URLへの変換と、それに伴う画像再取得処理を削除。

## 共通モジュールへの影響

[lib-multisrc/gigaviewer](../lib-multisrc/gigaviewer) には以下の変更があります。

- 設定・エラー・単行本の章表記の日本語化。
- 表紙URLを加工するための `coverUrl()` と、HTTPクライアント設定を再利用するための差し込み口。
- エピソード取得件数を指定する `episodePageSize`。既定値は未指定で、対象2拡張が100を指定。

日本語化部分は、このテーマを使う他の拡張をビルドした場合にも反映されます。一方、表紙加工と100件取得は対象2拡張で明示的に有効化しています。

[lib-multisrc/comiciviewer](../lib-multisrc/comiciviewer) には以下の変更があります。

- 設定・検索フィルター名と、URL認識エラー・ログイン／購入案内の日本語化。
- 表紙URL用の`coverUrl()`と、本文画像の復号処理を保ったままHTTPクライアントを設定する`configureComiciViewerClient()`を追加。
- ランキング用・検索／詳細用DTOとHTML一覧の表紙URLに上記フックを適用。

ComiciViewerの日本語化は、このテーマを使う他の拡張にも反映されます。表紙加工はビビビコミックのみで有効にし、他の拡張では元の表紙URLを返します。個人ストアの配布対象は7拡張です。

## 配布・ドキュメント・検証の補足

- [store.json](../store.json) で配布対象7モジュールを指定。
- [.github/workflows/personal_store.yml](../.github/workflows/personal_store.yml) と [.github/scripts/build-personal-index.py](../.github/scripts/build-personal-index.py) に個人ストア用のビルド・配布処理を追加。
- ビビビコミック追加時に個人ストアの変更検知パスへ同拡張とComiciViewerを追加し、共通テーマのrelease lintも直接実行するよう更新。
- ブックウォーカー、DMM、GigaViewer、表紙共通ライブラリにテスト・検証コードを追加。
- [README.md](../README.md) と [README-JA.md](../README-JA.md) を7拡張・8ソースに更新。DMMの実機確認項目も現在のJSON APIに合わせ、ビビビコミックの表紙・設定・ログイン案内の確認項目を追加。
- 初回調査ではビルド・テスト・実アカウントでの確認を実行していない。今回の改修の検証結果は下記に記録する。購入済み一覧の実アカウント確認とAndroid実機での読書確認は引き続き未実施。

### ビビビコミック改修の検証結果（2026-10-24）

JDK 25とAndroid SDK 37で以下を実行し、すべて成功しました。ローカル標準のJDK 27ではJavaとKotlinのターゲットが一致しないため、一時ディレクトリに用意したJDK 25を使用しています。

```sh
./gradlew :src:ja:bibibicomic:spotlessApply \
  :lib-multisrc:comiciviewer:spotlessApply \
  :src:ja:bibibicomic:assembleDebug \
  :src:ja:bibibicomic:lintRelease \
  :lib-multisrc:comiciviewer:lintRelease \
  :lib:coverbackground:testDebugUnitTest
```

- ビビビコミック・ComiciViewerのrelease lintはエラーなし。ビビビコミックは生成Manifestの`android:allowBackup`に関する`DataExtractionRules`の指摘が1件あり、ComiciViewerは指摘なし。
- 表紙共通ライブラリの既存20テストが成功。加工対象の限定、元のURLフラグメントの保持、画像の配置・出力を検証。
- 生成コードでソースID `3726193166490568923`、表示名「ビビビコミック」を確認。生成Manifestのバージョンは`1.6.3`、パッケージ名は従来の`eu.kanade.tachiyomi.extension.ja.bibibicomic`。
- デバッグAPK：`src/ja/bibibicomic/build/outputs/apk/debug/tachiyomi-ja.bibibicomic-v1.6.3.apk`。
- `store.json`の7モジュールが重複せず存在することと、`git diff --check`の成功を確認。
- ビビビコミックのAndroid実機での表示・読書・ログイン／購入の確認は未実施。[README-JA.md](../README-JA.md#実機確認項目)に確認項目を記載。

## 次のスレッドへの引き継ぎ

まずこの文書を読み、追加変更がある場合は上記コミット以降の差分を確認してください。コードを変更する場合は、リポジトリの [AGENTS.md](../AGENTS.md) と [CONTRIBUTING.md](../CONTRIBUTING.md) に従ってください。この文書は調査時点の記録であり、実装規約の代替ではありません。
