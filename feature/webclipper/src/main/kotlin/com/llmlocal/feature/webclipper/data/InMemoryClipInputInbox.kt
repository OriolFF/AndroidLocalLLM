package com.llmlocal.feature.webclipper.data

import com.llmlocal.feature.webclipper.domain.input.ClipInputInbox
import com.llmlocal.feature.webclipper.domain.input.PendingClipInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-lifetime [ClipInputInbox] backed by a `MutableStateFlow`. */
class InMemoryClipInputInbox : ClipInputInbox {

    private val _pending = MutableStateFlow<PendingClipInput?>(null)
    override val pending: StateFlow<PendingClipInput?> = _pending

    override fun offer(input: PendingClipInput) {
        _pending.value = input
    }

    override fun consume(): PendingClipInput? {
        while (true) {
            val current = _pending.value ?: return null
            if (_pending.compareAndSet(current, null)) return current
        }
    }
}
