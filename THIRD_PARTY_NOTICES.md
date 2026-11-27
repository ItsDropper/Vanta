# Third-party notices

## minecraft2shell-fix Java agent

Vanta downloads the upstream `m2sfix-agent-2.3.0.jar` at runtime when Mojang's
official Minecraft metadata still selects an unpatched FreeType build.

- Project: https://github.com/sunrisemc-tw/minecraft2shell-fix
- Release: https://github.com/sunrisemc-tw/minecraft2shell-fix/releases/tag/v2.3.0
- SHA-256: `c073f82a460559151f720f635266077248807eb6939a0eee4aa10d5081405bbe`
- License: MIT
- Copyright: Copyright (c) 2026 duckducktw

The upstream agent is a temporary client-side mitigation, not a Mojang fix.
It disables font hinting in its default safe policy and applies an additional
image-input guard. This changes small-font rendering slightly. Upstream testing
covers macOS game clients; Windows has not been reported as tested. Vanta
verifies the downloaded artifact against the pinned SHA-256 digest and checks
its Java-agent manifest before attaching it to Minecraft.
