# End-to-end tests against a local backend

Runs the app's instrumented tests on an Android emulator against a local copy of the
[OpenBenches website](https://github.com/openbenches/openbenches.org). No third-party accounts are needed.

```sh
e2e/run-android-e2e.sh
```

That script starts the backend (`e2e/backend/up.sh`) and runs `./gradlew connectedDebugAndroidTest`
with the app pointed at it. The tests in `app/src/androidTest/java/org/openbenches/e2e/` skip themselves
in builds that point at openbenches.org.

## One-time setup (macOS, Apple Silicon)

```sh
brew install openjdk@17 colima docker docker-compose
colima start --cpu 4 --memory 6 --vm-type vz --mount-type virtiofs
# Let the docker CLI find the compose plugin: add this to ~/.docker/config.json
#   "cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"]

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=~/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "emulator" "system-images;android-35;google_apis;arm64-v8a" "cmdline-tools;latest"
avdmanager create avd -n openbenches_e2e -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_6
echo "sdk.dir=$ANDROID_HOME" > local.properties

emulator -avd openbenches_e2e &   # add -no-window to run headless
```

## What's in the backend

`e2e/backend/docker-compose.yml` runs:

- **web**: the openbenches.org Symfony app (PHP 8.5 + Apache) on <http://localhost:8000>. `up.sh` clones
  the website into `e2e/backend/.openbenches-src` at a pinned commit of
  [rossng/openbenches.org@docker-local-dev](https://github.com/rossng/openbenches.org/tree/docker-local-dev),
  which adds the Docker setup (`dev/`). Override `OPENBENCHES_REPO` / `OPENBENCHES_REF` to test another commit.
- **db**: MariaDB seeded with the website's sample database plus `fixtures.sql` (bench 900001 at 0.5,0.5
  with a photo in `photos/`, and 900002 with no stored address). `./up.sh --reset` wipes and re-seeds it.
- **fake-auth0**: a stand-in for Auth0 on <http://localhost:8002>, with three test users. It signs ID tokens
  like Auth0 does and serves its keys at `https://auth.openbenches.test/.well-known/jwks.json` inside the
  Docker network; the website (with `AUTH0_DOMAIN` pointed there in `env.dev.local`) fetches them and
  verifies tokens with its normal code. It makes its own throwaway CA, which only the web container trusts.
- **fakes**: a stand-in for the Stadia Maps geocoder the website calls when a bench is added
  (`fakes/Caddyfile`, wired up through `env.dev.local`). Every new bench gets the address
  "Fixture Park, Testville, Testshire, England, United Kingdom".

The sample database has no photos. `./fetch-photos.sh LAT LNG` downloads the real ones (CC BY-SA) from
openbenches.org for benches near a location, e.g. `./fetch-photos.sh 51.3811 -2.3590` for Bath. They're gitignored.

## Running the app by hand against the local backend

```sh
./gradlew installDebug -Popenbenches.baseUrl=http://10.0.2.2:8000/ -Pauth.fakeUrl=http://10.0.2.2:8002/
```

With `auth.fakeUrl` (debug builds only), "Sign in" offers the fake's test users instead of Auth0's login
page. Release builds always use Auth0. `10.0.2.2` is your Mac as seen from the emulator. Debug builds allow plain HTTP to it
(`app/src/debug/res/xml/network_security_config.xml`). Release builds don't.

## Not covered yet

- Auth0's own login page and token exchange: the Auth0 SDK only talks HTTPS to a real domain, so tests swap
  in a fake sign-in on the app side. Everything from the ID token onwards (refresh, upload, the server's
  verification) is the real code.
- Benches added during tests stay in the database until `./up.sh --reset`.
- Map tiles (OpenStreetMap) and the About/Privacy WebViews still load from the internet.
