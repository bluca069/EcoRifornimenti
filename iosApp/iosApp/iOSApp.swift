import SwiftUI

/// Punto d'ingresso dell'app iOS: ospita a tutto schermo la UI Compose
/// Multiplatform (framework EcoRifornimentiKit), la stessa che gira su Android.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}
