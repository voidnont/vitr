import SwiftUI

struct ContentView: View {
    @State private var showingInfo = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            VitrWebView()
                .ignoresSafeArea(edges: .bottom)

            Button {
                showingInfo = true
            } label: {
                Image(systemName: "ellipsis.circle.fill")
                    .font(.system(size: 24, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(.white)
                    .padding(14)
            }
            .accessibilityLabel("Vitr info")
        }
        .sheet(isPresented: $showingInfo) {
            VitrInfoView()
        }
    }
}

private struct VitrInfoView: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var updateChecker = UpdateChecker()

    var body: some View {
        NavigationStack {
            Form {
                Section("Vitr") {
                    LabeledContent("Publisher", value: "Blood")
                    LabeledContent("Version", value: updateChecker.currentVersion)

                    Button("Check for updates") {
                        Task { await updateChecker.check() }
                    }

                    switch updateChecker.state {
                    case .idle:
                        EmptyView()
                    case .checking:
                        HStack {
                            ProgressView()
                            Text("Checking bloodvitr/vitr releases…")
                        }
                    case .upToDate(let version):
                        Text("Up to date · \(version)")
                            .foregroundStyle(.secondary)
                    case .available(let version, let url):
                        Button("Vitr \(version) is available") {
                            openURL(url)
                        }
                    case .unavailable(let message):
                        Text(message)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Links") {
                    Button("Support Vitr on GitHub") {
                        openURL(VitrLinks.support)
                    }
                    Button("Donate on Ko-fi") {
                        openURL(VitrLinks.donation)
                    }
                    Button("Open releases") {
                        openURL(VitrLinks.releasesPage)
                    }
                }
            }
            .navigationTitle("Vitr")
        }
        .preferredColorScheme(.dark)
    }
}
