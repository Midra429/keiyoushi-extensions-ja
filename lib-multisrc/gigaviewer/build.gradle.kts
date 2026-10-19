import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 4
    libVersion = "1.6"

    deeplink {
        path("/episode/..*")
        path("/volume/..*")
    }
}

extensions.configure<LibraryExtension> {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}
