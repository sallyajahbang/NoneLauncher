Staging only, NOT active. Source: Amethyst-Android (LGPL-3.0), jre_lwjgl3glfw/lwjgl-3.4.1.

jars/        Official LWJGL 3.4.1 modules used by Amethyst.
src-patched/ Android-patched Java sources (CallbackBridge, GLFW, Android stubs).

Not yet wired in. The matching native libraries (liblwjgl*.so) for 3.4.1 are
not included, and must come from the same build. Java and native must be the
same version or the launcher can crash. Switch on together with the natives.
