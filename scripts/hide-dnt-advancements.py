"""Hide the Dungeons and Taverns librarian advancements the enchant disabler misses.

Each story/librarian_* advancement rewards trading for a book of DnT
enchantments, which the enchant disabler strips. The disabler hides the ones it
knows by name, so a DnT release that renames them leaves them showing and
earnable. This writes every one the disabler doesn't override into the pack
directory, with the disabler's own edit: hidden, and an impossible criterion
required alongside the real one.

The pack overrides DnT's files, so its directory must sort after DnT's pack name.

Usage: hide-dnt-advancements.py <DnT.zip> <disabler.zip> <pack directory>
"""

import json
import pathlib
import sys
import zipfile

ADVANCEMENTS = "data/nova_structures/advancement/story/"
PREFIX = ADVANCEMENTS + "librarian_"
IMPOSSIBLE = "dnt_enchant_disabler_impossible"


def librarian_advancements(names):
    return {n for n in names if n.startswith(PREFIX) and n.endswith(".json")}


def hidden(advancement):
    advancement["display"]["hidden"] = True
    advancement["criteria"][IMPOSSIBLE] = {"trigger": "minecraft:impossible"}
    advancement["requirements"].append([IMPOSSIBLE])
    return advancement


def main(dnt_zip, disabler_zip, pack_dir):
    dnt_name = pathlib.Path(dnt_zip).stem
    pack = pathlib.Path(pack_dir)
    if pack.name <= dnt_name:
        sys.exit(f"error: {pack.name} sorts before {dnt_name}, so DnT's advancements would override it")

    with zipfile.ZipFile(disabler_zip) as disabler:
        covered = librarian_advancements(disabler.namelist())
    with zipfile.ZipFile(dnt_zip) as dnt:
        found = librarian_advancements(dnt.namelist())
        if not found:
            sys.exit(f"error: {dnt_name} has no {PREFIX}* advancements: find what it calls them now")
        missed = found - covered
        if not missed:
            sys.exit(f"error: the enchant disabler hides every {PREFIX}* advancement itself: remove {pack.name}")
        for name in sorted(missed):
            out = pack / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(hidden(json.loads(dnt.read(name))), indent=2) + "\n")
    print(f"{pack.name}: hid {len(missed)} DnT librarian advancements")


if __name__ == "__main__":
    main(*sys.argv[1:])
