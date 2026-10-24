# 日本語拡張ストア（個人用）

[Keiyoushi Extensions](https://github.com/keiyoushi/extensions-source)を基にした個人用の改修です。Keiyoushiの公式配布ではありません。

リポジトリ名：`Midra429/keiyoushi-extensions-ja`。

## 対象

| モジュール | 表示名 | 変更内容 |
| --- | --- | --- |
| `src/ja/dmm` | DMMブックス / FANZAブックス | 日本語化、購入済み一覧のフィルターを追加 |
| `src/ja/shonenjumpplus` | 少年ジャンプ＋ | 表示名と共通設定・メッセージを日本語化 |
| `src/ja/tonarinoyoungjump` | となりのヤングジャンプ | 表示名と共通設定・メッセージを日本語化 |
| `src/ja/kadocomi` | カドコミ | 表示名、設定、検索、詳細、メッセージを日本語化 |
| `src/ja/bookwalkerjp` | ブックウォーカー | 日本語化、購入済み一覧の取得条件と書籍IDの扱いを修正、無料章を非表示にする設定を追加 |
| `src/ja/onepunchman` | ワンパンマン（ＯＮＥ） | ＯＮＥ公式サイトの話一覧・ページ画像の取得に対応 |
| `src/ja/bibibicomic` | ビビビコミック | 表示名、共通設定・検索フィルター・ログイン案内を日本語化、共通表紙処理を適用 |

拡張機能が生成する表示テキストを日本語にしています。作品名や説明などサイトから受け取る内容、アプリ本体・Android・ネットワークライブラリのメッセージはそれぞれの提供元に従います。

## 配布の状態

ローカルの実装と個人用署名でのビルドが完了しています。上流の正式なフォーク上で、改修と配布設定を管理します。
`store.json`には配布対象の7モジュールのみを登録しています。DMMとFANZAが別ソースのため、7拡張・8ソースです。

公開後に追加するストアURL：

```text
https://github.com/Midra429/keiyoushi-extensions-ja/releases/latest/download/index.pb
```

リリースで配布されるインデックスです。新しい拡張API 1.6に対応したMihon・TachiyomiSY・Komikkuが対象です。Komikku v1.14.1のインデックス読み込み実装に合わせた、gzip圧縮Protobuf形式の`index.pb`を生成します。

## 既存の拡張から切り替える

1. アプリでライブラリをバックアップする。
2. Keiyoushi版の対象拡張をアンインストールする。署名が異なるため、そのまま上書き更新はできない。
3. 個人用ストアの拡張をインストールし、必要ならアプリ内で信頼を設定する。
4. WebViewでDMM/FANZA・ブックウォーカーへ再ログインする。

元のパッケージ名とソースIDを維持しています。日本語名への変更でライブラリの紐付けが変わるのを防いでいます。アンインストールすると拡張の設定やログイン状態は引き継がれない場合があります。

## 購入済み一覧

各ソース内の検索フィルターで「購入済み」を選択してください。購入済みを選択した場合、検索語・通常の並び順より購入一覧を優先します。

- DMM/FANZA：WebViewでログインしたアカウントの本棚からシリーズを取得します。
- ブックウォーカー：購入一覧APIへシリーズ表示を明示し、HTTPキャッシュを使わず取得します。シリーズ情報がない単独書籍は書籍IDを保持します。
- 両サービスのログイン済み購入データを使った実機確認は未実施です。実機確認項目を下記に記載しています。

## ブックウォーカーの無料章

ソース設定の「無料の章を非表示にする」は初期状態で有効です。

- 「無料で読む」の章を非表示にします。
- `【期間限定無料】`、`［無料お試し版］`など、無料版・試し読み版として明記された別版も非表示にします。
- 通常版を購入済みの場合は、無料公開の有無にかかわらず残します。
- 「話・連載」にも適用します。
- 設定を変更した後は作品の章一覧を更新してください。

## ビルドと検証

`.github/workflows/personal_store.yml`が対象7拡張をビルド・検証します。通常のpushとプルリクエストでは検証のみ行い、配布用の署名鍵は使いません。このときのActions成果物はデバッグ署名の検証用です。

更新を公開する場合は、Actionsの「Japanese extension store」を`main`から手動実行し、`publish`を有効にします。`SIGNING_KEY`（個人用署名鍵のBase64）、`KEY_STORE_PASSWORD`をリポジトリのActions Secretsへ登録する必要があります。公開用の署名鍵は初回リリースと同じものを使ってください。

上流のワークフローは残し、上流専用のビルド・Issue自動処理はフォークで動かない条件を付けています。セキュリティ検査用ワークフローは維持しています。

JDK 25、Android SDK 37、Python 3とProtobuf 7.36.2を使います。上流のビルド設定を引き継いでいます。

```sh
./gradlew :src:ja:dmm:assembleRelease :src:ja:dmm:lintRelease \
  :src:ja:bookwalkerjp:assembleRelease :src:ja:bookwalkerjp:lintRelease \
  :src:ja:shonenjumpplus:assembleRelease :src:ja:shonenjumpplus:lintRelease \
  :src:ja:tonarinoyoungjump:assembleRelease :src:ja:tonarinoyoungjump:lintRelease \
  :src:ja:kadocomi:assembleRelease :src:ja:kadocomi:lintRelease \
  :src:ja:onepunchman:assembleRelease :src:ja:onepunchman:lintRelease \
  :src:ja:bibibicomic:assembleRelease :src:ja:bibibicomic:lintRelease \
  :lib-multisrc:gigaviewer:lintRelease \
  :lib-multisrc:comiciviewer:lintRelease \
  :src:ja:bookwalkerjp:testDebugUnitTest
```

リリース署名にはルートの`signingkey.jks`と環境変数`ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD`を使用します。鍵とパスワードはGitに追加しないでください。鍵を変更すると以後の上書き更新ができなくなるため、同じ鍵を保管して使い続けてください。

インデックス生成：

```sh
python -m pip install protobuf==7.36.2
python .github/scripts/build-personal-index.py \
  --output dist --tag RELEASE_TAG --signing-key SIGNING_CERTIFICATE_SHA256
```

`SIGNING_CERTIFICATE_SHA256`はAPKを署名した証明書のSHA-256指紋（小文字64桁）です。生成物は署名済みAPK・JAR、アイコン、`index.pb`、`index.json`、`SHA256SUMS`です。インデックスのURLは指定したリリースタグを参照します。リリースを公開する際は、生成物を同じタグへまとめてアップロードしてください。

## 実機確認項目

- 7拡張の表示名、設定、検索フィルター、章の接頭辞を確認する。
- DMMとFANZAそれぞれで、ログアウト時はログイン案内が表示されること、ログイン後は実際の本棚と購入済み一覧が一致することを確認する。
- DMM/FANZAで購入済み一覧の2ページ目以降も表示されることを確認する。購入済み一覧は`/ajax/bff/library/`のJSON APIを使用する。
- ブックウォーカーで購入一覧と実際のアカウントの所持作品が一致し、シリーズ未登録の単独書籍でもエラーが出ないことを確認する。
- ブックウォーカーで通常の購入巻が残り、無料公開巻・期間限定無料版が消えることを確認する。
- 無料章設定をオフにして一覧を更新すると、無料章が戻ることを確認する。
- ビビビコミックでランキング・更新順・検索・詳細の表紙が2:3のぼかし背景付きで表示され、本文画像は正常に読めることを確認する。既存ライブラリの表紙はメタデータ更新後に確認する。
- ビビビコミックで「ロックされた章を表示する」「ログインが必要な章を表示する」の切り替えと、WebViewでのログイン・購入案内を確認する。
- 公開後、ストアURLを追加して7拡張が表示・インストールできることを確認する。

## ライセンスと上流

上流：[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source)。基点コミット：`314a0d1b2b6b194f6a4f9aee252fec9ce55ae5ca`。

元の著作権表示とApache License 2.0を保持しています。[LICENSE](LICENSE)を参照してください。個人用の変更は本書とGit差分に記録しています。
