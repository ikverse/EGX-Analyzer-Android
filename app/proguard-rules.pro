# R8, turned on for release builds on 2026-09-12.
#
# **What it is actually for here is one dependency.** `material-icons-extended` ships every Material
# icon there is as generated code - a 36 MB artifact - and this app draws perhaps forty of them. The
# library is built on the assumption that R8 strips the rest; unminified, the release APK carried
# 56 MB of dex, most of it icons nothing references. Everything else the shrinker does is a rounding
# error next to that.
#
# The native libraries are untouched by any of this: TDLib is 15-26 MB of `.so` per architecture and
# no Java shrinker has an opinion about it. That half of the APK is what the ABI split is for.

# ── Names are kept, and that is deliberate ───────────────────────────────────────────────────────
#
# The size win above comes from *shrinking* - removing code nothing reaches - and not from renaming
# what is left. Renaming would buy a few per cent more and would cost the thing that was built the
# same day this was turned on: the crash log. A stack trace out of an obfuscated build reads as
# `a.b.c(Unknown Source)` and is worth nothing without the mapping file for that exact release,
# which means finding a build artifact before a user's report can be read at all.
#
# So: shrink, do not obfuscate. If that trade is ever revisited, `-dontobfuscate` comes out and
# `app/build/outputs/mapping/release/mapping.txt` has to be published with every release and kept
# forever - a mapping file that has been lost is a crash log that cannot be read.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# ── TDLib ────────────────────────────────────────────────────────────────────────────────────────
#
# Nothing is needed here. `tdl-coroutines.aar` carries its own consumer rules - it keeps
# `org.drinkless.tdlib.JsonClient`'s native methods and its log callback - and AGP applies them
# without being asked. Written down because their absence from this file looks like an omission.

# ── Room, and the launch failure of 3.6.15 ───────────────────────────────────────────────────────
#
# WorkManager keeps its queue in a Room database, and Room builds the generated `_Impl` class by
# reflection - `getDeclaredConstructor().newInstance()` - so nothing references that constructor and
# R8 deleted it. The app then died before it drew anything:
#
#   java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider
#   Caused by: java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
#
# **Room ships a rule for this and it is not enough.** room-runtime 2.6.1 contributes
# `-keep class * extends androidx.room.RoomDatabase`, which keeps the class and lets R8 shrink the
# members off it; `usage.txt` listed `public void <init>()` under `WorkDatabase_Impl` in as many
# words. The member has to be named, which is what later Room versions ship and what this is.
#
# **It crashes before `CrashLog` exists.** `InitializationProvider` is a ContentProvider, and those
# are installed ahead of `Application.onCreate` - so the handler was not yet in place and the file
# it writes was empty. A crash log cannot report a crash that happens before the app starts, and
# `logcat -b crash` is what reads that one.
#
# WorkManager's own workers were never at risk: work-runtime 2.10.5 does name the members, with
# `-keepclassmembers public class * extends androidx.work.ListenableWorker { public <init>(...); }`.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ── Everything else ──────────────────────────────────────────────────────────────────────────────
#
# No rules are needed for the app's own code: there is no reflection in it at all - no
# `Class.forName`, no `getDeclaredField`, no `newInstance` - and the eight manifest components are
# kept by AGP's own rules because the manifest names them.
#
# **That was the whole of the reasoning when this file was written, and it was the wrong question.**
# What matters is not whether this app reflects but whether anything in the process does, and Room
# does, one dependency down, on a database this app never mentions. The rule above cost a release.
# So: a new dependency is a reason to read its `proguard.txt` rather than to assume it has one that
# works, and the check is `usage.txt` in `app/build/outputs/mapping/release/` - it lists every
# member R8 removed, by name.
