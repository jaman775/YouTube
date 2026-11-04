# Online engine updates

YouTube changes often. The part of the app that depends on it — the **engine** (NewPipe Extractor),
the **PO-token page** and the **browser script** (`inject.js`) — can therefore be replaced while the app
is installed, without a new APK. This file explains how it works and how to switch it on.

## What updates, and what does not

| Updates online | Needs a new app version |
| --- | --- |
| NewPipe Extractor and the engine code around it | Screens, player, downloader, Cast |
| PO-token page (BotGuard glue) | Changes to the engine API (`engine-api` module) |
| `inject.js` (ad hiding, Shorts hiding, link routing) | Android permissions |

An update is a small signed bundle. If the engine API ever changes, old apps see `engineApi` in the
manifest, refuse the bundle and keep working with what they have.

## How safe is it?

The app downloads code, so every step is checked:

1. `manifest.json` carries a signature (`manifest.json.sig`, ECDSA P-256 / SHA-256). The app holds the
   matching **public key** and rejects anything else, so only whoever owns the private key can ship an update.
2. Every file in the manifest has a SHA-256 that must match what was downloaded.
3. Only HTTPS is used, file names cannot contain paths, and only `potoken.html` and `inject.js` may be replaced.
4. Each update has a `sequence` number; an update must be newer than the installed one (no rollback attacks).
5. The new engine must load and report a version before it replaces the old one. If it fails later, it is
   marked bad and the engine built into the app takes over again.

Guard the private key (`ota-private.pem`). Anyone who has it can run code inside your users' apps.
Updates need Android 8 or newer; older devices keep using the built-in engine.

## One-time setup

1. **Make a signing key**

   ```
   python3 tools/gen_keys.py
   ```

   It writes `ota-private.pem` (keep it secret, never commit it) and prints the public key.

2. **Put the repository on GitHub** and add the private key's *contents* as a repository secret named
   `OTA_SIGNING_KEY` (Settings → Secrets and variables → Actions).

3. **Tell the app where to look.** In `app/build.gradle.kts`, set:

   ```kotlin
   resValue("string", "engine_manifest_url", "https://github.com/OWNER/REPO/releases/download/ota/manifest.json")
   resValue("string", "engine_public_key", "<the public key from step 1>")
   ```

   Build and install the app once with these values.

4. **Run the workflow once.** Actions → "Publish engine update" → Run workflow. After that it runs every
   day, takes the newest NewPipe Extractor release, builds and signs the engine and publishes it to the
   `ota` release. Nothing happens when nothing changed.

From then on the app checks about every six hours, and also right after the engine fails (at most once
every 15 minutes). Settings → *YouTube engine & updates* shows the running engine and has a
"Check for engine updates" button.

## Publishing by hand

```
ANDROID_HOME=~/Android/Sdk OTA_KEY=ota-private.pem tools/build-ota.sh 42 ota-out
```

Upload everything in `ota-out/` to the folder the manifest URL points at. Use a higher sequence than last time.

## Using another host

Any HTTPS folder works: put `manifest.json`, `manifest.json.sig` and the files it lists there, and point
`engine_manifest_url` at `manifest.json`. File names in the manifest are relative to that URL.

## Troubleshooting

* "Update rejected: its signature was not valid" – the public key in the app does not match the private
  key that signed the bundle, or the files were uploaded from different runs.
* "Online updates are not set up" – `engine_manifest_url` or `engine_public_key` is empty.
* Diagnostics: About → Save diagnostic logs; lines tagged `Engine` and `PoToken` describe each step.
