import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(kei.plugins.library)
}

extensions.configure<LibraryExtension> {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
}
