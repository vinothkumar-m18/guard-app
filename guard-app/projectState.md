# PROJECT_STATE.md

## What this is
Android app that introduces deliberate friction between urge and action for compulsive porn use,
as a harm-reduction alternative to hard blocking (a prior 6-7 day hard lock backfired). Goal:
delay, not deny — urges peak and decay within minutes if not acted on. Future phases (not started):
extend friction to Reels/Shorts and gaming.

## Core mechanism (why, not just what)
- Urges are time-limited (Marlatt's urge surfing) — a 5-15 min delay lets the urge decay naturally.
- Hot-cold empathy gap (Loewenstein) — delay lets the rational system re-engage before acting.
- Total restriction triggers reactance (Brehm) / ironic suppression (Wegner) — why the 6-7 day
  lock failed. Short delay avoids this.
- Friction interrupts automaticity (Wood & Neal) — forcing conscious engagement breaks the
  cue-to-action habit loop. This is the design principle behind every friction-scoping decision
  below — friction must reapply at each new deliberate decision point, not just once per session.

## CURRENT STATUS (as of Session 9)
Core detect → delay → reflect → confirm → temporary-unlock loop is BUILT and has been tested
working end-to-end, on-device, at both shortened and production delay values.

**FrictionState.kt was just rewritten (Session 9) and is UNTESTED on-device.** Do not assume it
works until confirmed via the test plan below.

## Architecture (BUILT, confirmed working as of Session 8)
- VpnService, DNS-only scoped routing (port 53 UDP + narrow /32 routes to known DoH resolver IPs:
  8.8.8.8, 8.8.4.4, 1.1.1.1, 1.0.0.1) — actual page/content traffic bypasses the tunnel entirely,
  full native speed. This was a deliberate tradeoff: do NOT route all traffic (port 443/HTTPS)
  through the tunnel to solve scoping problems — that undoes this speed guarantee (see Session 9
  design discussion for why this was considered and rejected).
- DnsHandler.kt: hand-parsed DNS packets (DnsParser.kt), blocklist check, either relays to real
  DNS (8.8.8.8, via protected socket) or returns fake NXDOMAIN, OR (new) consults FrictionState.
- Blocklist: StevenBlack/hosts porn-only list, 76,749 domains, local asset, in-memory HashSet
  (BlocklistLoader.kt). Plus a small AMP-cache keyword fallback (xhamster/pornhub/xvideos/xnxx)
  for Google-domain-proxied content that blocklist membership alone can't catch.
- Friction UI: ReflectionActivity.kt + activity_reflection.xml — full-screen popup, live countdown,
  "Proceed anyway" (hidden until delay elapses) / "Never mind" (clears attempt, fresh delay next
  time). Fixed this session-chain: was rendering fully transparent (text bled through the browser
  underneath) — fixed with explicit opaque white background + black text.
- NotificationHelper.kt: fires on new blocked-domain attempt, launches ReflectionActivity.

## FrictionState.kt — scoping design history (IMPORTANT — read before touching this file)
Three approaches were tried across different AI tool sessions. Know which one is CURRENT:

1. **Per-domain scoping** (Session 6, original): each literal hostname got its own temp-allow.
   BROKEN — one page load touches 10-20+ different domains (CDNs, ad networks, analytics), each
   independently blocklisted, so confirming one didn't unlock the page's own resources → popup
   storm, page never fully loaded. Replaced.

2. **Session-wide fixed window** (Session 8, built + tested working in Claude): one confirm sets
   one global `sessionAllowExpiresAt`, all domains relay for 25 min. FIXED the popup-storm bug,
   confirmed working on-device. BUT: weak on the actual behavioral goal — after one confirm, ANY
   site (not just the one you confirmed) opens with zero friction for 25 min, including unrelated
   sites and fresh deliberate visits. Undermines "friction interrupts automaticity" since
   automaticity is only interrupted once per window, not at each new decision.
   NOTE: a separate, undocumented ChatGPT session also produced a conflicting "registrable-root"
   version of this file around the same time — this caused real confusion (see below). Superseded.

3. **Registrable-root scoping** (found on disk unexpectedly at start of Session 9, from the
   ChatGPT session referenced in Session 6's second log entry — was never actually tested,
   despite Session 8's log describing a "confirmed working" test that was actually against
   version 2 above, before it got overwritten). Groups by last-two-labels of the domain (e.g.
   cdn.pornhub.com → pornhub.com). STILL BROKEN for the actual problem: a page's CDN/ad/analytics
   domains are usually on ENTIRELY DIFFERENT root domains (phncdn.com, trafficjunky.net,
   tsyndicate.com are not subdomains of pornhub.com) — root-matching doesn't group them.
   Superseded before ever being tested.

4. **Rolling-activity session (Option B) — CURRENT, Session 9, UNTESTED.** Instead of a fixed
   expiry set once, tracks a rolling `lastActivityAt` timestamp refreshed on every allowed
   blocked-domain query. Session stays "active" (all domains allowed) as long as new blocked-
   domain queries keep arriving within `IDLE_GAP_MS` (45 sec) of each other — this naturally
   covers a page's own CDN/ad bursts regardless of which root domain they're on, since they fire
   in rapid succession. If 45+ seconds pass with no blocked-domain activity, the session lapses —
   next blocked domain requires a fresh full delay, treated as a new deliberate decision rather
   than page-load continuation. `MAX_SESSION_MS` (25 min) is a hard ceiling so continuous active
   browsing can't extend forever.
   KNOWN, ACCEPTED LIMITATION: cannot distinguish "same page loading resources" from "fast
   deliberate hop to a new site within the idle gap" — both look identical to this heuristic.
   Considered and rejected: true per-page accuracy via SNI/traffic inspection — would require
   routing ALL HTTPS traffic (port 443) through the tunnel, undoing the DNS-only speed design.
   Judged not worth the tradeoff.

### IMMEDIATE NEXT STEP — Option B is built but NOT YET TESTED on-device
Just built + BUILD SUCCESSFUL confirmed. Never installed/run. Test plan (production 5-15 min
delay values are currently active, so this takes real time):
1. Install, start VPN, visit a blocked site, wait through full delay, confirm.
2. TEST 1 (active browsing stays unlocked): keep actively using that site for 1-2 min — confirm
   no repeat popups.
3. TEST 2 (idle re-locks it — the actual point of this rewrite): go idle 50+ seconds (lock phone
   or switch apps), then try a blocked site again — confirm the popup DOES reappear with a fresh
   delay. This is correct/expected, not a bug.
4. Pull log via the method below, confirm behavior matches log lines (ALLOWED / FRICTION START /
   FRICTION PENDING / REFLECTION: user proceeded / etc.)
If the person wants a faster test pass first: temporarily shrink DELAY_MIN_MS/DELAY_MAX_MS in
FrictionState.kt the same way it was done in Session 7-8 (10-20 sec), test, then MUST restore to
5*60*1000L / 15*60*1000L before trusting/using the app for real — this was forgotten temporarily
before and caught late, don't repeat that.

## Also in progress, further behind: Accessibility Service (started via ChatGPT session, NOT reviewed/build-tested)
Purpose: friction on two escape routes outside the VPN's reach —
1. Android's Private DNS setting (Settings > Network & Internet > Private DNS) — confirmed OFF on
   the test device, but one tap away from fully bypassing the VPN's DNS interception (OS-level
   bypass, cannot be fixed inside the VPN service itself). Device Owner mode could fully disable
   this setting via `DISALLOW_CONFIG_PRIVATE_DNS`, but requires factory reset + QR provisioning —
   explicitly parked for v2, only if friction alone proves insufficient.
2. Uninstall/Force-Stop protection (App Info screen).
Plan agreed: minimal skeleton service first (logs every window/screen change: package + class
name, NO detection logic yet) → navigate to both target screens manually while logging → read log
to find this device's real screen identifiers (Vivo/OriginOS may differ from stock Android) →
THEN write matching/interrupt logic.
STATUS: A ChatGPT session (see Session 6 second log entry below) built GuardAccessibilityService.kt
already, but skipped the skeleton-first step — current version logs the full node tree on EVERY
screen change, system-wide, continuously, not scoped to Settings navigation. Flagged as needing a
rewrite before use (battery/log-bloat risk) — NOT reviewed or build-tested since. Manifest additions
(POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, SYSTEM_ALERT_WINDOW permissions; service registration)
also from that session, also not verified.
accessibility_service_config.xml was separately created in a Claude session (typeWindowStateChanged,
feedbackGeneric, flagReportViewIds, canRetrieveWindowContent=true) — verify this still matches
what's on disk before continuing, given the cross-tool divergence issues seen with FrictionState.kt.

## Known limitations (accepted, not blockers)
- Raw IP address entry bypasses DNS-based blocking. Rare in practice. v2 item.
- isDomainBlocked() is O(n) over 76,749 entries for the subdomain-match case. Works fine at
  current scale; could be optimized to O(domain levels) later.
- Blocklist load takes ~7 sec on VPN startup (one-time). Acceptable.
- Some blocklist entries (e.g. cdn.tsyndicate.com) are generic domains also used by adult sites —
  rare false-positive risk on unrelated content. Accepted: no porn leaks > zero overblocking.
- Android allows only one active VPN at a time.
- No reflection-text persistence — typed input is behavioral only (forces engagement), not logged
  to storage. Open design decision, not yet made, whether to persist for pattern-tracking.

## Dev environment
- VS Code (Kotlin ext) + Gradle CLI, NOT Android Studio (laptop can't sustain it). No emulator —
  physical Vivo/OriginOS phone via adb. No visual layout editor — XML written by hand.
- JDK 17 specifically. Android SDK cmdline-tools + adb standalone.

## Debugging method — logcat does NOT work on this device
adb logcat is suppressed for third-party app logs on this Vivo/OriginOS device (confirmed). Use
file-based logging only:

~/android-sdk/platform-tools/adb shell run-as com.vinoth.guardapp cat files/guard_log.txt > guard_log.txt && cat guard_log.txt

Other common commands:

~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/android-sdk/platform-tools/adb shell am start -n com.vinoth.guardapp/.MainActivity
./gradlew.bat assembleDebug > build_output.log 2>&1 && cat build_output.log

Note: bare `adb` sometimes shows unexpanded `%ANDROID_HOME%` on this Windows/Git Bash setup —
use the full path above if that happens. Not root-caused.

## File/module map
- `MainActivity.kt` — UI entry, VPN permission flow, starts GuardVpnService.
- `GuardVpnService.kt` — VPN interface (DNS + DoH-IP scoped), packet read loop → DnsHandler.
- `DnsParser.kt` — extracts domain names from raw DNS packets.
- `DnsHandler.kt` — blocklist check, relay-or-NXDOMAIN, now consults FrictionState.
- `FrictionState.kt` — friction state machine (see scoping history above — just rewritten, untested).
- `BlocklistLoader.kt` — loads blocklist asset into HashSet.
- `ReflectionActivity.kt` + `activity_reflection.xml` — friction popup UI.
- `NotificationHelper.kt` — fires notification/launches ReflectionActivity on new attempt.
- `GuardAccessibilityService.kt` + `accessibility_service_config.xml` — settings/uninstall
  friction, in progress, not reviewed (see above).
- `FileLog.kt` — file-based logger.
- `app/src/main/assets/porn_blocklist.txt` — 76,749-domain blocklist.

## Next steps (in priority order)
1. **Test the Session 9 FrictionState.kt rewrite on-device** (see test plan above) — this is
   the immediate next action for a new session.
2. Verify/rewrite GuardAccessibilityService.kt: scope it to Settings/App-Info navigation only
   (currently logs everything, system-wide, continuously — needs fixing before use).
3. Complete Accessibility Service: detect Private DNS screen + App Info/uninstall screen on this
   specific device, add friction interrupt to both.
4. Escalation logic: delay length increases based on same-day trigger frequency (not yet built —
   deferred from Session 6, still relevant, possibly now more important given the rolling-session
   approach lets a whole browsing session go unlocked after one confirm).
5. Optimize isDomainBlocked() to O(domain levels).
6. UI polish pass (currently minimal/debug).
7. Once fully proven: extend friction mechanism to Reels/Shorts and gaming (different detection
   approach needed — no single discrete trigger like a DNS query).

## Tooling workflow
- Primary: Claude chat, one well-scoped file/unit at a time, verify-with-cat after every write,
  before every build. Architecture decisions happen in chat, get written here, before any
  agentic tool touches code.
- Agentic tools (Cursor/Copilot): multi-file wiring / build-error loops only.
- Secondary chat (ChatGPT/Gemini): boilerplate once architecture is decided, using this doc as
  context. CAUTION (learned the hard way, Session 6/9): a ChatGPT session diverged from this
  doc's plan and produced a conflicting FrictionState.kt version without updating this file or
  verifying against `cat` output — always diff what's actually on disk against this doc's
  described state before trusting either, when switching tools.
- This file updates at the end of every session, before switching tools/accounts.

## Session Log
- **Sessions 1-3**: Environment setup (JDK 17, Android SDK, adb), minimal project skeleton built
  and confirmed launching on physical device, no Android Studio/emulator used.
- **Session 4**: GuardVpnService MVP — VPN interface + packet reading confirmed working.
  Discovered logcat is suppressed on this device; switched to file-based logging permanently.
  DnsParser.kt built, confirmed extracting real domain names from live traffic.
- **Session 5**: Fixed a total-blackout bug (was routing ALL traffic). Redesigned to DNS-only
  selective routing + DoH-IP blocking. Replaced a trivially-evaded 3-domain blocklist with the
  76,749-domain StevenBlack list + AMP-cache keyword fallback. Confirmed working: detect+block
  core complete, still instant/permanent (not yet friction-based).
- **Session 6**: Built the friction state machine (delay + notification, per-domain scoping v1),
  ReflectionActivity skeleton, NotificationHelper. Confirmed notification triggers correctly.
  [Separate, later ChatGPT session also logged as "Session 6"]: built reflection UI in full
  (countdown, typed-reflection gating, Never Mind), attempted per-registrable-root scoping
  (untested, later found broken for third-party CDN/ad domains), and an early, unreviewed
  Accessibility Service draft (logs everything system-wide, needs rescoping).
- **Session 7**: Full-screen-intent auto-launch built. Found + fixed transparent-popup UI bug
  (text bleeding through from the page underneath) — added opaque white background + black text.
- **Session 8**: Diagnosed the per-domain scoping bug via log analysis (one page load touches
  10-20+ unrelated blocklisted domains). Fixed via session-wide fixed-window scoping (v2 above);
  confirmed working on-device at both shortened and production delay values. This was believed
  to close out the core mechanism.
- **Session 9 (current)**: Discovered the file on disk was actually the ChatGPT session's
  registrable-root version (v3), not the tested session-wide version — the two tool sessions had
  silently diverged. Diagnosed that v3 is also broken (third-party CDN/ad domains aren't
  subdomains of the confirmed site's root). Discussed and rejected true per-page accuracy (would
  require inspecting HTTPS/SNI traffic, undoing the DNS-only speed design). Designed and built
  v4 — rolling-activity-timestamp session scoping (see design section above). BUILD SUCCESSFUL.
  **NOT YET TESTED ON-DEVICE — this is the next session's first task.**
  cat >> PROJECT_STATE.md << 'EOF'

- **Session 10**: Found accessibility service was dumping full node tree on every screen event
  system-wide (not scoped to Settings nav as intended) — was pegging device I/O, caused a blank
  white MainActivity screen. Disabled the service on-device, cleared bloated log (was 19MB/228k
  lines). Confirmed FrictionState v4 rolling-session logic itself is correct (FRICTION
  PENDING/START behaving as designed). Separately discovered OverlayHelper.kt described as
  "BUILT" in this doc's Architecture section did NOT actually exist on disk, and
  SYSTEM_ALERT_WINDOW was missing from the manifest — friction was silently degrading to
  tap-only notifications (MainActivity had a stray comment "no overlay permission handling
  anymore", confirming it was removed at some point, another cross-tool divergence case).
  Rebuilt OverlayHelper.kt (WindowManager TYPE_APPLICATION_OVERLAY, reproduces
  ReflectionActivity's countdown/reflection/proceed/never-mind logic via inflated
  activity_reflection.xml), added SYSTEM_ALERT_WINDOW to manifest, added overlay permission
  request flow to MainActivity.onCreate, wired DnsHandler's FRICTION START branch to prefer
  OverlayHelper.showFrictionOverlay() with NotificationHelper as fallback when permission is
  missing. BUILD SUCCESSFUL, installed. Log shows "OverlayHelper: overlay shown for
  www.pornhub.com" followed by a Never Mind reflection entry — suggests overlay did render and
  was interacted with, but user reported the "Display over other apps" settings screen did NOT
  auto-open on launch as MainActivity's new code expects, and Guard was still absent from that
  settings list as of last check this session.
  **UNRESOLVED / NEXT SESSION FIRST TASK**: reconcile these two facts — either overlay permission
  was already effectively granted through some other path (stale grant surviving reinstall, or
  OriginOS auto-granting once the manifest declares it) despite not showing in the Settings UI,
  or the log line is misleading and needs re-verification with the user directly confirming they
  *visually saw* the full-screen popup (not inferred from log alone). Do not assume the overlay
  fix is confirmed working until this is nailed down with an explicit user-observed test.
  ReflectionActivity.kt (the old full-screen-intent Activity path) still exists and is unused now
  that DnsHandler routes through OverlayHelper first — decide later whether to keep as fallback
  reference or remove.
