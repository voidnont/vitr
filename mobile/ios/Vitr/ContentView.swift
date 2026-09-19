import SwiftUI

struct ContentView: View {
    var body: some View {
        VitrWebView()
            .background(Color.black)
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
    }
}
