# ASTRA PHYSICS — Minecraft 1.21.11 build fix

This source package fixes the 12 Java compilation errors reported on Termux/Gradle 9.7.1.

Changes:
- Command permission check migrated to the Minecraft 1.21.11 permission API.
- ResourceKey `location()` calls migrated to `identifier()`.
- Player log name no longer depends on the Authlib GameProfile getter.
- SimpleContainer persistence rewritten to serialize each non-empty ItemStack through ItemStack.CODEC + RegistryOps/NbtOps.

Build:

```sh
chmod +x BUILD-TERMUX.sh
./BUILD-TERMUX.sh
```

The Termux warning about missing native Gradle integration is expected and is not the Java compile failure.
