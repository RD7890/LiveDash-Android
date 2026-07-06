# LiveDash — Android

[![Build](https://github.com/RD7890/LiveDash-Android/actions/workflows/build.yml/badge.svg)](https://github.com/RD7890/LiveDash-Android/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/RD7890/LiveDash-Android?include_prereleases)](https://github.com/RD7890/LiveDash-Android/releases/latest)

Serverless live screenshot dashboard for Android. No internet. No cloud. Works on local hotspot.

## How it works

1. **Viewer phone** turns on hotspot → opens LiveDash → picks **Viewer** → starts server
2. **Sender phone(s)** connect to that hotspot → open LiveDash → pick **Sender** → enter IP → overlay appears
3. Tap the floating camera button on any app to beam a screenshot to the dashboard instantly
4. Two-way chat between dashboard and all senders

## Features
- Serverless — runs entirely on your hotspot, no cloud
- Live screenshot stream + 40-shot history
- Two-way chat (dashboard ↔ all senders)
- Floating overlay over any app (sender side)
- MediaProjection — captures entire screen, not just the app
- Dual APK: **arm64-v8a** (64-bit) + **armeabi-v7a** (32-bit)

## Stack
- Kotlin + Jetpack Compose + Material 3
- Java-WebSocket (embedded WS server + client)
- MediaProjection API (screen capture)
- WindowManager TYPE_APPLICATION_OVERLAY (floating panel)
- Foreground Services

## Install
Download the correct APK for your device from [Releases](https://github.com/RD7890/LiveDash-Android/releases/latest):
- `LiveDash-vX.X-arm64.apk` → most modern phones (64-bit)
- `LiveDash-vX.X-arm32.apk` → older 32-bit phones
