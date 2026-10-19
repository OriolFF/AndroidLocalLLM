package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.domain.LibraryFilter
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary
import com.llmlocal.recipekeeper.recipes.domain.SaveResult
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SaveRecipeUseCaseTest {

    @get:Rule val tmp = TemporaryFolder()

    private val saved = FakeRecipeRepository.recipe("r1", "Soup", 100L, ingredients = listOf("a", "b", "c"))
        .copy(
            steps = listOf("s1", "s2"),
            checkedIngredients = setOf(0, 2),
            isFavourite = true,
            truncated = true,
            picturesFailed = true,
            pictureCandidates = listOf("https://x/y.jpg"),
        )
    private val repository = FakeRecipeRepository(listOf(saved))
    private lateinit var store: TempDirPictureStore

    private fun useCase(repo: FakeRecipeRepository = repository): SaveRecipeUseCase {
        store = TempDirPictureStore(tmp.newFolder())
        return SaveRecipeUseCase(repo, store, TestDispatchers(UnconfinedTestDispatcher()), { "new-id" }, { 999L })
    }

    private fun lines(vararg text: String, sourceIndexed: Boolean = false) =
        text.mapIndexed { i, t -> EditableLine(i.toLong(), t, if (sourceIndexed) i else null) }

    private fun createDraft(
        title: String = "Pancakes",
        ingredients: List<EditableLine> = lines("flour", ""),
        steps: List<EditableLine> = lines("  mix ", ""),
        prep: Int? = null,
        cook: Int? = null,
        total: Int? = null,
        pictures: List<EditorPicture> = emptyList(),
    ) = RecipeEdit(
        id = null, origin = RecipeOrigin.CREATED, title = title, ingredients = ingredients, steps = steps,
        prepMinutes = prep, cookMinutes = cook, totalMinutes = total, pictures = pictures,
    )

    private fun editDraft(
        ingredients: List<EditableLine>,
        pictures: List<EditorPicture> = emptyList(),
    ) = RecipeEdit(
        id = "r1", origin = RecipeOrigin.CLIPPED, title = " Better soup ", ingredients = ingredients,
        steps = lines("s1", "s2", sourceIndexed = true), pictures = pictures,
        sourceUrl = saved.sourceUrl, sourceHost = saved.sourceHost,
    )

    @Test
    fun `create builds a created recipe with a new id and clippedAt`() = runTest {
        val result = useCase()(createDraft())
        assertThat(result).isEqualTo(SaveResult.Saved("new-id"))
        val recipe = repository.recipes.first { it.id == "new-id" }
        assertThat(recipe.origin).isEqualTo(RecipeOrigin.CREATED)
        assertThat(recipe.clippedAt).isEqualTo(999L)
        assertThat(recipe.editedAt).isNull()
        assertThat(recipe.sourceUrl).isEmpty()
        assertThat(recipe.ingredients).containsExactly("flour")
        assertThat(recipe.steps).containsExactly("mix")
        assertThat(recipe.description).isNull()
        assertThat(recipe.isFavourite).isFalse()
    }

    @Test
    fun `edit keeps identity and favourite, sets editedAt and clears truncated`() = runTest {
        val result = useCase()(editDraft(lines("a", "b", "c", sourceIndexed = true)))
        assertThat(result).isEqualTo(SaveResult.Saved("r1"))
        val recipe = repository.recipes.single { it.id == "r1" }
        assertThat(recipe.title).isEqualTo("Better soup")
        assertThat(recipe.clippedAt).isEqualTo(100L)
        assertThat(recipe.editedAt).isEqualTo(999L)
        assertThat(recipe.isFavourite).isTrue()
        assertThat(recipe.truncated).isFalse()
        assertThat(recipe.origin).isEqualTo(RecipeOrigin.CLIPPED)
        assertThat(recipe.pictureCandidates).containsExactly("https://x/y.jpg")
        assertThat(recipe.picturesFailed).isTrue()
    }

    @Test
    fun `checked ingredients follow rows after remove and insert and reorder`() = runTest {
        // original: a(0,checked) b(1) c(2,checked); remove b, insert new row first, c before a.
        val draft = editDraft(
            listOf(
                EditableLine(10, "new", null),
                EditableLine(2, "c", 2),
                EditableLine(0, "a", 0),
            ),
        )
        useCase()(draft)
        val recipe = repository.recipes.single { it.id == "r1" }
        assertThat(recipe.ingredients).containsExactly("new", "c", "a").inOrder()
        assertThat(recipe.checkedIngredients).containsExactly(1, 2)
    }

    @Test
    fun `blank rows are dropped before remapping checks`() = runTest {
        useCase()(editDraft(listOf(EditableLine(0, "a", 0), EditableLine(1, "  ", 1), EditableLine(2, "c", 2))))
        val recipe = repository.recipes.single { it.id == "r1" }
        assertThat(recipe.ingredients).containsExactly("a", "c").inOrder()
        assertThat(recipe.checkedIngredients).containsExactly(0, 1)
    }

    @Test
    fun `total is derived only when prep and cook are both set`() = runTest {
        useCase()(createDraft(prep = 10, cook = 20))
        assertThat(repository.recipes.last().totalMinutes).isEqualTo(30)
        useCase()(createDraft(prep = 10))
        assertThat(repository.recipes.last().totalMinutes).isNull()
        useCase()(createDraft(prep = 10, cook = 20, total = 45))
        assertThat(repository.recipes.last().totalMinutes).isEqualTo(45)
    }

    @Test
    fun `new pictures are moved in and dropped ones deleted, order preserved`() = runTest {
        val temp = tmp.newFile("t.jpg").apply { writeText("x") }
        val keep = EditorPicture(1, "/old/keep.jpg", isNew = false)
        val fresh = EditorPicture(2, temp.absolutePath, isNew = true)
        val repo = FakeRecipeRepository(listOf(saved.copy(pictures = listOf("/old/drop.jpg", "/old/keep.jpg"))))
        val result = useCase(repo)(
            editDraft(lines("a", sourceIndexed = true), pictures = listOf(fresh, keep)),
        )
        assertThat(result).isInstanceOf(SaveResult.Saved::class.java)
        val pictures = repo.recipes.single().pictures
        assertThat(pictures).hasSize(2)
        assertThat(pictures[1]).isEqualTo("/old/keep.jpg")
        assertThat(File(pictures[0]).exists()).isTrue()
        assertThat(temp.exists()).isFalse()
        assertThat(store.deleted).containsExactly("r1" to listOf("/old/drop.jpg"))
        // pictures now exist, so "pictures unavailable" no longer applies
        assertThat(repo.recipes.single().picturesFailed).isFalse()
    }

    @Test
    fun `picturesFailed stays while the recipe still has no pictures`() = runTest {
        val repo = FakeRecipeRepository(listOf(saved))
        useCase(repo)(editDraft(lines("a", sourceIndexed = true)))
        assertThat(repo.recipes.single().pictures).isEmpty()
        assertThat(repo.recipes.single().picturesFailed).isTrue()
    }

    @Test
    fun `invalid draft persists nothing`() = runTest {
        val repo = FakeRecipeRepository()
        val result = useCase(repo)(createDraft(title = "", ingredients = lines(""), steps = lines("")))
        assertThat(result).isEqualTo(SaveResult.Invalid(setOf(EditorError.TitleRequired, EditorError.NeedContent)))
        assertThat(repo.recipes).isEmpty()
    }

    @Test
    fun `repository failure becomes Failed`() = runTest {
        val failing = object : FakeRecipeRepositoryDelegate() {
            override suspend fun upsert(recipe: ClippedRecipe) = throw IllegalStateException("disk full")
        }
        val result = SaveRecipeUseCase(failing, TempDirPictureStore(tmp.newFolder()), TestDispatchers(UnconfinedTestDispatcher()), { "x" }, { 1L })(createDraft())
        assertThat(result).isInstanceOf(SaveResult.Failed::class.java)
    }

    @Test
    fun `editing an unknown recipe fails`() = runTest {
        val result = useCase(FakeRecipeRepository())(editDraft(lines("a")))
        assertThat(result).isInstanceOf(SaveResult.Failed::class.java)
    }

    /** Fake repository whose `upsert` the test overrides. */
    private open class FakeRecipeRepositoryDelegate(
        private val inner: FakeRecipeRepository = FakeRecipeRepository(),
    ) : com.llmlocal.recipekeeper.recipes.domain.RecipeRepository by inner

    /** Real moves in a temp dir; records `delete` calls instead of touching files. */
    private class TempDirPictureStore(private val root: File) : RecipePictureStore {
        val deleted = mutableListOf<Pair<String, List<String>>>()
        override fun dirFor(recipeId: String) = File(root, recipeId)
        override fun moveIn(recipeId: String, files: List<File>): List<String> {
            val dir = dirFor(recipeId).apply { mkdirs() }
            return files.mapIndexed { i, f ->
                File(dir, "%03d.jpg".format(i + 1)).also { f.renameTo(it) }.absolutePath
            }
        }
        override fun delete(recipeId: String, paths: List<String>) { deleted += recipeId to paths }
        override fun deleteAll(recipeId: String) { dirFor(recipeId).deleteRecursively() }
    }
}
