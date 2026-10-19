import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "となりのヤングジャンプ"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "gigaviewer"

    source {
        id = 5726629019734498294L
        lang = "ja"
        baseUrl = "https://tonarinoyj.jp"
    }
}
