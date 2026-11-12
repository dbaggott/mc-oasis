# mc-oasis

Hosted Paper Minecraft server for Java and Bedrock players: server image, plugins, and config, plus the
server's website at www.oasis-smp.com.

This repo builds the server's Docker image. The image is
[itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server) running Paper, with
[Geyser](https://geysermc.org) and Floodgate so Bedrock players can join, and the world's datapacks baked in. The infrastructure that runs it
lives elsewhere.

## No player data in this repo

The repo is public. Usernames, gamertags, UUIDs and anything else that identifies a player never go here:
not in config, comments, tests or examples. The allowlist and ops list are managed privately and handed to
the container when it starts.

## Layout

```
Dockerfile               the image: base image, server settings, baked jars
server-icon.png          the 64x64 server-list icon
artifacts.lock           every third-party jar and datapack baked in, and the libraries the repo's own plugins compile against, with each one's version, URL and sha256
plugins/<Name>/          config files the repo owns for a plugin, copied over the plugin's own at every start
plugins/LuckPerms/yaml-storage/  the permission groups and tracks; which group each player is in is set in game
plugins/OasisSpawn/      spawn as built on the live server, spawn.schem, and the SHA-256 of the seed it was built in; OasisSpawn pastes it into a newly generated world on that seed, so spawn comes back on its own
data/                    server config files the repo owns, copied over the same paths under the server's /data at every start
datapacks/<name>/        the repo's own datapacks, each zipped as <name>.zip in the image
plugin-src/<Name>/       the repo's own plugins, each a plugin.yml and Java sources under src/, compiled into <Name>.jar in the image; tests under test/ run at build
hook-dupe-agent/         a Java agent the server starts with, which puts back tripwire hook duping; its class comment says how; tests under test/ run at build
resourcepack/            the Java resource pack every player is required to take: Technoblade's crown, painted on the pig variant in datapacks/80-oasis-technoblade/; CREDITS.txt says where it comes from
resourcepack-bedrock/    the Bedrock pack Geyser sends every Bedrock player: Technoblade's crown, modelled on the head of the pig OasisTechnoblade marks; CREDITS.txt says where it comes from
scripts/
  fetch-artifacts.sh     downloads everything in artifacts.lock and verifies each sha256
  patch-worldguard.py    run by the Docker build: sets the repeat delay on WorldGuard's region messages to 1.5 s
  smoke-test.sh          starts a built image on a throwaway world; checks every plugin and datapack loads and every config setting applies
  bump-bedrock.sh        moves the Geyser, Floodgate and MCXboxBroadcast lines in artifacts.lock to their newest builds at least 48 hours old
  build-pack.sh          zips a resource pack so the same files always give the same bytes
  java-pack.sh           builds resourcepack/ as oasis-pack-<sha1>.zip and prints the SHA-1
web/                     the website: a Vite site; its rules page renders plugins/OasisRules/rules.txt
api/                     the website's API: a parent's request for their child to be let in, as a container-image Lambda
shared/                  what the site's forms and the API both import, so they agree on every field
.github/workflows/ci.yml builds and tests every PR; publishes main and dispatched branches (images to ECR, the website to S3)
.github/workflows/bump-bedrock.yml  daily, runs bump-bedrock.sh and opens or updates an auto-merging PR on bump/bedrock
.github/workflows/mirror-lambda-adapter.yml  on dispatch, copies a Lambda Web Adapter release from ECR Public to GHCR for api/Dockerfile
```

## Building locally

```bash
scripts/fetch-artifacts.sh
docker build --platform linux/arm64 --build-arg RESOURCE_PACK_SHA1="$(scripts/java-pack.sh build/resourcepack)" \
  --tag mc-oasis:local .
scripts/smoke-test.sh mc-oasis:local
```

The image names the resource pack's URL on the website, which a local server can't serve until the pack is deployed
there. To play on a local server with the pack, serve the pack yourself and point the server at it:

```bash
python3 -m http.server --directory build/resourcepack 8000 &
docker run --rm -it -p 25565:25565 -p 19132:19132/udp -p 19132:19132/tcp -v mc-oasis-local:/data \
  -e WHITELIST=<you> -e OPS=<you> \
  -e RESOURCE_PACK="http://localhost:8000/oasis-pack-$(scripts/java-pack.sh build/resourcepack).zip" mc-oasis:local
```

The client fetches the pack, so `localhost` is the machine the game runs on. Use the machine's LAN address instead
for a client on another device.

Bedrock players reach a local server only once Geyser advertises an address they can get to: inside Docker it offers
the container's own. After the first start, set `advertise-addresses` under `bedrock.signaling` in
`plugins/Geyser-Spigot/config.yml` on the volume to `["<this machine's LAN address>"]`, and start it again. The
server's 6 GB heap may not fit Docker's memory on a laptop; `-e MEMORY=2G` shrinks it.

The smoke test needs Docker and `jq`.

## Working on the website

```bash
npm ci --prefix web && npm ci --prefix api
npm run dev:api --prefix web   # the API on :8787, keeping requests in memory
npm run dev --prefix web       # the site, proxying /api to it
```

`npm test --prefix api` tests the API; `npm run build --prefix web && npm test --prefix web` builds the site and
checks the build. Requests sent to a local API are kept in memory and never leave the machine.

## Changing a plugin, datapack or the Paper build

Edit its line in `artifacts.lock`: version, URL and sha256 together. Take the sha256 from the publisher
where they give one (the Paper and GeyserMC download APIs both do, and GitHub's release API gives each
asset's as its `digest`). Otherwise download the file and hash
it yourself. A mismatch fails the build.

OasisTechnoblade compiles against the Geyser jar in `artifacts.lock`, for the entity property that puts Technoblade's
crown on its head for Bedrock players, and the smoke test checks Geyser took the property. A Geyser bump that changes
that API fails the build or the smoke test.

Geyser, Floodgate and the MCXboxBroadcast extension are bumped for you: the Bump Bedrock workflow opens a PR on
`bump/bedrock` daily when there is a newer build, which merges itself once CI passes. If an extension release
fails CI, pin its line back by hand in that PR, or the day's Geyser bump is held back with it. The next day's
run proposes that release again, so this repeats daily until one passes. When a Bedrock
client update has locked players out before a build is 48 hours old, run it from the Actions tab with
`skip_wait` checked.

A WorldGuard build fails the Docker build at `scripts/patch-worldguard.py`, which is pinned to the one class it
rewrites. Check the new class still compiles the delay as the one long 500, then pin the class's new sha256 there.

A Paper build can change the vanilla method `hook-dupe-agent/` rewrites. If the agent no longer recognises it,
the smoke test fails on the agent's check, and the matcher in `HookDupeAgent.java` follows the new code.

Changing the Minecraft version also means changing `VERSION` in the `Dockerfile` to match the new Paper
jar, the formats in each `datapacks/*/pack.mcmeta` to the new version's data pack format, and the format in
`resourcepack/pack.mcmeta` to its resource pack format.

The `library` lines are what `plugin-src/` compiles against, so they follow what the server runs: `paper-api`
the Paper build, the `adventure-*` libraries the Adventure version, `guava` and `brigadier` the versions that
`paper-api`'s POM names, `jetbrains-annotations` the version `adventure-api`'s POM names, `luckperms-api` the LuckPerms
plugin's major and minor version, `voicechat-api` the Simple Voice Chat plugin, `libertybans-api` the LibertyBans
plugin, `omnibus` the version `libertybans-api`'s parent POM names, `worldguard-core` the WorldGuard plugin, and
`worldedit-core` and `worldedit-bukkit` the WorldEdit plugin. Changing the Java version
means changing the JDK image and `--release` in the `Dockerfile`'s `repo-plugins` stage too.

Worldgen datapacks (terrain and structures) shape the world only as it is first generated, and removing one
from an existing world breaks what it generated. The comment at the top of `artifacts.lock` covers their
order.

## Changing the resource pack

Every Java player has to take the pack in `resourcepack/`: one who declines it is disconnected. The website serves it
as `assets/oasis-pack-<sha1>.zip`, named by its SHA-1, and the server image built in the same CI run names that URL and
SHA-1. A site deploy never deletes a file under `assets/`, so once deployed, a pack stays served.

**Deploy the site release that carries a new pack before the server image that names it.** Until then the server
points every Java player at a URL that isn't served, and they can't join. The zip is built the same way every time
(`scripts/build-pack.sh`), so this only matters when something under `resourcepack/` changed.

Bedrock players get `resourcepack-bedrock/` from Geyser instead. Raise the `version` in its `manifest.json` (header and
module alike) whenever anything in it changes: Bedrock clients keep a pack they already have at the same version.

Java players see the crown painted on the pig variant `oasis:technoblade`, which `datapacks/80-oasis-technoblade/`
defines and `resourcepack/` textures. `oasis:technoblade_glaring` beside it is the same pig with red eyes, which
Technoblade has while it stares at a player who killed it; Bedrock players see that stare with the usual eyes.
Bedrock players see a 3D crown on any pig whose Bedrock entity property
`oasis:crowned` is set, which OasisTechnoblade sets on Technoblade alone. The crown comes from an MIT-licensed pack,
whose notice `resourcepack-bedrock/CREDITS.txt` carries and has to keep; `resourcepack/CREDITS.txt` says where the
painted crown comes from.

`resourcepack-bedrock/entity/pig.entity.json` replaces Bedrock's own pig for Bedrock players, so it is Mojang's file
with the crown added. When a Bedrock release changes its pig, take the new file from
[bedrock-samples](https://github.com/Mojang/bedrock-samples) and add the crown's lines back, or every pig a Bedrock
player sees keeps the old definition.
