# UX Spec — Recipe Keeper features: Favourites, Edit recipe, Create recipe

Produced 2026-10-19 by the project `android-ux` agent persona (brief `docs/plans/briefs/features-ux.md`) from the repo state at `3f424c8` (branch `recipekeeper/main` after the R1–R4 clean-architecture refactor and the S1–S4 strings migration). Companion to `recipekeeper-features-plan.md` (implementation streams). Extends `recipekeeper-ux-spec.md` §5 (library/detail) and §7 (labels); nothing in the browser/extraction flow changes.

Facts relied on (from the code, not the older spec):
- Library = `AppScaffold` + `LazyVerticalGrid(Adaptive(168.dp))`, FAB `ExtendedFloatingActionButton` "Open browser" in the scaffold slot, top-bar actions Search + Models, long-press tile menu (Open original / Share / Delete), search field pinned above the grid, no-model `GradientHero`, undo-delete snackbar above the FAB. `LibraryState(recipes, query, searchActive, isLoading, modelStatus)`.
- Detail = own `Scaffold`, collapsing 16:10 hero with scrim + transparent `TopAppBar` (`ArrowBack`, `Share`, `MoreVert` → Open original / Re-extract / Keep screen on / Delete), meta chips, source `ListItem`, ingredients `toggleable` rows, steps with `NumberBadge`, pager, notes; two panes at ≥ 600dp. `RecipeDetailState(recipe, isLoading, currentStep, keepScreenOn, showDeleteDialog, retryingPictures)`.
- `ClippedRecipe(id, title, description, servings: String?, prep/cook/totalMinutes, ingredients, steps, notes, sourceUrl, sourceHost, pictures: List<path>, truncated, clippedAt, checkedIngredients: Set<Int>, picturesFailed, pictureCandidates)`; `RecipeSummary(id, title, sourceHost, totalMinutes, heroPicture, truncated, clippedAt)`.
- Room `recipes` (v1, shipped on the QA emulator and the real phone, so **v2 needs a migration**) + `pictures(recipeId, position, path)`; `RecipeDao.observeSummaries()/searchSummaries(q)` order by `clippedAt DESC`; `RoomRecipeRepository.upsert` keeps `deletedAt`; `FileRecipePictureStore.moveIn` numbers files `001.jpg…` in a folder per recipe.
- Labels: `RecipeKeeperLibraryLabels` (no defaults) built by `rememberRecipeKeeperLibraryLabels()` from `strings_recipekeeper.xml`; `TestRecipeKeeperLibraryLabels` fixture in tests; the app can override feature strings by name (`strings_webclipper_overrides.xml`).
- Navigation 2.10 string routes in `app/RecipeKeeperNavHost.kt` (`library`, `browser?url=`, `extract/{candidateId}`, `recipe/{recipeId}`, `modelManagement`); one `SnackbarHostState`; root `scope` for snackbars that must outlive a popped screen.
- Shared primitives available: `AppScaffold`, `GradientHero`, `EmptyState`, `SectionHeader(trailing)`, `NumberBadge(selected)`, `StatusDot`, `SkeletonLine`, `AppLoadingIndicator`. `material-icons-extended` is on the classpath. Material 3 1.4.0: `FilterChip`, `IconToggleButton`, `SingleChoiceSegmentedButtonRow` are stable; `FloatingActionButtonMenu` is still behind `ExperimentalMaterial3ExpressiveApi`.
- `WebClipConfig.maxPictures = 8`; pictures are resized to ≤ 1280 px long edge JPEG q85 by `ImageDownloader`.
- Skills applied: `edge-to-edge` (Scaffold `safeDrawing` + `contentPadding`, never `imePadding()` on top of it, `adjustResize` already in the manifest), `adaptive` (600dp two-pane, `GridCells.Adaptive`), `navigation-3` (project stays on Navigation 2.10; Nav 3 is not proposed here — the three new destinations fit the existing string-route graph and a migration would be a separate stream), `styles` (not applicable: experimental, Material components only).

---

## 0. Decisions at a glance

| # | Decision | Why |
|---|---|---|
| D1 | Favourites filter = a **`FilterChip` row** under the top bar ("Favourites"), not a segmented control or a tab. | It composes with search (AND), scales to the next filters this plan introduces (`origin` = "Created by you") and to tags later, needs no navigation change, and `FilterChip` carries selected semantics for TalkBack. A segmented control models mutually exclusive *views*, which is wrong once search is involved; a tab would duplicate the grid and split search state. |
| D2 | Heart lives in **three places**: an always-visible toggle on each library tile, a top-bar toggle on the detail, and a long-press menu item on the tile. | One tap from the grid is the main gesture; the menu item covers users who cannot hit a 48dp overlay on a photo (switch access, screen readers prefer list items). |
| D3 | Library order stays **newest-added first** in both views; favourites are not pinned to the top of "All". | Pinning would make tiles jump when a heart is tapped; the heart on every tile already shows the state. |
| D4 | Edit entry = **pencil action in the detail top bar** (`[←] … [♡] [✎] [⋮]`) and an "Edit" item in the tile long-press menu. **Share moves into the overflow.** No FAB on the detail. | Fixing an extraction is the core reason for the feature; three visible actions is M3's ceiling; a FAB would cover the steps a cook is reading and fight the collapsing hero. Share stays one tap further away (first overflow item). If the user prefers Share visible, swap the two — one-line change, flagged in §9. |
| D5 | **One editor screen** for create and edit (`RecipeEditorScreen`, mode by nullable id). | Same fields, same validation, same picture handling; the only differences are the title, the source row and the post-save navigation. |
| D6 | Create entry = **"New recipe" action in the library top bar** (`[Search] [+ New recipe] [Models]`) plus a secondary "Write a recipe" button in the onboarding empty state. **The FAB stays "Open browser".** | Clipping is the product's primary action and must stay one tap; create is secondary. The M3 `FloatingActionButtonMenu` would be the nicer speed-dial but is still an experimental Expressive API, and a "pick an action" bottom sheet behind the FAB would add a tap to every clip. Revisit (Phase 2) when the FAB menu is stable. |
| D7 | Minimum viable recipe = **title + at least one ingredient or one step**. | A shopping-list-only recipe and a technique-only note are both real use cases; forcing both would make the form nag. |
| D8 | Saving an edit keeps the **id and `clippedAt`**, sets **`editedAt = now`**, clears `truncated` (the user reviewed it), remaps `checkedIngredients` by row identity (kept rows keep their tick even if the text changed; removed rows drop; new rows unticked). | Predictable and loses nothing the cook did; `clippedAt` keeps the library order stable (D3). |
| D9 | Pictures in the editor: **keep / remove existing, add from the system photo picker** (`PickVisualMedia`, up to `maxPictures = 8`), first tile = cover, "Use as cover" reorders. **No camera capture in v1.** | The photo picker already shows the camera roll; `TakePicture` needs a `FileProvider` + manifest config and is listed in Phase 2 with its mechanics. Removed files are deleted only on Save so Discard restores them. |
| D10 | Created recipes carry `origin = CREATED`, empty `sourceUrl`/`sourceHost`; the library shows "Created by you" where the host goes, the detail shows a "Created by you · Created <date>" row and hides Open original / Re-extract; Share sends the full recipe text instead of title + URL. | A URL-less share would be useless; everything else follows from "there is no source". |
| D11 | Unsaved changes: system back and the Close button both open **"Discard changes?"** when the draft differs from the loaded one; Save is always enabled and validates on tap with inline errors. | Disabled Save buttons leave the user guessing; inline errors plus a scroll-to-first-error say exactly what is missing. |
| D12 | No new shared `:core:designsystem` primitives. The reorderable editable list and the picture strip stay in the app (`recipes/presentation/editor/components/`). | They are recipe-shaped (ingredients/steps/pictures); promoting them later is cheap, while touching the shared module now adds merge risk for three parallel streams (lesson from WS3). |

---

## 1. Favourites

### 1.1 Where the heart is

**Library tile (`RecipeTile`)** — a 48dp `IconToggleButton` overlaid **top-end of the hero image**, drawn on a 36dp disc of `Color.Black.copy(alpha = 0.35f)` so the white glyph reads on photos *and* on the light gradient placeholder. `Icons.Filled.Favorite` when favourite, `Icons.Outlined.FavoriteBorder` otherwise; glyph colour white in both states (state is carried by fill + semantics, not by colour). The existing `IncompleteBadge` is top-start, so the two never overlap. Tap toggles immediately (optimistic; the repository emission confirms). No snackbar. The heart is placed **outside the card's merged semantics node** (as a sibling in the tile's outer `Box`, like the `DropdownMenu`) so TalkBack gets two nodes: the tile and the toggle.

**Detail top bar** — `IconToggleButton` between the back arrow and the pencil: `[←]                 [♡] [✎] [⋮]`. Tint follows the bar's existing `overImage` logic (white over the hero, `onSurface` after it collapses). Filled when favourite. The state comes from the observed recipe, so a toggle from another screen shows up live.

**Tile long-press menu** — first item "Add to favourites" / "Remove from favourites" with the matching icon, above Edit / Open original / Share / Delete (final menu order: Favourite toggle, Edit, Share, Open original, Delete; "Open original" hidden for created recipes).

### 1.2 Filtering (D1)

```
┌ AppScaffold top bar: "Your recipes"        [🔍] [＋] [🧠] ┐
│ (search field when active)                                 │
│ ( ♥ Favourites )                              ← chip row   │
├────────────────────────────────────────────────────────────┤
│  tile   tile                                               │
│  tile   tile                                               │
└──────────────────────────────── [✂ Open browser] FAB ──────┘
```

- `LibraryFilterRow`: a horizontally scrollable `Row` (16dp start/end padding, 8dp spacing, 48dp tall) holding one `FilterChip(selected = state.filter.favouritesOnly, label = labels.favouritesFilter, leadingIcon = Favorite/FavoriteBorder)`. Pinned between the search field and the grid (same place the search field lives), **hidden while the onboarding empty state shows** (nothing to filter) and while loading.
- Filter and search are ANDed: `ObserveLibraryUseCase(query, LibraryFilter(favouritesOnly))` → repository `observeAll(favouritesOnly)` / `search(query, favouritesOnly)`.
- Selecting the chip does not reorder: newest-added first within the filtered set (D3).
- The chip survives search open/close; closing search clears the query only. The filter is ViewModel state (not persisted across process death — the library is the start destination, so a fresh start shows All).

### 1.3 States

| State | Rendering |
|---|---|
| Favourites on, none yet | `EmptyState(Icons.Outlined.FavoriteBorder, labels.favouritesEmptyTitle, labels.favouritesEmptyMessage, actionLabel = labels.showAllRecipes)` → clears the filter. Spans all columns. |
| Favourites on + search on, no match | `EmptyState(SearchOff, labels.searchEmptyFavouritesTitle(q), actionLabel = labels.clearSearch)` (keeps the filter; the chip is visible to clear it). |
| Favourites on, model missing | The no-model `GradientHero` still renders above the grid (unchanged). |
| Toggle while the Favourites filter is on (un-heart) | The tile animates out (`animateItem()` on the grid items, 220 ms fade+shrink). No undo: one tap on the heart re-adds it. |
| Detail un-heart, back to a filtered library | The recipe is simply gone from the filtered grid; the chip stays selected. |

### 1.4 Persistence

- `recipes.isFavourite INTEGER NOT NULL DEFAULT 0` (+ index) — Room v2, see plan §2.
- `RecipeSummary.isFavourite`, `ClippedRecipe.isFavourite`.
- `RecipeRepository.setFavourite(id, value)`; `ToggleFavouriteUseCase(id, favourite)`. Toggling does **not** touch `editedAt` (a flag, not content).
- Re-clipping a page and choosing **Replace** keeps the favourite flag (the sink carries it over; plan F2).

### 1.5 TalkBack

- Tile heart: `IconToggleButton` → announced as "Favourite, checkbox, checked/not checked"; add `stateDescription = labels.favouriteStateOn / favouriteStateOff` so the state reads "In your favourites" instead of "checked". `contentDescription = labels.favourite`.
- Tile node: existing `recipeTileDescription(title, host)` plus `stateDescription = favouriteStateOn` when favourite, so the grid can be scanned without entering each heart.
- Detail heart: same semantics; the bar's `traversalIndex` order is back, heart, edit, more.
- Filter chip: M3 `FilterChip` already exposes `selected`; `contentDescription` not needed (label is the text).
- Menu item text switches between add/remove, no state needed.

---

## 2. Edit recipe

### 2.1 Entry points (D4)

- Detail top bar pencil (`Icons.Outlined.Edit`, `contentDescription = labels.editRecipe`) → `RecipeDetailIntent.Edit` → `RecipeDetailEffect.NavigateToEditor(id)` → `recipe/{id}/edit`.
- Tile long-press menu "Edit" → `LibraryIntent.EditRecipe(id)` → `LibraryEffect.NavigateToEditor(id)`.
- Detail overflow after the change: Share, Open original*, Re-extract from source*, Keep screen on, Delete (*hidden for created recipes).

### 2.2 Editor screen (`RecipeEditorScreen`, route `recipe/{recipeId}/edit` and `recipe/new`)

Own `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` so `innerPadding` already includes status bar, navigation bar **and IME**; the content list gets `contentPadding = innerPadding (+16dp sides)` and `Modifier.consumeWindowInsets(innerPadding)` — no `imePadding()` anywhere (edge-to-edge skill, "RIGHT" pattern 1). The manifest already has `adjustResize`.

```
┌ statusBars ─────────────────────────────────────────────┐
│ [✕]  Edit recipe                                 [Save] │  pinned TopAppBar; Save = TextButton (labelLarge)
├─────────────────────────────────────────────────────────┤
│ ┌────┐ ┌────┐ ┌ + ┐                                     │  Pictures strip (LazyRow, 96dp tiles, 8dp gap)
│ │ ▣✕ │ │  ✕ │ │   │   first tile carries a "Cover" pill │
│ └────┘ └────┘ └───┘                                     │
│ Title                                                   │  OutlinedTextField, required
│ Description                                             │  OutlinedTextField, 2–4 lines
│ [Servings   ] [Prep min ] [Cook min ] [Total min ]      │  FlowRow of 4 fields (2×2 at phone width)
│ ◉ Ingredients  — One per line. Paste a list to split.   │  SectionHeader + hint
│ ≡  [ 2 eggs                               ]  ✕          │  EditableLineRow
│ ≡  [ 100 g flour                          ]  ✕          │
│ [ + Add ingredient ]                                    │  full-width OutlinedButton
│ ◉ Steps                                                 │
│ ≡ ① [ Whisk the eggs…                     ]  ✕          │  NumberBadge + multi-line field
│ [ + Add step ]                                          │
│ ◉ Notes                                                 │  OutlinedTextField, multi-line
│ 🔗 Clipped from bbcgoodfood.com (read-only)             │  only for CLIPPED
└ innerPadding bottom (nav bar or IME) ───────────────────┘
```

**Top bar.** `TopAppBar(title = labels.editTitle / newTitle, navigationIcon = Close (labels.close), actions = TextButton(labels.save))`. `pinnedScrollBehavior` so Save is reachable with the keyboard open. While saving: the Save label swaps for a 20dp `CircularProgressIndicator` and both buttons are disabled.

**Fields.**

| Field | Component | Keyboard | Rules |
|---|---|---|---|
| Title | `OutlinedTextField`, single line | `Capitalization.Sentences`, `ImeAction.Next` | required, ≤ 200 chars; error `labels.titleRequired` as `supportingText` with `isError` |
| Description | `OutlinedTextField`, `minLines = 2, maxLines = 4` | Sentences, Next | optional, ≤ 1 000 |
| Servings | `OutlinedTextField` | `KeyboardType.Text`, Next | free text as the source writes it ("4", "6–8 cookies"), ≤ 40 |
| Prep / Cook / Total min | `OutlinedTextField` | `KeyboardType.Number`, Next | digits only, ≤ 4 digits; Total placeholder shows `labels.totalDerived(prep + cook)` when both are set and Total is blank; on save Total = typed ?: (prep + cook when **both** set) else null |
| Ingredients | `EditableLineList(section = Ingredients)` | Sentences, Next | one per row, `maxLines = 3`; blank rows dropped on save; ≤ 500 chars per row |
| Steps | `EditableLineList(section = Steps)` | Sentences, `ImeAction.Default` (Enter = newline inside a step) | `maxLines = 8`; blank rows dropped |
| Notes | `OutlinedTextField`, `minLines = 3` | Sentences, Default | optional, ≤ 4 000 |
| Source | `ListItem(Link, labels.sourceReadOnly(host), supporting = url)` | — | read-only for CLIPPED (the URL is the duplicate-check key and backs Open original / Re-extract); absent for CREATED |

**`EditableLineRow`** = `[≡ handle 48dp] [field weight 1] [✕ 48dp]` (steps insert a `NumberBadge(n)` between handle and field). `ImeAction.Next` on the **last** ingredient row adds a new row and focuses it; on any other row it moves to the next row. Empty rows are allowed while editing. "Add ingredient" / "Add step" = full-width `OutlinedButton` with `Icons.Outlined.Add`, 48dp, appends a row and focuses it (`RecipeEditorEffect.FocusLine`).

**Reorder.** Long-press the ≡ handle and drag (`detectDragGesturesAfterLongPress` on the handle, the row lifts with 4dp elevation and a `surfaceContainerHigh` background, other rows shift with `animateItem()`); dropping sends `MoveLine(section, from, to)`. Every handle also exposes two accessibility **custom actions** `labels.moveUp` / `labels.moveDown` (`semantics { customActions = … }`) so TalkBack, switch access and keyboard users reorder without dragging. The handle's `contentDescription = labels.reorderIngredient(n)` / `reorderStep(n)`.

**Multi-line paste splitting.** The ViewModel, not the composable, owns the rule: `LineChanged(section, key, text)` with a `\n` in `text` runs `SplitPastedLinesUseCase(text, section)`:
- Ingredients: split on every newline; trim; drop blanks; strip leading bullets `- * • ·` and numbering `1. 1) (1)`; the current row takes the first line, the others are inserted after it; focus moves to the last inserted row.
- Steps: if ≥ 2 lines start with a step marker (`1.`, `1)`, `Step 1`, `①`), split on the markers (keep multi-line bodies); else if the text has blank lines, split on blank lines; else keep it as **one** step (a single newline typed inside a step is legitimate).
- A pasted single-line value is just text.

**Pictures strip.** `LazyRow` of 96dp `Card` tiles (`shapes.medium`): existing pictures (Coil from the file), new pictures (Coil from the temp file), then the **Add tile** (dashed `outline` border, `Icons.Outlined.AddPhotoAlternate`, `labels.addPicture`; hidden when the strip holds 8). Each picture tile has a 24dp `Close` glyph inside a 48dp hit area at its top-end (`labels.removePicture(i, n)`), a "Cover" pill (`labels.coverBadge`, `tertiaryContainer`) on the first tile, and a tap menu with `labels.useAsCover` (moves it first). Add → `PickMultipleVisualMedia(maxItems = 8 − current)` when ≥ 2 slots remain, `PickVisualMedia` when 1 remains (the multiple contract requires `maxItems > 1`); result URIs go to `PicturesPicked(uris)`. The ViewModel imports each one through `AddRecipePictureUseCase` (copy + downscale to ≤ 1280 px long edge JPEG q85 into `cacheDir/recipe-editor/<session>/`) and shows a `SkeletonLine`-style placeholder tile per pending import; a failed import shows the snackbar `labels.pictureFailed` and no tile. Removing a tile only edits the draft; the files are deleted when Save commits (existing) or when the editor is discarded (new).

### 2.3 Validation

Runs on Save (and live afterwards, so errors clear as the user types):

| Rule | Where it shows |
|---|---|
| Title blank | `isError` + `supportingText = labels.titleRequired` on the Title field; the list scrolls to it and it takes focus |
| No non-blank ingredient **and** no non-blank step | `labels.needContent` in `error` colour under the Steps section header (`AnimatedVisibility`), the list scrolls to the Ingredients header |
| Minutes not 0–9999 | cannot happen with the digit filter; the ViewModel clamps |
| Over 8 pictures | cannot happen (picker capped); defensive snackbar `labels.picturesLimit(8)` |
| Repository failure on save | snackbar `labels.saveFailed`; the editor stays open with the draft |

### 2.4 Unsaved changes (D11)

- `isDirty = draft != initialDraft` (data-class equality; keys are stable so reorders count as changes).
- `BackHandler(enabled = isDirty)` and the Close button → `RequestClose` → when dirty `showDiscardDialog = true`, else `Closed`.
- `AlertDialog(title = labels.discardTitle, text = labels.discardMessageEdit / discardMessageNew, confirm = labels.discard in error colour, dismiss = labels.keepEditing)`. Confirm → `DiscardRecipeEditsUseCase` deletes the new temp pictures → `Closed` → `popBackStack()`.
- Predictive back: because the handler is enabled only when dirty, a clean editor gets the system's predictive-back animation; a dirty one intercepts and shows the dialog (accepted).
- Process death: the draft (every field, the line lists with keys, the temp picture paths) lives in `SavedStateHandle`; on restore the ViewModel rebuilds the state from it instead of reloading the recipe. Temp pictures live in `cacheDir`, which survives process death.

### 2.5 Save behaviour (D8)

`SaveRecipeUseCase(edit)`:
1. Validate (§2.3) → `SaveResult.Invalid(errors)`.
2. Edit mode: load the current `ClippedRecipe` (`repository.observe(id).first()`); build the new one with `id`, `clippedAt`, `isFavourite`, `origin`, `sourceUrl/Host`, `pictureCandidates` copied from it; `editedAt = clock()`; `truncated = false`; `checkedIngredients` = indices of kept rows whose `sourceIndex ∈ old checked`; `picturesFailed = old && pictures.isEmpty()`.
3. Pictures: new temp files are moved into `filesDir/recipes/<id>/` (numbered after the highest existing file so nothing is clobbered); files of removed pictures are deleted; the stored order is the strip order (cover first) via the `pictures` table `position`.
4. `repository.upsert(recipe)` (keeps `deletedAt` as today).
5. `SaveResult.Saved(id)` → effect `Saved(id, mode)`.

After save from an edit: `popBackStack()` to the detail (which observes the row and re-renders; `currentStep` is cleared if it no longer indexes a step) or to the library (snackbar `labels.changesSaved` from the root scope either way).

### 2.6 Keyboard, IME and edge-to-edge

- `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` → `innerPadding` includes the IME; `LazyColumn(contentPadding = innerPadding)` + `consumeWindowInsets(innerPadding)`; the focused field scrolls into view by itself (Compose text fields bring themselves into view inside a lazy list).
- The pinned top bar keeps Save visible with the keyboard open.
- Tapping outside a field does not clear focus (the cook may be looking at the keyboard); the Close/Save buttons work regardless.
- Dialogs (`AlertDialog`) are not full-screen, so no `decorFitsSystemWindows` work.

### 2.7 Adaptive (≥ 600dp)

Same breakpoint as the detail screen (`BoxWithConstraints`, `maxWidth >= 600.dp`): two independently scrolling `LazyColumn`s inside a `Row(widthIn(max = 1280.dp))` — left (weight 1): pictures, title, description, meta, notes, source; right (weight 1.1): ingredients, steps. Each list takes `contentPadding = innerPadding` so both clear the keyboard. Below 600dp the single column is capped at 720dp and centred (tall phones in landscape are ≥ 600dp wide and get the two panes). Drag-to-reorder stays inside one list, so the split is safe.

---

## 3. Create recipe

### 3.1 Entry points (D6)

- Library top bar: `IconButton(Icons.Outlined.Add, contentDescription = labels.newRecipe)` placed between Search and Models (search hides while active, as today) → `LibraryIntent.CreateRecipe` → `LibraryEffect.NavigateToEditor(null)` → `recipe/new`.
- Onboarding empty state: `EmptyState(... actionLabel = openBrowser)` followed by a `TextButton(labels.writeRecipe)` in the same grid item (the shared `EmptyState` is unchanged; the second button sits 8dp below it).
- The FAB and its "Open browser" label are untouched. Phase 2: replace FAB + top-bar action with `FloatingActionButtonMenu` (Clip from the web / Write a recipe) once it leaves `ExperimentalMaterial3ExpressiveApi`.

### 3.2 Create mode

- Same `RecipeEditorScreen`; title `labels.newTitle`; starts with one empty ingredient row and one empty step row so the shape is obvious; Title takes focus and opens the keyboard (`LaunchedEffect` + `FocusRequester`, only on first composition, not after rotation).
- No source row; no pictures required.
- Save: `id = UUID`, `origin = CREATED`, `clippedAt = now` (the column keeps its name; it means "added to the library"), `editedAt = null`, `sourceUrl = ""`, `sourceHost = ""`, `truncated = false`, `pictureCandidates = []`, `checkedIngredients = {}`, `isFavourite = false`.
- After save: `navigate("recipe/{id}") { popUpTo("library") }` so the editor leaves the back stack and back from the new recipe returns to the library. No snackbar (landing on the detail is the feedback).
- Discard dialog text: `labels.discardMessageNew` ("This recipe won't be saved."). A create editor with nothing typed is not dirty: back just pops.

### 3.3 How created recipes look (D10)

| Place | Clipped | Created |
|---|---|---|
| Tile meta | `host · 30 min` | `labels.createdByYou · 30 min` |
| Tile description | "title, from host" | `labels.recipeTileDescriptionCreated(title)` = "title, created by you" |
| Tile menu | Favourite, Edit, Share, Open original, Delete | Favourite, Edit, Share, Delete |
| Detail hero without picture | `GradientHero(title, host)` | `GradientHero(title, labels.createdByYou)` |
| Detail source row | `ListItem(Link, host, "Clipped 18 Oct 2026 · Edited 19 Oct 2026", Open original)` | `ListItem(EditNote, labels.createdByYou, "Created 19 Oct 2026 · Edited …")`, no trailing button |
| Detail overflow | Share, Open original, Re-extract, Keep screen on, Delete | Share, Keep screen on, Delete |
| Possibly-incomplete badge | when truncated | never |
| Share text | `title\nurl` | title, blank line, "Ingredients:" + `- line`s, blank line, "Steps:" + `1. line`s (`labels.shareCreatedTemplate`, bullets via `shareBullet` / `shareNumbered`) |
| Re-clip duplicate check | by `sourceUrl` | never matches (empty URL) |

---

## 4. Navigation map additions

| Destination | Route | Presentation | Arrives from |
|---|---|---|---|
| Editor (edit) | `recipe/{recipeId}/edit` | full screen, own `Scaffold` (§2.2) | detail pencil, tile menu Edit |
| Editor (create) | `recipe/new` | same composable, `recipeId = null` | library "+", empty-state "Write a recipe" |

Registration note (Navigation 2.10): `recipe/new` is an exact route and `recipe/{recipeId}` a pattern; exact matches win (`NavDestination.DeepLinkMatch` compares `isExactDeepLink` first), and `recipe/{recipeId}/edit` has a different segment count, so there is no ambiguity. Register `recipe/new` before `recipe/{recipeId}` anyway for readability.

Back-stack rules:
- Library → Editor(new) → Save → Detail (`popUpTo(library)`) → back → Library.
- Library → Editor(new) → back/Close (dirty → dialog) → Library.
- Detail → Editor(edit) → Save → `popBackStack()` → Detail (same entry, live data, snackbar "Changes saved").
- Library (tile menu Edit) → Editor(edit) → Save → `popBackStack()` → Library (snackbar "Changes saved").
- Editor never navigates to model management or the browser; the source row is read-only.
- `launchSingleTop = true` on both editor routes so a double tap on the pencil cannot stack two editors.
- Existing rules (extraction `popUpTo(browser)`, browser `launchSingleTop`, detail → browser) are unchanged.

ViewModels: `LibraryViewModel` (+ filter, favourite toggle, editor effects), `RecipeDetailViewModel` (+ favourite toggle, edit effect), new `RecipeEditorViewModel(recipeId?, SavedStateHandle, use cases)`.

---

## 5. Reuse vs new

Reuse as-is: `AppScaffold` (library), `EmptyState` (no favourites, search-in-favourites), `GradientHero` (created detail without picture), `SectionHeader(trailing)` (editor section titles; trailing slot unused — the Add buttons are rows), `NumberBadge` (editor step rows), `SkeletonLine` (pending picture import), `IncompleteBadge` (app), `AppLoadingIndicator` (editor loading in edit mode), M3 `FilterChip`, `IconToggleButton`, `OutlinedTextField`, `AlertDialog`, `ActivityResultContracts.PickVisualMedia` / `PickMultipleVisualMedia` (activity 1.13).

New, app-only (`recipes/presentation/editor/components/`): `EditableLineList` + `EditableLineRow` (drag handle, custom actions, remove), `PictureStrip` + `AddPictureTile`, `MetaFields` (servings/prep/cook/total `FlowRow`), `DiscardChangesDialog`; library: `LibraryFilterRow`, `FavouriteToggle` (the scrim-disc heart used by tile and detail). No `:core:designsystem` change (D12).

New shared non-UI: none. New dependency: none required (`activity-compose` 1.13 ships the photo picker contracts; Coil shows files). Optional for tests: `androidx.room:room-testing` for the migration test (plan F2).

---

## 6. Labels (keys + English copy)

All new copy is added to `app/recipekeeper/src/main/res/values/strings_recipekeeper.xml` and to the label classes (fields without defaults). Favourites / entry-point / created-recipe strings extend `RecipeKeeperLibraryLabels`; the editor gets its own `RecipeEditorLabels` (`rememberRecipeEditorLabels()`), so the already-large library class does not double in size. One feature string is overridden by name (duplicate dialog) so Replace warns about edits.

### 6.1 `RecipeKeeperLibraryLabels` additions

| Field | Key | English |
|---|---|---|
| `favourite` | `recipekeeper_favourite` | Favourite |
| `favouriteAdd` | `recipekeeper_favourite_add` | Add to favourites |
| `favouriteRemove` | `recipekeeper_favourite_remove` | Remove from favourites |
| `favouriteStateOn` | `recipekeeper_favourite_state_on` | In your favourites |
| `favouriteStateOff` | `recipekeeper_favourite_state_off` | Not in your favourites |
| `favouritesFilter` | `recipekeeper_favourites_filter` | Favourites |
| `favouritesEmptyTitle` | `recipekeeper_favourites_empty_title` | No favourites yet |
| `favouritesEmptyMessage` | `recipekeeper_favourites_empty_message` | Tap the heart on any recipe to keep it here. |
| `showAllRecipes` | `recipekeeper_show_all_recipes` | Show all recipes |
| `searchEmptyFavouritesTitle: (String) -> String` | `recipekeeper_search_empty_favourites_title` | No favourite matches “%1$s” |
| `editRecipe` | `recipekeeper_edit_recipe` | Edit recipe |
| `newRecipe` | `recipekeeper_new_recipe` | New recipe |
| `writeRecipe` | `recipekeeper_write_recipe` | Write a recipe |
| `createdByYou` | `recipekeeper_created_by_you` | Created by you |
| `recipeTileDescriptionCreated: (String) -> String` | `recipekeeper_tile_description_created` | %1$s, created by you |
| `createdOn: (String) -> String` | `recipekeeper_created_on` | Created %1$s |
| `editedOn: (String) -> String` | `recipekeeper_edited_on` | Edited %1$s |
| `changesSaved` | `recipekeeper_changes_saved` | Changes saved |
| `shareCreatedTemplate: (title, ingredientsHeader, ingredientLines, stepsHeader, stepLines) -> String` | `recipekeeper_share_created_template` | %1$s\n\n%2$s:\n%3$s\n\n%4$s:\n%5$s |
| `shareBullet: (String) -> String` | `recipekeeper_share_bullet` | - %1$s |
| `shareNumbered: (Int, String) -> String` | `recipekeeper_share_numbered` | %1$d. %2$s |

### 6.2 `RecipeEditorLabels` (new class)

| Field | Key | English |
|---|---|---|
| `editTitle` | `recipekeeper_editor_title_edit` | Edit recipe |
| `newTitle` | `recipekeeper_editor_title_new` | New recipe |
| `close` | `recipekeeper_editor_close` | Close |
| `save` | `recipekeeper_editor_save` | Save |
| `saving` | `recipekeeper_editor_saving` | Saving… |
| `titleLabel` | `recipekeeper_editor_title_label` | Title |
| `titleRequired` | `recipekeeper_editor_title_required` | Add a title |
| `descriptionLabel` | `recipekeeper_editor_description_label` | Description |
| `servingsLabel` | `recipekeeper_editor_servings_label` | Servings |
| `prepLabel` | `recipekeeper_editor_prep_label` | Prep (min) |
| `cookLabel` | `recipekeeper_editor_cook_label` | Cook (min) |
| `totalLabel` | `recipekeeper_editor_total_label` | Total (min) |
| `totalDerived: (Int) -> String` | `recipekeeper_editor_total_derived` | Prep + cook: %1$d |
| `ingredientsHeader` | `recipekeeper_ingredients_header` (reused) | Ingredients |
| `ingredientsHint` | `recipekeeper_editor_ingredients_hint` | One ingredient per line. Paste a list to add several at once. |
| `ingredientLabel: (Int) -> String` | `recipekeeper_editor_ingredient_label` | Ingredient %1$d |
| `ingredientPlaceholder` | `recipekeeper_editor_ingredient_placeholder` | e.g. 2 eggs |
| `addIngredient` | `recipekeeper_editor_add_ingredient` | Add ingredient |
| `removeIngredient: (Int) -> String` | `recipekeeper_editor_remove_ingredient` | Remove ingredient %1$d |
| `reorderIngredient: (Int) -> String` | `recipekeeper_editor_reorder_ingredient` | Reorder ingredient %1$d |
| `stepsHeader` | `recipekeeper_steps_header` (reused) | Steps |
| `stepsHint` | `recipekeeper_editor_steps_hint` | One step per line. |
| `stepLabel: (Int) -> String` | `recipekeeper_editor_step_label` | Step %1$d |
| `stepPlaceholder` | `recipekeeper_editor_step_placeholder` | What to do |
| `addStep` | `recipekeeper_editor_add_step` | Add step |
| `removeStep: (Int) -> String` | `recipekeeper_editor_remove_step` | Remove step %1$d |
| `reorderStep: (Int) -> String` | `recipekeeper_editor_reorder_step` | Reorder step %1$d |
| `moveUp` | `recipekeeper_editor_move_up` | Move up |
| `moveDown` | `recipekeeper_editor_move_down` | Move down |
| `needContent` | `recipekeeper_editor_need_content` | Add at least one ingredient or step |
| `notesLabel` | `recipekeeper_notes_header` (reused) | Notes |
| `picturesHeader` | `recipekeeper_pictures_header` (reused) | Pictures |
| `addPicture` | `recipekeeper_editor_add_picture` | Add picture |
| `removePicture: (Int, Int) -> String` | `recipekeeper_editor_remove_picture` | Remove picture %1$d of %2$d |
| `pictureDescription: (Int, Int) -> String` | `recipekeeper_picture_description` (reused) | Picture %1$d of %2$d |
| `useAsCover` | `recipekeeper_editor_use_as_cover` | Use as cover |
| `coverBadge` | `recipekeeper_editor_cover_badge` | Cover |
| `picturesLimit: (Int) -> String` | `recipekeeper_editor_pictures_limit` | You can add up to %1$d pictures |
| `pictureFailed` | `recipekeeper_editor_picture_failed` | That picture couldn't be added |
| `sourceReadOnly: (String) -> String` | `recipekeeper_editor_source_read_only` | Clipped from %1$s |
| `sourceReadOnlyHint` | `recipekeeper_editor_source_read_only_hint` | The source link can't be changed |
| `discardTitle` | `recipekeeper_discard_title` | Discard changes? |
| `discardMessageEdit` | `recipekeeper_discard_message_edit` | Your edits to this recipe won't be saved. |
| `discardMessageNew` | `recipekeeper_discard_message_new` | This recipe won't be saved. |
| `discard` | `recipekeeper_discard` | Discard |
| `keepEditing` | `recipekeeper_keep_editing` | Keep editing |
| `saveFailed` | `recipekeeper_editor_save_failed` | Couldn't save the recipe — try again |
| `recipeNotFound` | `recipekeeper_recipe_not_found` (reused) | This recipe is no longer in your library |

### 6.3 Feature string override (app, `strings_webclipper_overrides.xml`)

| Key | English |
|---|---|
| `webclipper_duplicate_message` | You clipped this page before. Replace the saved recipe with a fresh clip? Any edits you made to it are lost. |

Test fixture: `TestRecipeKeeperLibraryLabels` gains the new fields; a `TestRecipeEditorLabels` fixture is added (each field renders its own name).

---

## 7. Accessibility checklist

- [ ] Every heart is an `IconToggleButton` with `contentDescription = favourite` and `stateDescription` on/off; tile hearts are separate nodes from the merged tile; detail heart sits in the bar's traversal order after Back.
- [ ] Filter chip: `FilterChip` selected state; the row is reachable before the grid (it is above it in composition order).
- [ ] Tile node carries `stateDescription = favouriteStateOn` when favourite; created tiles say "created by you" instead of a host.
- [ ] Editor: every `OutlinedTextField` has a visible `label` (never placeholder-only); errors use `isError` + `supportingText` (M3 sets the `error` semantics); the first error receives focus so it is announced.
- [ ] Line rows: handle `contentDescription` (reorder ingredient n / step n) **and** `customActions` Move up / Move down; remove button `removeIngredient(n)` / `removeStep(n)`; field `label = ingredientLabel(n)` / `stepLabel(n)` (visually hidden label is acceptable via `semantics { contentDescription }` only if the design drops the label — keep the label).
- [ ] Drag feedback is not the only reorder path (custom actions); drag threshold is long-press so scrolling still works.
- [ ] Picture tiles: `pictureDescription(i, n)` on the image, `removePicture(i, n)` on the ✕ (48dp hit area), `useAsCover` in the tap menu, Add tile `Role.Button` + `addPicture`; pending import tiles are `invisibleToUser()` skeletons.
- [ ] Touch targets ≥ 48dp: tile heart (48dp button on a 36dp disc), handles, remove buttons, Add tile (96dp), chip (48dp row).
- [ ] Contrast: white heart glyph on a 35 % black disc over any photo ≥ 4.5:1 for the icon; `Cover` pill `onTertiaryContainer` on `tertiaryContainer`; error text `error` on `surface` (M3 pair).
- [ ] Dialogs: `AlertDialog` traps focus; confirm text "Discard" in `error` colour and dismiss "Keep editing" both ≥ 48dp.
- [ ] Reduced motion: `animateItem()` and the lift elevation follow the animator duration scale; no shimmer added.
- [ ] Keyboard: `ImeAction.Next` chain title → description → servings → prep → cook → total → first ingredient; Enter on the last ingredient adds a row; steps keep Enter as newline.
- [ ] Nothing under the system bars or the IME: `safeDrawing` insets through `innerPadding` → `contentPadding`; the pinned top bar keeps Save reachable.
- [ ] 600dp+: two panes; the focused pane scrolls independently; both lists get the IME inset.

---

## 8. Motion

- Grid tiles: `Modifier.animateItem()` so un-hearting in the Favourites view and deleting animate out instead of jumping.
- Heart: `IconToggleButton` default ripple; the glyph crossfades filled/outlined with `AnimatedContent(targetState = checked)` 150 ms.
- Filter chip: M3 default.
- Editor rows: `animateItem()` for add/remove/reorder; the dragged row lifts (elevation 4dp, scale 1.02) and others shift; inline error `AnimatedVisibility(expandVertically + fadeIn)`.
- Save: label → 20dp ring crossfade; on success the pop transition is the NavHost default.

---

## 9. Ranked risks / open questions

1. **Share moved into the overflow on the detail (D4).** Cheap to reverse (swap pencil and share). Decide before F1 starts; the spec assumes Edit visible.
2. **Room migration on devices that already hold v1** (QA emulator-5556 and the Sony phone). `MIGRATION_1_2` is three `ALTER TABLE ADD COLUMN`s + one index; the schema export must match exactly or Room throws at open. Plan F2 adds a `MigrationTestHelper` test and the QA stream opens the app on the existing emulator DB without `pm clear`.
3. **Replace on re-clip discards edits.** Mitigated by the duplicate-dialog message override (§6.3) and by keeping the favourite flag; a per-edit backup is out of scope.
4. **Drag-to-reorder is hand-rolled** (no foundation API). Scope it to the two editor lists, long-press threshold, and always ship the custom actions; if it misbehaves on device the actions alone are an acceptable v1.
5. **Photo picker availability on API 24–29**: `PickVisualMedia` falls back to `ACTION_OPEN_DOCUMENT`; the returned URI is readable only during the result delivery, which is why the importer copies immediately.
6. **Large images** (a 50 MP camera JPEG): decode with `inSampleSize` to ≤ 1280 px; never load the full bitmap.
7. **Process death mid-edit**: `SavedStateHandle` draft + cache-dir pictures; the editor temp dir is swept at app start (files older than 24 h).
8. **`checkedIngredients` after reorder**: remapped by `sourceIndex` (D8); verify with the use-case test "reorder keeps the ticks on the same rows".
9. **Created recipes and the `sourceUrl` index**: many rows with `sourceUrl = ''` are fine for the index; `findIdBySourceUrl` is only called with real page URLs.
10. **Library "+" discoverability**: if analytics-free dogfooding shows users miss it, the Phase 2 FAB menu is the upgrade path (D6).
11. **Take a photo** (Phase 2): `ActivityResultContracts.TakePicture` + `FileProvider` (`androidx.core.content.FileProvider`, authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml` exposing `cache/recipe-editor/`), no `CAMERA` permission needed; same importer afterwards.
12. **Servings stays free text** in the editor (no scaling); quantity parsing/scaling remains Phase 2 as before.
