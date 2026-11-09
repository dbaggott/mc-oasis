"""Copy the WorldGuard jar with its region messages' repeat delay at 1.5 s.

WorldGuard tells a player a region stopped them, and "Don't disembark here!",
at most once per LAST_MESSAGE_DELAY, 500 ms, in RegionProtectionListener. No
setting changes it, and the compiler copied the constant into the class as the
only long 500 in its constant pool, so that entry is rewritten. The class's
sha256 is pinned: a different WorldGuard fails here, to be checked for the
constant and pinned again, rather than patched blind.

Usage: patch-worldguard.py <WorldGuard.jar> <patched WorldGuard.jar>
"""

import hashlib
import struct
import sys
import zipfile

CLASS = "com/sk89q/worldguard/bukkit/listener/RegionProtectionListener.class"
# WorldGuard 7.0.20's.
CLASS_SHA256 = "eddfd7cedb87cb07493cfad24112f4725c0417073ad86e76dfe930cbde36e488"
DELAY_MS = 1500


def long_constant(value):
    """A CONSTANT_Long constant pool entry: tag 5, then the value big-endian."""
    return b"\x05" + struct.pack(">q", value)


def main(source, destination):
    with zipfile.ZipFile(source) as jar:
        original = jar.read(CLASS)
        digest = hashlib.sha256(original).hexdigest()
        if digest != CLASS_SHA256:
            sys.exit(f"error: {CLASS} has sha256 {digest}, not the {CLASS_SHA256} this patch was written for")
        matches = original.count(long_constant(500))
        if matches != 1:
            sys.exit(f"error: {CLASS} has {matches} long 500 constants, not the one delay")
        patched = original.replace(long_constant(500), long_constant(DELAY_MS))
        with zipfile.ZipFile(destination, "w") as out:
            for entry in jar.infolist():
                out.writestr(entry, patched if entry.filename == CLASS else jar.read(entry))
    print(f"WorldGuard's region message delay set to {DELAY_MS} ms")


if __name__ == "__main__":
    main(*sys.argv[1:])
