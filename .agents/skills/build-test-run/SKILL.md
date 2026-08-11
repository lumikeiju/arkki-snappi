---
name: build-test-run
description: Build, unit-test, and run the arkki-snappi JOSM plugin with the Gradle wrapper. Use when compiling, running tests, producing the plugin JAR, or launching JOSM for manual testing.
triggers:
  - gradle build
  - run tests
  - plugin jar
  - runJosm
  - build ArkkiSnappi
paths:
  - build.gradle
  - settings.gradle
  - gradle.properties
  - gradlew
  - gradlew.bat
---

# Build, Test, Run

Java 11+ required (Java 21 recommended — matches CI). The Gradle wrapper is the only build tool.

## Commands

| Task        | Command            | Notes                                            |
| ----------- | ------------------ | ------------------------------------------------ |
| Unit tests  | `./gradlew test`   | Pure EastNorth arithmetic; no JOSM instance      |
| Build JAR   | `./gradlew build`  | Runs tests first; output `build/dist/ArkkiSnappi.jar` |
| Run in JOSM | `./gradlew runJosm`| Launches JOSM with the plugin pre-loaded         |
| Clean       | `./gradlew clean`  | Removes `build/`                                 |

Windows: use `.\gradlew.bat` instead of `./gradlew`.

## Gotchas

- JavaExec and `test` tasks need `--add-exports` JVM flags — already configured in `build.gradle`; don't remove them.
- Gradle version is pinned to 8.x. The JOSM Gradle plugin 0.8.2 uses Provider.forUseAtConfigurationTime(), which was removed in Gradle 9. Do not upgrade the Gradle wrapper past 8.x until the JOSM Gradle plugin is updated. The Dependabot gradle ecosystem entry has been intentionally omitted for this reason.
- CI runs `./gradlew build` on JDK 21 (Temurin); tags `v*` also create a GitHub Release with the JAR attached.
- Manual JOSM install: copy `build/dist/ArkkiSnappi.jar` into the JOSM plugins dir, or register local update site `file:/<repo>/build/localDist/list` for hot reload during development.
