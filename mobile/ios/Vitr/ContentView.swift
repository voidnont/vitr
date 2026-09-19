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
                    .foregroundStyle(Color(red: 0.949, green: 0.184, blue: 0.365))
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
                Section {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("vitr")
                            .font(.title.bold())
                        Text("/ by blood")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color(red: 0.949, green: 0.184, blue: 0.365))
                    }
                    .padding(.vertical, 4)
                }

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

                Section("Blood") {
                    Link("github.com/bloodvitr", destination: VitrLinks.support)
                    Link("ko-fi.com/bloodvitr", destination: VitrLinks.donation)
                    Button("Open releases") {
                        openURL(VitrLinks.releasesPage)
                    }
                }
            }
            .navigationTitle("Settings")
        }
        .tint(Color(red: 0.949, green: 0.184, blue: 0.365))
        .preferredColorScheme(.dark)
    }
}
