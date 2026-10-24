import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ビビビコミック"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "comiciviewer"

    source {
        id = 3726193166490568923L
        lang = "ja"
        baseUrl = "https://bibibi-comic.com"
    }
}

dependencies {
    implementation(project(":lib:coverbackground"))
}
