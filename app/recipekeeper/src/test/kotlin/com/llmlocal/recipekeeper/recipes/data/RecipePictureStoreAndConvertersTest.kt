package com.llmlocal.recipekeeper.recipes.data

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecipePictureStoreAndConvertersTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `moveIn numbers files, skips missing sources, deleteAll removes the folder`() {
        val store = FileRecipePictureStore(temp.newFolder("root"))
        val a = temp.newFile("hero.jpeg").apply { writeText("a") }
        val b = temp.newFile("noext").apply { writeText("b") }
        val missing = File(temp.root, "missing.jpg")

        val paths = store.moveIn("r1", listOf(a, missing, b))

        assertThat(paths.map { File(it).name }).containsExactly("001.jpeg", "002.jpg").inOrder()
        assertThat(paths.all { File(it).isFile }).isTrue()
        assertThat(a.exists()).isFalse()
        assertThat(store.dirFor("r1").listFiles()!!.size).isEqualTo(2)

        store.deleteAll("r1")
        assertThat(store.dirFor("r1").exists()).isFalse()
        store.deleteAll("r1") // idempotent
    }

    @Test
    fun `moveIn into a non-empty folder numbers after the highest file`() {
        val store = FileRecipePictureStore(temp.newFolder("root"))
        store.moveIn("r1", listOf(temp.newFile("a.jpg"), temp.newFile("b.jpg"), temp.newFile("c.jpg")))
        store.delete("r1", listOf(File(store.dirFor("r1"), "002.jpg").absolutePath)) // leaves a gap: 001, 003

        val paths = store.moveIn("r1", listOf(temp.newFile("d.png"), temp.newFile("e.jpg")))

        assertThat(paths.map { File(it).name }).containsExactly("004.png", "005.jpg").inOrder()
        assertThat(store.dirFor("r1").list()!!.toList()).containsExactly("001.jpg", "003.jpg", "004.png", "005.jpg")
    }

    @Test
    fun `delete removes only files inside the recipe folder`() {
        val store = FileRecipePictureStore(temp.newFolder("root"))
        val own = store.moveIn("r1", listOf(temp.newFile("a.jpg"), temp.newFile("b.jpg")))
        val other = store.moveIn("r2", listOf(temp.newFile("c.jpg"))).single()
        val foreign = temp.newFile("foreign.jpg")
        val traversal = File(store.dirFor("r1"), "../../${foreign.name}").path

        store.delete("r1", listOf(own[0], other, foreign.absolutePath, traversal, File(store.dirFor("r1"), "missing.jpg").path))

        assertThat(File(own[0]).exists()).isFalse()
        assertThat(File(own[1]).exists()).isTrue()
        assertThat(File(other).exists()).isTrue()
        assertThat(foreign.exists()).isTrue()
        store.deleteAll("r1")
        assertThat(store.dirFor("r1").exists()).isFalse()
        assertThat(File(other).exists()).isTrue()
    }

    @Test
    fun `converters round-trip lists and sets, tolerate garbage`() {
        val c = RecipeKeeperConverters()
        val list = listOf("2 cups \"00\" flour", "1 tsp salt, plus more", "")
        assertThat(c.jsonToStringList(c.stringListToJson(list))).isEqualTo(list)
        assertThat(c.jsonToStringList(c.stringListToJson(emptyList()))).isEmpty()
        assertThat(c.jsonToStringList("not json")).isEmpty()

        assertThat(c.jsonToIntSet(c.intSetToJson(setOf(3, 1, 2)))).isEqualTo(setOf(1, 2, 3))
        assertThat(c.intSetToJson(setOf(3, 1))).isEqualTo("[1,3]")
        assertThat(c.jsonToIntSet("[")).isEmpty()
    }

    @Test
    fun `entity mapping round-trips a ClippedRecipe`() {
        val recipe = ClippedRecipe(
            id = "id", title = "T", description = "d", servings = "4", prepMinutes = 5, cookMinutes = 10, totalMinutes = 15,
            ingredients = listOf("a", "b"), steps = listOf("s1"), notes = "n", sourceUrl = "https://x.example.com/r",
            sourceHost = "x.example.com", pictures = listOf("/p/001.jpg", "/p/002.jpg"), truncated = true, clippedAt = 7L,
            checkedIngredients = setOf(1),
        )
        val entity = recipe.toEntity(deletedAt = 123L)
        assertThat(entity.deletedAt).isEqualTo(123L)
        val pictures = recipe.toPictureEntities()
        assertThat(pictures.map { it.position }).containsExactly(0, 1).inOrder()

        val back = RecipeWithPictures(entity, pictures.reversed()).toDomain()
        assertThat(back).isEqualTo(recipe)

        val row = RecipeSummaryRow("id", "T", "x.example.com", 15, "/p/001.jpg", true, 7L, isFavourite = true, origin = "CREATED").toDomain()
        assertThat(row.heroPicture).isEqualTo("/p/001.jpg")
        assertThat(row.title).isEqualTo("T")
        assertThat(row.isFavourite).isTrue()
        assertThat(row.origin).isEqualTo(RecipeOrigin.CREATED)
    }
}
