# Vanta UX System

This document defines the interaction contract for Vanta's JavaFX interface. It is intended to prevent new screens from reintroducing the inconsistent styling and dead-end states the shared UX layer is designed to eliminate.

## Visual language

- Use the looked-up tokens from `ThemeManager`: `-vanta-bg`, `-vanta-surface`, `-vanta-surface-raised`, `-vanta-surface-hover`, `-vanta-border`, `-vanta-text`, `-vanta-muted`, `-vanta-subtle`, `-vanta-accent`, `-vanta-danger`, and `-vanta-success`.
- Prefer one clear primary action per view. Secondary actions should not compete with the primary action.
- Use 8px control radii and 12px surface radii unless a component has a strong reason to differ.
- Keep hierarchy legible: page title, short supporting description, section label, content, then contextual actions.
- Avoid hard-coded green or blue for normal states; theme tokens must drive accent and status colors.
- Avoid decorative effects that reduce contrast or obscure content.

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
