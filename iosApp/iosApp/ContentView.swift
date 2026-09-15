import SwiftUI
import UIKit
import EcoRifornimentiKit

/// Ponte fra SwiftUI e la UIViewController Compose. Non c'e' altro da fare qui:
/// mappa, ricerca e preferenze vivono tutte nel modulo condiviso.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(edges: .all)
    }
}
