package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.usecase.AddRecipePictureUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.DiscardRecipeEditsUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.LoadRecipeForEditUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SaveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SplitPastedLinesUseCase
import com.llmlocal.recipekeeper.testing.FakePictureImporter
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.MainDispatcherRule
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeRecipeRepository(
        listOf(FakeRecipeRepository.recipe("r1", "Soup", 1L, ingredients = listOf("water", "salt"))),
    )
    private val importer = FakePictureImporter(failing = setOf("content://bad"))
    private val dispatchers = TestDispatchers(dispatcher)

    private fun viewModel(
        id: String? = "r1",
        handle: SavedStateHandle = SavedStateHandle(),
        maxPictures: Int = 8,
    ): RecipeEditorViewModel {
        val store = object : RecipePictureStore {
            override fun dirFor(recipeId: String) = File(tmp.root, "store/$recipeId")
            override fun moveIn(recipeId: String, files: List<File>) = emptyList<String>()
            override fun delete(recipeId: String, paths: List<String>) = Unit
            override fun deleteAll(recipeId: String) = Unit
        }
        return RecipeEditorViewModel(
            recipeId = id,
            savedStateHandle = handle,
            loadForEdit = LoadRecipeForEditUseCase(repository),
            saveRecipe = SaveRecipeUseCase(repository, store, dispatchers, { "new-id" }, { 5L }),
            addPicture = AddRecipePictureUseCase(importer, File(tmp.root, "cache"), dispatchers),
            discardEdits = DiscardRecipeEditsUseCase(dispatchers),
            splitPasted = SplitPastedLinesUseCase(),
            dispatchers = dispatchers,
            maxPictures = maxPictures,
        )
    }

    @Test
    fun `edit mode loads the draft and is clean`() = runTest {
        val vm = viewModel()
        val s = vm.state.value
        assertThat(s.isLoading).isFalse()
        assertThat(s.edit?.title).isEqualTo("Soup")
        assertThat(s.mode).isEqualTo(EditorMode.EDIT)
        assertThat(s.isDirty).isFalse()
    }

    @Test
    fun `unknown id is not found`() = runTest {
        assertThat(viewModel("nope").state.value.notFound).isTrue()
    }

    @Test
    fun `create mode starts blank, clean and focuses the title`() = runTest {
        val vm = viewModel(null)
        vm.effects.test { assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusTitle) }
        assertThat(vm.state.value.mode).isEqualTo(EditorMode.CREATE)
        assertThat(vm.state.value.isDirty).isFalse()
    }

    @Test
    fun `typing makes the draft dirty and caps length`() = runTest {
        val vm = viewModel()
        vm.onIntent(RecipeEditorIntent.TitleChanged("x".repeat(300)))
        assertThat(vm.state.value.isDirty).isTrue()
        assertThat(vm.state.value.edit?.title).hasLength(RecipeEditorViewModel.TITLE_MAX)
        vm.onIntent(RecipeEditorIntent.PrepChanged("12a345"))
        assertThat(vm.state.value.edit?.prepMinutes).isEqualTo(1234)
        vm.onIntent(RecipeEditorIntent.TotalChanged(""))
        assertThat(vm.state.value.edit?.totalMinutes).isNull()
    }

    @Test
    fun `request close is clean pop or dialog when dirty`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.RequestClose)
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.Closed)
            vm.onIntent(RecipeEditorIntent.NotesChanged("n"))
            vm.onIntent(RecipeEditorIntent.RequestClose)
            expectNoEvents()
        }
        assertThat(vm.state.value.showDiscardDialog).isTrue()
        vm.onIntent(RecipeEditorIntent.DismissDiscard)
        assertThat(vm.state.value.showDiscardDialog).isFalse()
    }

    @Test
    fun `confirm discard deletes new pictures and closes`() = runTest {
        val vm = viewModel()
        vm.onIntent(RecipeEditorIntent.PicturesPicked(listOf("content://a", "content://b")))
        val files = vm.state.value.edit!!.pictures.map { File(it.path) }
        assertThat(files).hasSize(2)
        assertThat(files.all { it.exists() }).isTrue()
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.RequestClose)
            vm.onIntent(RecipeEditorIntent.ConfirmDiscard)
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.Closed)
        }
        assertThat(files.none { it.exists() }).isTrue()
    }

    @Test
    fun `pasting lines splits the row and focuses the last inserted one`() = runTest {
        val vm = viewModel()
        val key = vm.state.value.edit!!.ingredients.first().key
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.LineChanged(EditSection.INGREDIENTS, key, "- eggs\n- flour\n- milk"))
            val ingredients = vm.state.value.edit!!.ingredients
            assertThat(ingredients.map { it.text }).containsExactly("eggs", "flour", "milk", "salt").inOrder()
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusLine(EditSection.INGREDIENTS, ingredients[2].key))
        }
        assertThat(vm.state.value.edit!!.ingredients[1].sourceIndex).isNull()
    }

    @Test
    fun `a newline typed inside a step stays in that step`() = runTest {
        val vm = viewModel()
        val key = vm.state.value.edit!!.steps.first().key
        vm.onIntent(RecipeEditorIntent.LineChanged(EditSection.STEPS, key, "Mix\n"))
        assertThat(vm.state.value.edit!!.steps.map { it.text }).containsExactly("Mix\n", "Bake.").inOrder()
    }

    @Test
    fun `add remove and move lines`() = runTest {
        val vm = viewModel()
        val first = vm.state.value.edit!!.ingredients.first().key
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.AddLine(EditSection.INGREDIENTS, first))
            val added = vm.state.value.edit!!.ingredients[1]
            assertThat(added.text).isEmpty()
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusLine(EditSection.INGREDIENTS, added.key))
        }
        vm.onIntent(RecipeEditorIntent.MoveLine(EditSection.INGREDIENTS, 0, 2))
        assertThat(vm.state.value.edit!!.ingredients.map { it.text }).containsExactly("", "salt", "water").inOrder()
        vm.onIntent(RecipeEditorIntent.RemoveLine(EditSection.INGREDIENTS, vm.state.value.edit!!.ingredients[0].key))
        assertThat(vm.state.value.edit!!.ingredients.map { it.text }).containsExactly("salt", "water").inOrder()
        vm.onIntent(RecipeEditorIntent.MoveLine(EditSection.INGREDIENTS, 0, 9))
        assertThat(vm.state.value.edit!!.ingredients.map { it.text }).containsExactly("salt", "water").inOrder()
    }

    @Test
    fun `pictures over the cap raise PICTURES_LIMIT and a failed import PICTURE_FAILED`() = runTest {
        val vm = viewModel(maxPictures = 2)
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.PicturesPicked(listOf("content://a", "content://b", "content://c")))
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.Notify(EditorNotice.PICTURES_LIMIT))
            assertThat(vm.state.value.edit!!.pictures).hasSize(2)
            assertThat(vm.state.value.importingPictures).isEqualTo(0)
        }
        val vm2 = viewModel(maxPictures = 4)
        vm2.effects.test {
            vm2.onIntent(RecipeEditorIntent.PicturesPicked(listOf("content://bad")))
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.Notify(EditorNotice.PICTURE_FAILED))
        }
        assertThat(vm2.state.value.edit!!.pictures).isEmpty()
    }

    @Test
    fun `cover moves a picture first and remove deletes a temp file`() = runTest {
        val vm = viewModel()
        vm.onIntent(RecipeEditorIntent.PicturesPicked(listOf("content://a", "content://b")))
        val (a, b) = vm.state.value.edit!!.pictures
        vm.onIntent(RecipeEditorIntent.SetCover(b.key))
        assertThat(vm.state.value.edit!!.pictures.map { it.key }).containsExactly(b.key, a.key).inOrder()
        vm.onIntent(RecipeEditorIntent.RemovePicture(a.key))
        assertThat(File(a.path).exists()).isFalse()
        assertThat(vm.state.value.edit!!.pictures.map { it.key }).containsExactly(b.key)
    }

    @Test
    fun `invalid save reports errors and scrolls to the title`() = runTest {
        val vm = viewModel(null)
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusTitle)
            vm.onIntent(RecipeEditorIntent.Save)
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.ScrollTo(EditorTarget.TITLE))
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusTitle)
        }
        assertThat(vm.state.value.errors).containsExactly(EditorError.TitleRequired, EditorError.NeedContent)
        // errors clear live as the user fixes them
        vm.onIntent(RecipeEditorIntent.TitleChanged("Pie"))
        assertThat(vm.state.value.errors).containsExactly(EditorError.NeedContent)
    }

    @Test
    fun `content error scrolls to the ingredients`() = runTest {
        val vm = viewModel(null)
        vm.onIntent(RecipeEditorIntent.TitleChanged("Pie"))
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.FocusTitle)
            vm.onIntent(RecipeEditorIntent.Save)
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.ScrollTo(EditorTarget.INGREDIENTS))
        }
    }

    @Test
    fun `valid save emits Saved with the mode`() = runTest {
        val vm = viewModel()
        vm.onIntent(RecipeEditorIntent.TitleChanged("Better soup"))
        vm.effects.test {
            vm.onIntent(RecipeEditorIntent.Save)
            assertThat(awaitItem()).isEqualTo(RecipeEditorEffect.Saved("r1", EditorMode.EDIT))
        }
        assertThat(repository.recipes.single().title).isEqualTo("Better soup")
        assertThat(vm.state.value.isSaving).isFalse()
    }

    @Test
    fun `saved state handle restores the draft without reloading`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.onIntent(RecipeEditorIntent.TitleChanged("Edited"))
        first.onIntent(RecipeEditorIntent.PicturesPicked(listOf("content://a")))
        val draft = first.state.value.edit!!
        // The recipe changed underneath; a restore must not reload it.
        repository.upsert(repository.recipes.single().copy(title = "Changed elsewhere"))

        val restored = viewModel(handle = handle)
        assertThat(restored.state.value.edit).isEqualTo(draft)
        assertThat(restored.state.value.isDirty).isTrue()
        assertThat(restored.state.value.initial?.title).isEqualTo("Soup")
    }
}
