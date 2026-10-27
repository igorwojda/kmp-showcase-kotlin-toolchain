package com.igorwojda.showcase.feature.base.presentation.flowmvi

import com.rickclephas.kmp.observableviewmodel.ViewModel
import com.rickclephas.kmp.observableviewmodel.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.api.Provider

/**
 * Base ViewModel that owns a FlowMVI [Container.store] and adds typed entry points for the iOS consumer.
 *
 * `Store` is an interface (an ObjC protocol), so its generics are erased in Swift. [watchStates], [watchActions]
 * and [onIntent] re-expose the store with concrete [S], [I] and [A] types, as callbacks Swift can consume without
 * Kotlin-Swift interop tooling (Flow and suspend functions aren't usable from Swift as is).
 *
 * - **iOS consumer** (Swift): use [watchStates], [watchActions] and [onIntent].
 * - **Android consumer** (Kotlin): use [Container.store] directly, e.g. `store.subscribe { … }` and `store.intent(…)`.
 *
 * [watchStates] and [watchActions] read the store only through `subscribe`, like the Compose `subscribe` does, so the
 * store counts the iOS screen as a subscriber while it watches them (e.g. for `whileSubscribed`).
 */
abstract class StoreViewModel<S : MVIState, I : MVIIntent, A : MVIAction> :
    ViewModel(),
    Container<S, I, A> {
    /**
     * iOS consumer only: calls [onEach] with the store's state, typed as [S], until the returned [Watch] is cancelled.
     * Android consumer should use [store] directly.
     *
     * Watching opens a store subscription, closed by [Watch.cancel] (or when the ViewModel is cleared), so the store
     * counts the iOS screen as a subscriber (e.g. for `whileSubscribed`).
     */
    fun watchStates(onEach: (S) -> Unit): Watch = watch(subscription { states }, onEach)

    /**
     * iOS consumer only: calls [onEach] with one-off [A] actions until the returned [Watch] is cancelled.
     * Android consumer should use [store] directly.
     *
     * Watching opens a store subscription, so [pro.respawn.flowmvi.api.ActionShareBehavior.Distribute] still sees the
     * screen arrive and leave.
     */
    fun watchActions(onEach: (A) -> Unit): Watch = watch(subscription { actions }, onEach)

    /**
     * iOS consumer only: sends [intent] to the store. Typed as [I], unlike `store.intent`, which accepts
     * any [MVIIntent] in Swift. Android consumer should call `store.intent(…)` directly.
     */
    fun onIntent(intent: I) = store.intent(intent)

    private fun <T> watch(
        flow: Flow<T>,
        onEach: (T) -> Unit,
    ): Watch {
        val job = flow.onEach(onEach).launchIn(viewModelScope.coroutineScope)
        return Watch { job.cancel() }
    }

    /**
     * A cold flow backed by its own store subscription, open while the flow is collected.
     * Inside [select], `states` and `actions` are the subscription's flows, not this class's properties.
     */
    private fun <T> subscription(select: Provider<S, I, A>.() -> Flow<T>): Flow<T> =
        channelFlow {
            with(store) { subscribe { select().collect { send(it) } } }.join()
        }
}

/** Handle of a [StoreViewModel.watchStates] / [StoreViewModel.watchActions] subscription. */
fun interface Watch {
    fun cancel()
}
