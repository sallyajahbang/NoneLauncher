# CI log fix — 2026-10-27 (two unit-test failures)

## Evidence
The latest `logs_109771058559.zip` shows Gradle reaching `:app:testDebugUnitTest`, with 40 tests completed and 2 failing. Kotlin and Java compilation completed before the unit-test task. The failing tests were:

- `EngineCompatibilityInspectorTest.reportsMissingAndroidEngineBundleAsBlockers`, assertion at test line 44. It looks for the lowercase substring `patched GLFW classes` in a blocker, but the diagnostic used a different phrase/capitalization.
- `EngineCompatibilityInspectorTest.blocksJavaVersionsWithoutMatchingCaciocavalloToolkit`, assertion at test line 76. It looks for `supports Java 8 and 17 only`, while the runtime diagnostic previously said `support for Java 8 and 17 only`.

## Change made
In `app/src/main/java/com/sally/launcher/core/engine/EngineCompatibilityInspector.kt`, only the two diagnostic strings were changed to include the exact substrings asserted by these tests. No test was removed or weakened, and no dependency/toolchain versions were changed in this patch.

## Validation and limitation
The exact substring predicates were checked locally and both match. Attempting `./gradlew --no-daemon :app:testDebugUnitTest` in this environment could not start Gradle because `services.gradle.org` failed DNS resolution (`UnknownHostException`). Therefore this patch addresses the two failures directly indicated by the CI log, but a full green CI run is still required to confirm the complete workflow and APK build.
