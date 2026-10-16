# Releasing (rossng fork)

Release APKs are signed with the fork's own key, not the original author's, so they can't update an
install of the official app: people need to uninstall that first. Every release must use the same
key, or updating will need an uninstall too.

- Keystore: `~/.android/openbenches-fork-release.jks` (alias `openbenches-fork`). Keep a backup.
- Password: macOS Keychain item `openbenches-fork-release-keystore`.
- Certificate SHA-256: `E9:CF:74:8F:D2:DE:69:A5:20:EB:85:B0:CF:EC:B5:E3:7E:A0:AA:DE:FE:7D:3C:12:77:6B:0B:37:83:FF:60:36`

To build and publish:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export OB_KEYSTORE_PATH=~/.android/openbenches-fork-release.jks
export OB_KEYSTORE_PASSWORD="$(security find-generic-password -s openbenches-fork-release-keystore -w)"
./gradlew assembleRelease   # bump versionCode/versionName in app/build.gradle.kts first
cp app/build/outputs/apk/release/app-release.apk OpenBenches-<version>.apk
gh release create v<version> OpenBenches-<version>.apk -R rossng/OpenBenches-Android-App --target browse-ux --prerelease
```
