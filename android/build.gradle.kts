// Bridge module so `./gradlew android:assembleDebug` and `gradle android:assembleDebug`
// build the Android launcher APK and expose it under both:
// - app/build/outputs/apk/debug/app-debug.apk
// - android/build/outputs/apk/debug/android-debug.apk

tasks.register<Copy>("assembleDebug") {
  group = "build"
  description = "Assembles the debug APK for AethelgardSurvival (:android -> :app)"
  dependsOn(":app:assembleDebug")
  from("${rootProject.projectDir}/app/build/outputs/apk/debug/app-debug.apk")
  into(layout.buildDirectory.dir("outputs/apk/debug"))
  rename("app-debug.apk", "android-debug.apk")
}

tasks.register<Copy>("assembleRelease") {
  group = "build"
  description = "Assembles the release APK for AethelgardSurvival (:android -> :app)"
  dependsOn(":app:assembleRelease")
  from("${rootProject.projectDir}/app/build/outputs/apk/release")
  into(layout.buildDirectory.dir("outputs/apk/release"))
}
