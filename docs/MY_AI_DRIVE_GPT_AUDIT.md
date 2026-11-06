# My AI Drive GPT — audit and verification log

## Branch and scope
- Working branch: `feature/my-bmw-drive-gpt`, forked from `a388721539f59b5760297c38d81ee4822c26fb13`.
- Keep `main`, `feature/my-bmw-drive` and Claude's branch untouched.
- Display label: My AI Drive GPT.
- Android debug application ID: `me.hufman.androidautoidrive.gpt` (base application ID plus Gradle debug suffix).
- CDS provider authority now derives from `BuildConfig.APPLICATION_ID` to avoid an install-time provider authority collision. **Compatibility with external CDS consumers must be tested**.

## Verified source-level findings
1. `gradle.properties` sets `AndroidAutoIdrive_SpotifyApiKey=invalid`. The existing manual CI workflow does not provide a replacement. `SpotifyAppController.hasSupport()` rejects `invalid`; therefore Spotify App Remote integration cannot work in this build without a correctly registered Spotify client ID and redirect URI.
2. Spotify's remote redirect URI is hard-coded as `me.hufman.androidautoidrive://spotify_callback`. A new Android package ID does not automatically register a new Spotify redirect. Spotify Developer Dashboard configuration must match the actual redirect, and any Android app authentication constraints must be respected.
3. Android notification-listener access is a separate prerequisite for general media session discovery and control. The user's screenshots show Android restricting this permission. Do not bypass Android security controls; audit APK provenance/signature and request permission through the supported OS interface.
4. The dashboard's six Kotlin prototype classes are not wired into BMW RHMI navigation. Existing dashboard values are simulated; they must never be presented as live CDS telemetry.
5. The new `DashboardBitmapRenderer` can render both themes off-screen for future RHMI image delivery, but does not itself register a graphical car menu or stream images.
6. The existing BMW RHMI Car Information category now includes a `My AI Drive GPT` text page using six real CDS-backed flows, preserving the old categories. This is not the graphical dashboard.

## Security and compatibility concerns
- The inherited manifest requests sensitive permissions including SMS and calendar and exports a CDS provider. Review actual feature use and access controls before requesting additional grants.
- The application ID suffix isolates installation and storage, but does not prove independent BMW application discovery, Spotify authorization or notification-listener compatibility.
- Do not claim the APK is safe solely because the source builds or GitHub Actions succeeds.
- Android debug signing and GitHub artifact provenance should be checked before installing on a personal phone.

## Acceptance tests (pending)
- [ ] GitHub Actions build succeeds on GPT branch.
- [ ] Inspect merged manifest and verify package/provider authorities and exported components.
- [ ] Install alongside official AAIdrive and Claude edition without collisions.
- [ ] Verify notification listener is available through normal Android settings.
- [ ] Test Spotify and other music-app metadata/controls; document credentials and limitations.
- [ ] Verify BMW iDrive 6 detects the application and navigates correctly.
- [x] Add a text-only My AI Drive GPT RHMI category backed by existing CDS telemetry flows (source change, not yet car-tested).
- [ ] Wire graphical dashboard bitmaps to RHMI and validate on the actual vehicle.
- [ ] Measure connection reliability, CPU/memory usage and error logs.

## Current status
Changes are committed but no successful build or in-car validation has been established for this branch.
