plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.aethelgard.survival.core"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  api(libs.gdx.core)
  testImplementation(libs.junit)
}
