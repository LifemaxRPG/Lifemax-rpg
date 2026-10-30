
## Native Cash pilot (v207 / native API 7)
Cash is implemented in Kotlin and Jetpack Compose (`feature/cash/CashActivity.kt`). The rest of the app remains in the Capacitor WebView. The native screen sends actions through `NativeCashPlugin`; the web app saves them using the same state, XP rules and backup format. There is no separate native money database.

The build patch enables the Kotlin 2.0.21 Compose compiler and pins Compose dependencies through the 2024.12.01 BOM. CashActivity is internal (not exported), follows app theme/motion/haptics preferences, and stops its tilt sensor in the background. The screen offers **Open classic Cash** for comparison and recovery. Native API 7 ensures older APKs are offered an APK installation before receiving this web bundle.
