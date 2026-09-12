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

# ── Everything else ──────────────────────────────────────────────────────────────────────────────
#
# No rules are needed for the app's own code: there is no reflection in it at all - no
# `Class.forName`, no `getDeclaredField`, no `newInstance` - and the eight manifest components are
# kept by AGP's own rules because the manifest names them. The one thing that would change that is
# a library that looks classes up by name, so a dependency added here is a reason to check this file.
