// Combine provides the default `objectWillChange` (required with the MemberImportVisibility Swift feature)
import Combine
import KotlinModules
import SwiftUI

/// Owns a Kotlin ViewModel for the lifetime of a SwiftUI view, like `@StateObject`, and clears it when the view goes
/// away, which cancels its `viewModelScope` (and the store running in it).
///
/// The ViewModel is created once per view identity: `StateObject` evaluates the autoclosure lazily.
/// The ViewModel isn't observed here; screens render its state through `Observing`.
@MainActor
@propertyWrapper
struct StateViewModel<VM: Kmp_observableviewmodel_coreViewModel>: DynamicProperty {
    @StateObject private var owner: ViewModelOwner<VM>

    init(wrappedValue: @autoclosure @escaping () -> VM) {
        _owner = StateObject(wrappedValue: ViewModelOwner(wrappedValue()))
    }

    var wrappedValue: VM { owner.viewModel }
}

private final class ViewModelOwner<VM: Kmp_observableviewmodel_coreViewModel>: ObservableObject {
    let viewModel: VM

    init(_ viewModel: VM) {
        self.viewModel = viewModel
    }

    deinit {
        viewModel.clear()
    }
}
