# LedgerLeaf Android APK via GitHub Actions

**Prototype only. Use fictional data.** The APK has not been compiled or tested in this environment.

1. Create a **private** repository at https://github.com/new named `ledgerleaf-android`.
2. Upload the **contents** of this project directory to the repository root. Include the hidden `.github/workflows/android-apk.yml` workflow. For a reliable upload, use a computer with Git, GitHub Desktop, or Codespaces; GitHub's web upload may omit hidden paths.
3. Select **Actions** > **Build LedgerLeaf Android APK** > **Run workflow**, or push to main to start a build.
4. If the build succeeds, select the finished run and download the **LedgerLeaf-Android-Debug-APK** artifact ZIP.
5. On Android, extract the ZIP to obtain `app-debug.apk`. Tap the APK, allow your Files application to install apps if prompted, and install.
6. Only enter sample data. If the build fails, share the GitHub Actions error log for corrections.

GitHub compiles the APK; the app itself performs prototype bookkeeping offline.

**Security:** This is an unsigned-for-release debug build, with a debug signing key managed by GitHub's runner. The SQLite database is not encrypted, there is no full backup, and it is not suitable for real bank, employee, or customer financial information. No live financial APIs are included.
