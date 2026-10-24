package com.llmlocal.recipekeeper.settings.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.usecase.ObserveStartPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.SetStartPageUseCase
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveDetailBackdropUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SetDetailBackdropUseCase
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsEffect
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsIntent
import com.llmlocal.recipekeeper.settings.presentation.mvi.StartPageOption
import com.llmlocal.recipekeeper.testing.FakeDetailBackdropRepository
import com.llmlocal.recipekeeper.testing.FakeStartPageRepository
import com.llmlocal.recipekeeper.testing.MainDispatcherRule
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStartPageRepository()
    private val backdrops = FakeDetailBackdropRepository()

    private fun viewModel() = SettingsViewModel(
        observeStartPage = ObserveStartPageUseCase(repository),
        setStartPage = SetStartPageUseCase(repository),
        observeBackdrop = ObserveDetailBackdropUseCase(backdrops),
        setBackdrop = SetDetailBackdropUseCase(backdrops),
        dispatchers = TestDispatchers(mainDispatcherRule.dispatcher),
    )

    @Test
    fun `loads the saved start page, Google by default`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.isLoading).isTrue()

        advanceUntilIdle()

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.startPage).isEqualTo(StartPageOption.GOOGLE)
        assertThat(vm.state.value.customUrl).isEmpty()
    }

    @Test
    fun `loads a saved custom page into the field`() = runTest {
        repository.current.value = StartPage.Custom("https://www.cooking.example/")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.startPage).isEqualTo(StartPageOption.CUSTOM)
        assertThat(vm.state.value.customUrl).isEqualTo("https://www.cooking.example/")
    }

    @Test
    fun `picking Google or Blank saves immediately`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SelectStartPage(StartPageOption.BLANK))
        advanceUntilIdle()
        assertThat(repository.current.value).isEqualTo(StartPage.Blank)
        assertThat(vm.state.value.startPage).isEqualTo(StartPageOption.BLANK)

        vm.onIntent(SettingsIntent.SelectStartPage(StartPageOption.GOOGLE))
        advanceUntilIdle()
        assertThat(repository.current.value).isEqualTo(StartPage.Google)
    }

    @Test
    fun `picking Custom with an empty field keeps the previous page until a valid address is committed`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SelectStartPage(StartPageOption.CUSTOM))
        advanceUntilIdle()
        assertThat(vm.state.value.startPage).isEqualTo(StartPageOption.CUSTOM)
        assertThat(repository.current.value).isEqualTo(StartPage.Google)

        vm.onIntent(SettingsIntent.UpdateCustomUrl("bbcgoodfood.com"))
        vm.onIntent(SettingsIntent.CommitCustomUrl)
        advanceUntilIdle()

        assertThat(repository.current.value).isEqualTo(StartPage.Custom("https://bbcgoodfood.com"))
        assertThat(vm.state.value.customUrl).isEqualTo("https://bbcgoodfood.com")
        assertThat(vm.state.value.customUrlError).isFalse()
    }

    @Test
    fun `committing an invalid address flags the field and saves nothing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SelectStartPage(StartPageOption.CUSTOM))
        vm.onIntent(SettingsIntent.UpdateCustomUrl("not a url"))
        vm.onIntent(SettingsIntent.CommitCustomUrl)
        advanceUntilIdle()

        assertThat(vm.state.value.customUrlError).isTrue()
        assertThat(repository.current.value).isEqualTo(StartPage.Google)

        // Typing again clears the error.
        vm.onIntent(SettingsIntent.UpdateCustomUrl("not a url!"))
        assertThat(vm.state.value.customUrlError).isFalse()
    }

    @Test
    fun `picking Custom with a valid address already in the field saves it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.UpdateCustomUrl("https://www.cooking.example/"))
        vm.onIntent(SettingsIntent.SelectStartPage(StartPageOption.CUSTOM))
        advanceUntilIdle()

        assertThat(repository.current.value).isEqualTo(StartPage.Custom("https://www.cooking.example/"))
    }

    @Test
    fun `shows the saved recipe background, soft tint by default`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.backdrop).isEqualTo(DetailBackdrop.TINT)

        backdrops.current.value = DetailBackdrop.PHOTO
        advanceUntilIdle()
        assertThat(vm.state.value.backdrop).isEqualTo(DetailBackdrop.PHOTO)
    }

    @Test
    fun `picking a recipe background saves it immediately`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.SelectBackdrop(DetailBackdrop.NONE))
        advanceUntilIdle()

        assertThat(vm.state.value.backdrop).isEqualTo(DetailBackdrop.NONE)
        assertThat(backdrops.current.value).isEqualTo(DetailBackdrop.NONE)
    }

    @Test
    fun `manage models row emits navigation`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(SettingsIntent.OpenModelManagement)
            assertThat(awaitItem()).isEqualTo(SettingsEffect.NavigateToModelManagement)
        }
    }
}
