#!/data/data/com.termux/files/usr/bin/bash
# Builds ASTRA Physics on Termux (Android ARM64).
set -e

printf '\n[ASTRA] Java:\n'
java -version

printf '\n[ASTRA] Gradle:\n'
gradle -v | head -n 12

# The asset checks need no network and no Minecraft jar, so they run first: a missing
# translation or a blockstate pointing at a renamed model would otherwise only show up
# in game, long after a successful build.
printf '\n[ASTRA] Validating assets...\n'
python3 tools/validate.py

printf '\n[ASTRA] Building...\n'
gradle clean build --no-daemon

printf '\n[ASTRA] Build complete. JAR files:\n'
ls -lh build/libs/
