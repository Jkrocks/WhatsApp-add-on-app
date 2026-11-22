import SwiftUI

@main
struct SahiBolApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

struct ContentView: View {
    @State private var test = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Type like you talk. Send like a pro.")
                        .font(.headline)
                }
                Section("Set up (1 minute)") {
                    Label("Settings › General › Keyboard › Keyboards › Add New Keyboard › Sahi Bol", systemImage: "1.circle.fill")
                    Label("Tap Sahi Bol › turn on Allow Full Access (needed to fix text online)", systemImage: "2.circle.fill")
                    Label("In WhatsApp, hold 🌐 and pick Sahi Bol", systemImage: "3.circle.fill")
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }
                Section("Try it here") {
                    TextField("price is less, you take 2 i give discount", text: $test, axis: .vertical)
                        .lineLimit(3...6)
                    Text("Type a message, then tap ✓ Fix on the keyboard.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Sahi Bol")
            .tint(Color(red: 11/255, green: 122/255, blue: 83/255))
        }
    }
}
