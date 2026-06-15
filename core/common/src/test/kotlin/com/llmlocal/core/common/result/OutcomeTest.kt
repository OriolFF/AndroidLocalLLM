package com.llmlocal.core.common.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutcomeTest {

    @Test
    fun `Success carries value`() {
        val outcome = Outcome.Success(42)
        assertThat(outcome.isSuccess()).isTrue()
        assertThat(outcome.getOrNull()).isEqualTo(42)
        assertThat(outcome.exceptionOrNull()).isNull()
    }

    @Test
    fun `Failure carries error`() {
        val ex = RuntimeException("boom")
        val outcome: Outcome<Int> = Outcome.Failure(ex)
        assertThat(outcome.isFailure()).isTrue()
        assertThat(outcome.exceptionOrNull()).isSameInstanceAs(ex)
        assertThat(outcome.getOrNull()).isNull()
    }

    @Test
    fun `fold dispatches to the right branch`() {
        val s: Outcome<Int> = Outcome.Success(7)
        val f: Outcome<Int> = Outcome.Failure(RuntimeException("x"))

        assertThat(s.fold({ it * 2 }, { -1 })).isEqualTo(14)
        assertThat(f.fold({ it * 2 }, { -1 })).isEqualTo(-1)
    }

    @Test
    fun `runCatchingOutcome converts success`() {
        val outcome = runCatchingOutcome { "ok" }
        assertThat(outcome).isEqualTo(Outcome.Success("ok"))
    }

    @Test
    fun `runCatchingOutcome converts failure`() {
        val outcome = runCatchingOutcome { error("nope") }
        assertThat(outcome.isFailure()).isTrue()
        assertThat(outcome.exceptionOrNull()).hasMessageThat().isEqualTo("nope")
    }
}
