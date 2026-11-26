plugins {
    alias(libs.plugins.homeassistant.android.application)
    alias(libs.plugins.homeassistant.android.flavor)
    alias(libs.plugins.homeassistant.android.dependencies)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    useLibrary("android.car")

    defaultConfig {
        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
        manifestPlaceholders["sentryDsn"] = System.getenv("SENTRY_DSN") ?: ""

        testInstrumentationRunner = "io.homeassistant.companion.android.util.HAAndroidJUnitRunner"

        bundle {
            language {
                // We want to keep the translations in the final AAB for all the language
                enableSplit = false
            }
        }
    }

    // Keep the installed fork's package and data when moving from minimal to full.
    if (providers.gradleProperty("forkDistribution").isPresent) {
        productFlavors.named("full") {
            applicationId = "io.homeassistant.companion.android.minimal"
        }
    }

    buildTypes {
        debug {
            // Required for HWASan wrap.sh to be included uncompressed in the APK
            // See https://developer.android.com/ndk/guides/hwasan
            packaging {
                jniLibs {
                    useLegacyPackaging = true
                }
            }
        }
    }

    lint {
        // Until we fully migrate to Material3 this lint issue is too verbose https://github.com/home-assistant/android/issues/5420
        disable += listOf("UsingMaterialAndMaterial3Libraries")
    }
}

dependencies {
    // Most of the dependencies are coming from the convention plugin to avoid duplication with `:automotive` module.
    "fullImplementation"(libs.car.projected)
    ksp(project(":provides-sensor-processor"))
}
