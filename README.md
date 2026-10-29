# MCA Polyamory

A Fabric addon for [MCA Reborn](https://github.com/Luke100000/minecraft-comes-alive) (Minecraft Comes Alive) that lets you court, get engaged to, and marry several villagers and players at once.

## What it does

- Removes MCA's single-partner limit so a player (or villager) can hold multiple simultaneous relationships — courting, engagement, and marriage.
- Tracks each set of partners and designates a **primary** partner, used wherever MCA expects a single spouse (family tree, procreation, home, etc.).
- Adds a `/polyamory` command to list your current partners and choose which one is primary.

## Requirements

- Minecraft 26.1.2 (Fabric)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MCA Reborn](https://github.com/Luke100000/minecraft-comes-alive) `8.1.11+26.1.2`

## Building

The project compiles against MCA, which is not redistributed here. Copy the jar listed in [`libs/README.md`](libs/README.md) into `libs/`, then:

```
./gradlew build
```

The built jar lands in `build/libs/`.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

This is an independent addon that depends on and extends **Minecraft Comes Alive (MCA Reborn)** by Luke100000 and contributors, which is itself licensed under GPL-3.0. MCA is not included in this repository; see the [MCA source](https://github.com/Luke100000/minecraft-comes-alive). This project is not affiliated with or endorsed by the MCA team.
