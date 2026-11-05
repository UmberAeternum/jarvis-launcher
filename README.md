# UMBRA Launcher — the Face (Android)

[![CI](https://github.com/UmberAeternum/jarvis-launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/UmberAeternum/jarvis-launcher/actions/workflows/ci.yml)

A sci-fi **home-screen replacement** for Android: a pulsing AI core at the center of a HUD, your apps arranged around it, inline chat with your JARVIS brain, and a **hold-to-talk** dock that listens, thinks, and answers out loud.

Bring-your-own-key, same as the desktop: your API key is stored encrypted on the device and sent only to the provider you choose.

## Install

Requires Android 10+ (API 29).

1. Grab `app-debug.apk` from the latest CI run's artifacts (or build it yourself: `./gradlew assembleDebug`)
2. Sideload it on your phone (enable "install unknown apps" for your file manager/browser)
3. Press Home → choose **JARVIS** as your default launcher
4. Open ⚙ → pick a provider → paste your API key → SAVE → TEST LINK

## The HUD

| Element | What it does |
|---|---|
| **AI core** | Pulsing reactor at the center — tap it to open the chat link |
| **Radial ring** | Your 6 most recent launchable apps orbit the core; tap to launch |
| **Hold-to-talk dock** | Press and hold → Android speech recognition (on-device) → your JARVIS brain → the answer is spoken back. Waveform animates while listening |
| **Status strip** | Time, date, battery — HUD style |
| **APPS ▲** | Full drawer of every launchable app |
| **⚙** | Brain configuration |

Providers: Z.AI (GLM), OpenAI, Zhipu (CN), Gemini, Ollama (local), or any OpenAI-compatible endpoint. On the emulator use `http://10.0.2.2:11434/v1` to reach an Ollama on the host.

## Security model

- The key is stored with Android Keystore-backed encryption (`EncryptedSharedPreferences`); if the device crypto stack fails, the app falls back to private preferences and tells you so in settings.
- The key is never included in chat messages, never logged, and only ever sent to the provider you chose.
- Speech recognition and TTS use the platform engines (on-device on modern Android).
- Errors are reported honestly — an expired or rejected key shows up as `ERROR 401: ...` in the link, never a fake success.

## Build

```bash
./gradlew assembleDebug
```

CI builds the debug APK on every push and uploads it as an artifact.

Companion project: [JARVIS (desktop brain)](https://github.com/UmberAeternum/JARVIS) — the same brain, bring-your-own-key, on your PC.

## License

MIT — see [LICENSE](LICENSE).
