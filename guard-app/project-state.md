# PROJECT_STATE.md

## What this is
Android app that introduces deliberate friction between urge and action for compulsive porn use, 
as a harm-reduction alternative to hard blocking (which backfired in a prior attempt via 6-7 day locks). 
Goal: delay, not deny — exploit the fact that urges peak and decay within minutes if not acted on.
Future phases (not yet started): extend friction mechanism to Reels/Shorts and gaming.

## Core mechanism (why, not just what)
- Urges are time-limited (Marlatt's urge surfing / craving curve) — a 5-15 min delay lets the urge decay naturally.
- Hot-cold empathy gap (Loewenstein) — delay gives the rational system time to re-engage before acting.
- Long/total restriction triggers reactance (Brehm) and ironic suppression effects (Wegner) — this is why 
  the previous 6-7 day lock failed. Short delay avoids this failure mode.
- Friction interrupts automaticity (Wood & Neal) — cue-to-action habit loops require the routine step to run 
  with minimal deliberation; forcing conscious engagement breaks that.

## Architecture decisions
- **Detection**: Android has no hosts-file access without root (unlike prior desktop version). 
  Using VpnService API (local loopback VPN, not routing through remote server) to inspect/block DNS 
  requests to porn domains. Same pattern used by NetGuard, One Sec.
- **Persistence/watchdog**: Foreground service checks VPN/filter is still active. Android shows a 
  persistent notification whenever a VPN is active — built-in accountability signal, not something 
  we need to build ourselves.
- **Uninstall/disable friction**: Accessibility Service detects navigation toward Settings > App Info > 
  Uninstall/Force Stop and interrupts (cooldown + reflection prompt) rather than allowing immediate action. 
  Does not make removal impossible — raises cost/delay, consistent with the friction-not-restriction 
  philosophy of the whole app.
  - Parked for later (v2, only if v1 proves the mechanism works but isn't sticky enough): Device Owner 
    mode via factory reset + QR provisioning, for actual non-removability. Heavyweight, not needed for v1.
- **Anti-habituation design** (fixes a failure mode identified before coding started):
  - Randomize delay within a band (5-15 min), not fixed, to resist habituation.
  - Require active engagement during wait (short typed reflection: what triggered this, what you're 
    feeling) rather than a passive countdown that can be idled out.
  - Escalate delay based on same-day frequency (tie friction strength to actual behavior data, not 
    a static constant).

## Dev environment / tooling decision
- **NOT using Android Studio** — laptop can't sustain it (heavy IDE + bundled emulator + indexing + 
  Gradle daemon running simultaneously).
- Using **VS Code** as editor (Kotlin language extension for syntax/completion) + **Gradle CLI** 
  (`./gradlew assembleDebug`, etc.) for builds, run outside the IDE shell.
- **No emulator** — testing happens on physical Android phone via USB debugging (adb). This is 
  actually preferable here regardless of hardware constraints, since VpnService and Accessibility 
  Service behavior should be validated against real hardware/OS behavior, not emulator approximations.
- **No visual layout editor** — UI written by hand (XML layouts or Compose). Acceptable trade-off: 
  this app's UI surface is small (lock screen, reflection prompt, settings page), doesn't need 
  drag-and-drop tooling.
- Still requires: Android SDK command-line tools + `adb`, installed standalone (not via Android Studio's 
  installer wizard).

## Current stage
Not yet started writing code. Dev environment decided. Next: install/verify SDK + adb + Gradle setup 
in VS Code before touching app logic.

## File/module map
(empty — to be filled in as files are created)

## Next steps
1. Install & verify: JDK, Android SDK command-line tools, adb, Gradle — confirm phone connects via 
   `adb devices` before writing any app code
2. Set up minimal Android project skeleton by hand (build.gradle, AndroidManifest.xml, one empty 
   activity) — no Android Studio project wizard, so this is manual/templated
3. Implement VpnService domain-blocklist MVP (porn domains only, hardcoded list to start)
4. Implement foreground service watchdog
5. Implement Accessibility Service for both (a) URL/trigger detection and (b) uninstall friction
6. Wire the three components together (delay logic + reflection prompt UI)
7. Add logging (ties into existing Notion behavior-log habit — consider exporting app's own trigger 
   log in same format for consistency)

## Known issues / open questions
- Android allows only one active VPN at a time — will conflict if user has another VPN app running.
- VpnService + Accessibility Service both require the user to grant significant permissions during 
  setup — first-run flow needs to explain why, or user (hot-state self) may just decline them.
- No solution yet for detecting incognito/private browsing tabs specifically — VPN-level DNS blocking 
  works regardless of browser mode, so this may be a non-issue, but needs verification once built.
- No Android Studio means no visual debugger UI — debugging via `adb logcat` and CLI tools only. 
  Slightly steeper but standard for CLI-based Android dev.

## Tooling workflow (for context across AI tool switches)
- Primary: Claude chat for step-by-step guidance + code for single well-scoped units (one file/function 
  at a time). Architecture and interface decisions happen here, get written into this doc, before any 
  agentic tool touches code.
- Agentic tools (Cursor/Copilot): reserved for multi-file integration/wiring and build-error debugging 
  loops only — not for boilerplate or single-function generation, to conserve limited free-tier quota.
- Secondary chat (ChatGPT, then Gemini): boilerplate/volume generation once architecture is decided, 
  using this doc as pasted context.
- This file is updated at the end of every session, before switching tools or accounts.