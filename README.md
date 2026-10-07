# mc-oasis

Hosted Paper Minecraft server for Java and Bedrock players: server image, plugins, and config.

This repo builds the server's Docker image. The image is
[itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server) running Paper, with
[Geyser](https://geysermc.org) and Floodgate so Bedrock players can join, and the world's datapacks baked in. The infrastructure that runs it
lives elsewhere.

## No player data in this repo

The repo is public. Usernames, gamertags, UUIDs and anything else that identifies a player never go here:
not in config, comments, tests or examples. The whitelist and ops list are managed privately and handed to
the container when it starts.

## Layout

```
Dockerfile               the image: base image, server settings, baked jars
server-icon.png          the 64x64 server-list icon
artifacts.lock           every third-party jar and datapack baked in, and the libraries the repo's own plugins compile against, with each one's version, URL and sha256
plugins/<Name>/          config files the repo owns for a plugin, copied over the plugin's own at every start
plugins/LuckPerms/yaml-storage/  the permission groups and tracks; which group each player is in is set in game
data/                    server config files the repo owns, copied over the same paths under the server's /data at every start
datapacks/<name>/        the repo's own datapacks, each zipped as <name>.zip in the image
plugin-src/<Name>/       the repo's own plugins, each a plugin.yml and Java sources under src/, compiled into <Name>.jar in the image; tests under test/ run at build
scripts/
  fetch-artifacts.sh     downloads everything in artifacts.lock and verifies each sha256
  smoke-test.sh          starts a built image on a throwaway world; checks every plugin and datapack loads and every config setting applies
.github/workflows/ci.yml builds and tests every PR; pushes main and dispatched branches to ECR
```

## Building locally

```bash
scripts/fetch-artifacts.sh
docker build --platform linux/arm64 --tag mc-oasis:local .
scripts/smoke-test.sh mc-oasis:local
```

## Changing a plugin, datapack or the Paper build

Edit its line in `artifacts.lock`: version, URL and sha256 together. Take the sha256 from the publisher
where they give one (the Paper and GeyserMC download APIs both do). Otherwise download the file and hash
it yourself. A mismatch fails the build.

Changing the Minecraft version also means changing `VERSION` in the `Dockerfile` to match the new Paper
jar, and the formats in each `datapacks/*/pack.mcmeta` to the new version's data pack format.

The `library` lines are what `plugin-src/` compiles against, so they follow what the server runs: `paper-api`
the Paper build, the `adventure-*` libraries the Adventure version and `guava` the version that `paper-api`'s
POM names, `jetbrains-annotations` the version `adventure-api`'s POM names, `luckperms-api` the LuckPerms
plugin's major and minor version, and `voicechat-api` the Simple Voice Chat plugin. Changing the Java version means changing the JDK image and `--release` in the
`Dockerfile`'s `repo-plugins` stage too.

Worldgen datapacks (terrain and structures) shape the world only as it is first generated, and removing one
from an existing world breaks what it generated. The comment at the top of `artifacts.lock` covers their
order.
