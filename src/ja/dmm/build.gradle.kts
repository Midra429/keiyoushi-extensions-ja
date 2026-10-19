import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DMM"
    versionCode = 7
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

android {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
}

// Unit tests do not declare extension entry points.
tasks.matching { it.name.startsWith("ksp") && it.name.contains("UnitTest") }.configureEach {
    enabled = false
}

dependencies {
    implementation(project(":lib:coverbackground"))
    implementation(project(":lib:publus"))
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}
