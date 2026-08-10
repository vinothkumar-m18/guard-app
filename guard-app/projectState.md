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

## IMPORTANT CURRENT STATUS FLAG
The app currently performs INSTANT, PERMANENT blocking of porn domains — not the delay/friction 
mechanism that is the actual core idea. We built the detection/blocking engine first (harder 
technical problem). The 5-15 min randomized delay + reflection-prompt mechanism (the actual 
"friction, not restriction" design) is NOT YET BUILT. This is the next major stage.

## Architecture decisions

### Detection & blocking (BUILT, confirmed working)
- Android has no hosts-file access without root (unlike prior desktop version). Using VpnService 
  API (local loopback VPN) — but SCOPED to DNS-only routing, not all traffic (see below).
- **Selective routing** (not "route everything"): VPN only intercepts DNS (port 53) traffic, plus 
  narrow /32 routes to known DoH resolver IPs (8.8.8.8, 8.8.4.4, 1.1.1.1, 1.0.0.1) to prevent 
  browsers bypassing regular DNS via DNS-over-HTTPS. All other traffic (actual page/video content) 
  bypasses the tunnel entirely — full native speed, confirmed via testing (Wikipedia/YouTube/X/
  W3Schools all load normally).
- **DNS relay logic** (DnsHandler.kt): for allowed domains, opens a protected UDP socket (via 
  VpnService.protect() to avoid routing loop), forwards the real query to 8.8.8.8, relays the 
  real answer back. For blocked domains, constructs a fake NXDOMAIN response directly and returns 
  it — browser sees a standard "domain doesn't exist" failure before ever reaching the site's IP.
- **DNS packet parsing** (DnsParser.kt): hand-parses raw IP/UDP/DNS packet structure to extract 
  queried domain names — no external library needed, DNS uses a fixed length-prefixed label format.
- **Blocklist**: uses StevenBlack/hosts "porn-only" curated list (76,749 domains), bundled as a 
  local asset (app/src/main/assets/porn_blocklist.txt), loaded into an in-memory HashSet at VPN 
  startup (BlocklistLoader.kt). Replaced an earlier 3-domain hand-typed list, which was easily 
  evaded by mirror domains (e.g. xhamster's own xh*-branded bypass domains: xhopen.com, 
  xhaccess.com, xhamster46.desi).
- **AMP-cache fallback**: small keyword list (xhamster, pornhub, xvideos, xnxx) specifically to 
  catch AMP-cache-proxied content (e.g. amp-xhamster-com.cdn.ampproject.org), which uses Google's 
  own domain rather than the original site's — blocklist membership alone doesn't catch this 
  pattern since it's a different domain entirely.

### NOT YET BUILT
- **Delay/friction mechanism**: randomized 5-15 min delay + forced reflection input before 
  allowing access, instead of current instant/permanent block. This is the actual core idea 
  from the original concept — not yet implemented.
- **Anti-habituation design** (planned, not built): randomize delay within a band (not fixed), 
  require active engagement during wait (typed reflection) rather than passive countdown, 
  escalate delay based on same-day frequency.
- **Uninstall/disable friction**: Accessibility Service to detect and delay navigation toward 
  Settings > App Info > Uninstall/Force Stop. Not started.
  - Parked for v2 (only if v1 proves mechanism works but isn't sticky enough): Device Owner mode 
    via factory reset + QR provisioning, for actual non-removability.
- **UI polish**: currently just one button ("Start Guard VPN") and a debug log view. No real 
  screen design yet.

## Known limitations (accepted for v1, not blockers)
- Raw IP address entry (typing an IP directly instead of a domain) bypasses DNS-based blocking 
  entirely. Rare in practice for porn site usage. v2 hardening item.
- Android Private DNS (DNS-over-TLS, port 853) is a separate potential bypass from DoH — not yet 
  investigated/closed. Needs testing: check Settings > Network & Internet > Private DNS behavior 
  against our VPN.
- isDomainBlocked() does an O(n) loop over all 76,749 entries per DNS query for the subdomain-match 
  case. Works for testing; should be optimized to O(number of domain levels) by checking parent-
  domain segments directly against the HashSet.
- Blocklist load takes ~7 seconds on VPN service startup (one-time, not per-query). Acceptable for 
  now; could be moved earlier/made async later.
- Some blocklist entries (e.g. cdn.tsyndicate.com) are generic ad/syndication domains also used by 
  adult sites — possible rare false-positive blocks on unrelated content. Accepted given stated 
  priority: no porn leaks > zero overblocking.
- Android allows only one active VPN at a time — will conflict if user has another VPN app running.

## Dev environment / tooling decision
- **NOT using Android Studio** — laptop can't sustain it. Using **VS Code** (Kotlin extension) + 
  **Gradle CLI** (`./gradlew assembleDebug`) for builds, outside the IDE shell.
- **No emulator** — testing on physical Android phone (Vivo/OriginOS) via USB debugging (adb).
- **No visual layout editor** — UI written by hand (XML layouts).
- Requires: JDK 17 specifically (not newer versions — AGP/Gradle compatibility), Android SDK 
  command-line tools + adb, installed standalone.

## Debugging method (IMPORTANT — logcat does not work on this device)
- **adb logcat is suppressed for third-party app logs on this Vivo/OriginOS device.** Confirmed 
  via full-buffer + PID-filtered logcat showing zero custom log lines despite OS-level lifecycle 
  events being visible. Do NOT rely on logcat or Toast messages (Toast also unreliable due to 
  Android's rate-limiting on rapid successive toasts) for debugging on this project.
- **Standard debugging method going forward**: file-based logger (FileLog.kt) writes timestamped 
  lines to the app's internal storage (files/guard_log.txt). Pull it to the project dir with:
  ```
  ~/android-sdk/platform-tools/adb shell run-as com.vinoth.guardapp cat files/guard_log.txt > guard_log.txt && cat guard_log.txt
  ```
- Common commands used throughout:
  - Install/reinstall: `~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - Launch app directly (bypasses tapping icon): `~/android-sdk/platform-tools/adb shell am start -n com.vinoth.guardapp/.MainActivity`
  - Build: `./gradlew.bat assembleDebug > build_output.log 2>&1` then `cat build_output.log`
- Note: `adb` PATH resolution has a known quirk on this Windows/Git Bash setup — sometimes shows 
  literal `%ANDROID_HOME%` unexpanded. Workaround: call adb via full path 
  `~/android-sdk/platform-tools/adb` if the bare `adb` command isn't found. Not yet root-caused/fixed.

## File/module map
- `app/src/main/java/com/vinoth/guardapp/MainActivity.kt` — UI entry point, VPN permission request 
  flow, starts GuardVpnService.
- `app/src/main/java/com/vinoth/guardapp/GuardVpnService.kt` — establishes VPN interface (DNS + 
  DoH-IP scoped routing), runs packet read loop, delegates each packet to DnsHandler.
- `app/src/main/java/com/vinoth/guardapp/DnsParser.kt` — extracts queried domain name from raw 
  IP/UDP/DNS packet bytes.
- `app/src/main/java/com/vinoth/guardapp/DnsHandler.kt` — core blocking logic: checks domain 
  against blocklist + AMP-cache keywords, either returns fake NXDOMAIN or relays to real DNS server.
- `app/src/main/java/com/vinoth/guardapp/BlocklistLoader.kt` — loads porn_blocklist.txt asset into 
  in-memory HashSet at startup.
- `app/src/main/java/com/vinoth/guardapp/FileLog.kt` — file-based logger (see Debugging method above).
- `app/src/main/assets/porn_blocklist.txt` — StevenBlack/hosts porn-only list, 76,749 domains.
- `app/src/main/res/layout/activity_main.xml` — single button + scrollable log TextView.
- `app/src/main/res/values/themes.xml` — minimal AppCompat theme.
- `app/src/main/AndroidManifest.xml` — permissions (INTERNET, FOREGROUND_SERVICE), activity + 
  VPN service registration.

## Next steps
1. Build the delay/friction mechanism (randomized 5-15 min delay + reflection prompt) — this is 
   the actual core idea, not yet implemented. Currently blocking is instant/permanent.
2. Investigate/close Android Private DNS (DoH-over-TLS, port 853) as a potential bypass.
3. Optimize isDomainBlocked() from O(n) to O(domain levels) lookup.
4. Build Accessibility Service for uninstall/disable friction.
5. UI polish pass (currently minimal/debug-only).
6. Once porn-blocking + friction mechanism is fully proven: extend to Reels/Shorts and gaming 
   (different detection approach needed — no single discrete trigger moment like a DNS query; 
   likely session-start or time-boxing based instead).

## Tooling workflow (for context across AI tool switches)
- Primary: Claude chat for step-by-step guidance + code for single well-scoped units (one file/function 
  at a time). Architecture and interface decisions happen here, get written into this doc, before any 
  agentic tool touches code.
- Agentic tools (Cursor/Copilot): reserved for multi-file integration/wiring and build-error debugging 
  loops only — not for boilerplate or single-function generation, to conserve limited free-tier quota.
- Secondary chat (ChatGPT, then Gemini): boilerplate/volume generation once architecture is decided, 
  using this doc as pasted context.
- This file is updated at the end of every session, before switching tools or accounts.

## Session Log (stage-level updates, for cross-tool continuity)

### Session 1
- Repo `guard-app` created (private) on GitHub, cloned locally, origin configured.
- Dev environment set up: JDK 17 installed and set as active Java version (was conflicting with 
  pre-existing JDK 25 — fixed via JAVA_HOME + PATH ordering, had to specifically deal with Oracle's 
  auto-managed `javapath` entry overriding manual PATH order on Windows).
- Confirmed working: `java --version` correctly returns 17.x in project terminal.

### Session 2
- Installed Android SDK command-line tools into `~/android-sdk/cmdline-tools/latest`.
- Set `ANDROID_HOME` env var + PATH entries. Installed platform-tools, platforms;android-34, 
  build-tools;34.0.0 via sdkmanager.
- Verified `adb` works and connects to physical phone via USB debugging.

### Session 3
- Built minimal project skeleton by hand (Gradle files, manifest, theme, layout, MainActivity).
- Fixed build errors: missing android.useAndroidX=true, missing themes.xml file (lesson: always 
  verify file content with `cat` after creating, don't assume heredoc succeeded).
- BUILD SUCCESSFUL, installed on physical phone, confirmed app launches. Full toolchain verified 
  end-to-end with no Android Studio, no emulator.

### Session 4
- Built GuardVpnService MVP: establishes VPN interface, reads raw packets in background thread.
- Discovered adb logcat is suppressed for third-party logs on this Vivo device — switched to 
  file-based logging (FileLog.kt) as the standard debugging method going forward.
- CONFIRMED WORKING: VPN interface establishes and receives real packets.
- Built DnsParser.kt to extract domain names from raw DNS query packets — confirmed working on 
  live traffic.

### Session 5
- Discovered initial VPN routed ALL traffic (addRoute 0.0.0.0/0) with no forwarding logic — 
  total internet blackout. User clarified actual requirement: normal speed preserved + effective 
  porn blocking with no easy bypasses.
- Redesigned to DNS-only selective routing + DoH-IP blocking + real DNS relay for allowed domains 
  + fake NXDOMAIN response for blocked domains (DnsHandler.kt).
- Discovered hand-typed 3-domain blocklist was trivially evaded by mirror/bypass domains 
  (xhamster's own xh*-branded network: xhopen.com, xhaccess.com, xhamster46.desi, AMP-cache 
  proxying via cdn.ampproject.org).
- Replaced with real curated blocklist: StevenBlack/hosts porn-only list (76,749 domains), bundled 
  as local asset, loaded into in-memory HashSet (BlocklistLoader.kt). Added AMP-cache keyword 
  fallback for the Google-domain-proxy case that blocklist membership alone can't catch.
- CONFIRMED FIXED via retest: all previously-escaping domains now correctly blocked, normal sites 
  still load normally. This completes the "detect and block" core mechanism.
- STATUS: blocking is currently instant/permanent, NOT the delay/friction mechanism that was the 
  original core idea. That is the next major stage — not yet started.
  ## HANDOFF NOTE FOR NEXT SESSION (new Claude account/instance)

If you're a new Claude instance picking this up: read this whole file first, especially the 
"IMPORTANT CURRENT STATUS FLAG" and "Debugging method" sections above before doing anything. 
The user (Vinoth) prefers first-principles explanations backed by research, values simplicity 
over complexity, wants honest/direct feedback without sugarcoating, and is building this himself 
using free-tier AI tools only (no subscriptions) — so keep guidance in small, exact, copy-pasteable 
steps (he's explicitly asked for low-level, non-abstract instructions, broken into sub-steps). 
He is a CS background /learning software engineering, so batch conceptual explanations at stage 
boundaries rather than narrating every line.

### Immediate next task
Build the delay/friction mechanism — this is the actual core idea of the whole project and is 
NOT yet built. Currently the app does instant, permanent DNS-level blocking of porn domains 
(confirmed working). What's needed instead:
1. When a blocked domain is queried, instead of immediately returning NXDOMAIN, start a 
   randomized delay window (5–15 minutes, randomized each time — not fixed, to resist habituation).
2. During the delay, show the user a screen requiring active engagement (e.g. typed reflection: 
   what triggered this urge, what they're feeling) — not a passive countdown, which can be idled 
   out without any cognitive engagement.
3. After the delay expires, only THEN allow the domain through (relay to real DNS) if the user 
   still wants to proceed — or continue blocking if they navigate away/close the flow.
4. Escalate delay length based on same-day trigger frequency (tie friction strength to actual 
   behavior data, not a static constant).

### Implementation considerations to think through with the user before coding
- Where does the delay/reflection UI live? DNS blocking happens deep inside GuardVpnService 
  (a background service, not an Activity) — need a way to surface a foreground UI from there 
  (likely: service posts a notification or launches an Activity over other apps) when a blocked 
  domain is first hit, distinct from just returning NXDOMAIN silently.
- Need a way to track "this domain is currently in its delay window" vs "permanently blocked" 
  vs "user chose to proceed after delay" — some kind of in-memory (or persisted) state per domain 
  attempt, with timestamps.
- Decide how "the user chooses to proceed after the delay" is actually communicated back to the 
  DNS layer — e.g. a temporary allowlist entry with expiry, set after the reflection flow completes.

### After that, remaining stages (see "Next steps" section above for full list)
- Investigate/close Android Private DNS (port 853) as a bypass — not yet tested.
- Optimize blocklist lookup from O(n) to O(domain levels).
- Build Accessibility Service for uninstall/disable friction (not started at all).
- UI polish (currently one button + debug log view only).
- Eventually extend friction mechanism to Reels/Shorts and gaming — explicitly deferred until 
  porn-blocking + delay mechanism is fully proven. Note: these need a different detection approach 
  entirely (no single discrete trigger moment like a DNS query — likely session-start or 
  time-boxing based instead), do not assume the DNS approach generalizes.

### Workflow reminder
User cycles across multiple free-tier AI accounts (Claude/ChatGPT/Gemini x3 each, Copilot x2) to 
manage rate limits, using this file as shared memory across switches. Update this Session Log 
with a new dated entry at the end of your session, batched at the stage level (not line-by-line), 
before handoff to the next tool/account.