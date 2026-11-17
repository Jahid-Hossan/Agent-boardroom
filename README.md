# Muse Boardroom — Multi-Agent AI Command Center (Android)

A native **Android** application built with **Kotlin** and **Jetpack Compose** that lets you command **Muse AI**, **Google Gemini**, **ChatGPT**, **Grok**, custom **OpenAI-compatible / Webhook** endpoints, and in-app web AI sessions from one unified boardroom workspace.

---

## Features

- **Boardroom Broadcast & Direct 1-on-1 Chat**: Broadcast a single prompt to all connected agents (`@all` or `@AgentName` mention routing) or switch into dedicated 1-on-1 threads.
- **Multi-Provider Agent Support**:
  - **In-App Browser (`inapp-browser`)**: Interactive Android `WebView` connector for signing into AI web apps (`muse.ai`, `gemini.google.com`, `chatgpt.com`, `grok.com`, `claude.ai`) with DOM prompt injection and reply scraping.
  - **Google Gemini (`gemini`)**: Powered by **Firebase AI Logic** (`Firebase.ai` + App Check) with real-time streaming responses and multimodal image/document support — no client-side Gemini API key required.
  - **OpenAI / Groq / Custom API (`openai`)**: Direct HTTP integration via OkHttp for any OpenAI-compatible `/v1/chat/completions` endpoint.
  - **Custom Webhook / Docker (`docker-web`)**: Connects to self-hosted or webhook endpoints with automatic `/v1/chat/completions`, `/api/chat`, and `/chat` probing plus a built-in connection tester.
  - **Offline Demo (`simulation`)**: Local echo mode for offline testing.
- **In-App Browser Workspace Modal**: Tabbed `WebView` browser with quick AI presets, URL bar, Back/Reload navigation, Check Ready session detector, Close Tab, `+ New Account` cloning, and a Quick Text / OTP + Enter injection bar.
- **Rich Attachments & Voice-to-Prompt**:
  - Zero-permission **Android Photo Picker** (`PickVisualMedia`) for images and **Storage Access Framework** (`OpenDocument`) for documents and audio files.
  - Live microphone recording (`MediaRecorder`) and audio-file transcription powered by **Firebase AI Logic** (`gemini-3.8-flash`).
- **Local Persistence**: **Room** database stores configured agents and chat history across restarts, alongside copied attachment files in app-private storage.
- **Adaptive Layout**: Supports compact handheld layouts (navigation drawer sidebar) and medium/expanded tablet layouts (persistent side panel).

---

## Architecture & Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3
- **Fonts**: Local `Plus Jakarta Sans` & `JetBrains Mono`
- **Persistence**: Room (`BoardroomDatabase`, `BoardroomRepository`)
- **Cloud AI**: Firebase AI Logic (`com.google.firebase:firebase-ai`) + Firebase App Check (Debug & Play Integrity / reCAPTCHA providers)
- **Networking**: OkHttp (`4.12.0`), `org.json`
- **Image Loading**: Coil (`2.7.0`)

---

## Building

Requires **JDK 21** and **Android SDK 36**.

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
