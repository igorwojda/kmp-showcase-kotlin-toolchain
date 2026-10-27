import SwiftUI
import KotlinModules

@main
struct iOSApp: App {
    init() {
        KoinInit_iosKt.initializeKoin()
    }

    var body: some Scene {
        WindowGroup {
            WeeklyForecastScreen()
        }
    }
}
