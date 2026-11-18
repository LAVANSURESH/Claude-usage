# Claude Usage (Android widget)

Home-screen widget that tracks your Claude message usage per rolling window.
Claude.ai has no public usage API, so this is a local counter: tap **+1** when you send a message.
The counter clears itself when the window (default 5 h from your first message) ends.

## Get the APK
1. Push this folder to a GitHub repo.
2. Open the Actions tab > "Build APK" > latest run > download artifact `ClaudeUsage-apk`.
3. Unzip, install `app-debug.apk` on your phone (allow "install unknown apps").
4. Long-press home screen > Widgets > Claude Usage.

Or open the folder in Android Studio and press Run.

Settings (message limit, window hours) are in the app.

## Sync from claude.ai
Tap **Sync** (widget or app). Sign in to claude.ai in the page that opens (email login works best; Google sign-in is often blocked in WebViews). The app reads Settings > Usage text locally and shows session % and weekly %. This is unofficial and may break if the page layout changes; your login cookie stays in the app's WebView on your phone only.
