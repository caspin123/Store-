#!/data/data/com.termux/files/usr/bin/bash
set -e
printf '\n[ASTRA] Java:\n'
java -version
printf '\n[ASTRA] Gradle:\n'
gradle -v | head -n 12
printf '\n[ASTRA] Building 0.0.10-alpha...\n'
gradle clean build --no-daemon
printf '\n[ASTRA] Build complete. JAR files:\n'
ls -lh build/libs/
