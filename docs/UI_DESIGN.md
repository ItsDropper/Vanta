# Vanta UI design system

This is the contract for every view under `ui/` and every stylesheet under
`resources/css/`. Read it before changing either.

## Intent

Vanta is a tool for people who manage many Minecraft setups. The interface is
a workbench: quiet graphite surfaces, one gold accent used for the single
action that matters, hard-edged pixel art where there is a picture, and exact
mono type wherever a value is technical (versions, sizes, paths, durations).

What makes it recognisable:

- **Cover art.** Every instance has a procedural pixel-art landscape
  (`kit/CoverArt`), seeded from its name and tinted by its loader. Instance
  cards, the Home hero and detail headers are built around it.
- **Loader colour is information.** Fabric sand, Forge steel, NeoForge ember,
  Quilt violet, Vanilla green. Use `Badge.loader`, `InstancePlate`,
  `LoaderTheme`. Never use these hues decoratively.
- **Layered surfaces, not shadows.** Cards are a 1px hairline, a fill and a
  faint top gloss, stacked as background layers (see `.kit-card`).
- **Mono for values.** `.value-mono`, `.value-mono-strong`, `Badge.mono`.

## Tokens (tokens.css, ThemeManager)

Theme colours come from `ThemeManager` as looked-up colours:
`-vantabg -vantasurface -vantasurfaceraised -vantasurfacehover -vantaborder
-vantatext -vantamuted -vantasubtle -vantaaccent -vantaaccenthover
-vantaaccentforeground -vantasuccess -vantadanger` plus the accent alpha
ladder `-vantaaccent-a10 -a18 -a30 -a55`.

Derived tokens (use these, do not invent hex values):

| Token | Use |
|---|---|
| `-v-hair`, `-v-hair-strong` | 1px outlines as the bottom background layer |
| `-v-gloss` | top highlight of a surface |
| `-v-frame` `-v-page` `-v-card` `-v-card-hover` `-v-well` | surface ladder |
| `-v-success-soft` `-v-danger-soft` `-v-warning(-soft)` `-v-info(-soft)` | status fills |
| `-v-loader-fabric/forge/neoforge/quilt/vanilla` | loader hues |

Scale: spacing 4 8 12 16 24 32. Radius: control 8/7, card 12/11, panel 14/13.
Type: 11 / 12 / 13 / 15 / 22 (Geist; Geist Medium and Geist SemiBold are
separate families in JavaFX, so choose weight via `-fx-font-family`).

### Surface recipe

```css
-fx-background-color: -v-hair-strong, -v-card, linear-gradient(to bottom, -v-gloss, transparent 55%);
-fx-background-insets: 0, 1, 1;
-fx-background-radius: 12, 11, 11;
```

Focus ring: `-fx-border-color: -vantaaccent-a55; -fx-border-width: 2px;
-fx-border-insets: -2px;` (every interactive thing needs `:focused` and
`:hover` states).

## Kit (`ui/kit`)

Compose these before writing new CSS. Each has its styles in `utilities.css`.

| Component | Purpose |
|---|---|
| `CoverArt(seed, loader).rounded(r).sized(w,h)` | instance picture |
| `InstancePlate(iconName, loader, size)` | small tinted icon tile |
| `Badge`, `Badge.mono/loader/status` | metadata and status |
| `Chip` | filter chip (toggle) |
| `SegmentedControl<T>` | one of N options (view mode, sort, tabs-lite) |
| `SearchField(prompt)` | search input with icon and clear |
| `IconButton(icon, label[, style])` | square icon button with tooltip |
| `SectionHeader(title, description, actions...)` | titled block header |
| `Card`, `Card.titled(...)`, `.interactive()` | raised surface |
| `StatTile(name, value, hint)` | one figure |
| `ActionTile(icon, title, description, action)` | large clickable row |
| `KeyValueRow(key, value)` | label / mono value |
| `Callout(tone, title, message, actions...)` | inline notice |
| `Switch` + `SettingRow(title, description, control)` | settings |
| `FormField(label, control)` + `.hint()/.error()` | forms |
| `Toolbar(...)` + `Toolbar.spacer()` | search, filters, actions row |

Buttons: `VantaUI.styleButton(button, variant)` or the classes
`vanta-button-primary | -secondary | -quiet | -danger`, with
`button-compact` / `button-large` modifiers. One primary button per view.

Icons: `IconView.create(type, size)`; new glyphs go in `IconView.Type`.

## Page anatomy

```
PageHeader        title (22 semibold) + one short description        actions (right)
Toolbar           SearchField · SegmentedControl · Chips ........... primary action
Body              cards / grid / list on the page sheet
```

- Spacing between blocks is 20, inside cards 12–16.
- One primary action per view, top right or in the hero. Everything else is
  secondary or quiet.
- Nested pages (an instance's mods, a project's details) show where they live
  with the back button and a short title; do not add breadcrumb strings.

## Rules

1. Sentence case. No ALL CAPS labels and no `toUpperCase()` on UI text. No
   tracked-out eyebrows above headings, no `·` separated meta strings: use
   badges or a `KeyValueRow`.
2. No one-sided coloured borders as an accent. No gradient washes as
   decoration (cover art is the one picture).
3. Text is minimal and in the user's terms: "Play", "Add mods", not taglines.
   Empty states say what to do. Errors say what happened and how to fix it.
4. No hard-coded colours in Java. Use style classes and the tokens. Remove
   `setStyle(...)` calls that only set colours or fonts.
5. Do not change behaviour: handlers, services, navigation, localisation keys
   and public method signatures stay. New strings go in `messages.properties`
   and `messages_en.properties` (other locales fall back to the root bundle).
6. Motion answers an action (press, open, expand). No decorative entrance
   animation on every card.
7. Keep CI working: `--capture-ui-screenshots` must still produce every
   screenshot and each must differ.
8. Responsive: pages must work from 1180x760 upward without horizontal
   scrolling; use `FlowPane`/`TilePane`/grow priorities, not fixed widths.

## Files

| Stylesheet | Owner area |
|---|---|
| `tokens.css` | tokens, type roles, surface roles, page chrome |
| `base.css` | standard controls, button vocabulary |
| `utilities.css` | the kit |
| `shell.css` | window, sidebar, title bar, palette, account menu, sign-in, notifications |
| `home.css` | Home |
| `library.css` | Instances, instance cards |
| `content.css` | installed mods, global mods, screenshots |
| `browse.css` | content browser, project details, modpacks, markdown |
| `manage.css` | accounts, skins, servers, downloads, state centre, crash lab |
| `create.css` | create, import, presets, instance settings, repair |
| `settings.css` | settings, onboarding, experimental |
| `progress.css` | progress dialogs |
| `vanta.css` | cross-cutting variants that must win the cascade |

`Stylesheets.java` lists them in cascade order. A feature stylesheet defines
only the classes its views use, built from tokens and kit classes. Delete a
class from Java and CSS together when it stops being used.
