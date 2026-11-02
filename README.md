# Icon Studio

Gives your apps restyled icons (Liquid Glass, Glassmorphism, Nothing dot-matrix, Neumorphism,
Retromorphism, Claymorphism) **on your normal launcher**. No default-launcher change, no Shizuku, no root.

It works by pinning shortcuts: each pinned icon opens the real app and carries the styled icon.
Pinned icons stay on the home screen until you remove them.

## Build on GitHub
1. Create a repo and upload the contents of this folder (keep the `.github` folder; `build.gradle.kts` must be at the repo root).
2. Actions tab > "Build APK" > Run workflow (it also runs on every push).
3. Download the `IconStudio-APK` artifact, unzip it, and install the APK.

## Use
1. Open Icon Studio, pick a style. For Liquid Glass, use the "Glass depth" slider.
2. Tap apps to select them, then "Pin (N)". Confirm each prompt from your launcher.
   Long-press an app to pin just that one.
3. Remove the old plain icons from the home screen. The originals remain in the app drawer.
4. Changed your mind on the style? Pick a new style and tap "Update pinned" to restyle the
   already-pinned icons in place.
