# The Oasis server: Paper with the plugins and datapacks in artifacts.lock and
# the repo's own datapacks, run by itzg/minecraft-server. Build after
# scripts/fetch-artifacts.sh, which puts the verified jars in build/artifacts/.
#
# Everything the server runs is baked in. The base image's own downloads are
# switched off below, so a container start fetches no code. There are two
# exceptions, each checked against hashes inside a jar artifacts.lock pins.
# Paperclip, inside the Paper jar, fetches the vanilla server and its libraries
# from Mojang on first start. LibertyBans' jar is only a loader: it fetches the
# plugin itself and its libraries from Maven repositories.
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

# The repo's own plugins, one folder each under plugin-src/ holding its
# plugin.yml and its Java sources under src/, compiled against the libraries in
# artifacts.lock into <folder>.jar. The JDK's major version is the server's.
# A plugin's tests, any test/**/*Test.java, are each run from the repo root,
# where they can read the plugin's config under plugins/, and a failing one
# fails the build.
FROM eclipse-temurin:25-jdk-alpine@sha256:3fd2d245c4e0eba615fe366a71b8bd25f5db7104f53e4026b24bf508b880bd2a AS repo-plugins
COPY build/artifacts/libraries/ /libraries/
COPY plugin-src/ /repo/plugin-src/
COPY plugins/ /repo/plugins/
RUN set -e; mkdir /out; cd /repo/plugin-src; for plugin in */; do \
      [ -d "$plugin" ] || continue; \
      plugin="${plugin%/}"; \
      mkdir -p "/classes/${plugin}"; \
      javac --release 25 -Xlint:all,-classfile -Werror -cp '/libraries/*' \
        -d "/classes/${plugin}" $(find "${plugin}/src" -name '*.java'); \
      if [ -d "${plugin}/test" ]; then \
        mkdir -p "/test-classes/${plugin}"; \
        javac --release 25 -Xlint:all,-classfile -Werror -cp "/classes/${plugin}:/libraries/*" \
          -d "/test-classes/${plugin}" $(find "${plugin}/test" -name '*.java'); \
        for test in $(cd "${plugin}/test" && find . -name '*Test.java'); do \
          test="${test#./}"; \
          (cd /repo && java -cp "/test-classes/${plugin}:/classes/${plugin}:/libraries/*" \
            "$(echo "${test%.java}" | tr / .)"); \
        done; \
      fi; \
      cp "${plugin}/plugin.yml" "/classes/${plugin}/"; \
      jar --create --file "/out/${plugin}.jar" -C "/classes/${plugin}" .; \
    done

# LibertyBans' LuckPerms exemption add-on, taken from inside the pinned
# LibertyBans jar so it is always the version of the plugin it runs in, under
# the name LibertyBans' own `addon install` gives it.
FROM python:3.14-alpine@sha256:f6a589d43c42b9e7f7dc67a12d37132491f362859a5d750607710cc56da3bc72 AS libertybans-addons
COPY build/artifacts/plugins/LibertyBans.jar /LibertyBans.jar
RUN mkdir -p /out/LibertyBans/addons \
    && unzip -p /LibertyBans.jar dependencies/addon-jars/addon-exemption-luckperms.jar \
      > /out/LibertyBans/addons/addon-exemption-luckperms.jar \
    && test -s /out/LibertyBans/addons/addon-exemption-luckperms.jar

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
# and a jar no longer in the image is removed. Only top-level jars go, so
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
# The world borders: blocks -10,000 to 10,000 in the overworld and -5,000 to
# 5,000 in the Nether; the End is unbounded. An odd width centered on a whole
# block, which is what `center 0 0` gives (0.5, 0.5), runs the same number of
# blocks either way from block 0.
#
# Everyone spawns on the spawn point itself: respawn_radius, the rule older
# versions call spawnRadius, is 0.
#
# One player in bed is enough to skip the night: vanilla needs at least one
# sleeper whatever the percentage, so 0 means one. There is no locator bar.
# Paper keeps game rules per dimension and RCON runs in the overworld, so a rule
# that matters outside it is set in each dimension with `execute in`.
#
# BlazeandCave's Advancements Pack greets each player in chat on their first
# join: the first player ever with a thank-you for downloading it, everyone
# after with a welcome. `introduced` 1 marks the thank-you as already given and
# `intro_msg` 0 turns off the welcome. The pack's load function sets up its
# settings, intro_msg 1 in a new world, before these run.
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
ENV RCON_CMDS_STARTUP="\
execute in minecraft:overworld run worldborder center 0 0\n\
execute in minecraft:overworld run worldborder set 20001\n\
execute in minecraft:the_nether run worldborder center 0 0\n\
execute in minecraft:the_nether run worldborder set 10001\n\
setworldspawn -468 63 898\n\
gamerule respawn_radius 0\n\
gamerule players_sleeping_percentage 0\n\
execute in minecraft:overworld run gamerule locator_bar false\n\
execute in minecraft:the_nether run gamerule locator_bar false\n\
execute in minecraft:the_end run gamerule locator_bar false\n\
scoreboard players set introduced bac_settings 1\n\
scoreboard players set intro_msg bac_settings 0\n\
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
rg redefine -w world spawn"

COPY build/artifacts/server.jar /opt/server.jar
COPY build/artifacts/plugins/ /plugins/
# The repo's own plugins, built in the repo-plugins stage.
COPY --from=repo-plugins /out/ /plugins/
COPY --from=libertybans-addons /out/ /plugins/
# Plugin configs the repo owns, one folder per plugin by its declared name.
# LuckPerms' groups and tracks are files here; which group each player is in
# stays in its database on the world volume.
# ClickVillagers' turns on trade resetting and turns off its villager hoppers
# and update check.
# Simple Voice Chat's turns recording off: some players are minors.
# TradeCycle's keeps only its swap-hands-key trigger: its sneak-click one is
# ClickVillagers' pickup.
# TAB's turns on its spectator fix, so a player in spectator doesn't show as
# one in non-ops' tab lists, and puts each player's LuckPerms prefix before
# their name in the tab list.
# OasisFilter's are its word lists, and OasisRules' the server rules.
COPY plugins/ /plugins/
COPY data/ /config/
COPY build/artifacts/datapacks/ /datapacks/
COPY --from=repo-datapacks /out/ /datapacks/
COPY server-icon.png /server-icon.png

# The copy over /data/plugins adds and replaces files but never deletes them,
# so LuckPerms' group and track folders and OasisFilter's word lists on the
# world volume are emptied first: a group, track or word list file the repo no
# longer has is gone, not still loaded. As the server's own user, which owns
# them; root has no capability to.
ENTRYPOINT ["/bin/bash", "-c", "gosu minecraft:minecraft rm -rf /data/plugins/LuckPerms/yaml-storage/groups /data/plugins/LuckPerms/yaml-storage/tracks /data/plugins/OasisFilter/lists && exec /image/scripts/start \"$@\"", "start"]
