package com.llmlocal.recipekeeper.clip

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.testing.loadJsonLd
import com.llmlocal.recipekeeper.testing.testDurationParser
import org.junit.Test

class SchemaOrgRecipeMapperTest {

    private val mapper = SchemaOrgRecipeMapper(testDurationParser)
    private val url = "https://www.example.com/recipes/banana-bread"

    @Test
    fun `single recipe with ImageObject and HowToSteps maps every field`() {
        val recipe = mapper.map(loadJsonLd("single_recipe"), url, id = "r1", clippedAt = 42L)!!

        assertThat(recipe.id).isEqualTo("r1")
        assertThat(recipe.title).isEqualTo("Classic Banana Bread")
        assertThat(recipe.description).isEqualTo("Moist & easy banana bread with a crackly top.")
        assertThat(recipe.servings).isEqualTo("1 loaf (10 slices)")
        assertThat(recipe.prepMinutes).isEqualTo(15)
        assertThat(recipe.cookMinutes).isEqualTo(60)
        assertThat(recipe.totalMinutes).isEqualTo(75) // prep + cook, no totalTime
        assertThat(recipe.ingredients).hasSize(8)
        assertThat(recipe.ingredients.first()).isEqualTo("3 ripe bananas, mashed")
        assertThat(recipe.steps).containsExactly(
            "Preheat the oven to 175°C and butter a loaf pan.",
            "stir the melted butter into the mashed bananas, then mix in the sugar, egg and vanilla.",
            "Sprinkle the baking soda and salt over the mixture and stir in. Add the flour and mix until just combined.",
            "Pour into the pan and bake for 60 minutes, until a tester comes out clean.",
        ).inOrder()
        assertThat(recipe.sourceUrl).isEqualTo(url)
        assertThat(recipe.sourceHost).isEqualTo("example.com")
        assertThat(recipe.pictures).isEmpty()
        assertThat(recipe.truncated).isFalse()
        assertThat(recipe.clippedAt).isEqualTo(42L)
        assertThat(mapper.imageUrls(loadJsonLd("single_recipe").raw)).containsExactly("https://cdn.example.com/banana-bread.jpg")
    }

    @Test
    fun `HowToSections are flattened, image arrays mixed, yield array prefers the unit entry`() {
        val node = loadJsonLd("howto_sections")
        val recipe = mapper.map(node, url)!!

        assertThat(recipe.title).isEqualTo("Lasagne al forno")
        assertThat(recipe.steps).containsExactly(
            "Brown the mince with the onion.",
            "Add the passata and simmer for 1 hour.",
            "Melt the butter, whisk in the flour, then the milk until thick.",
            "Layer and bake at 180°C for 40 minutes.",
        ).inOrder()
        assertThat(recipe.servings).isEqualTo("6 servings")
        assertThat(recipe.totalMinutes).isEqualTo(150)
        assertThat(recipe.prepMinutes).isNull()
        assertThat(mapper.imageUrls(node.raw)).containsExactly(
            "https://cdn.example.com/lasagne-1x1.jpg",
            "https://cdn.example.com/lasagne-4x3.jpg",
            "https://cdn.example.com/lasagne-16x9.jpg",
        ).inOrder()
    }

    @Test
    fun `Text instructions split on newlines, legacy ingredients key, QuantitativeValue yield, odd ISO durations`() {
        val recipe = mapper.map(loadJsonLd("text_instructions"), url)!!

        assertThat(recipe.ingredients).containsExactly("6 huevos", "4 patatas medianas", "1 cebolla", "Aceite de oliva", "Sal").inOrder()
        assertThat(recipe.steps).hasSize(3)
        assertThat(recipe.steps[1]).isEqualTo("Freír a fuego medio con la cebolla hasta que estén tiernas.")
        assertThat(recipe.servings).isEqualTo("4 raciones")
        assertThat(recipe.prepMinutes).isEqualTo(20)
        assertThat(recipe.cookMinutes).isEqualTo(25)
        assertThat(recipe.totalMinutes).isEqualTo(45)
    }

    @Test
    fun `Text array with inline numbering is stripped, numeric yield rendered`() {
        val recipe = mapper.map(loadJsonLd("legacy_text_array"), url)!!

        assertThat(recipe.steps).containsExactly(
            "Halve and pit the avocados; scoop into a bowl.",
            "Mash with the lime juice.",
            "Fold in the onion and season.",
        ).inOrder()
        assertThat(recipe.servings).isEqualTo("4")
        assertThat(recipe.totalMinutes).isEqualTo(10)
    }

    @Test
    fun `ItemList instructions, duplicated step names, newline-separated ingredient string, data URLs dropped`() {
        val node = loadJsonLd("itemlist_instructions")
        val recipe = mapper.map(node, url)!!

        assertThat(recipe.ingredients).containsExactly("1/2 cup rolled oats", "1/2 cup milk", "1 tbsp chia seeds", "1 tsp honey").inOrder()
        assertThat(recipe.steps).containsExactly("Combine everything in a jar.", "Chill overnight.").inOrder()
        assertThat(mapper.imageUrls(node.raw)).isEmpty()
        assertThat(recipe.servings).isNull()
        assertThat(recipe.totalMinutes).isNull()
    }

    @Test
    fun `node without ingredients or instructions maps to null`() {
        assertThat(mapper.map(loadJsonLd("no_recipe_content"), url)).isNull()
    }

    @Test
    fun `missing name falls back to Untitled recipe`() {
        val node = loadJsonLd("legacy_text_array").also { it.raw.remove("name") }
        assertThat(mapper.map(node, url)!!.title).isEqualTo("Untitled recipe")
    }
}
