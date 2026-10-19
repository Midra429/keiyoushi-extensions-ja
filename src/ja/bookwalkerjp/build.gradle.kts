import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ブックウォーカー"
    versionCode = 15
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        id = 7753308828123873766L
        lang = "ja"
        baseUrl = "https://bookwalker.jp"
    }
}

android {
    sourceSets.named("test") {
        kotlin.directories.add("test")
        resources.srcDir("test/resources")
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
