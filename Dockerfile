# The Oasis server: Paper with the plugins and datapacks in artifacts.lock and
# the repo's own datapacks, run by itzg/minecraft-server. Build after
# scripts/fetch-artifacts.sh, which puts the verified jars in build/artifacts/.
#
# Everything the server runs is baked in. The base image's own downloads are
# switched off below, so a container start fetches no code. The one exception
# is Paperclip, inside the Paper jar, which fetches the vanilla server and its
# libraries from Mojang on first start and checks them against hashes the Paper
# build carries.
#
# No player lists here: this repo is public, so WHITELIST and OPS are supplied
# by the host when the container starts.

# The repo's own datapacks, one folder each under datapacks/, zipped under the
# folder's name: the base image copies only zips into the world.
FROM python:3.14-alpine@sha256:f6a589d43c42b9e7f7dc67a12d37132491f362859a5d750607710cc56da3bc72 AS repo-datapacks
COPY datapacks/ /src/
RUN mkdir /out && cd /src && for pack in */; do \
      pack="${pack%/}" && (cd "$pack" && python -m zipfile -c "/out/${pack}.zip" ./*); \
    done

# A release tag for the image's scripts, java25 because Minecraft 26.2 requires
# Java 25, and the multi-arch digest so a re-pushed tag can't change it.
FROM itzg/minecraft-server:2026.9.2-java25@sha256:de5d1b1a83eba576f6c8a688fac2a3523ce457724cdebc8ea48d7818b74cdf6e

# The baked Paper jar instead of a download, and no default configs fetched
# from GitHub at start. VERSION must name the jar's Minecraft version: the
# image's scripts branch on it.
ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=26.2 \
    PAPER_CUSTOM_JAR=/opt/server.jar \
    SKIP_DOWNLOAD_DEFAULTS=true

ENV MEMORY=6G \
    USE_AIKAR_FLAGS=true

# Access control. The whitelist is only as good as online-mode, which makes
# Mojang (and Xbox Live, through Floodgate) prove who a player is. SYNCHRONIZE
# makes the host-supplied lists authoritative: in-game /whitelist and /op
# changes are replaced at the next start.
ENV ONLINE_MODE=true \
    ENABLE_WHITELIST=true \
    ENFORCE_WHITELIST=true \
    EXISTING_WHITELIST_FILE=SYNCHRONIZE \
    EXISTING_OPS_FILE=SYNCHRONIZE \
    HIDE_ONLINE_PLAYERS=true

# The server-list icon. OVERRIDE_ICON makes the image's copy replace the one
# a previous start left on the world volume.
ENV ICON=/server-icon.png \
    OVERRIDE_ICON=true

ENV MOTD="§bThe Oasis SMP" \
    DIFFICULTY=normal \
    MAX_PLAYERS=20 \
    VIEW_DISTANCE=20 \
    SIMULATION_DISTANCE=15

# Spawn is protected by the WorldGuard region set up in RCON_CMDS_STARTUP, so
# vanilla's square of op-only blocks around it is off.
ENV SPAWN_PROTECTION=0

# For `rcon-cli` inside the container only; the port is never published. With
# no RCON_PASSWORD the image generates a fresh one at every start.
ENV ENABLE_RCON=true

# Stop the server after 20 minutes with nobody on, or 30 after a start nobody
# joins. Touching /data/.skip-stop holds it up, e.g. while Chunky pre-generates.
ENV PLAYER_IDLE_TIMEOUT=120 \
    ENABLE_AUTOSTOP=TRUE \
    AUTOSTOP_TIMEOUT_EST=1200 \
    AUTOSTOP_TIMEOUT_INIT=1800

# The image is authoritative over /data/plugins and over the server configs
# the repo owns, which data/ holds at their paths under /data. Its files
# overwrite live ones even where the server or a plugin rewrote them since,
# and a jar no longer in artifacts.lock is removed. Only top-level jars go, so
# a removed plugin's data folder survives.
ENV SYNC_SKIP_NEWER_IN_DESTINATION=false \
    COPY_CONFIG_DEST=/data \
    REMOVE_OLD_MODS=TRUE \
    REMOVE_OLD_MODS_INCLUDE=*.jar \
    REMOVE_OLD_MODS_DEPTH=1

# The datapacks in artifacts.lock and the repo's own, copied into the world's
# datapacks folder at every start. Packs already there are removed first, so
# the image is authoritative.
ENV DATAPACKS=/datapacks \
    REMOVE_OLD_DATAPACKS=true

# Commands run through RCON at every start, so the repo stays authoritative
# over what they set, and a change made in game lasts only until the next start.
#
# ClickVillagers is pickup-and-place and trade resetting only. Its other
# features are each a permission it grants everyone by default, denied here,
# while LuckPerms keeps the rest of its data, which names players, on the world
# volume. The baked config turns on trade resetting and turns off its villager
# hoppers and update check.
#
# The world borders: blocks -10,000 to 10,000 in the overworld and -5,000 to
# 5,000 in the Nether; the End is unbounded. An odd width centered on a whole
# block, which is what `center 0 0` gives (0.5, 0.5), runs the same number of
# blocks either way from block 0.
#
# Everyone spawns on the spawn point itself: respawn_radius, the rule older
# versions call spawnRadius, is 0.
#
# Spawn is on an island, and the WorldGuard region `spawn` covers all of it:
# the island runs x -504 to -437 and z 854 to 928, the region 8 blocks of water
# past that, from the bottom of the world to the top. The console has no
# selection of its own, so the region is drawn from WorldEdit positions.
# `define` fails once the region exists, and `redefine` then resets its area to
# these bounds. `redefine` comes after the flags: it copies the region at once
# but swaps the copy in later, in the background, so a flag set after it could
# land on the region being replaced. With no owners or members, only ops can
# build or break blocks in it; the flags cover what membership doesn't.
#
# The game is frozen until the server opens to players, when the `tick freeze`
# line comes out. Nothing saves a freeze, so it is set at every start.
ENV RCON_CMDS_STARTUP="\
lp group default permission set clickvillagers.claim false\n\
lp group default permission set clickvillagers.anchor false\n\
lp group default permission set clickvillagers.partner false\n\
lp group default permission set clickvillagers.change-biome false\n\
lp group default permission set clickvillagers.hopper false\n\
execute in minecraft:overworld run worldborder center 0 0\n\
execute in minecraft:overworld run worldborder set 20001\n\
execute in minecraft:the_nether run worldborder center 0 0\n\
execute in minecraft:the_nether run worldborder set 10001\n\
setworldspawn -468 63 898\n\
gamerule respawn_radius 0\n\
//world world\n\
//pos1 -512,-64,846\n\
//pos2 -429,319,936\n\
rg define -w world spawn\n\
rg flag -w world spawn pvp allow\n\
rg flag -w world spawn mob-spawning deny\n\
rg flag -w world spawn creeper-explosion deny\n\
rg flag -w world spawn tnt deny\n\
rg flag -w world spawn other-explosion deny\n\
rg flag -w world spawn ghast-fireball deny\n\
rg flag -w world spawn fire-spread deny\n\
rg flag -w world spawn lava-fire deny\n\
rg flag -w world spawn lighter deny\n\
rg flag -w world spawn enderman-grief deny\n\
rg flag -w world spawn use allow\n\
rg flag -w world spawn chest-access allow\n\
rg redefine -w world spawn\n\
tick freeze"

COPY build/artifacts/server.jar /opt/server.jar
COPY build/artifacts/plugins/ /plugins/
# Plugin configs the repo owns, one folder per plugin by its declared name.
# Simple Voice Chat's turns recording off: some players are minors.
# TradeCycle's keeps only its swap-hands-key trigger: its sneak-click one is
# ClickVillagers' pickup.
COPY plugins/ /plugins/
COPY data/ /config/
COPY build/artifacts/datapacks/ /datapacks/
COPY --from=repo-datapacks /out/ /datapacks/
COPY server-icon.png /server-icon.png
