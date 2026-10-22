package com.llmlocal.recipekeeper.clip.domain.detection

/**
 * What the fast detector already knows, so learning does not re-learn it.
 * Mirrors the word lists in `RecipeKeeperClipConfig`; keep both in step
 * (they are lower-cased here).
 */
object BuiltInVocabulary {
    val units: Set<String> = setOf(
        "g", "gr", "gram", "grams", "kg", "mg", "ml", "l", "dl", "cl", "cup", "cups", "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds", "pinch", "dash", "clove", "cloves", "slice", "slices", "can", "cans", "stick", "sticks",
        "bunch", "handful", "piece", "pieces",
        "el", "tl", "tasse", "tassen", "prise", "stück", "becher", "cuillère", "cuillere", "cuillères", "cuilleres", "tasses", "pincée", "gousse", "gousses",
        "cucchiaio", "cucchiai", "cucchiaino", "cucchiaini", "tazza", "tazze", "pizzico", "spicchio", "spicchi",
        "grs", "gramos", "litro", "litros", "taza", "tazas", "cucharada", "cucharadas", "cda", "cdas", "cucharadita", "cucharaditas", "cdta", "cdtas",
        "diente", "dientes", "rebanada", "rebanadas", "lata", "latas", "unidad", "unidades", "ud", "uds", "pieza", "piezas", "manojo", "puñado",
        "vaso", "vasos", "vasito", "vasitos", "chorrito", "pizca",
        "litre", "litres", "tassa", "cullerada", "cullerades", "culleradeta", "culleradetes", "pessic", "gra", "grans", "llesca", "llesques",
        "llauna", "llaunes", "unitat", "unitats", "peça", "peces", "manat", "grapat", "got", "gots", "raig",
        "c/s", "c/c", "c.s.", "c.p.",
        "γρ", "γρ.", "γραμμάρια", "γραμμάριο", "γραμ.", "κιλό", "κιλά", "λίτρο", "λίτρα", "φλ.", "φλιτζάνι", "φλιτζάνια", "κ.σ.", "κουταλιά", "κουταλιές",
        "κ.γ.", "κουταλάκι", "κουταλάκια", "πρέζα", "σκελίδα", "σκελίδες", "φέτα", "φέτες", "κονσέρβα", "κονσέρβες", "τεμ.", "τεμάχιο", "τεμάχια",
        "ματσάκι", "χούφτα", "ποτήρι", "ποτήρια",
    )

    val headings: Set<String> = setOf(
        "ingredients", "ingredient", "ingredientes", "zutaten", "ingrédients", "ingredienti", "υλικά", "υλικα", "συστατικά", "συστατικα",
        "instructions", "instruction", "method", "directions", "steps", "preparation", "procedure", "how to make",
        "instrucciones", "preparación", "preparacion", "elaboración", "elaboracion", "pasos", "paso a paso", "modo de preparación", "modo de preparacion",
        "procedimiento", "zubereitung", "anleitung", "schritte", "préparation", "étapes", "etapes", "preparazione", "procedimento",
        "elaboració", "preparació", "pas a pas", "com es fa", "instruccions", "procediment",
        "εκτέλεση", "εκτελεση", "διαδικασία", "διαδικασια", "οδηγίες", "οδηγιες", "βήματα", "βηματα", "παρασκευή", "παρασκευη", "μέθοδος", "μεθοδος",
        "notes", "note", "tips", "tip", "notas", "consejos", "hinweise", "tipps", "remarques", "conseils", "consells", "σημειώσεις", "σημειωσεις", "συμβουλές", "συμβουλες",
    )
}
