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
# folder's name: the base image copies only zips into the world. The
# advancements in 41-dnt-librarian-advancements come from the pinned DnT and
# enchant disabler, so they follow whichever versions artifacts.lock pins.
FROM python:3.14-alpine@sha256:a18b20ad69e74df6cf6d2e1b1a260d7a1ecf5380fd83b310f83ba88c461cf894 AS repo-datapacks
COPY datapacks/ /src/
COPY build/artifacts/datapacks/20-dungeons-and-taverns.zip build/artifacts/datapacks/40-dnt-enchant-disabler.zip /dnt/
COPY scripts/hide-dnt-advancements.py /hide-dnt-advancements.py
RUN python /hide-dnt-advancements.py /dnt/20-dungeons-and-taverns.zip /dnt/40-dnt-enchant-disabler.zip \
      /src/41-dnt-librarian-advancements
RUN mkdir /out && cd /src && for pack in */; do \
      pack="${pack%/}" && (cd "$pack" && python -m zipfile -c "/out/${pack}.zip" ./*); \
    done

# The Bedrock resource pack, resourcepack-bedrock/, zipped as Geyser takes it:
# Technoblade's crown for Bedrock players, modelled on the head of any pig
# OasisTechnoblade marks through Geyser.
FROM python:3.14-alpine@sha256:a18b20ad69e74df6cf6d2e1b1a260d7a1ecf5380fd83b310f83ba88c461cf894 AS bedrock-pack
COPY scripts/build-pack.sh /build-pack.sh
COPY resourcepack-bedrock/ /src/
RUN mkdir /out && sh /build-pack.sh /src /out/oasis.mcpack

# The repo's own plugins, one folder each under plugin-src/ holding its
# plugin.yml and its Java sources under src/, compiled against the libraries in
# artifacts.lock into <folder>.jar. The JDK's major version is the server's.
# A plugin's tests, any test/**/*Test.java, are each run from the repo root,
# where they can read the plugin's config under plugins/, and a failing one
# fails the build.
# The `path` warning is off: worldedit-bukkit's manifest puts jars on the class
# path, for archive and scripting support, that aren't here and aren't needed.
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS repo-plugins
COPY build/artifacts/libraries/ /libraries/
# Geyser's API, for OasisTechnoblade's Bedrock crown, from the Geyser jar the
# server runs, so the two always match. Last on the class path, so nothing
# else Geyser bundles stands in for a library in artifacts.lock.
COPY build/artifacts/plugins/Geyser-Spigot.jar /geyser/
COPY plugin-src/ /repo/plugin-src/
COPY plugins/ /repo/plugins/
RUN set -e; mkdir /out; cd /repo/plugin-src; for plugin in */; do \
      [ -d "$plugin" ] || continue; \
      plugin="${plugin%/}"; \
      mkdir -p "/classes/${plugin}"; \
      javac --release 25 -Xlint:all,-classfile,-path -Werror -cp '/libraries/*:/geyser/*' \
        -d "/classes/${plugin}" $(find "${plugin}/src" -name '*.java'); \
      if [ -d "${plugin}/test" ]; then \
        mkdir -p "/test-classes/${plugin}"; \
        javac --release 25 -Xlint:all,-classfile,-path -Werror -cp "/classes/${plugin}:/libraries/*:/geyser/*" \
          -d "/test-classes/${plugin}" $(find "${plugin}/test" -name '*.java'); \
        for test in $(cd "${plugin}/test" && find . -name '*Test.java'); do \
          test="${test#./}"; \
          (cd /repo && java -cp "/test-classes/${plugin}:/classes/${plugin}:/libraries/*:/geyser/*" \
            "$(echo "${test%.java}" | tr / .)"); \
        done; \
      fi; \
      cp "${plugin}/plugin.yml" "/classes/${plugin}/"; \
      jar --create --file "/out/${plugin}.jar" -C "/classes/${plugin}" .; \
    done

# The repo's Java agent, hook-dupe-agent/: it rewrites one vanilla check as
# the server loads the class, which no plugin can. Its class comment says what
# and why. Its tests, any test/**/*Test.java, run here, and a failing one
# fails the build.
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS hook-dupe-agent
COPY hook-dupe-agent/ /agent/
RUN javac --release 25 -Xlint:all -Werror -d /classes $(find /agent/src -name '*.java') \
    && javac --release 25 -Xlint:all -Werror -cp /classes -d /test-classes $(find /agent/test -name '*.java') \
    && cd /agent/test && for test in $(find . -name '*Test.java'); do \
      test="${test#./}" && java -cp /test-classes:/classes "$(echo "${test%.java}" | tr / .)" || exit 1; \
    done \
    && jar --create --file /hook-dupe-agent.jar --manifest /agent/MANIFEST.MF -C /classes .

# LibertyBans' LuckPerms exemption add-on, taken from inside the pinned
# LibertyBans jar so it is always the version of the plugin it runs in, under
# the name LibertyBans' own `addon install` gives it.
FROM python:3.14-alpine@sha256:a18b20ad69e74df6cf6d2e1b1a260d7a1ecf5380fd83b310f83ba88c461cf894 AS libertybans-addons
COPY build/artifacts/plugins/LibertyBans.jar /LibertyBans.jar
RUN mkdir -p /out/LibertyBans/addons \
    && unzip -p /LibertyBans.jar dependencies/addon-jars/addon-exemption-luckperms.jar \
      > /out/LibertyBans/addons/addon-exemption-luckperms.jar \
    && test -s /out/LibertyBans/addons/addon-exemption-luckperms.jar

# WorldGuard with the repeat delay on its region messages at 1.5 s rather than
# its 0.5 s, which no setting changes: scripts/patch-worldguard.py rewrites it.
FROM python:3.14-alpine@sha256:a18b20ad69e74df6cf6d2e1b1a260d7a1ecf5380fd83b310f83ba88c461cf894 AS worldguard
COPY build/artifacts/plugins/WorldGuard.jar /WorldGuard.jar
COPY scripts/patch-worldguard.py /patch-worldguard.py
RUN mkdir /out && python /patch-worldguard.py /WorldGuard.jar /out/WorldGuard.jar

# A release tag for the image's scripts, java25 because Minecraft 26.3 requires
# Java 25, and the multi-arch digest so a re-pushed tag can't change it.
FROM itzg/minecraft-server:2026.11.0-java25@sha256:8d5080d8dcd0a2629c2e4f248678373a337a40537ad6ef2857c7dbcda6e3eadd

# The baked Paper jar instead of a download, and no default configs fetched
# from GitHub at start. VERSION must name the jar's Minecraft version: the
# image's scripts branch on it.
ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=26.3 \
    PAPER_CUSTOM_JAR=/opt/server.jar \
    SKIP_DOWNLOAD_DEFAULTS=true

ENV MEMORY=6G \
    USE_AIKAR_FLAGS=true

# Bedrock players connect over NetherNet, whatever transport Geyser's config on
# the world volume holds: builds before 1273 wrote `raknet` there, and current
# Bedrock clients no longer speak it. Signaling stays Geyser's default, built
# in on the Bedrock port over TCP. JVM_OPTS rather than JVM_DD_OPTS, which the
# host replaces (mc-oasis-boot in dbaggott/infrastructure's apps/mc-oasis).
# The agent, built in the hook-dupe-agent stage, puts back tripwire hook duping.
ENV JVM_OPTS="-DgeyserTransport=nethernet -javaagent:/opt/hook-dupe-agent.jar"

# Access control. The allowlist is only as good as online-mode, which makes
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

ENV MOTD="§bThe OASIS School SMP" \
    DIFFICULTY=normal \
    MAX_PLAYERS=20 \
    VIEW_DISTANCE=20 \
    SIMULATION_DISTANCE=15

# Spawn is protected by the WorldGuard region in plugins/OasisRegions/regions.txt,
# so vanilla's square of op-only blocks around it is off.
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
# 30% of the players in the overworld, rounded up, must sleep to skip the
# night; spectators don't count, and it is never fewer than one. There is no
# locator bar.
# Paper keeps game rules per dimension and RCON runs in the overworld, so a rule
# that matters outside it is set in each dimension with `execute in`.
#
# The vanilla "moved too quickly" check is off for players, flying or not,
# since GrimAC checks movement; data/spigot.yml turns off the rest.
#
# BlazeandCave's Advancements Pack greets each player in chat on their first
# join: the first player ever with a thank-you for downloading it, everyone
# after with a welcome. `introduced` 1 marks the thank-you as already given and
# `intro_msg` 0 turns off the welcome. The pack's load function sets up its
# settings, intro_msg 1 in a new world, before these run.
ENV RCON_CMDS_STARTUP="\
execute in minecraft:overworld run worldborder center 0 0\n\
execute in minecraft:overworld run worldborder set 20001\n\
execute in minecraft:the_nether run worldborder center 0 0\n\
execute in minecraft:the_nether run worldborder set 10001\n\
setworldspawn -468 63 898\n\
gamerule respawn_radius 0\n\
gamerule players_sleeping_percentage 30\n\
execute in minecraft:overworld run gamerule locator_bar false\n\
execute in minecraft:the_nether run gamerule locator_bar false\n\
execute in minecraft:the_end run gamerule locator_bar false\n\
execute in minecraft:overworld run gamerule player_movement_check false\n\
execute in minecraft:the_nether run gamerule player_movement_check false\n\
execute in minecraft:the_end run gamerule player_movement_check false\n\
execute in minecraft:overworld run gamerule elytra_movement_check false\n\
execute in minecraft:the_nether run gamerule elytra_movement_check false\n\
execute in minecraft:the_end run gamerule elytra_movement_check false\n\
scoreboard players set introduced bac_settings 1\n\
scoreboard players set intro_msg bac_settings 0"

COPY build/artifacts/server.jar /opt/server.jar
COPY --from=hook-dupe-agent /hook-dupe-agent.jar /opt/hook-dupe-agent.jar
COPY build/artifacts/plugins/ /plugins/
# The repo's own plugins, built in the repo-plugins stage.
COPY --from=repo-plugins /out/ /plugins/
COPY --from=libertybans-addons /out/ /plugins/
COPY --from=bedrock-pack /out/ /plugins/Geyser-Spigot/packs/
COPY --from=worldguard /out/ /plugins/
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
# their name in the tab list and over their head.
# OasisFilter's are its word lists, OasisRules' the server rules,
# OasisRegions' the WorldGuard regions it sets up, and OasisSpawn's the spawn
# it pastes into a new world on the seed spawn was built in, with that seed's
# hash: the seed itself stays out of this public repo. OasisTechnoblade's are the
# column where /technoblade add places Technoblade and the settings for how it
# behaves. Its record of which pig is Technoblade is written beside those on
# the world volume, and the image never carries one, so a new world has no
# Technoblade until staff add it.
# Geyser-Spigot's is MCXboxBroadcast's, under the extension's id in Geyser's
# extensions/: the file the pinned build generates, with expiry off, so a
# console player who stops playing for a while stays on its friend list. Its
# Xbox sign-in, cache.json beside it, is never in the image and stays on the
# world volume.
COPY plugins/ /plugins/
COPY data/ /config/
COPY build/artifacts/datapacks/ /datapacks/
COPY --from=repo-datapacks /out/ /datapacks/
COPY server-icon.png /server-icon.png

# The Java resource pack, resourcepack/, which paints Technoblade's crown on
# its pig variant, is required: a player who declines it is disconnected. It is served by the
# website, named by its SHA-1, so the build takes that SHA-1, as
# scripts/java-pack.sh prints it, and fails without one. The site release that
# carries the pack has to be deployed before an image naming it (README,
# "Changing the resource pack"). Geyser accepts the pack for Bedrock players,
# who get resourcepack-bedrock/ instead. Last, so a new pack rebuilds only this.
ARG RESOURCE_PACK_SHA1
RUN echo "${RESOURCE_PACK_SHA1}" | grep --quiet --extended-regexp '^[0-9a-f]{40}$' \
    || { echo "build with --build-arg RESOURCE_PACK_SHA1=\"\$(scripts/java-pack.sh build/resourcepack)\"" >&2; exit 1; }
ENV RESOURCE_PACK="https://www.oasis-smp.com/assets/oasis-pack-${RESOURCE_PACK_SHA1}.zip" \
    RESOURCE_PACK_SHA1="${RESOURCE_PACK_SHA1}" \
    RESOURCE_PACK_ENFORCE=true \
    RESOURCE_PACK_PROMPT="{\"text\":\"This adds components necessary for playing on OASIS SMP\"}"

# The copy over /data/plugins adds and replaces files but never deletes them,
# so LuckPerms' group and track folders and OasisFilter's word lists on the
# world volume are emptied first: a group, track or word list file the repo no
# longer has is gone, not still loaded. As the server's own user, which owns
# them; root has no capability to.
ENTRYPOINT ["/bin/bash", "-c", "gosu minecraft:minecraft rm -rf /data/plugins/LuckPerms/yaml-storage/groups /data/plugins/LuckPerms/yaml-storage/tracks /data/plugins/OasisFilter/lists && exec /image/scripts/start \"$@\"", "start"]
