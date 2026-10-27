import KotlinModules
import SwiftUI

/// kotlinx-datetime isn't exported to Swift under its own name; the aliases keep the screens readable.
typealias LocalDate = Kotlinx_datetimeLocalDate
typealias LocalDateTime = Kotlinx_datetimeLocalDateTime

/// A `StoreViewModel.watchStates` / `watchActions` subscription as an `AsyncStream`.
/// The Kotlin subscription is cancelled when the stream's consumer (e.g. a SwiftUI `.task`) goes away.
func values<T>(_ watch: @escaping (@escaping (T) -> Void) -> Watch) -> AsyncStream<T> {
    AsyncStream { continuation in
        let subscription = watch { continuation.yield($0) }
        continuation.onTermination = { _ in subscription.cancel() }
    }
}

/// Renders the latest state of a `StoreViewModel.watchStates` subscription, kept open while the view is visible.
/// Shows a progress indicator until the first state arrives.
struct Observing<Value, Content: View>: View {
    private let watch: (@escaping (Value) -> Void) -> Watch
    private let content: (Value) -> Content
    @State private var value: Value?

    init(
        _ watch: @escaping (@escaping (Value) -> Void) -> Watch,
        @ViewBuilder content: @escaping (Value) -> Content
    ) {
        self.watch = watch
        self.content = content
    }

    var body: some View {
        Group {
            if let value {
                content(value)
            } else {
                ProgressView()
            }
        }
        .task {
            for await state in values(watch) {
                value = state
            }
        }
    }
}
