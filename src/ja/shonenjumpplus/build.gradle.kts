import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "少年ジャンプ＋"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "gigaviewer"

    source {
        id = 5161357527629843269L
        lang = "ja"
        baseUrl = "https://shonenjumpplus.com"
    }
}
