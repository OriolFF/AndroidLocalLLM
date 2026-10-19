package com.llmlocal.recipekeeper.recipes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary

/**
 * Room row for a clipped recipe — the full `ClippedRecipe` minus the
 * picture list, which lives in [PictureEntity] (one row per picture so the
 * library summary can pick the hero with a sub-select and the sink can
 * replace the list atomically).
 *
 * `ingredients`, `steps`, `checkedIngredients` and `pictureCandidates` are
 * JSON-array columns (see [RecipeKeeperConverters]); storing ingredients as
 * JSON keeps the `LIKE` search in SQL without an extra table. `deletedAt`
 * is the soft-delete flag used by undo-delete (`null` = live).
 * `picturesFailed` + `pictureCandidates` back "Pictures unavailable / Retry
 * pictures" on the detail screen (WS11).
 */
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["clippedAt"]), Index(value = ["deletedAt"]), Index(value = ["sourceUrl"])],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val servings: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val totalMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<String>,
    val notes: String?,
    val sourceUrl: String,
    val sourceHost: String,
    val truncated: Boolean,
    val clippedAt: Long,
    val checkedIngredients: Set<Int>,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val picturesFailed: Boolean = false,
    @ColumnInfo(defaultValue = "[]") val pictureCandidates: List<String> = emptyList(),
)

/**
 * One locally stored picture of a recipe. [position] 0 is the hero. Rows
 * cascade-delete with their recipe.
 *
 * @property path absolute local file path under `filesDir/recipes/<id>/`
 */
@Entity(
    tableName = "pictures",
    primaryKeys = ["recipeId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recipeId"])],
)
data class PictureEntity(
    val recipeId: String,
    val position: Int,
    val path: String,
)

/** A recipe row joined with its ordered pictures (Room `@Relation`). */
data class RecipeWithPictures(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val pictures: List<PictureEntity>,
)

/** Projection returned by the list / search queries. */
data class RecipeSummaryRow(
    val id: String,
    val title: String,
    val sourceHost: String,
    val totalMinutes: Int?,
    @ColumnInfo(name = "heroPicture") val heroPicture: String?,
    val truncated: Boolean,
    val clippedAt: Long,
)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

fun ClippedRecipe.toEntity(deletedAt: Long? = null): RecipeEntity = RecipeEntity(
    id = id,
    title = title,
    description = description,
    servings = servings,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    totalMinutes = totalMinutes,
    ingredients = ingredients,
    steps = steps,
    notes = notes,
    sourceUrl = sourceUrl,
    sourceHost = sourceHost,
    truncated = truncated,
    clippedAt = clippedAt,
    checkedIngredients = checkedIngredients,
    deletedAt = deletedAt,
    picturesFailed = picturesFailed,
    pictureCandidates = pictureCandidates,
)

fun ClippedRecipe.toPictureEntities(): List<PictureEntity> =
    pictures.mapIndexed { index, path -> PictureEntity(recipeId = id, position = index, path = path) }

fun RecipeWithPictures.toDomain(): ClippedRecipe = ClippedRecipe(
    id = recipe.id,
    title = recipe.title,
    description = recipe.description,
    servings = recipe.servings,
    prepMinutes = recipe.prepMinutes,
    cookMinutes = recipe.cookMinutes,
    totalMinutes = recipe.totalMinutes,
    ingredients = recipe.ingredients,
    steps = recipe.steps,
    notes = recipe.notes,
    sourceUrl = recipe.sourceUrl,
    sourceHost = recipe.sourceHost,
    pictures = pictures.sortedBy { it.position }.map { it.path },
    truncated = recipe.truncated,
    clippedAt = recipe.clippedAt,
    checkedIngredients = recipe.checkedIngredients,
    picturesFailed = recipe.picturesFailed,
    pictureCandidates = recipe.pictureCandidates,
)

fun RecipeSummaryRow.toDomain(): RecipeSummary = RecipeSummary(
    id = id,
    title = title,
    sourceHost = sourceHost,
    totalMinutes = totalMinutes,
    heroPicture = heroPicture,
    truncated = truncated,
    clippedAt = clippedAt,
)
