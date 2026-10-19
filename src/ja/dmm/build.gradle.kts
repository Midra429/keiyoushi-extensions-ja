import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DMMブックス / FANZAブックス"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "DMMブックス"
        id = 7153958782880204235L
        lang = "ja"
        baseUrl = "https://book.dmm.com"
    }

    source {
        name = "FANZAブックス"
        id = 6425776937733405129L
        lang = "ja"
        baseUrl = "https://book.dmm.co.jp"
    }
}

dependencies {
    implementation(project(":lib:publus"))
}
