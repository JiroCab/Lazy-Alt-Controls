package newcontrols.input;


import arc.Core;
import arc.Graphics;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.input.PlaceMode;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.meta.BlockFlag;
import newcontrols.NCVars;
import newcontrols.ui.fragments.ActionPanel;
import newcontrols.util.LocalBlockIndexer;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.input.PlaceMode.*;

/**
 * Emulates basic player actions && handles thumbstick controls, configurable.
**/
public class AIInput extends InputHandler {

	public enum AIAction {NONE, AUTO, ATTACK, MINE, REPAIR, RETREAT, BUILD, ASSIST, REBUILD, PATROL, TURRET, IDLE }

	public Interval updateInterval = new Interval(4);
	public boolean paused = false, manualMode = false, ignoreBuildings;
	public float lastZoom = -1, scalar = -1;//no idea

	//Whether these actions are enabled. todo: actually make this comprehensible?
	public boolean attack = true, mine = true, build = true, repair = true, retreat = true,  patrol = true, assist = true, rebuild = true ;

	/** Current action selected by the user */
	public AIAction current = AIAction.AUTO, previous = AIAction.AUTO;
	/** If the current action is auto, this field is used to save the current auto action */
	public AIAction auto = AIAction.ATTACK;

	public Teamc target = null, lastTarget = null;
	public Tile mineTile = null, patrolTile = null, tappedMineTile = null;
	public Item mineItem = null;
	public boolean mining = false, useCorePos = false;
	public float mineMinimumPercent = 1f;

	//resetting
	/** Current movement direction, used for manual control. -1 to 1. Reset every frame. */
	public Vec2 moveDir = new Vec2();
	/** Current shoot direction, used for manual control. -1 to 1. Reset every frame. */
	public Vec2 shootDir = new Vec2();
	/** Whether the unit should shoot, used by manual control. Reset every frame */
	public boolean shoot = false, freeCam = false, autoAim = false;

	//settings
	public float attackRadius = 1200f, mineRadius = 2f ,respawnThreshold = 500f, crosshairScale;
	public boolean retreatInstead = false, shouldRetreat = true, fullyHealed = false;
	/** Items that won't be mined */
	public final Seq<Item> mineExclude = new Seq();

	public Unit unitTapped;
	public Building buildingTapped;

	public static Vec2 movement = new Vec2();

	public @Nullable Teams.BlockPlan lastPlan;
	public boolean shouldShoot = false, panning = false, strictMovement = true;
	public static LocalBlockIndexer localIndexer;

	/** Whether selecting mode is active. */
	public PlaceMode mode;
	public boolean schematicMode;

	/** Whether no recipe was available when switching to break mode. */
	public @Nullable Block lastBlock;
	public Graphics.Cursor cursorType = Graphics.Cursor.SystemCursor.arrow;
	//Builder Ai
	public @Nullable Unit assistFollowing, following;

	/** Mouse pan speed. */
	public float panScale = 0.005f, panSpeed = 4.5f, panBoostSpeed = 15f;
	public InputHandler inputHandler;

	@Override
	public boolean tap(float x, float y, int count, KeyCode button){
		if(mobile) {
			float worldx = input.mouseWorld(x, y).x, worldy = input.mouseWorld(x, y).y;
			Tile cursor = world.tile(Math.round(worldx / 8), Math.round(worldy / 8));

			if (cursor == null || scene.hasMouse(x, y)) return false;

			Call.tileTap(player, cursor);
			Tile linked = cursor.build == null ? cursor : cursor.build.tile;

			//control units
			if (count == 2) {
				Unit unit = player.unit();

				//control a unit/block detected on first tap of double-tap
				if (unitTapped != null) {
					Call.unitControl(player, unitTapped);
					recentRespawnTimer = 1f;
				} else if (buildingTapped != null) {
					Call.buildingControlSelect(player, buildingTapped);
					recentRespawnTimer = 1f;
				} else if (cursor.block() == Blocks.air && unit.within(cursor, unit.type.mineRange)) {
					unit.mineTile = mineTile;
				}
				return false;
			}

			if (player.dead()) {
				cursorType = Graphics.Cursor.SystemCursor.arrow;
				if (!scene.hasMouse()) {
					graphics.cursor(cursorType);
				}
			}

			if (!scene.hasMouse()) {
				graphics.cursor(cursorType);
			}

			cursorType = Graphics.Cursor.SystemCursor.arrow;
			tileTappedH(linked.build);

			unitTapped = selectedUnit();
			buildingTapped = selectedControlBuild();

			return false;
		} else {
			if(scene.hasMouse() || !commandMode) return false;

			tappedOne = true;

			//click: select a single unit
			if(button == KeyCode.mouseLeft){
				if(count >= 2){
					selectTypedUnits();
				}else{
					tapCommandUnit();
				}
			}

			return super.tap(x, y, count, button);
		}
	}

	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button){
		if(scene.hasMouse() || !commandMode) return false;

		if(button == KeyCode.mouseRight){
			commandTap(x, y);
		}

		return super.touchDown(x, y, pointer, button);
	}

	/** @Anuke#4986 why the fuck does this method has default visibility */
	protected boolean tileTappedH(Building build) {
		// !!! notice
		// fully copy-pasted from the superclass
		if(build == null){
			inv.hide();
			config.hideConfig();
			//commandBuild = null;
			return false;
		}
		boolean consumed = false, showedInventory = false;

		//select building for commanding
		if(build.block.commandable && commandMode){
			//TODO handled in tap.
			consumed = true;
		}else if(build.block.configurable && build.interactable(player.team())){ //check if tapped block is configurable
			consumed = true;
			if((!config.isShown() && build.shouldShowConfigure(player)) //if the config fragment is hidden, show
				//alternatively, the current selected block can 'agree' to switch config tiles
				|| (config.isShown() && config.getSelected().onConfigureBuildTapped(build))){
				Sounds.click.at(build);
				config.showConfig(build);
			}
			//otherwise...
		}else if(!config.hasConfigMouse()){ //make sure a configuration fragment isn't on the cursor
			//then, if it's shown and the current block 'agrees' to hide, hide it.
			if(config.isShown() && config.getSelected().onConfigureBuildTapped(build)){
				consumed = true;
				config.hideConfig();
			}

			if(config.isShown()){
				consumed = true;
			}
		}

		//call tapped event
		if(!consumed && build.interactable(player.team())){
			build.tapped();
		}

		//consume tap event if necessary
		if(build.interactable(player.team()) && build.block.consumesTap){
			consumed = true;
		}else if(build.interactable(player.team()) && build.block.synthetic() && (!consumed || build.block.allowConfigInventory)){
			if(build.block.hasItems && build.items.total() > 0){
				inv.showFor(build);
				consumed = true;
				showedInventory = true;
			}
		}

		if(!showedInventory){
			inv.hide();
		}

		return consumed;
	}

	@Override
	public void buildPlacementUI(Table table){
		table.image().color(Pal.gray).height(4f).colspan(4).growX();
		table.row();
		table.left().margin(0f).defaults().size(48f).left();
		if(!manualMode){ //desktop ui
			table.button(Icon.paste, Styles.cleari, () -> ui.schematics.show()).tooltip("@schematics");
			table.button(Icon.book, Styles.cleari, () -> ui.database.show()).tooltip("@database");
			table.button(Icon.tree, Styles.cleari, () -> ui.research.show()).visible(() -> state.isCampaign()).tooltip("@research");
			table.button(Icon.map, Styles.cleari, () -> ui.planet.show()).visible(() -> state.isCampaign()).tooltip("@planetmap");
		}
		else { //mobile ui
			table.button(Icon.hammer, Styles.clearTogglei, () -> {
				mode = mode == breaking ? block == null ? none : placing : breaking;
				lastBlock = block;
			}).update(l -> l.setChecked(mode == breaking)).name("breakmode");

			//diagonal swap button
			table.button(Icon.diagonal, Styles.clearTogglei, () -> Core.settings.put("swapdiagonal", !Core.settings.getBool("swapdiagonal"))).update(l -> l.setChecked(Core.settings.getBool("swapdiagonal")));

			//rotate button
			table.button(Icon.right, Styles.clearTogglei, () -> {
				if(block != null && block.rotate){
					rotation = Mathf.mod(rotation + 1, 4);
				}else{
					schematicMode = !schematicMode;
					if(schematicMode){
						block = null;
						mode = none;
					}
				}
			}).update(i -> {
				boolean arrow = block != null && block.rotate;

				i.getImage().setRotationOrigin(!arrow ? 0 : rotation * 90, Align.center);
				i.getStyle().imageUp = arrow ? Icon.right : Icon.copy;
				i.setChecked(!arrow && schematicMode);
			});
		}
	}

	boolean showHint(){
		return ui.hudfrag.shown && Core.settings.getBool("hints") && selectPlans.isEmpty() &&
				(!isBuilding && !Core.settings.getBool("buildautopause") || player.unit().isBuilding() || !player.dead() && !player.unit().spawnedByCore());
	}

	@Override
	public void buildUI(Group origin) {
		super.buildUI(origin);
		origin.fill(t -> {
			t.center().bottom();
			ActionPanel.buildLandscape(t, this);
		});

		/*building and respawn hints*/
		origin.fill(t -> {
			if(!manualMode) {
				t.color.a = 0f;
				t.visible(() -> (t.color.a = Mathf.lerpDelta(t.color.a, Mathf.num(showHint()), 0.15f)) > 0.001f);
				t.bottom();
				t.table(Styles.black6, b -> {
					StringBuilder str = new StringBuilder();
					b.defaults().left();
					b.label(() -> {
						if (!showHint()) return str;
						str.setLength(0);
						if (!isBuilding && !Core.settings.getBool("buildautopause") && !player.unit().isBuilding()) {
							str.append(Core.bundle.format("enablebuilding", Core.keybinds.get(Binding.pause_building).key.toString()));
						} else if (player.unit().isBuilding()) {
							str.append(Core.bundle.format(isBuilding ? "pausebuilding" : "resumebuilding", Core.keybinds.get(Binding.pause_building).key.toString()))
									.append("\n").append(Core.bundle.format("cancelbuilding", Core.keybinds.get(Binding.clear_building).key.toString()))
									.append("\n").append(Core.bundle.format("selectschematic", Core.keybinds.get(Binding.schematic_select).key.toString()));
						}
						if (!player.dead() && !player.unit().spawnedByCore()) {
							str.append(str.length() != 0 ? "\n" : "").append(Core.bundle.format("respawn", Core.keybinds.get(Binding.respawn).key.toString()));
						}
						return str;
					}).style(Styles.outlineLabel);
				}).margin(10f);
			}
		});
	}


	@Override
	public void drawOverSelect(){
		//draw list of plans
		for(BuildPlan plan : selectPlans){
			Tile tile = plan.tile();

			if(tile == null) continue;

			if((!plan.breaking && validPlace(tile.x, tile.y, plan.block, plan.rotation))
					|| (plan.breaking && validBreak(tile.x, tile.y))){
				plan.animScale = Mathf.lerpDelta(plan.animScale, 1f, 0.2f);
			}else{
				plan.animScale = Mathf.lerpDelta(plan.animScale, 0.6f, 0.1f);
			}

			Tmp.c1.set(Draw.getMixColor());

			if(!plan.breaking && plan.block != null){
				Draw.mixcol();
				if(plan.block.rotate) drawArrow(plan.block, tile.x, tile.y, plan.rotation);
			}

			Draw.reset();
			drawPlan(plan);
			if(!plan.breaking){
				drawOverPlan(plan);
			}

			//draw last placed plan
			if(!plan.breaking && plan.block != null && plan.block.drawArrow){
				boolean valid = validPlace(tile.x, tile.y, plan.block, rotation);
				Draw.mixcol();
				plan.block.drawPlace(tile.x, tile.y, rotation, valid);

				drawOverlapCheck(plan.block, tile.x, tile.y, valid);

			}
		}

		//draw targeting crosshair
		if(target != null && !state.isEditor() && ( !manualMode || autoAim)){
			if(target != lastTarget){
				crosshairScale = 0f;
				lastTarget = target;
			}

			crosshairScale = Mathf.lerpDelta(crosshairScale, 1f, 0.2f);

			Drawf.target(target.getX(), target.getY(), 7f * Interp.swingIn.apply(crosshairScale), 0.5f, Pal.remove);
		}

		Draw.reset();
	}

	//REGION CONTROLS
	@Override
	public void update() {
		super.update();

		boolean locked = locked();
		boolean panCam = false;
		float camSpeed = (!input.keyDown(Binding.boost) ? panSpeed : panBoostSpeed) * Time.delta;

		if (!paused || !inputHandler.locked() ) {
			if (manualMode) {
				manualMovement(player.unit());
				auto = AIAction.NONE;
			} else {
				aiActions(player.unit());
			}
		}

		//Heal & retreat handles
		fullyHealed = player.unit().health == player.unit().maxHealth;
		shouldRetreat = retreatInstead && player.unit().health <= respawnThreshold;
		shouldShoot = !scene.hasMouse() && !locked;

		if(Core.input.keyRelease(Binding.select) || player.shooting && !canShoot()){
			player.shooting = false;
		}

		//Possessing
		if(!scene.hasMouse() && state.rules.possessionAllowed){
			if(Core.input.keyDown(Binding.control) && Core.input.keyTap(Binding.select)){
				Unit on = selectedUnit();
				var build = selectedControlBuild();
				if(on != null){
					Call.unitControl(player, on);
					shouldShoot = false;
					recentRespawnTimer = 1f;
				}else if(build != null){
					Call.buildingControlSelect(player, build);
					recentRespawnTimer = 1f;
				}
			}
		}

		if (!freeCam && !scene.hasField() && !scene.hasDialog() && !(player.dead() || state.isPaused())) {
			Core.camera.position.lerpDelta(player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
		}

		if (!ui.chatfrag.shown() && Math.abs(input.axis(Binding.zoom)) > 0) {
			renderer.scaleCamera(input.axis(Binding.zoom));
		}

		if(!locked && block == null && !scene.hasField() &&
				//disable command mode when player unit can boost and command mode binding is the same
				!(!player.dead() && player.unit().type.canBoost && keybinds.get(Binding.command_mode).key == keybinds.get(Binding.boost).key)){
			if(settings.getBool("commandmodehold")){
				commandMode = input.keyDown(Binding.command_mode);
			}else if(input.keyTap(Binding.command_mode)){
				commandMode = !commandMode;
			}
		}else{
			commandMode = false;
		}


		//Shortcut keys
		if(state.isGame() && !scene.hasDialog() && !(scene.getKeyboardFocus() instanceof TextField)){
			if(input.keyTap(Binding.minimap)) ui.minimapfrag.toggle();
			if(input.keyTap(Binding.planet_map) && state.isCampaign()) ui.planet.toggle();
			if(input.keyTap(Binding.research) && state.isCampaign()) ui.research.toggle();
			if(Core.input.keyTap(Binding.research) && Core.input.keyDown(Binding.boost)) NCVars.enabler.ToggleAi();
			if(Core.input.keyTap(Binding.planet_map) && Core.input.keyDown(Binding.boost)) manualMode = !manualMode;
		}

		if(Core.input.keyTap(Binding.pause_building)){
			isBuilding = !isBuilding;
			buildWasAutoPaused = false;

			if(isBuilding){
				player.shooting = false;
			}
		}

		if (input.keyTap(Binding.pan)){
			freeCam = true;
		}else if(input.keyTap(Binding.mouse_move)){
			freeCam = false;
		}

		if(input.keyDown(Binding.mouse_move) && (!scene.hasField())){
			panning = false;
		}

		if(input.keyDown(Binding.pan) && !scene.hasField() && !scene.hasDialog()){
			panCam = true;
			panning = true;
		}

		if(net.active() && Core.input.keyTap(Binding.player_list) && (scene.getKeyboardFocus() == null || scene.getKeyboardFocus().isDescendantOf(ui.listfrag.content) || scene.getKeyboardFocus().isDescendantOf(ui.minimapfrag.elem))){
			ui.listfrag.toggle();
		}

		if(!locked){
			if(((player.dead() || state.isPaused()) && !ui.chatfrag.shown()) && !scene.hasField() && !scene.hasDialog()){
				if(input.keyDown(Binding.mouse_move)){
					panCam = true;
				}

				Core.camera.position.add(Tmp.v1.setZero().add(input.axis(Binding.move_x), input.axis(Binding.move_y)).nor().scl(camSpeed));
			}else if(!player.dead() && !panning){
				Core.camera.position.lerpDelta(player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
			}

			if(panCam){
				Core.camera.position.x += Mathf.clamp((input.mouseX() - Core.graphics.getWidth() / 2f) * panScale, -1, 1) * camSpeed;
				Core.camera.position.y += Mathf.clamp((input.mouseY() - Core.graphics.getHeight() / 2f) * panScale, -1, 1) * camSpeed;
			}

		}

		//respawn shortcut, just in case
		if(!player.dead() && !state.isPaused() && !scene.hasField() && !locked){
			//updateMovement(player.unit());

			if(input.keyTap(Binding.respawn)){
				controlledType = null;
				recentRespawnTimer = 1f;
				Call.unitClear(player);
			}
		}

		if(input.keyTap(Binding.select) && !scene.hasMouse()){
			Tile selected = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
			if(selected != null){
				Call.tileTap(player, selected);
			}
		}
		if (input.keyTap(Binding.boost) && input.keyTap(Binding.select)){
			tappedMineTile = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
		}

		//update payload input, global and manual
		if(player.unit() instanceof Payloadc){
			if(Core.input.keyTap(Binding.pickupCargo)){
				tryPickupPayload();
			}

			if(Core.input.keyTap(Binding.dropCargo)){
				tryDropPayload();
			}
		}

		//Strict movement, Make the AI be at max range for building, rebuilding ,assisting, patrolling & repairing
		if(!strictMovement){
			scalar = 0;
		}
	}

	protected void manualMovement(Unit unit) {
		if (!moveDir.isZero()) {
			unit.movePref(moveDir.scl(unit.speed()));
		}
		if (autoAim){
			passiveAttack(unit, unit.type.omniMovement, true);
		}else {
			if (shootDir.isZero()) {
				if (!moveDir.isZero()) {
					aimLook(moveDir.add(unit));
				}
			} else {
				aimLook(shootDir.scl(1600f).add(unit));
			}
			unit.controlWeapons(false, player.shooting = shoot);
			shoot = false;
		}



		//reset to prevent stucking
		moveDir.set(0, 0);
		shootDir.set(0, 0);
		unit.mineTile = null;
	}

	protected void aiActions(Unit unit) {
		UnitType type = unit.type;
		if (type == null) return;

		player.shooting = false;
		unit.mineTile = null;

		boolean canAttack = false;
		for (Weapon w : type.weapons) {
			if (w.bullet != null && w.bullet.collides && w.bullet.damage > 1f && !w.noAttack) {
				canAttack = true;
				break;
			}
		}
		boolean canHealBuilding = false;
		for (var h : type.weapons) {
			if (h.bullet.healPercent > 0 ) {
				canHealBuilding = true;
				break;
			}
		}

		Building core = unit.closestCore();
		if (core != null && (updateInterval.get(2, 60) || mineItem == null)) {
			mineItem = Item.getAllOres().min(i ->
				!mineExclude.contains(i) && indexer.hasOre(i) && unit.canMine(i) && core.acceptStack(i, 1, unit) > 0, i -> core.items.get(i)
			);
		}
		boolean shouldAssistBuilding = net.active() && assistFollowing != null && assistFollowing.activelyBuilding() && unit.canBuild();
		boolean shouldRebuildBuildings = !unit.team.data().plans.isEmpty() && unit.canBuild();

		//Mining is now split to High-Low, To avoid cases where Ai only mines, instead Ai mines to a minimum then switches to other task before mining to full
		boolean shouldMineLow = core != null && unit.canMine() && mineItem != null && (indexer.findClosestOre(unit, mineItem)  != null || localIndexer.findClosestWallOre(unit, mineItem) != null);
		boolean shouldMineHigh = shouldMineLow && player.team().items().get(mineItem) > Math.round( mineMinimumPercent * 0.1 * player.team().core().storageCapacity);

		Teamc checkTarget = null;
		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			float x = useCorePos ? unit.closestCore().getX() : unit.x;
			float y = useCorePos ? unit.closestCore().getY() : unit.y;

			checkTarget = Units.closestTarget(unit.team, x, y, attackRadius > 0 ? attackRadius : Float.MAX_VALUE, u -> true, b -> ignoreBuildings);
		}

		if (current == AIAction.AUTO && updateInterval.get(20)) {
			if (retreat && (shouldRetreat && !fullyHealed )) {
				auto = AIAction.RETREAT;
			} else if (attack && canAttack && checkTarget != null) {
				auto = AIAction.ATTACK;
			} else if (mine && shouldMineHigh) {
				auto = AIAction.MINE;
			} else if (repair && canHealBuilding && (target = Units.findDamagedTile(unit.team, unit.x, unit.y)) != null) {
				auto = AIAction.REPAIR;
			} else if (build && unit.canBuild() && unit.buildPlan() != null && isBuilding) {
				auto = AIAction.BUILD;
			} else if (build && shouldAssistBuilding){
				auto = AIAction.ASSIST;
			}else if (assist && shouldRebuildBuildings){
				auto = AIAction.REBUILD;
			} else if (mine && shouldMineLow) {
				auto = AIAction.MINE;
			} else if (patrol && canAttack && ((state.rules.waves && spawner.countSpawns() > 0) || (indexer.getEnemy(unit.team(), BlockFlag.core) != null))) {
				auto = AIAction.PATROL;
			} else if (!unit.moving() || unit.type == UnitTypes.block){
				auto = AIAction.TURRET;
			} else {
				auto = AIAction.IDLE;
			}
			if (AiEnabler.extraLogs && current != previous){
				Log.info("Auto Ai has switched to -" + current);
				previous = current;
			}
		}

		AIAction action = current != AIAction.AUTO ? current : auto;

		switch (action) {
			case RETREAT -> retreatAI(unit);
			case ATTACK -> attackAI(unit);
			case MINE -> mineAI(unit);
			case BUILD -> buildAi(unit);
			case REBUILD -> rebuildAi(unit);
			case ASSIST -> assistAi(unit);
			case REPAIR -> repairAi(unit);
			case PATROL -> patrolAI(unit);
			case TURRET -> turretAi(unit);
		}

		unit.controlWeapons(false, player.shooting);

		//Building
		if(input.keyTap(Binding.clear_building)){
			player.unit().clearBuilding();
		}
	}

	protected void retreatAI(Unit unit) {
		//retreat to heal point else to a core
		Building retreatTile = Geometry.findClosest(unit.x, unit.y, indexer.getFlagged(unit.team(), BlockFlag.repair));
		if(retreatTile == null){ retreatTile = Geometry.findClosest(unit.x, unit.y, indexer.getFlagged(unit.team, BlockFlag.core));}

		commonMovement(unit, retreatTile, 60f);

		//Attack Units While in transit to the tile
		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * 5, t -> true);
		}

		if(target != null && unit.dst(retreatTile) > 60f){passiveAttack(unit);
		} else if(retreatTile != null) aimLook(retreatTile);

	}

	protected void attackAI(Unit unit) {
		Building core = unit.closestCore();
		float x = useCorePos && core != null ? core.getX() : unit.x;
		float y = useCorePos && core != null ? core.getY() : unit.y;

		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			if(!ignoreBuildings){
				target = Units.closestTarget(unit.team, x, y, unit.range() * 5, t -> true);
			} else {
				target = Units.closestEnemy(unit.team, x, y, unit.range() * 5, t -> true);
			}
		}

		UnitType type = unit.type;
		if (target != null && type != null){

			if (respawnThreshold > 0 && player.unit().health <= (respawnThreshold * 5) % player.unit().maxHealth()) {
				if ( auto != AIAction.AUTO && (retreatInstead || !fullyHealed)) {
					//Allow for retreating when exclusively attacking
					retreatAI(unit);
				} else{
					//Respawn when threshold is met to save time by not getting lock on the unit's death, for max uptime
					controlledType = null;
					recentRespawnTimer = 1f;
					Call.unitClear(player);
				}
			} else {
				float bulletSpeed = unit.hasWeapons() ? type.weapons.first().bullet.speed : 0;

				float approachRadius = 0.50f;
				//Handles if units have close range weapons or bombing weapons
				for(var r : type.weapons) {
					if (r.minShootVelocity >= 0 || r.bullet.hittable) {
						approachRadius = 0.95f;
						break;
					}
				}

				float dist = unit.range() * approachRadius;
				float angle = target.angleTo(unit);
				Tmp.v1.set(Angles.trnsx(angle, dist), Angles.trnsy(angle, dist));
				movement.set(target).add(Tmp.v1).sub(unit).limit(unit.speed());
				unit.movePref(movement);

				Vec2 intercept = Predict.intercept(unit, target, bulletSpeed);
				player.shooting = unit.within(intercept, unit.range() * 1.25f);
				if(intercept != null) aimLook(intercept);
			}
		}
	}

	//Yes, This is just BuilderAi but split into three
	protected void buildAi(Unit unit){
		if(unit.team.data().plans.isEmpty() || !unit.canBuild()) return;
		passiveAttack(unit, unit.type.omniMovement);

		BuildPlan req = unit.buildPlan();
		if(unit.buildPlan() != null){

			//approach plan if building
			boolean valid =
					!(lastPlan != null && lastPlan.removed) &&
							((req.tile() != null && req.tile().build instanceof ConstructBlock.ConstructBuild cons && cons.current == req.block) ||
									(req.breaking ?
											Build.validBreak(unit.team(), req.x, req.y) :
											Build.validPlace(req.block, unit.team(), req.x, req.y, req.rotation)));

			if(valid){
				unit.updateBuilding = true;
				commonMovement(unit, req.tile(), unit.type.buildRange);
			}else{
				//Don't linger on one thing but don't clear it
				unit.plans.addLast(unit.plans.first());
				unit.plans.removeFirst();
				lastPlan = null;
			}
		}
	}

	protected void rebuildAi(Unit unit){

		if(unit.team.data().plans.isEmpty() || !unit.canBuild()) return;
		Queue<Teams.BlockPlan> blocks = unit.team.data().plans;
		Teams.BlockPlan block = blocks.first();

		if(unit.buildPlan() == null){
			//check if it's already been placed
			if(world.tile(block.x, block.y) != null && world.tile(block.x, block.y).block().id == block.block){
				blocks.removeFirst();
			}else if(Build.validPlace(content.block(block.block), unit.team(), block.x, block.y, block.rotation)){ //it's valid
				lastPlan = block;
				//add build plan
				unit.addBuild(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config));
				//shift build plan to tail so next unit builds something else
				blocks.addLast(blocks.removeFirst());
			}else if (!state.rules.revealedBlocks.contains(content.block(block.block))) {
				/*If the block can't be rebuilt, remove from plan*/
				blocks.removeFirst();
			}else{
				//shift head of queue to tail, try something else next time
				blocks.addLast(blocks.removeFirst());
			}

		} else if (current != AIAction.AUTO && build){
			//When in auto, let auto handle switching but when exclusively this, call buildAi
			buildAi(unit);
		}
	}

	protected void assistAi(Unit unit){

		if(!net.active() || !unit.canBuild()) return;

		if(unit.buildPlan() == null){

			//Builder Ai helper
			if(assistFollowing != null && assistFollowing.activelyBuilding()){
				following = assistFollowing;
			}

			if(following != null && (!following.isValid() || !following.activelyBuilding())){
				following = null;
			}
			if (following != null && following.activelyBuilding()){
				unit.plans.addFirst(following.buildPlan());
			}

		} else if (current != AIAction.AUTO && build){
			//When in auto, let auto handle switching but when exclusively this, call buildAi
			buildAi(unit);
		}

	}

	protected void repairAi(Unit unit) {
		Building target = Units.findDamagedTile(unit.team, unit.x, unit.y);
		if(target instanceof ConstructBlock.ConstructBuild) target = null;

		if(target != null){
			if (unit.within(target, unit.range())){
				player.shooting = true;
				aimLook(target);
			} else {passiveAttack(unit);}

			float dist = unit.range() * 0.9f;
			for(var r : unit.type.weapons){
				//Bombing Weapons (quad)
				if(r.minShootVelocity >= 1){
					dist = 0;
					break;
				}
				//Less strict for inaccurate weapons for better heal (Poly) and close range (Pulsar, Oxynoe)
				if(r.inaccuracy >= 5 || !r.bullet.hittable){
					dist = unit.range() * 0.6f;
					break;
				}

			}

			commonMovement(unit, target, dist);
		}
	}

	//Yes, yes and yes. I literally copied the MinerAI.
	protected void mineAI(Unit unit) {
		Building core = unit.closestCore();

		//to avoid forcing units who can't mine to mine at all
		if(!unit.canMine() || core == null) return;

		if (mining) {
			//Core doesn't need this item
			if (mineItem != null && core.acceptStack(mineItem, 1, unit) == 0) {
				if (unit.stack.amount > 0) dropItem(player, unit.rotation);
				mineItem = null;
				return;
			}

			//Mine TODO:: add don't mine when erekir cores and mineItem.buildable = false
			if (unit.stack.amount >= unit.type.itemCapacity || (mineItem != null && !unit.acceptsItem(mineItem) || unit.item() != mineItem)) {
				mining = false;
			} else {

				Position mineLocation = useCorePos ? unit.closestCore() : unit;
				float mineLocationX = mineLocation.getX(), mineLocationY = mineLocation.getY();

				if (updateInterval.get(3, 30) && mineItem != null) {

					if(unit.type.mineFloor){
						mineTile = indexer.findClosestOre(mineLocationX, mineLocationY, mineItem);
					} else {
						mineTile = localIndexer.findClosestWallOre(mineLocationX, mineLocationY, mineItem);
					}

				}

				if (tappedMineTile != null){
					mineTile = tappedMineTile;
				}

				if(mineTile != null){
					aimLook(Tmp.v1.set(mineTile).scl(8));
					commonMovement(unit, mineTile, mineRadius);
					if(unit.type.omniMovement){passiveAttack(unit);}

					//Prevents units that can't mine walls/floors to mine ore walls/floors
					if ((mineTile.block() == Blocks.air && unit.type.mineFloor|| mineTile.block() != Blocks.air && unit.type.mineWalls) && unit.within(mineTile, unit.type.mineRange)) {
						unit.mineTile = mineTile;
					}else {
						mining = false;
					}
				}
			}
		} else {
			//Unload to core
			if (unit.stack.amount == 0 || unit.stack.item != mineItem && unit.stack.amount <= unit.type.itemCapacity) {
				mining = true;
				return;
			}

			if (core.acceptStack(unit.stack.item, unit.stack.amount, unit) > 0 ) {
				tryDropItems(core, player.x, player.y);
			}

			commonMovement(unit, core, 60f);
			aimLook(core);
			if(unit.type.omniMovement){passiveAttack(unit);}
		}
	}

	protected void patrolAI(Unit unit) {
		Building candidate = Geometry.findClosest(unit.x, unit.y, indexer.getEnemy(unit.team(), BlockFlag.core));

		patrolTile = candidate != null ? candidate.tile() : null;

		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * 2, t -> true);
		}

		if (target != null) {attackAI(unit); return;}

		float offset;
		if (patrolTile != null) {
			offset = unit.range() * 0.8f;
		} else {
			patrolTile = Geometry.findClosest(unit.x, unit.y, Vars.spawner.getSpawns());
			offset = state.rules.dropZoneRadius + 96f;
		}

		if (patrolTile != null) {
			if (unit.type.flying) {
				float dst = unit.dst(patrolTile);
				movement.set(patrolTile).sub(unit).limit(unit.speed());
				if (dst < offset - 16f) {
					movement.scl(scalar);
					unit.movePref(movement);
				} else if (dst > offset) {
					unit.movePref(movement);
				}

				aimLook(patrolTile);
			} else {
				pathfind(unit, Pathfinder.fieldCore);
			}
		}
	}

	protected void turretAi(Unit unit){
		unit.aim(Core.input.mouseWorld());
		unit.controlWeapons(true, player.shooting);

		player.boosting = Core.input.keyDown(Binding.boost);
		player.mouseX = unit.aimX();
		player.mouseY = unit.aimY();
		player.shooting = Core.input.keyDown(Binding.select);
	}
	//ENDREGION CONTROLS

	protected void pathfind(Unit unit, int pathType) {
		int costType = unit.pathType();
		Tile tile = unit.tileOn();
		if (tile == null) return;
		Tile targetTile = pathfinder.getTargetTile(tile, pathfinder.getField(unit.team, costType, pathType));

		if (tile == targetTile || (costType == Pathfinder.costNaval && !targetTile.floor().isLiquid)) return;

		unit.movePref(movement.set(targetTile).sub(unit).limit(unit.speed()));
		aimLook(targetTile);
	}

	@Override
	public boolean zoom(float initialDistance, float distance) {
		//todo: what the fuck does last zoom do
		if (Core.settings.getBool("keyboard")) return false;
		if (lastZoom < 0) {
			lastZoom = renderer.getScale();
		}

		renderer.setScale(distance / initialDistance * lastZoom);
		return true;
	}

	/** I have no idea why this method is required. But it just doesn't work on servers if i hardcode these methods. */
	public void tryDropItems(Building build, float x, float y) {
		ItemStack stack = player.unit().stack;
		if (build != null && build.acceptStack(stack.item, stack.amount, player.unit()) > 0 && build.interactable(player.team()) && build.block.hasItems && player.unit().stack().amount > 0 && build.interactable(player.team())) {
			Call.transferInventory(player, build);
		} else {
			Call.dropItem(player.angleTo(x, y));
		}
	}

	/** Multiplayer-compatible aiming */
	public void aimLook(Position pos) {
		player.unit().aimLook(pos);
		player.mouseX = pos.getX();
		player.mouseY = pos.getY();
	}

	/** Should be called when the AI is being disabled */
	public void finish() {
		player.shooting = false;
		player.unit().mineTile = null;
	}

	/** Represents the current AI action, formatted according to bundle */
	@Override
	public String toString() {
		final String first = "newcontrols.ai.action-";

		return manualMode ? bundle.get(first + AIAction.NONE) :
		       current == AIAction.AUTO ? bundle.format(first + current, bundle.get(first + auto)) :
						"[accent]" + bundle.get(first + current);
	}

	public void passiveAttack(Unit unit, Boolean aimLook, boolean handleShooting){
		boolean canHealBuilding = false;
		for (var h : unit.type.weapons) {
			if (h.bullet.healPercent > 0 ) {
				canHealBuilding = true;
				break;
		}	}

		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y) && !ignoreBuildings) {
			if(!ignoreBuildings){
				target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * 5, t -> true);
 			} else if(canHealBuilding){
				Building checkDmg = Units.findDamagedTile(unit.team, unit.x, unit.y);
				float offset = unit.type.range * 0.9f;
				float dst = unit.dst((checkDmg));

				if(dst > offset ){ target = checkDmg;}
 			} else {
				Unit check = Units.closestEnemy(unit.team, unit.x, unit.y, unit.range() * 5, t -> true);
				if (check != null){target = check;}
			}
		}
		if(target == null || !unit.hasWeapons()) return;

		float bulletSpeed = unit.hasWeapons() ? unit.type.weapons.first().bullet.speed : 0;
		Vec2 intercept = Predict.intercept(unit, target, bulletSpeed);

		//For cases, you only don't want to shoot and slow the unit if the unit's weapon is not in a turret or other reasons
		if (aimLook && intercept != null) {aimLook(intercept);}
		if (handleShooting) { shoot = unit.within(intercept, unit.range() * 1.25f);
			player.shooting = shoot;}
	}

	public void passiveAttack(Unit unit){
		passiveAttack(unit, true, false);
	}
	public void passiveAttack(Unit unit, boolean aimLook){
		passiveAttack(unit, aimLook, false);
	}

	/* Now less copying and pasting the same code*/
	public void commonMovement(Unit unit, Position point, float offset, float dist){
		if(point == null) return;
		movement.set(point).sub(unit).limit(unit.speed());
		if (dist < offset - 16f) {
			movement.scl(scalar);
			unit.movePref(movement);
		} else if (dist > offset) {
			unit.movePref(movement);
		}
	}

	public void commonMovement(Unit unit, Position pos, float offset){
		commonMovement(unit, pos, offset, unit.dst(pos));
	}

}
