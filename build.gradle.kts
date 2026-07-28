plugins {
    id("com.android.application") version "9.3.0" apply false
    id("com.android.library") version "9.3.0" apply false
    kotlin("android") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.1.10-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
