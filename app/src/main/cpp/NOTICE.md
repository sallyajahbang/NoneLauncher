# Notice: third-party code in this folder

Files in this folder are copied from **ZalithLauncher2**
(https://github.com/ZalithLauncher/ZalithLauncher2, GPL-3.0, snapshot taken 2026-10-29)
and are used under the terms of the GNU General Public License v3.0.

| File | Origin | Notes |
|---|---|---|
| `jre_launcher.c` | ZalithLauncher2 `jni/jre_launcher.c` | Derived from the OpenJDK launcher; keeps the original Oracle header (GPL-2.0 with Classpath exception). |
| `utils.c`, `utils.h` | ZalithLauncher2 `jni/utils.*` | |
| `logger/` | ZalithLauncher2 `jni/logger/` | |
| `environ/` | ZalithLauncher2 `jni/environ/` | |
| `stdio_is.c`, `stdio_is.h` | ZalithLauncher2 `jni/stdio_is.*` | Provides `nominal_exit`. |
| `ctxbridges/common.h` | ZalithLauncher2 `jni/ctxbridges/common.h` | |

The Java side (`app/src/main/java/com/oracle/dalvik/VMLauncher.java`) is also copied from ZalithLauncher2.

Original copyright and license notices in each file have been left unchanged.

**Changes from the originals (only added standard includes that the NDK headers
provided implicitly):**
- `jre_launcher.c`: `#include <signal.h>`
- `stdio_is.c`: `#include <signal.h>`
- `utils.c`: `#include <stdint.h>`
- `environ/environ.h`: `#include <stdbool.h>`

Not copied yet: `exit_hook.c` (needs bytehook), renderers (`egl_bridge.c`, `ctxbridges/*.c` except `common.h`), and the other JNI modules.
