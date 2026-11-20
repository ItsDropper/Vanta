# Vanta UX System

This document defines the interaction contract for Vanta's JavaFX interface. It is intended to prevent new screens from reintroducing the inconsistent styling and dead-end states the shared UX layer is designed to eliminate.

## Visual language

- Use the looked-up tokens from `ThemeManager`: `-vanta-bg`, `-vanta-surface`, `-vanta-surface-raised`, `-vanta-surface-hover`, `-vanta-border`, `-vanta-text`, `-vanta-muted`, `-vanta-subtle`, `-vanta-accent`, `-vanta-danger`, and `-vanta-success`.
- Prefer one clear primary action per view. Secondary actions should not compete with the primary action.
- Use 6px control radii, 8px card radii and 10px sheet/window radii unless a component has a strong reason to differ.
- Keep hierarchy legible: page title, short supporting description, section label, content, then contextual actions.
- Avoid hard-coded green or blue for normal states; theme tokens must drive accent and status colors.
- Avoid decorative effects that reduce contrast or obscure content.

## Design system stylesheet

`css/vanta.css` is loaded after every other stylesheet and owns the visual language. Earlier files keep structure and legacy page selectors; new visual rules go in `vanta.css` and derive colours from the `ThemeManager` tokens instead of hard-coding hex values. `ThemeManager.apply` adds the `vanta-ui-root` class, so popups, dialogs and secondary windows that are themed through it receive the same rules.

Derived tokens defined on `.vanta-ui-root`: `-vantaframe` (window chrome, darker than the sheet), `-vantaline` (hairline borders), `-vantalinestrong` (control borders), `-vantahover`, `-vantaselected`, `-vantaicon`, `-vantaaccentwash`/`-vantaaccentline`, `-vantadangerwash`/`-vantadangerline`, `-vantasuccesswash` and `-vantascrim`.

Surface ladder, dark to light: frame (title bar, sidebar) → sheet (`-vantabg`, the content area) → surface (cards) → raised (popovers, hover, selected rows).

- The sidebar and title bar belong to the frame; pages live on one inset sheet (`app-content`). Navigation groups are separated by space, not labels or rules.
- Controls share one height (32 px; the launch button is 40 px) and three emphases: primary (accent fill), secondary (surface with border) and quiet (text only), plus a danger treatment.
- Cards are flat: hairline border, 8 px radius, no shadow. Selection uses an accent border and wash rather than a filled pill.
- Icons are 24-unit outlines with a 2-unit stroke drawn by `IconView`; colour comes from CSS (`.vanta-icon` strokes, `.vanta-icon-solid` fills), so they follow hover, selection and button variants.

## Geometry, rhythm and typography

- Use a 4 px base spacing unit and multiples of 8 px for common gaps. Prefer VantaUI spacing helpers over unrelated scales.
- Shell and major-panel corners are plain CSS radii. `VantaUI` keeps an optional fourth-order superellipse clip, but it is disabled in the live scene.
- Use shared radius constants for windows, panels and cards. Shape hierarchy should follow component scale; do not apply the largest radius to tiny controls.
- Use a restrained type scale: 11 px metadata and eyebrows, 12 px control and secondary text, 13 px body, 15 px empty-state and dialog titles, and 20 px page titles. Use weights 400, 500 and 600 only. Preserve platform text rendering and allow content to wrap.
- Keep colors semantic and theme-derived. Target WCAG AA contrast: 4.5:1 for normal text and 3:1 for large text and meaningful UI boundaries. `ThemeManager` automatically adjusts primary, muted and subtle foreground tokens to at least 4.5:1 against the active raised/hover surface while preserving hue as much as possible.
- Motion is feedback, not decoration: prefer short opacity/translation transitions, avoid layout animation, and respect the user's animation preference.
- A squircle is a mathematical primitive, not proof that one curve is universally more beautiful. Validate actual screens at target window sizes, including keyboard/focus states.

## Interaction contract

- Every non-empty page must have an obvious next action. Empty states should explain what belongs there and provide a useful action when one exists.
- Controls must expose meaningful accessible text. Icon-only window controls need tooltips and accessible names.
- Hover, pressed, disabled, and keyboard-focused states must be distinguishable.
- Do not make essential functionality available only by clicking a large card. Provide a real focusable control for keyboard users.
- Keep long content inside a scrollable viewport; never hide actions below a fixed-height container.
- Destructive actions need explicit wording and a visually distinct danger treatment.
- Loading and failure states should explain what Vanta is doing and what the user can do next; never report success before the underlying operation succeeds.

## Keyboard navigation

| Shortcut | Destination |
| --- | --- |
| Ctrl+N | Create a new instance |
| Ctrl+1 | Home |
| Ctrl+2 | Instances |
| Ctrl+3 | Mods |
| Ctrl+4 | State |
| Ctrl+5 | Skin changer |
| Ctrl+6 | Accounts |
| Ctrl+7 | Settings |

Shortcuts are global navigation only and must not intercept ordinary text-editing shortcuts. Navigation buttons expose their shortcuts through tooltips and accessible labels.

## Verification checklist

For any significant UI change:

1. Run `./gradlew test` and the existing Java compile workflow.
2. Verify every destination with mouse and keyboard.
3. Check empty, loading, success, failure, and disabled states.
4. Verify theme changes affect shell, controls, surfaces, and overlays consistently.
5. Confirm window dragging does not steal clicks from interactive title-bar controls.
6. Ensure a first-run user can create or import an instance without having to discover a hidden workflow.
7. Preserve existing user data and settings; UI polish must not alter launcher behavior.

## Empty states

Use the shared `EmptyState` component whenever a page has no content to display. Empty states should identify what belongs in the region, explain why it is empty, and provide the most direct next action when one exists. Avoid leaving a large blank panel or using a perpetual loading message when no operation is actually running.

## Screenshot review

Run the complete production UI capture locally:

```bash
./gradlew run --args="--capture-ui-screenshots build/ui-screenshots"
```

The capture includes one PNG per sidebar destination plus nested production views such as instance mods, screenshots, content browsing and details, instance creation/import, presets, modpack browsing, instance settings and repair. The original nine sidebar images, index and overview remain at the output root; nested-view PNGs, index and overview are stored in `additional/`. CI captures both sets under Xvfb and uploads them as the `vanta-ui-screenshots` artifact. Capture mode skips account loading, update checks, onboarding, telemetry handlers/events and active-view lifecycle refreshes. A temporary preview instance is used for instance-scoped screens; no user instance is modified. On Linux, the launcher uses XDG_DATA_HOME or ~/.local/share; no Windows APPDATA override is required.

## Motion and responsiveness

- Interactive animation handlers must be additive; they must never replace a control's click, press, or release behavior.
- Stop an existing scale transition before starting another so hover and press states cannot race.
- Respect the user's animation setting even after controls have been created.
- Newly inserted controls should receive the same interaction behavior as controls present at first render.
- Pause view-specific polling when a view is detached, and ignore stale asynchronous refresh results.


## Typeface

Vanta ships Poppins (SIL Open Font License, `src/main/resources/fonts/OFL.txt`) and registers it in `VantaFonts`.
JavaFX chooses a weight by family name, so stylesheets never use numeric `-fx-font-weight`:

| Role | Family |
| --- | --- |
| Body, descriptions, inputs | `"Poppins"` (Regular) |
| Buttons, labels, navigation, list item names | `"Poppins Medium"` |
| Page and section titles, emphasis | `"Poppins SemiBold"` |
| Technical text (logs, paths) | monospace, unchanged |
