# UX Spec — Recipe Keeper input sources: web, images, voice (and the screenshot path)

Produced 2026-10-24 by the project `android-ux` agent persona (brief `docs/plans/briefs/input-sources-plan.md`) from the repo state at `ab2eadb` (branch `recipekeeper/dev`: features F1–F4, learning detection L1–L2, settings/start page merged). Companion to `recipekeeper-input-sources-plan.md` (contracts + streams I0–I4). Extends `recipekeeper-ux-spec.md` (§1 navigation, §4 extraction) and `recipekeeper-features-ux-spec.md` (§3 create entry points, D6/D10).

User requirement (2026-10-24): *"add a way to change the source from web, images, voice… we will implement this in the future, but now add this feature to choose the input"*, and, if feasible, implement screenshot/image extraction.

**Vision gate.** `docs/research/screenshot-extraction-eval.md` did not exist when this spec was written (the eval agent had only scaffolded `tools/eval/screenshots/`). Every part marked **[gated by the eval]** ships only if that document says Gemma 4 E2B can extract recipes from screenshots on-device at an acceptable speed. The source chooser, the voice placeholder and the URL share target ship regardless; "From images" is then hidden (see §2.4).

Facts relied on (from the code, not the older specs):
- Library: `AppScaffold`, top bar `[Search] [+ New recipe] [Settings] [⋮]`, FAB `ExtendedFloatingActionButton(ContentCut, "Open browser")` → `LibraryIntent.ClipRecipe`; onboarding empty state = `EmptyState(MenuBook, …, "Open browser")` + `TextButton("Write a recipe")`; FAB and filter row hidden on the empty state and while loading.
- Clip pipeline: `ClipSession.Current(snapshot, candidates)` → `extract/{candidateId}` → `ExtractionViewModel` → `ClipUseCase` phases Reading → Extracting → Pictures → Saving, `PhaseProgress`, app `renderDraft` slot (`ClipDraftCard`), picture strip with keep checkboxes, duplicate dialog keyed by `sourceUrl`, `onSaved` → `recipe/{id}` with `popUpTo(browser)`.
- `RecipeOrigin { CLIPPED, CREATED }` is stored as text (`origin TEXT NOT NULL DEFAULT 'CLIPPED'`, unknown names read back as `CLIPPED`), so a new value needs **no Room migration** (verified in `RecipeEntity.kt`; DB stays at version 3).
- `LlmRequest` is text-only; `LiteRtLlmEngine` builds `EngineConfig` without `visionBackend`; LiteRT-LM 0.18 offers `visionBackend`, `maxNumImages`, `Content.ImageBytes` and a visual token budget (research notes §A; the budget-setting API is unverified).
- `LlmModelDescriptor.family` has `MULTIMODAL` but no model uses it; the Gemma 4 E2B bundle ships the vision encoder.
- `activity-compose` 1.13 ships `PickVisualMedia` / `PickMultipleVisualMedia` (already used by the editor's picture strip; the multiple contract needs `maxItems > 1`); `PictureImporter` (content URI → ≤ 1280 px JPEG) exists in the app's `recipes/` feature.
- Skills applied: `edge-to-edge` (sheet and review screen through `Scaffold`/`ModalBottomSheet` insets, no `imePadding()` on top of `safeDrawing`), `adaptive` (sheet keeps the M3 max width, review grid `GridCells.Adaptive`), `navigation-3` (project stays on Navigation 2.10 string routes; the two new destinations fit the existing graph, no migration proposed), `styles` (not applicable; Material components only).

---

## 0. Decisions at a glance

| # | Decision | Why |
|---|---|---|
| S1 | **The source chooser is a `ModalBottomSheet` ("Add a recipe") opened from the library top-bar "+" action**, which replaces today's direct "New recipe" action. **The FAB stays "Open browser" (one tap).** The onboarding empty state keeps "Open browser" primary and swaps "Write a recipe" for "More ways to add…" → the same sheet. | Web is the only source with usage today and the product is a clipper, so the dominant path must stay one tap (brief constraint). The "+" already means "add something" and already lives in the bar, so no new chrome. A sheet beats a `DropdownMenu` because each source needs an icon, a one-line explanation and a status line ("Coming soon", "Needs a model that reads images"), which menus cannot carry accessibly. The M3 `FloatingActionButtonMenu` is still `ExperimentalMaterial3ExpressiveApi` (features spec D6). **Flip rule:** when images or voice reach meaningful use, the FAB becomes "Add recipe" → sheet (one-line change, `LibraryIntent.ClipRecipe` → `OpenSourceChooser`) and "From the web" becomes the first row; the spec is written so nothing else moves. |
| S2 | Sheet rows, in order: **From the web**, **From images** [gated], **By voice** (disabled, "Coming soon"), **Write it yourself**. | Frequency order, with the unavailable item next to its nearest sibling rather than last, so "Write it yourself" (always available) is the final row and the sheet never ends on a disabled item. |
| S3 | "From images" has three states: **available** (model ready and it reads images), **needs a model** (no model → tap opens model management, same rule as every no-model state), **model can't read images** (a text-only model is selected → tap opens an `AlertDialog` explaining it, with "Open models"). It is **hidden** only when the build has the images source switched off (eval says no). | A disabled row with no explanation teaches nothing; the dialog says what to do. Hiding is reserved for "the feature does not exist in this build". |
| S4 | Images entry points: the chooser (system Photo Picker, multiple, ordered when the OS supports it) and the **system share sheet** (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `image/*`). A `text/plain` share whose text is a URL opens the **browser** on that page. Camera capture is Phase 2 (features spec risk 11 has the mechanics). | Screenshots mostly live in another app (Instagram, WhatsApp, Photos); share is the natural path. The URL share target is cheap, source-independent and was already Phase 2 in the app plan. |
| S5 | **Review step before extraction when ≥ 2 images**; a single image goes straight to extraction. The review shows numbered thumbnails, Move left / Move right (buttons + accessibility custom actions, no drag in v1), Remove, Add more, the privacy line and the time expectation, and one primary button "Extract recipe". | Order matters (ingredients shot before steps shot); the ordered Photo Picker exists only on API 34+ with the modular picker; a wrong screenshot must be removable before a slow run. The same pattern as the candidate sheet: one candidate → go, several → confirm. |
| S6 | Extraction reuses `WebClipperExtractionScreen` unchanged in structure: phases **Reading images → Extracting recipe → Saving pictures → Saving to library**; the source strip reads "From your photos · 3 pictures"; "Not this one?" is hidden; the draft card, raw-output toggle, Stop button and the keep-picture strip work as today. The pictures in the strip are the input images (already local), all pre-checked. | One extraction screen for every source keeps the streaming UX, the a11y work and the tests. Pre-checking all images respects the user's explicit selection; they can untick a screenshot they do not want as a picture. |
| S7 | Saved images clips get `origin = IMAGES`, empty `sourceUrl`/`sourceHost`, pictures = the input images downscaled, `truncated` as today. Library tile meta and detail source row say **"From your photos"**; Open original / Re-extract are hidden; Share sends the full recipe text (same as CREATED). **No duplicate check in v1** (no URL; a content-hash key needs a Room column and is listed in Phase 2). | Everything follows from "there is no page". The duplicate dialog's value is saving compute on a re-clip of the same URL; re-sharing the same screenshot is rare and the user sees the result anyway. |
| S8 | Limits [gated; defaults until the eval]: **max 5 images** per clip at visual token budget 280, or **3** at 560; images are downscaled to **≤ 1536 px long edge** before encoding; a tall full-page screenshot (height > 2.5 × width) is **split into overlapping tiles** that count toward the cap. Oversupply (share of 12 images) keeps the first N and says so. | `maxNumTokens = 4096` minus `maxOutputTokens = 1024` minus ~250 prompt tokens leaves ~2,800 visual tokens: 5 × 280 or 3 × 560 (research notes §A). Tiling is what makes a "one tall capture" share usable; which variant wins is the eval's call. |
| S9 | **Voice is a visible, disabled row** ("By voice — Coming soon") with `disabled()` semantics and a `stateDescription`; its contract is `ClipInput.Voice(transcript)` and nothing else is built now. | The user asked for the choice to be visible today; a disabled row with a reason is the honest M3 way to show a roadmap item. |
| S10 | Privacy copy appears once per images clip (review step, or the extraction hint for a single image): "Everything happens on your phone — the pictures never leave it." | Screenshots can contain private chats; the app's whole proposition is on-device. Say it where the pictures are shown, not in a settings page. |
| S11 | No new `:core:designsystem` primitives; the chooser sheet and the review screen use M3 `ModalBottomSheet`, `ListItem`, `Card`, `NumberBadge`, `EmptyState`. | Same reasoning as features spec D12. |

---

## 1. Source chooser (`SourceChooserSheet`, app module)

### 1.1 Entry points

| Where | Control | Intent |
|---|---|---|
| Library top bar | `IconButton(Icons.Outlined.Add, contentDescription = labels.addRecipe)` (replaces the "New recipe" action in the same slot) | `LibraryIntent.OpenSourceChooser` |
| Onboarding empty state | primary `openBrowser` (unchanged) + `TextButton(labels.moreWaysToAdd)` (replaces "Write a recipe") | `LibraryIntent.OpenSourceChooser` |
| FAB | unchanged "Open browser" | `LibraryIntent.ClipRecipe` |
| System share sheet | `ShareReceiverActivity` (§2.2) — bypasses the chooser | — |

`LibraryState.sourceChooserOpen: Boolean`; `LibraryIntent.DismissSourceChooser`; `LibraryIntent.ChooseSource(source: RecipeSource)` with `enum class RecipeSource { WEB, IMAGES, VOICE, EDITOR }`. The ViewModel maps a choice to the existing effects (`NavigateToBrowser(null)`, `NavigateToEditor(null)`) plus new ones (`NavigateToImagesClip`, `NavigateToModelManagement`, `ShowImagesUnavailable`). The sheet is state-driven (not a navigation destination), like the candidate sheet.

### 1.2 Layout

```
┌──────────────────────────────────────────────────┐
│                     ▬▬▬▬ (drag handle)           │
│  Add a recipe                      titleLarge    │
│  Choose where it comes from        bodyMedium    │
│                                                  │
│  ◯  From the web                                 │  ListItem ≥ 72dp, leading 40dp icon disc
│     Browse a site and tap Clip                   │  (secondaryContainer), trailing ChevronRight
│  ◯  From images                                  │
│     Screenshots or photos of a recipe            │
│  ◯  By voice                       Coming soon   │  disabled: 38 % alpha text, trailing label chip
│     Dictate a recipe                             │
│  ◯  Write it yourself                            │
│     Type it in                                   │
│                                                  │
│  (navigationBars inset — ModalBottomSheet adds it)│
└──────────────────────────────────────────────────┘
```

- `ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))`, `dragHandle` default, content in a `Column` with 16dp horizontal padding and `Modifier.navigationBarsPadding()` only if the sheet is given `contentWindowInsets = { WindowInsets(0) }`; otherwise rely on the default M3 insets (preferred, edge-to-edge skill: M3 components manage their own safe areas).
- Rows are M3 `ListItem(headlineContent, supportingContent, leadingContent = SourceIcon, trailingContent)` wrapped in `Modifier.clickable(enabled, role = Role.Button, onClick)`; whole row tappable; icons: `Public` (web), `PhotoLibrary` (images), `Mic` (voice), `EditNote` (editor). The icon disc is 40dp `Surface(shape = CircleShape, color = secondaryContainer)`; brand values come from `MaterialTheme` only.
- Status line (trailing) per state, see §1.3. "Coming soon" is a `SuggestionChip`-styled label (`labelMedium`, `onSurfaceVariant`), not a chip control (it is not actionable).
- The sheet closes on a choice, then the effect fires; a choice that opens a dialog (§1.3) first closes the sheet so the dialog is never stacked on it.
- 600dp+: M3 caps the sheet at 640dp and centres it; nothing else changes. Landscape phones: the sheet is scrollable by default; four rows fit.

### 1.3 Row states

| Row | Condition | Visual | Tap |
|---|---|---|---|
| From the web | always | enabled | close → `NavigateToBrowser(null)` (same as FAB) |
| From images | `imagesAvailability == Available` | enabled | close → `NavigateToImagesClip` |
| From images | `NoModel` / `Downloading` | enabled, supporting text `labels.imagesNeedsModel` ("Choose a model first") | close → `NavigateToModelManagement` |
| From images | `ModelLacksVision` | enabled, supporting text `labels.imagesModelCantRead` ("Your current model can't read images") | close → `ShowImagesUnavailable` → `AlertDialog(imagesUnavailableTitle, imagesUnavailableMessage(modelName), confirm = openModels, dismiss = cancel)` |
| From images | `Disabled` (build flag off) | row absent | — |
| By voice | always (v1) | `enabled = false`, 38 % alpha, trailing "Coming soon" | nothing |
| Write it yourself | always | enabled | close → `NavigateToEditor(null)` |

`imagesAvailability` comes from `ObserveInputCapabilitiesUseCase` (plan §2.5): `Available`, `NoModel`, `ModelLacksVision`, `Disabled`. It is part of `LibraryState` (`inputCapabilities`) so the sheet is pure.

### 1.4 TalkBack

- Sheet opens with focus on the title (`ModalBottomSheet` default); title has `heading()`.
- Each row is one merged node: "From images, Screenshots or photos of a recipe, button". Status text is in the same node (it is supporting content), so the reason is read with the row.
- Voice row: `disabled()` + `stateDescription = labels.comingSoon` so it reads "By voice, Dictate a recipe, dimmed, Coming soon". Do not `invisibleToUser()` it: the user asked for it to be seen.
- Row height ≥ 72dp, icon disc not focusable (`contentDescription = null`), chevron decorative.
- The "+" action: `contentDescription = labels.addRecipe` ("Add a recipe"); the previous "New recipe" description moves to the editor row.

### 1.5 Copy (keys + English; all in `app/recipekeeper/src/main/res/values/strings_recipekeeper.xml`, fields on `RecipeKeeperLibraryLabels`)

| Field | Key | English |
|---|---|---|
| `addRecipe` | `recipekeeper_add_recipe` | Add a recipe |
| `moreWaysToAdd` | `recipekeeper_more_ways_to_add` | More ways to add… |
| `sourceChooserTitle` | `recipekeeper_source_chooser_title` | Add a recipe |
| `sourceChooserSubtitle` | `recipekeeper_source_chooser_subtitle` | Choose where it comes from |
| `sourceWeb` | `recipekeeper_source_web` | From the web |
| `sourceWebHint` | `recipekeeper_source_web_hint` | Browse a site and tap Clip |
| `sourceImages` | `recipekeeper_source_images` | From images |
| `sourceImagesHint` | `recipekeeper_source_images_hint` | Screenshots or photos of a recipe |
| `sourceVoice` | `recipekeeper_source_voice` | By voice |
| `sourceVoiceHint` | `recipekeeper_source_voice_hint` | Dictate a recipe |
| `sourceEditor` | `recipekeeper_source_editor` | Write it yourself |
| `sourceEditorHint` | `recipekeeper_source_editor_hint` | Type it in |
| `comingSoon` | `recipekeeper_coming_soon` | Coming soon |
| `imagesNeedsModel` | `recipekeeper_images_needs_model` | Choose a model first |
| `imagesModelCantRead` | `recipekeeper_images_model_cant_read` | Your current model can't read images |
| `imagesUnavailableTitle` | `recipekeeper_images_unavailable_title` | This model can't read images |
| `imagesUnavailableMessage: (String) -> String` | `recipekeeper_images_unavailable_message` | %1$s only reads text. Pick a model marked "reads images" in Models to clip from screenshots and photos. |
| `openModels` | `recipekeeper_choose_model_action` (reused) | Open models |
| `fromYourPhotos` | `recipekeeper_from_your_photos` | From your photos |
| `recipeTileDescriptionImages: (String) -> String` | `recipekeeper_tile_description_images` | %1$s, from your photos |
| `addedOn: (String) -> String` | `recipekeeper_added_on` | Added %1$s |
| `shareImagesTemplate` | — | reuses `recipekeeper_share_created_template` (full text) |

Model management: the model list should show a "Reads images" tag for vision-capable descriptors. That is the shared feature's `tags` list rendered as chips today, so the descriptor's `tags += "reads images"` would be a hardcoded literal in `:core:llm` — **not allowed** (rule 0 applies to `LlmModelCatalog.tags` too; existing tags are an accepted pre-existing deviation). Instead `ModelManagementLabels` gains `capabilityVision` ("Reads images", key `modelmanagement_capability_vision` in the feature's `strings_modelmanagement.xml`) and the list renders it from `descriptor.capabilities` (plan §2.1). Small, shared, listed in I3.

---

## 2. Images source [gated by the eval]

### 2.1 Flow

```
Chooser "From images"            Share sheet (image/*)
        │                                 │
        ▼                                 ▼
 Photo Picker (multiple, ordered)   ShareReceiverActivity → MainActivity (inbox)
        │ URIs                            │ URIs
        └──────────────┬──────────────────┘
                       ▼
            clip/images  (ImagesClipRoute)
            ├─ Preparing… (copy + downscale + tile; skeleton tiles)
            ├─ 1 image  → straight on
            └─ ≥ 2      → Review: order / remove / add more → "Extract recipe"
                       ▼
            extract/images  (existing extraction screen; phases renamed)
                       ▼
            recipe/{id}  (popUpTo library)
```

### 2.2 Entry points

**Chooser → Photo Picker.** `ImagesClipRoute` with no pending images launches `PickMultipleVisualMedia(maxItems = policy.maxImages)` on first composition (`rememberLauncherForActivityResult`, `PickVisualMediaRequest(mediaType = ImageOnly, isOrderedSelection = true)` where the activity version exposes it; verify the parameter name at I3, it is available from activity 1.11+). Cancelled picker (empty result) → pop back to the library silently (the user changed their mind; no snackbar). API 24–29 fall back to `ACTION_OPEN_DOCUMENT` inside the contract; the returned URIs are copied immediately (§2.3), so the transient read grant is enough.

**System share target.** `ShareReceiverActivity` (app module, `android:exported="true"`, `android:excludeFromRecents="true"`, `android:noHistory="true"`, translucent theme, no Compose content) with intent filters:

```xml
<intent-filter> <action android:name="android.intent.action.SEND"/>          <category android:name="android.intent.category.DEFAULT"/> <data android:mimeType="image/*"/> </intent-filter>
<intent-filter> <action android:name="android.intent.action.SEND_MULTIPLE"/> <category android:name="android.intent.category.DEFAULT"/> <data android:mimeType="image/*"/> </intent-filter>
<intent-filter> <action android:name="android.intent.action.SEND"/>          <category android:name="android.intent.category.DEFAULT"/> <data android:mimeType="text/plain"/> </intent-filter>
```

It parses the intent with a pure `ShareIntentParser` (unit-tested): `EXTRA_STREAM` (single `Uri` or `ArrayList<Uri>`, typed getters on API 33+) → `PendingClipInput.Images(uris)`; `EXTRA_TEXT` containing the first `http(s)://` URL → `PendingClipInput.Url(url)`; anything else → finish with a toast-free no-op (the share sheet never offered us for other types). It then starts `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, forwarding the original `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION` so the read grants travel, and finishes. `MainActivity` becomes `launchMode="singleTop"`; `onCreate` and `onNewIntent` hand the intent to the parser and put the result in the `ClipInputInbox` (plan §2.3). `RecipeKeeperNavHost` observes the inbox: `Images` → `navigate("clip/images") { launchSingleTop = true }`; `Url` → `navigateToBrowser(url)` (existing, `launchSingleTop`). If an extraction is running (`session.activeExtraction != null`) the inbox item is dropped with the snackbar `labels.clipAlreadyRunning` ("Finish the current clip first"). When the images source is disabled by the build flag the two `image/*` filters are **not declared** (manifest placeholder `imagesShareTarget` or a second manifest in a source set; plan I3) and an image share can never reach the app.

The share-sheet label is the app name ("Recipe Keeper"); no separate `android:label` (a verb label such as "Clip to Recipe Keeper" reads oddly for the URL case).

### 2.3 Preparing images (`clip/images`, state `Preparing`)

`PrepareImagesUseCase` copies each URI through `LocalImageImporter` (plan §2.2) into the clipper temp dir as JPEG q85, long edge ≤ `policy.maxLongEdgePx` (1536), EXIF-rotated, computes SHA-256, and, when `height > policy.tallAspect × width` (2.5), splits the image into vertical tiles of `width × (width × 1.6)` with 10 % overlap **[tiling gated by the eval; off by default]**. Each prepared image is a `ClipImage(file, width, height, sha256, fromTileOf: Int?)`. Failures: a URI that cannot be decoded is skipped and counted; the screen shows `labels.imagesSkipped(n)` as a snackbar ("2 pictures couldn't be read") and continues with the rest; zero usable images → `EmptyState(BrokenImage, imagesNoneReadableTitle, imagesNoneReadableMessage, action = pickDifferent)`. More than `maxImages` → keep the first N and show `labels.imagesCapped(n)` ("Only the first 5 pictures are used").

While preparing, the screen shows the review layout with `SkeletonLine`-style 96dp tiles (`invisibleToUser()`), the title "Getting your pictures ready…" and no primary button. Preparation is fast (< 1 s per image); no cancel beyond system back (which deletes temp files).

### 2.4 Review step (`ImagesReviewScreen`, shown when ≥ 2 prepared images)

```
┌ statusBars ─────────────────────────────────────────────┐
│ [✕]  Clip from images                                   │  TopAppBar, Close = cancel
├─────────────────────────────────────────────────────────┤
│ Put the pictures in reading order                        │  titleMedium
│ Ingredients first, then the steps.                       │  bodyMedium onSurfaceVariant
│ ┌────┐ ┌────┐ ┌────┐ ┌ + ┐                               │  LazyVerticalGrid Adaptive(104.dp), 8dp gaps
│ │ ①✕ │ │ ②✕ │ │ ③✕ │ │   │   number badge top-start,     │  tile = Card(medium) 3:4, AsyncImage(file)
│ └────┘ └────┘ └────┘ └───┘   remove ✕ top-end (48dp hit) │  Add tile hidden at the cap
│ ‹ ›  under the selected tile (Move left / Move right)    │  tap a tile to select; arrows 48dp IconButtons
│ 3 of 5 pictures                                          │  labelMedium
│ 🔒 Everything happens on your phone — the pictures never │  ListItem(Lock), bodySmall
│    leave it.                                             │
│ ⏱ Reading pictures takes longer than reading a page —    │  ListItem(Timer), bodySmall  [numbers from eval]
│    usually under a minute per picture.                   │
│                                                          │
│ [            Extract recipe             ]                │  Button, full width, 48dp, pinned bottom
└ navigationBars (Scaffold innerPadding) ──────────────────┘
```

- Own `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`; grid gets `contentPadding = innerPadding + 16dp`; the primary button sits in `bottomBar` so it stays above the gesture bar and the grid scrolls under it (edge-to-edge skill: list `contentPadding`, never a parent `padding`).
- Tile tap selects (2dp `primary` outline + `selected` semantics); the arrow row acts on the selected tile; each tile also exposes `customActions` Move left / Move right / Remove so TalkBack and switch users never need the arrow row. Tile `contentDescription = labels.imageTileDescription(i, n)` ("Picture 2 of 3") + `stateDescription = selected`. Remove ✕ `contentDescription = labels.removeImage(i, n)`.
- "Add more" tile (`AddPhotoAlternate`, dashed `outline`) → picker with `maxItems = remaining` (switch to the single contract when one slot remains, as the editor does). Hidden at the cap; the counter turns `error` colour when a share oversupplied and was capped.
- Reorder is pure state in `ImagesClipViewModel` (`MoveImage(from, to)`, `RemoveImage(key)`, `ImagesPicked(uris)`); no drag in v1 (features spec risk 4: hand-rolled drag stays scoped to the editor).
- "Extract recipe" → `ImagesClipIntent.Extract` → `session.set(ClipInput.Images(images), candidates = [imagesCandidate])` → effect `NavigateToExtraction(IMAGES_CANDIDATE_ID)`; the route navigates to `extract/images` with `popUpTo("clip/images") { inclusive = true }` so back from the extraction screen never shows the review again (cancel on the extraction screen pops to the library, §2.6).
- Single image: the ViewModel skips the review (`Extract` auto-fires after `Preparing` completes); the user still sees the privacy line as the extraction hint (§2.5).
- Model gate: the route is only reachable when `imagesAvailability == Available` (chooser) or via share; for share with no model the screen shows the same `GradientHero` the library uses (`libraryNoModelTitle`, action `chooseModelAction`) above the grid and disables "Extract recipe"; with a text-only model it shows the `imagesUnavailable*` copy as an `EmptyState` with "Open models".
- 600dp+: the grid simply gets more columns; the info rows and the button are capped at 720dp and centred.

### 2.5 Extraction for images (changes to `WebClipperExtractionScreen`)

| Element | Web page | Images |
|---|---|---|
| Title | "Clipping recipe" | same |
| Source strip | `host · page title` + "Not this one?" | `Icon(PhotoLibrary)` + `labels.imagesSourceStrip(n)` ("From your photos · 3 pictures"); no "Not this one?" |
| Phase 1 | "Reading page" | `labels.phaseReadingImages` ("Reading images") — Active while prefill runs (the first token has not arrived); this is the long one on CPU |
| Phase 2 | "Extracting recipe" | same; becomes Active on the first streamed token |
| Phase 3 | "Saving pictures" (downloads) | same label; `PicturesProposed` lists the input images as `Done` with their local files, all `keep = true`; the strip renders them with the existing checkboxes |
| Phase 4 | "Saving to library" | same |
| Hint under the active phase | "Running on your phone — this takes a moment" + elapsed | `labels.imagesExtractingHint` ("Reading your pictures on your phone — this takes longer than a page") + elapsed; for a single image the privacy sentence is appended as a second line (`labels.imagesPrivacyHint`) |
| Draft card | `ClipDraftCard` | same |
| Stop | cancels, pops to browser, snackbar | cancels, pops to the library, snackbar `clipCancelled` |
| Duplicate dialog | before the model runs | never (S7) |

Phase semantics: `ClipUseCase` emits `Reading → Active` when it starts the request and `Reading → Done`, `Extracting → Active` on the first `Streamed` delta (today Reading flips to Done before the model is called, because reading was text rendering). For images the prefill is the expensive part, so keeping Reading active until the first token gives the user a truthful progress picture. The same rule can apply to web pages without harm (prefill of a 9k-char page is also seconds); plan I2 decides and documents it.

Live region: the existing invisible `Text(liveRegion = Polite)` announces "Reading images", then "Extracting recipe" and the draft milestones; nothing else changes for TalkBack.

### 2.6 Failure states (images)

| Failure | Phase mark | Body | Actions |
|---|---|---|---|
| Model answered `NO RECIPE`, or parser null / `accept` rejected (no fallback for images) | Extracting ✕ | `EmptyState(ImageSearch, imagesNoRecipeTitle, imagesNoRecipeMessage)` + raw-output expander | `pickDifferentImages` → pops to `clip/images` with the same images (the review lets the user drop one / add one); `backToLibrary` |
| Engine says images unsupported (text-only model selected after the chooser was opened; `ExtractionError.ImagesUnsupported`) | Reading ✕ | `imagesUnavailableTitle` / `imagesUnavailableMessage` | `openModels`, `backToLibrary` |
| Engine init / generation failed | Extracting ✕ | as today (`modelFailedTitle`, reason) | `tryAgain`, `backToLibrary` |
| Output truncated | Extracting ⚠ | `truncatedWarning` | saves; detail shows `incompleteChip` |
| Too many / unreadable images | — (handled in §2.3 before extraction) | — | — |
| Model file removed mid-flow | Extracting ✕ | as today | `openModels` |
| Process death | — | `keepAppOpenHint` | temp files survive in `cacheDir`; the extraction restarts from `clip/images` state (SavedStateHandle holds the prepared file paths), the session is rebuilt from it |
| Cancel | — | — | temp files deleted, nothing persisted, pop to library, snackbar `clipCancelled` |

The web `backToPage` action is replaced by `backToLibrary` for images (the route passes `onCancelled` which pops; the label changes by input kind).

### 2.7 After saving

| Place | Images clip |
|---|---|
| Detail hero | first kept image (a screenshot is acceptable: it is what the user kept; "Use as cover" in the editor fixes it) or `GradientHero(title, fromYourPhotos)` when none kept |
| Detail source row | `ListItem(PhotoLibrary, fromYourPhotos, addedOn(date) · editedOn(…))`, no trailing button |
| Detail overflow | Share, Keep screen on, Edit (pencil stays in the bar), Delete; **no** Open original / Re-extract |
| Tile meta | `fromYourPhotos · 30 min`; tile description `recipeTileDescriptionImages(title)` |
| Tile menu | Favourite, Edit, Share, Delete |
| Share text | full recipe text (`shareCreatedTemplate`) |
| Editor source row | hidden (same as CREATED) |
| Possibly-incomplete chip | when truncated |
| Re-clip duplicate check | never matches (empty URL) |

### 2.8 Limits, time and memory expectations [numbers gated by the eval]

- Visual token budget 280 per image → prefill ≈ 280 tokens/image plus the text prompt; at the S26-class 557 tok/s CPU prefill that is ~0.5 s per image, on a mid-range CPU (150–300 tok/s) ~1–2 s per image **plus the vision encoder pass**, which is the unknown the eval measures. The emulator (CPU, no SME) is expected to be several times slower than a phone; QA notes a fallback to a real device.
- Memory: text engine ~1.7 GB peak; the vision tower adds an unverified amount (the eval reports it). `minRamMb` for vision-capable descriptors may need raising; the model list already warns from it.
- Copy promises nothing numeric except "usually under a minute per picture"; replace with the eval's median if it is materially different, and keep it conservative.

### 2.9 Copy (feature keys in `feature/webclipper/src/main/res/values/strings_webclipper.xml`, fields on `WebClipperLabels`; the app may override by name)

| Field | Key | English |
|---|---|---|
| `imagesTitle` | `webclipper_images_title` | Clip from images |
| `imagesPreparing` | `webclipper_images_preparing` | Getting your pictures ready… |
| `imagesOrderTitle` | `webclipper_images_order_title` | Put the pictures in reading order |
| `imagesOrderHint` | `webclipper_images_order_hint` | Ingredients first, then the steps. |
| `imagesCount: (Int, Int) -> String` | `webclipper_images_count` | %1$d of %2$d pictures |
| `imageTileDescription: (Int, Int) -> String` | `webclipper_images_tile_description` | Picture %1$d of %2$d |
| `imageSelected` | `webclipper_images_tile_selected` | Selected |
| `moveImageLeft` | `webclipper_images_move_left` | Move left |
| `moveImageRight` | `webclipper_images_move_right` | Move right |
| `removeImage: (Int, Int) -> String` | `webclipper_images_remove` | Remove picture %1$d of %2$d |
| `addImages` | `webclipper_images_add_more` | Add more |
| `imagesPrivacyHint` | `webclipper_images_privacy_hint` | Everything happens on your phone — the pictures never leave it. |
| `imagesTimeHint` | `webclipper_images_time_hint` | Reading pictures takes longer than reading a page — usually under a minute per picture. |
| `extractFromImages` | `webclipper_images_extract` | Extract recipe |
| `imagesCapped: (Int) -> String` | `webclipper_images_capped` | Only the first %1$d pictures are used |
| `imagesSkipped: (Int) -> String` (plural) | `webclipper_images_skipped` | one: 1 picture couldn't be read / other: %d pictures couldn't be read |
| `imagesNoneReadableTitle` | `webclipper_images_none_readable_title` | These pictures couldn't be read |
| `imagesNoneReadableMessage` | `webclipper_images_none_readable_message` | Try screenshots or photos saved on this phone. |
| `pickDifferentImages` | `webclipper_images_pick_different` | Pick different pictures |
| `imagesSourceStrip: (Int) -> String` (plural) | `webclipper_images_source_strip` | one: From your photos · 1 picture / other: From your photos · %d pictures |
| `phaseReadingImages` | `webclipper_extraction_phase_reading_images` | Reading images |
| `imagesExtractingHint` | `webclipper_images_extracting_hint` | Reading your pictures on your phone — this takes longer than a page |
| `imagesNoRecipeTitle` | `webclipper_images_no_recipe_title` | No recipe found in these pictures |
| `imagesNoRecipeMessage` | `webclipper_images_no_recipe_message` | The model read the pictures but found no ingredients or steps. Try clearer or closer shots. |
| `imagesUnsupportedTitle` | `webclipper_images_unsupported_title` | This model can't read images |
| `imagesUnsupportedMessage` | `webclipper_images_unsupported_message` | Pick a model that reads images in Models. |
| `backToLibrary` | `webclipper_extraction_back_to_library` | Back to library (app override: "Back to your recipes") |
| `clipAlreadyRunning` | `webclipper_clip_already_running` | Finish the current clip first |

Test fixture `TestWebClipperLabels` gains the fields (each renders its own name).

---

## 3. Voice source (placeholder only)

- Visible, disabled row in the chooser (§1.3) with "Coming soon"; no route, no permission, no manifest entry.
- Contract it will plug into (frozen in the plan, unused now): `ClipInput.Voice(transcript: String, language: String?)` → one `ClipCandidate` with `CandidatePayload.Transcript`, `WebClipConfig.renderLlmInput` renders the transcript as the user turn, no pictures, `origin = VOICE` (another text enum value, no migration). The future UX (record button, live transcript, "Stop" → extraction) is out of scope; only the enum value and the sealed subtype exist so the chooser's `RecipeSource.VOICE` has a destination type to grow into.

---

## 4. Navigation map additions

| Destination | Route | Presentation | Arrives from |
|---|---|---|---|
| Source chooser | state-driven (`LibraryState.sourceChooserOpen`) | `ModalBottomSheet` over Library | "+" action, empty-state "More ways to add…" |
| Images unavailable | state-driven (`LibraryState.imagesUnavailableDialog`) | `AlertDialog` | chooser row (text-only model) |
| Images clip (prepare + review) | `clip/images` | full screen, own `Scaffold` | chooser; share inbox |
| Extraction (images) | `extract/images` (existing pattern, `candidateId = "images"`) | existing feature screen | `clip/images` |
| Browser via URL share | `browser?url=` (existing) | existing | share inbox |

Back-stack rules:
- Library → (sheet) → `clip/images` → system back / Close: temp files deleted, pop to Library. No "discard?" dialog (nothing typed; re-picking is cheap).
- `clip/images` → Extract: `navigate("extract/images") { popUpTo("clip/images") { inclusive = true } }`. Extraction Cancel / "Back to library" → `popBackStack()` lands on Library.
- Extraction "Pick different pictures" → `navigate("clip/images")` with the prepared images carried in `ClipSession` (the route reads `session.current.input as? Images` before showing the picker) and `popUpTo("extract/images") { inclusive = true }`.
- Saved → `navigate("recipe/{id}") { popUpTo("library") }`; back from the detail → Library (there is no page to return to; cf. app plan A18 which is web-only).
- Share arrival while on any screen: `clip/images` is pushed on top with `launchSingleTop`; back returns to where the user was. Share arrival during an extraction: dropped with a snackbar (§2.2).
- URL share: `navigateToBrowser(url)` (`launchSingleTop`), exactly like "Open original".
- `MainActivity` becomes `singleTop`; nothing else about the task changes (`ShareReceiverActivity` is `noHistory`, `excludeFromRecents`).

ViewModels: `LibraryViewModel` (+ chooser state, `ChooseSource`, `ObserveInputCapabilitiesUseCase`), new `ImagesClipViewModel(pendingUris?, SavedStateHandle, prepareImages, discardPreparedImages, observeInputCapabilities, config, session, labels, dispatchers)`; `ExtractionViewModel` unchanged in shape (reads `session.current.input`).

---

## 5. Reuse vs new

Reuse as-is: `ModalBottomSheet`, `ListItem`, `AlertDialog`, `EmptyState`, `GradientHero`, `NumberBadge`, `SkeletonLine`, `PhaseProgress`, `WebClipperExtractionScreen` (+ small conditional branches), `ClipDraftCard`, `PickMultipleVisualMedia`/`PickVisualMedia`, Coil `AsyncImage` for file tiles.

New, app-only: `SourceChooserSheet`, `ImagesUnavailableDialog`, `ShareReceiverActivity`, `ShareIntentParser`, library/detail copy branches for `origin == IMAGES`, labels.

New, feature (`:feature:webclipper`): `ImagesClipRoute`/`ImagesReviewScreen`/`ImagesClipViewModel` (+ MVI), `ImageTile`, `ClipInputInbox`, `PrepareImagesUseCase`, images branches in `ClipUseCase`/extraction screen, labels.

New, core: `LocalImageImporter` in `:core:web` (content URI → downscaled JPEG, EXIF, tiling helper), `LlmRequest.images` + capabilities in `:core:domain`, vision wiring in `:core:llm`, `ModelCapability` in `:core:model`. No `:core:designsystem` change.

---

## 6. Accessibility checklist

- [ ] "+" action `contentDescription = addRecipe`; the sheet title is a heading; rows are single merged nodes with headline + supporting + status; row ≥ 72dp; chevrons/icons decorative.
- [ ] Voice row `disabled()` + `stateDescription = comingSoon`; not hidden from accessibility.
- [ ] Images row states read their reason (supporting text in the node); the unavailable dialog's confirm "Open models" and dismiss "Cancel" ≥ 48dp; dialog traps focus.
- [ ] Review grid: tiles `imageTileDescription(i, n)` + `stateDescription` when selected; `customActions` Move left / Move right / Remove on every tile; arrow buttons and ✕ ≥ 48dp; Add tile `Role.Button` + `addImages`; skeleton tiles `invisibleToUser()` while preparing.
- [ ] Order change announced: after a move, one invisible live-region `Text(liveRegion = Polite)` says `imageTileDescription(newIndex, n)`.
- [ ] Primary "Extract recipe" is in `bottomBar`, reachable with TalkBack linear navigation after the grid, above the gesture bar.
- [ ] Extraction: phase changes announced through the existing live region; source strip icon decorative; "Pick different pictures" and "Back to library" are `Button`/`TextButton` ≥ 48dp.
- [ ] Contrast: supporting/status text `onSurfaceVariant` on `surfaceContainerLow` (sheet) ≥ 4.5:1 with `RecipeKeeperTokens`; disabled row at 38 % is exempt (M3 disabled), but its "Coming soon" label stays at full `onSurfaceVariant` so the reason remains legible.
- [ ] Reduced motion: sheet uses M3 defaults; no shimmer added to skeleton tiles.
- [ ] Insets: sheet relies on M3 insets; review `Scaffold(contentWindowInsets = safeDrawing)` + grid `contentPadding`; no `imePadding()` (no text field on these screens).
- [ ] 600dp+: sheet max width 640dp centred; review content capped at 720dp; extraction unchanged (36 % left column in landscape per the base spec).
- [ ] Share target: `ShareReceiverActivity` has no UI to narrate; the first narrated thing is the review screen title or the no-model hero, both headings.

---

## 7. Motion

- Sheet: M3 default enter/exit; rows have the default ripple.
- Review tiles: `Modifier.animateItem()` so Move/Remove/Add animate instead of jumping; selected outline animates with `animateColorAsState` 150 ms.
- Preparing → review: tiles crossfade from skeleton to image (`AnimatedContent` per tile, 200 ms).
- Extraction: unchanged (`PhaseProgress`, draft card fades).

---

## 8. Ranked risks / open questions

1. **Vision feasibility and speed on CPU** — the gate. If prefill per image on a mid-range phone exceeds ~60 s, ship the chooser with the images row hidden and keep the share target for URLs only; revisit with GPU/NPU variants.
2. **OCR accuracy on quantities** (½ vs 1/2, 15 g vs 15 q) — the eval scores this; mitigation is the editor (already shipped) and the "Possibly incomplete" path; consider a post-extraction nudge "Check the quantities" chip for images clips (Phase 2).
3. **Tall screenshots** — single downscaled capture may lose small text; tiling multiplies prefill cost. Eval decides; both are behind `ImagePolicy`.
4. **FAB stays web (S1)** — the user may prefer the FAB to open the chooser; the flip is one intent change and the sheet already lists the web first. Decide after a dogfooding round.
5. **Share grants** — forwarding `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` from the receiver to `MainActivity` is the documented pattern, but some senders put URIs only in `EXTRA_STREAM`; the parser must re-attach them to `ClipData` before forwarding, and `PrepareImagesUseCase` must copy before any navigation animation ends (it runs in `init` of the ViewModel).
6. **`singleTop` MainActivity** — changes nothing for a single-activity app, but every `onNewIntent` path (none today) must be reviewed; the inbox makes the NavHost the only consumer.
7. **Screenshots as hero pictures** look poor in the grid — accepted for v1 (user picked them; editor "Use as cover" / remove exists). Alternative: default-check only the first image. Flagged for QA feedback.
8. **Memory on 4 GB devices** when the vision tower loads next to the WebView — the eval reports the tower's cost; `minRamMb` on vision descriptors may rise to 6 GB and the images row would then also depend on device RAM (the model list already shows the warning; the chooser does not re-check it in v1).
9. **Duplicate screenshots** — no check in v1; a `sourceKey` column (Room v4) with the SHA-256 of the image set is the Phase 2 mechanism and would also give images clips a real "Re-extract" (from the saved pictures).
10. **"Reads images" tag in model management** touches a shared label class and screen; small but cross-brand (recipes/medical must fill the new field). Listed in I3 with the exact field.
11. **Voice contract drift** — `ClipInput.Voice(transcript, language)` is a guess at the future; keep it minimal so the real voice stream can reshape it without touching the chooser.
12. **Eval numbers in copy** — "under a minute per picture" is a placeholder; I4 confirms on the emulator and a phone before release.
