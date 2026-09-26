# Debug-first project. Keep Shizuku UserService/AIDL names stable for future release builds.
-keep class com.debloatix.app.ShellService { *; }
-keep interface com.debloatix.app.IShellService { *; }
