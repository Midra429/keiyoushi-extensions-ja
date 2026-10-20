import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ワンパンマン（ＯＮＥ）"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "http://galaxyheavyblow.web.fc2.com"
        lang = "ja"
    }

    deeplink {
        path("/")
        path("/index.html")
        path("/fc2-imageviewer/")
    }
}

dependencies {
    implementation(project(":lib:coverbackground"))
}
