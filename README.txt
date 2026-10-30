Android GitHub Actions workflow fix

File path:
  .github/workflows/android-build.yml

Replace the existing Android build workflow with this file, or copy the
Set up Android SDK step into your existing workflow. The key fix is that
`tools` has been removed from the sdkmanager packages list; only
`platform-tools` is requested.

Note: This is a standalone workflow template based on the failure shown in
the supplied logs. If your project uses a different Gradle task, module path,
JDK version, or workflow filename, retain those project-specific settings.
