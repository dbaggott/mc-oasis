# Xaero's mods take the fair-play code from chat, so it goes to every player on
# every join. The tag catches a player's first join; a nonzero leave_game count
# catches each one after.
execute as @a[tag=!xaero_fair_play] run function xaero_fair_play:send
execute as @a[scores={xaero_fair_play=1..}] run function xaero_fair_play:send
