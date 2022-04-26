# New-Controls
A Fork of ***Mnemotechnician/New-Controls-Public*** to make an Ai do simple tasks for when your lazy and/or afk!

This mod ***is multiplayer-compatible***, meaning you can use all its features (even the unfair ones) in the multiplayer.
# Enabling
To enable the controls: press the 🔧 (wrench) button while in-game. You will see a panel with a status bar and a big red "DISABLED" label. Clicking on the label toggles the controls.

### Important notice
While the controls are enabled, you won't be able to perform some actions that are possible in vanilla mode (such as building). You can disable & enable the controls at any time so that shouldn't be a problem.

#Mobile mode
Mobile mode is half working, Aim And move joystick only no action pannel for now

# Ai mode
Can be enabled by disabling a toggle in the placement ui (right bottom corner). Reveals an ai control panel under the status label. Consists of these modes:
* auto: performs one of following based on situation, in top to bottom priority 
* ~~retreat: retreat to the nearest heal point when hp is bellow a threshold percent (off by default, see notes)~~ **(not implemented)**
* attack: targets the nearest units & buildings in a specified radius, keeps maximum possible distance
* repair: heal damaged blocks, only if unit's weapons can
* mine: automatically mines specified items
* build: builds everything in plan and rebuilds destroyed blocks
* patrol: goes to the nearest spawn point / enemy core

#Notes
* When hp is bellow 'Respawn threshold' either your instantly respawn to your core (to save time) or you'll retreat to a heal point (not implemented)
* Mine and Build radius use the same slider
