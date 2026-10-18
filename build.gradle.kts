import com.diffplug.gradle.spotless.SpotlessExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Android and Kotlin plugins are applied by buildSrc convention plugins (tvbro.android.library / tvbro.android.application).
// Only declare here plugins that subprojects apply via the version catalog and are not on buildSrc classpath.
plugins {
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Spotless + ktlint over every Kotlin source and Gradle script in the repo. `spotlessApply` is the
// fixer; `spotlessCheck` gates CI (lint job) and the optional `.githooks/` pre-commit hook. Rule-set
// decisions (currently: wildcard imports tolerated, see the follow-up note there) live in
// .editorconfig. buildSrc is a separate build whose tasks no root invocation can reach, so its
// sources are outside this surface.
fun Project.spotlessKotlinSources() {
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
        }
        kotlinGradle {
            target("*.kts")
            ktlint()
        }
    }
}

spotlessKotlinSources()
subprojects {
    apply(plugin = "com.diffplug.spotless")
    spotlessKotlinSources()
}
