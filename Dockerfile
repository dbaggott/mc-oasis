# The Oasis server: Paper with the plugins in artifacts.lock, run by
# itzg/minecraft-server. Build after scripts/fetch-artifacts.sh, which puts the
# verified jars in build/artifacts/.
#
# Everything the server runs is baked in. The base image's own downloads are
# switched off below, so a container start fetches no code. The one exception
# is Paperclip, inside the Paper jar, which fetches the vanilla server and its
# libraries from Mojang on first start and checks them against hashes the Paper
# build carries.
#
# No player lists here: this repo is public, so WHITELIST and OPS are supplied
# by the host when the container starts.

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

# For `rcon-cli` inside the container only; the port is never published. With
# no RCON_PASSWORD the image generates a fresh one at every start.
ENV ENABLE_RCON=true

# Stop the server after 20 minutes with nobody on, or 30 after a start nobody
# joins. Touching /data/.skip-stop holds it up, e.g. while Chunky pre-generates.
ENV PLAYER_IDLE_TIMEOUT=120 \
    ENABLE_AUTOSTOP=TRUE \
    AUTOSTOP_TIMEOUT_EST=1200 \
    AUTOSTOP_TIMEOUT_INIT=1800

# The image is authoritative over /data/plugins. Its files overwrite live ones
# even where a plugin rewrote its own config since, and a jar no longer in
# artifacts.lock is removed. Only top-level jars go, so a removed plugin's
# data folder survives.
ENV SYNC_SKIP_NEWER_IN_DESTINATION=false \
    REMOVE_OLD_MODS=TRUE \
    REMOVE_OLD_MODS_INCLUDE=*.jar \
    REMOVE_OLD_MODS_DEPTH=1

# The datapacks in artifacts.lock, copied into the world's datapacks folder at
# every start. Packs already there are removed first, so the lock is
# authoritative.
ENV DATAPACKS=/datapacks \
    REMOVE_OLD_DATAPACKS=true

# ClickVillagers is pickup-and-place only, and every other feature is a
# permission it grants everyone by default. These deny them at every start, so
# the repo stays authoritative while LuckPerms keeps the rest of its data, which
# names players, on the world volume. The baked config turns off its villager
# hoppers and update check.
ENV RCON_CMDS_STARTUP="\
lp group default permission set clickvillagers.claim false\n\
lp group default permission set clickvillagers.anchor false\n\
lp group default permission set clickvillagers.partner false\n\
lp group default permission set clickvillagers.change-biome false\n\
lp group default permission set clickvillagers.hopper false"

COPY build/artifacts/server.jar /opt/server.jar
COPY build/artifacts/plugins/ /plugins/
# Plugin configs the repo owns, one folder per plugin by its declared name.
# Simple Voice Chat's turns recording off: some players are minors.
COPY plugins/ /plugins/
COPY build/artifacts/datapacks/ /datapacks/
COPY server-icon.png /server-icon.png
