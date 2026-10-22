package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class MultilingualQuantityTest {

    private val quantity = RecipeKeeperClipConfig.quantityLine

    private fun assertQuantity(vararg lines: String) = lines.forEach {
        assertWithMessage("quantity: $it").that(quantity.containsMatchIn(it)).isTrue()
    }

    private fun assertNotQuantity(vararg lines: String) = lines.forEach {
        assertWithMessage("not quantity: $it").that(quantity.containsMatchIn(it)).isFalse()
    }

    @Test
    fun `spanish units`() = assertQuantity(
        "200 g de harina", "200 gr harina", "200 grs de azúcar", "200 gramos de harina", "1 kg de patatas", "250 ml de leche",
        "1 litro de agua", "2 litros de caldo", "1 taza de arroz", "2 cucharadas de aceite", "1 cda de azúcar", "2 cdas. de aceite",
        "1 cucharadita de sal", "1 cdta de levadura", "1 pizca de sal", "2 dientes de ajo", "2 rebanadas de pan", "1 lata de atún",
        "3 unidades de huevo", "2 ud de cebolla", "2 uds. de tomate", "2 piezas de pan", "1 manojo de perejil", "1 puñado de nueces",
        "1 vaso de leche", "1 chorrito de vinagre", "½ cebolla", "1/2 kg de lentejas",
    )

    @Test
    fun `catalan units`() = assertQuantity(
        "200 g de farina", "1 l de llet", "2 litres d'aigua", "1 tassa de sucre", "2 tasses de farina", "1 cullerada d'oli",
        "2 cullerades soperes d'oli", "1 culleradeta de sal", "2 culleradetes de llevat", "1 pessic de sal", "3 grans d'all",
        "1 gra d'all", "2 llesques de pa", "1 llauna de tonyina", "2 llaunes de tomàquet", "3 unitats de ou", "2 peces de pa",
        "1 manat de julivert", "1 grapat de nous", "1 got de llet", "2 gots d'aigua", "1 raig d'oli", "1 c/s d'oli", "1 c/c de sal",
        "2 grams de sucre",
    )

    @Test
    fun `greek units, matched unicode-aware and case-insensitively`() = assertQuantity(
        "200 γρ. αλεύρι", "200 γρ αλεύρι", "200 γραμμάρια ζάχαρη", "200 γραμ. βούτυρο", "1 κιλό πατάτες", "2 κιλά ντομάτες",
        "250 ml γάλα", "1 λίτρο νερό", "2 λίτρα ζωμό", "1 φλ. ζάχαρη", "1 φλιτζάνι ρύζι", "2 φλιτζάνια αλεύρι", "2 κ.σ. λάδι",
        "2 κουταλιές της σούπας λάδι", "1 κουταλιά σούπας ξύδι", "1 κ.γ. αλάτι", "1 κουταλάκι γλυκού αλάτι", "2 κουταλάκια του γλυκού ζάχαρη",
        "1 πρέζα αλάτι", "2 σκελίδες σκόρδο", "1 σκελίδα σκόρδο", "2 φέτες ψωμί", "1 κονσέρβα τόνο", "2 τεμ. κρεμμύδια",
        "1 τεμάχιο κρεμμύδι", "1 ματσάκι μαϊντανό", "1 χούφτα καρύδια", "1 ποτήρι γάλα", "2 ΚΙΛΑ ΝΤΟΜΑΤΕΣ", "3 αυγά",
    )

    @Test
    fun `number words and abbreviations`() = assertQuantity(
        "un diente de ajo", "una cucharada de miel", "una lata de maíz", "dos dientes de ajo", "tres cucharadas de aceite",
        "un got de llet", "una pessic de sal", "dues cullerades d'oli", "ένα φλιτζάνι ζάχαρη",
        "μία κ.σ. λάδι", "δύο σκελίδες σκόρδο", "τρία φλιτζάνια νερό", "1 c.s. de aceite", "1 c.p. de sal", "2 c/s d'oli",
    )

    @Test
    fun `quantity after the name`() = assertQuantity(
        "farina, 200 g", "Farina 200 g", "- sucre, 150 g", "harina, 200 g", "αλεύρι 200 γρ.", "ζάχαρη, 150 γρ", "llet 1 l", "aceite de oliva, 2 cucharadas",
    )

    @Test
    fun `prose and steps are not quantities`() = assertNotQuantity(
        "Una vez que esté listo, sirve.", "Mezclar todo bien.", "Preheat the oven.", "Σερβίρουμε ζεστό.", "Barregeu-ho bé.",
        "Publicado el día de hoy", "un poco de sal",
    )
}

class MultilingualGrammarTest {

    private val parser = RecipeKeeperParser()

    @Test
    fun `greek section headers are accepted`() {
        val recipe = parser.parse(
            "Τίτλος: Μπισκότα\nΜερίδες: 6 άτομα\nΥλικά:\n- 200 γρ. αλεύρι\n- 2 κ.σ. λάδι\nΕκτέλεση:\n1. Ανακατεύουμε.\n2. Ψήνουμε.\nΣημειώσεις:\nΦυλάξτε σε βάζο.",
        )!!
        com.google.common.truth.Truth.assertThat(recipe.title).isEqualTo("Μπισκότα")
        com.google.common.truth.Truth.assertThat(recipe.servings).isEqualTo("6 άτομα")
        com.google.common.truth.Truth.assertThat(recipe.ingredients).hasSize(2)
        com.google.common.truth.Truth.assertThat(recipe.steps).hasSize(2)
        com.google.common.truth.Truth.assertThat(recipe.notes).isEqualTo("Φυλάξτε σε βάζο.")
    }

    @Test
    fun `catalan and spanish section headers are accepted`() {
        val ca = parser.parse("Títol: Coca\nRacions: 4 persones\nIngredients:\n- 200 g de farina\n- 1 pessic de sal\nPas a pas:\n1. Barregeu.\n2. Courec.\nConsells:\nGuardeu-la tapada.")!!
        com.google.common.truth.Truth.assertThat(ca.steps).hasSize(2)
        com.google.common.truth.Truth.assertThat(ca.servings).isEqualTo("4 persones")
        com.google.common.truth.Truth.assertThat(ca.notes).isEqualTo("Guardeu-la tapada.")
        val es = parser.parse("Título: Tortilla\nPersonas: 4\nIngredientes:\n- 4 huevos\n- 1 pizca de sal\nElaboración:\n1. Bate.\n2. Cuaja.")!!
        com.google.common.truth.Truth.assertThat(es.servings).isEqualTo("4")
        com.google.common.truth.Truth.assertThat(es.steps).hasSize(2)
    }
}
