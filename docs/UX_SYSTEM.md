# Vanta UX System

This document defines the interaction contract for Vanta's JavaFX interface. It is intended to prevent new screens from reintroducing the inconsistent styling and dead-end states the shared UX layer is designed to eliminate.

## Visual language

- Use the looked-up tokens from `ThemeManager`: `-vanta-bg`, `-vanta-surface`, `-vanta-surface-raised`, `-vanta-surface-hover`, `-vanta-border`, `-vanta-text`, `-vanta-muted`, `-vanta-subtle`, `-vanta-accent`, `-vanta-danger`, and `-vanta-success`.
- Prefer one clear primary action per view. Secondary actions should not compete with the primary action.
- Use 8px control radii and 12px surface radii unless a component has a strong reason to differ.
- Keep hierarchy legible: page title, short supporting description, section label, content, then contextual actions.
- Avoid hard-coded green or blue for normal states; theme tokens must drive accent and status colors.
- Avoid decorative effects that reduce contrast or obscure content.

## Geometry, rhythm and typography

- Use a 4 px base spacing unit and multiples of 8 px for common gaps. Prefer VantaUI spacing helpers over unrelated scales.
- Shell and major-panel corners use a fourth-order superellipse (squircle), with normalized boundary |x/a|^4 + |y/b|^4 = 1. The curve is a clipped JavaFX path, independent of CSS circular corner radii.
- Use shared radius constants for windows, panels and cards. Shape hierarchy should follow component scale; do not apply the largest radius to tiny controls.
- Use a restrained type scale: 11 px metadata, 12–13 px control/body text, 15–16 px section labels, and 22–28 px page titles. Preserve platform text rendering and allow content to wrap.
- Keep colors semantic and theme-derived. Target WCAG AA contrast: 4.5:1 for normal text and 3:1 for large text and meaningful UI boundaries. Validate actual theme pairings.
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

Run the production sidebar capture locally:

```bash
./gradlew run --args="--capture-sidebar-screenshots build/ui-screenshots"
```

This generates one PNG per Sidebar.Page, a 3×3 overview contact sheet, and an index. CI captures the production views under Xvfb and uploads them as the vanta-sidebar-screenshots artifact. Capture mode skips account loading, update checks, onboarding, telemetry handlers/events and active-view lifecycle refreshes. On Linux, set APPDATA to a writable temporary directory because the launcher currently targets Windows roaming-data paths.

## Motion and responsiveness

- Interactive animation handlers must be additive; they must never replace a control's click, press, or release behavior.
- Stop an existing scale transition before starting another so hover and press states cannot race.
- Respect the user's animation setting even after controls have been created.
- Newly inserted controls should receive the same interaction behavior as controls present at first render.
- Pause view-specific polling when a view is detached, and ignore stale asynchronous refresh results.
