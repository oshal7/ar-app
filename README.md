# AR Flight & Bounce 🪁🏀

An augmented-reality physics playground for Android. Point your phone at the room,
scan a surface, then **fling a ball into the real world and watch it bounce off your
actual floor, table and walls** with real gravity, restitution and impact sound +
haptics.

This is **v1.0.0 — the Ball MVP** (Phase 1 of the PRD). It's built as a **native
Kotlin + ARCore** app so the whole thing compiles in the cloud on GitHub Actions —
**no laptop, no Android Studio required** — and ships as a downloadable APK.

---

## 📲 Install it on your phone

1. Open the **[Releases](../../releases)** page on your phone.
2. Download the latest `ar-flight-and-bounce-vX.Y.Z.apk`.
3. Tap it. If Android warns about installing from your browser/files, allow
   **"Install unknown apps"** for that app, then install.
4. Launch **AR Bounce**, grant the camera permission, and slowly pan the room until
   surfaces light up. Then **touch and hold to aim/charge, release to throw.**

Requires Android 7.0+ (API 24). On ARCore-capable phones (all modern Pixels, most
Galaxy/OnePlus/Xiaomi) you get full AR. On devices without ARCore, the app falls back
to a non-AR **tilt-and-bounce** mini game so it always runs.

---

## 🎮 Controls

| Action | Gesture |
|---|---|
| Aim | Point the phone; a dotted arc previews the throw |
| Charge power | Touch **and hold** — longer hold = harder throw |
| Throw | Release |
| Clear balls | **Reset** button |
| Plane / Bird | Coming in a later version |

---

## 🔢 Versioning

`version.properties` is the single source of truth:

```properties
VERSION_NAME=1.0.0        # semantic version shown to you
VERSION_CODE_BASE=1000    # floor for Android's integer versionCode
```

The effective Android `versionCode` is `VERSION_CODE_BASE + <CI run number>`, so it
**always increases** across builds — a hard requirement for in-place updates.

**To cut a new release (from your phone, no laptop):**

- **Option A — tag:** bump `VERSION_NAME`, commit, then create & push a tag
  `vX.Y.Z`. CI builds and publishes the Release automatically.
- **Option B — button:** open the repo's **Actions → Build & Release APK → Run
  workflow**. It builds from the current `VERSION_NAME` and publishes/updates the
  matching Release.

---

## 💾 Your data is kept across updates

You never have to uninstall to upgrade. Lifetime stats (throws, bounces, best air
time) live in a Room database in the app's private storage, which Android preserves
across an in-place update. Two things make the in-place update possible, and both are
handled for you:

1. **Same signing key every build.** The release keystore is committed at
   `keystore/release.jks` and CI signs every APK with it, so Android sees each new
   version as the *same app* and updates it in place instead of forcing a reinstall.
2. **Non-destructive database migrations.** The Room database never wipes on a schema
   change — every future schema bump ships an `ALTER`-based migration (see
   `AppDatabase.kt`).

> ⚠️ **Security trade-off (by design for a personal side-loaded app).** Because the
> keystore and its password are committed to this repo, the signing key is *not*
> secret — anyone with the repo could sign an APK claiming this package name. For a
> personal app that's never published to Google Play, this is a low, accepted risk and
> it's the simplest way to get reproducible, data-preserving updates with no laptop.
> If you ever want to harden this, move the keystore into GitHub Actions **secrets**
> (base64) and read it in the workflow instead of committing it.

---

## 🗺️ Roadmap (from the PRD)

- **v1.x (this line):** ball throwing + bounce off real planes, sound, haptics,
  trajectory preview, persistent stats. ✅
- **Next:** RC airplane with virtual joystick, smoke-trail particles and
  explode-on-impact.
- **Later:** animated bird AI with perching, real-world **occlusion** (ARCore Depth
  API), richer particle VFX, and full spatial-mesh collision.

The MVP intentionally uses ARCore **plane-based** collision rather than a full LiDAR
mesh, and defers occlusion — those are follow-up releases on the same foundation.

---

## 🛠️ Tech / project layout

- **Language/UI:** Kotlin, Android Views, Material 3.
- **AR:** Google **ARCore** (`com.google.ar:core`) with a hand-written OpenGL ES 2.0
  renderer (camera background, plane fill, point cloud, procedural lit sphere,
  trajectory dots) — no Unity, no binary 3D assets.
- **Physics:** small pure-Kotlin integrator (`physics/`) with gravity + plane
  collision at restitution `0.75`.
- **Persistence:** Room (`data/`).
- **CI/CD:** `.github/workflows/build-apk.yml` builds, signs, and publishes the APK.

```
app/src/main/java/com/arbounce/playground/
  MainActivity.kt          AR vs fallback, lifecycle, permissions, HUD
  ar/                      ArRenderer, DisplayRotationHelper, GameEvents, permissions
  rendering/               GL renderers (background, plane, point cloud, sphere, trajectory)
  physics/                 Ball, Surface, PhysicsWorld
  data/                    Room entity/DAO/DB/repository
  ui/                      Feedback (sound+haptics), FallbackView (non-AR sandbox)
```

Everything builds in CI; there is nothing you need to run locally.
