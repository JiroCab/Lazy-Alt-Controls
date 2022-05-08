package newcontrols.input;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
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
import mindustry.core.World;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.input.PlaceMode;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.meta.BlockFlag;
import newcontrols.ui.fragments.AIPanel;
import newcontrols.ui.fragments.ActionPanel;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.input.PlaceMode.*;

/** 
 * Emulates basic player actions && handles thumbstick controls, configurable.
 * 
 * TODO:
 * []  REFACTOR THIS SHIT, remove unused imports
 * []  split this shit into multiple classes
 * []  do something you fucking lazy fox
 * Alternative:
 * []  get rid of ai stuff at all and focus on mobile ui?
 * 
 * I really hate the way ive done this thing. I tried to refactor & split it, been doing that for several days, but...
 * I had to do a hard reset. All the changes were lost, and so was my motivation.
 * I couldn't and still can't find a non-dumb way of storing ai pattern-spicific values, such as mining range, mining items, etc.
 * 
 * FOR THE SAKE OWN YOUR SANITY, PLEASE, DO NOT READ THIS CODE
 * YOU WILL DIE OF CRINGE
 * SERIOUSLY, DONT
 * I WARNED YOU
 * ...
 *
 * TODO -JiroCab
 * fix hp threshold slider
 * add button for shouldRetreat
 * retreat ai
 * configurable maxRange offset when attacking
 * toggle attack buildings maybe
 * try to removing hide data base & map etc
**/
public class AIInput extends InputHandler {

	public enum AIAction {NONE, AUTO, ATTACK, MINE, REPAIR, RETREAT, BUILD, PATROL, IDLE }

	public Interval updateInterval = new Interval(4);
	public boolean paused = false, manualMode = false;
	public float lastZoom = -1; //no idea

	//Whether these actions are enabled. todo: actually make this comprehensible?
	public boolean attack = true, mine = true, build = true, repair = true, retreat = true,  patrol = true;

	/** Current action selected by the user */
	public AIAction current = AIAction.AUTO;
	/** If the current action is auto, this field is used to save the current auto action */
	public AIAction auto = AIAction.ATTACK;

	public Teamc target = null;
	public Tile mineTile = null;
	public Item mineItem = null;
	public boolean mining = false;
	public Tile patrolTile = null;
	public Tile retreatTile = null;

	//resetting
	/** Current movement direction, used for manual control. -1 to 1. Reset every frame. */
	public Vec2 moveDir = new Vec2();
	/** Current shoot direction, used for manual control. -1 to 1. Reset every frame. */
	public Vec2 shootDir = new Vec2();
	/** Whether the unit should shoot, used by manual control. Reset every frame */
	public boolean shoot = false;

	//settings
	public float attackRadius = 1200f;
	public float mineRadius = 2f;
	public float respawnThreshold = 5f * 100;
	public boolean retreatInstead = false;
	public boolean shouldRetreat = true;
	public boolean fullyHealed = false;
	/** Items that won't be mined */
	public final Seq<Item> mineExclude = new Seq();

	public Unit unitTapped;
	public Building buildingTapped;

	public static Vec2 movement = new Vec2();

	//Building related stuff
	public @Nullable Teams.BlockPlan lastPlan;
	/** Selected build request for movement. */
	public @Nullable BuildPlan sreq;
	/** Position where the player started dragging a line. */
	public int selectX = -1, selectY = -1, schemX = -1, schemY = -1;
	/** Maximum line length. */
	final static int maxLength = 100;
	/** Previously selected tile. */
	public Tile prevSelected;
	/** Whether selecting mode is active. */
	public PlaceMode mode;
	/** Whether the player is currently in line-place mode. */
	public boolean lineMode, schematicMode;
	/** Whether no recipe was available when switching to break mode. */
	public @Nullable Block lastBlock;
	/** Place requests to be removed. */
	public Seq<BuildPlan> removals = new Seq<>();
	/** Whether or not the player is currently shifting all placed tiles. */
	public boolean selecting;

	int tileX(float cursorX){
		Vec2 vec = Core.input.mouseWorld(cursorX, 0);
		if(selectedBlock()){vec.sub(block.offset, block.offset);}
		return World.toTile(vec.x);
	}
	int tileY(float cursorY){
		Vec2 vec = Core.input.mouseWorld(0, cursorY);
		if(selectedBlock()){vec.sub(block.offset, block.offset);}
		return World.toTile(vec.y);
	}
	/** Mouse pan speed. */
	public float panScale = 0.005f, panSpeed = 4.5f, panBoostSpeed = 15f;
	/** Whether player is currently deleting removal requests. */
	public boolean deleting = false, shouldShoot = false, panning = false;

	@Override
	public boolean tap(float x, float y, int count, KeyCode button){
		//if(state.isMenu()) return false;

		float worldx = Core.input.mouseWorld(x, y).x, worldy = Core.input.mouseWorld(x, y).y;
		Tile cursor = Vars.world.tile(Math.round(worldx / 8), Math.round(worldy / 8));

		if (cursor == null || scene.hasMouse(x, y)) return false;

		Call.tileTap(player, cursor);
		Tile linked = cursor.build == null ? cursor : cursor.build.tile;

		tileTappedH(linked.build);

		unitTapped = selectedUnit();
		buildingTapped = selectedControlBuild();

		return false;
	}

   // @Anuke#4986 why the fuck does this method has default visibility
	protected void tileTappedH(Building build) {
		if (build == null) {
			frag.inv.hide();
			frag.config.hideConfig();
			return;
		}
		boolean consumed = false, showedInventory = false;
		if (build.block.configurable && build.interactable(player.team())) {
			consumed = true;
			if ((!frag.config.isShown() && build.shouldShowConfigure(player)) //if the config fragment is hidden, show
			|| (frag.config.isShown() && frag.config.getSelectedTile().onConfigureTileTapped(build))) {
				Sounds.click.at(build);
				frag.config.showConfig(build);
			}
		} else if (!frag.config.hasConfigMouse()) { //make sure a configuration fragment isn't on the cursor
			if (frag.config.isShown() && frag.config.getSelectedTile().onConfigureTileTapped(build)) {
				consumed = true;
				frag.config.hideConfig();
			}
			if (frag.config.isShown()) {
				consumed = true;
			}
		}
		if (!consumed && build.interactable(player.team())) {
			build.tapped();
		}
		if (build.interactable(player.team()) && build.block.consumesTap) {
			consumed = true;
		} else if (build.interactable(player.team()) && build.block.synthetic() && (!consumed || build.block.allowConfigInventory)) {
			if (build.block.hasItems && build.items.total() > 0) {
				frag.inv.showFor(build);
				consumed = true;
				showedInventory = true;
			}
		}
		if (!showedInventory) {
			frag.inv.hide();
		}
	}

	@Override
	public void buildPlacementUI(Table table){
		table.image().color(Pal.gray).height(4f).colspan(4).growX();
		table.row();
		table.left().margin(0f).defaults().size(48f).left();
		if(!manualMode){ //desktop ui
			table.button(Icon.paste, Styles.clearPartiali, () -> ui.schematics.show()).tooltip("@schematics");
			table.button(Icon.book, Styles.clearPartiali, () -> ui.database.show()).tooltip("@database");
			table.button(Icon.tree, Styles.clearPartiali, () -> ui.research.show()).visible(() -> state.isCampaign()).tooltip("@research");
			table.button(Icon.map, Styles.clearPartiali, () -> ui.planet.show()).visible(() -> state.isCampaign()).tooltip("@planetmap");
		}
		else { //mobile ui
			table.button(Icon.hammer, Styles.clearTogglePartiali, () -> {
				mode = mode == breaking ? block == null ? none : placing : breaking;
				lastBlock = block;
			}).update(l -> l.setChecked(mode == breaking)).name("breakmode");

			//diagonal swap button
			table.button(Icon.diagonal, Styles.clearTogglePartiali, () -> {
				Core.settings.put("swapdiagonal", !Core.settings.getBool("swapdiagonal"));
			}).update(l -> l.setChecked(Core.settings.getBool("swapdiagonal")));

			//rotate button
			table.button(Icon.right, Styles.clearTogglePartiali, () -> {
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

			//confirm button
			table.button(Icon.ok, Styles.clearPartiali, () -> {
				for(BuildPlan request : selectRequests){
					Tile tile = request.tile();

					//actually place/break all selected blocks
					if(tile != null){
						if(!request.breaking){
							if(validPlace(request.x, request.y, request.block, request.rotation)){
								BuildPlan other = getRequest(request.x, request.y, request.block.size, null);
								BuildPlan copy = request.copy();

								if(other == null){
									player.unit().addBuild(copy);
								}else if(!other.breaking && other.x == request.x && other.y == request.y && other.block.size == request.block.size){
									player.unit().plans().remove(other);
									player.unit().addBuild(copy);
								}
							}

							rotation = request.rotation;
						}else{
							tryBreakBlock(tile.x, tile.y);
						}
					}
				}

				//move all current requests to removal array so they fade out
				removals.addAll(selectRequests.select(r -> !r.breaking));
				selectRequests.clear();
				selecting = false;
			}).visible(() -> !selectRequests.isEmpty()).name("confirmplace");

		}


		/* Removes the old one for the one from DesktopInput.java
		side effect of mobile having the desktop ui when In Ai or Joystick Mode but oh well

    	table.image().color(Pal.gray).height(4f).colspan(2).growX();
    	table.row();
    	table.left().margin(0f).defaults().size(48f). left();
   	 	table.button(b -> b.image(() -> paused ? Icon.pause.getRegion() : Icon.play.getRegion()), Styles.clearPartiali, () -> {
       		 paused = !paused;
    	}).tooltip("@newcontrols.ai.toggle");

   		 table.button(Icon.move, Styles.clearTogglePartiali, () -> {
		        manualMode = !manualMode;
    		}).update(l -> l.setChecked(manualMode)).tooltip("@ai.manual-mode");*/
     }

	 boolean showHint(){
		return ui.hudfrag.shown && Core.settings.getBool("hints") && selectRequests.isEmpty() &&
				(!isBuilding && !Core.settings.getBool("buildautopause") || player.unit().isBuilding() || !player.dead() && !player.unit().spawnedByCore());
	}
	 @Override

	 public void buildUI(Group origin) {
		 super.buildUI(origin);
		 if (manualMode){
			origin.fill(t -> {
				t.center().bottom();
				ActionPanel.buildLandscape(t, this);
			});}
		  //Add so we can have basic building (?)
		 else{
			  origin.fill(t -> {
				  t.color.a = 0f;
				  t.visible(() -> (t.color.a = Mathf.lerpDelta(t.color.a, Mathf.num(showHint()), 0.15f)) > 0.001f);
				  t.bottom();
				  t.table(Styles.black6, b -> {
					  StringBuilder str = new StringBuilder();
					  b.defaults().left();
					  b.label(() -> {
						  if(!showHint()) return str;
						  str.setLength(0);
						  if(!isBuilding && !Core.settings.getBool("buildautopause") && !player.unit().isBuilding()){
							  str.append(Core.bundle.format("enablebuilding", Core.keybinds.get(Binding.pause_building).key.toString()));
						  }else if(player.unit().isBuilding()){
							  str.append(Core.bundle.format(isBuilding ? "pausebuilding" : "resumebuilding", Core.keybinds.get(Binding.pause_building).key.toString()))
									  .append("\n").append(Core.bundle.format("cancelbuilding", Core.keybinds.get(Binding.clear_building).key.toString()))
									  .append("\n").append(Core.bundle.format("selectschematic", Core.keybinds.get(Binding.schematic_select).key.toString()));
						  }
						  if(!player.dead() && !player.unit().spawnedByCore()){
							  str.append(str.length() != 0 ? "\n" : "").append(Core.bundle.format("respawn", Core.keybinds.get(Binding.respawn).key.toString()));
						  }
						  return str;
					  }).style(Styles.outlineLabel);
				  }).margin(10f);
			  });
			  origin.fill(t -> {
				  t.visible(() -> ui.hudfrag.shown && lastSchematic != null && !selectRequests.isEmpty());
				  t.bottom();
				  t.table(Styles.black6, b -> {
					  b.defaults().left();
					  b.label(() -> Core.bundle.format("schematic.flip",
							  Core.keybinds.get(Binding.schematic_flip_x).key.toString(),
							  Core.keybinds.get(Binding.schematic_flip_y).key.toString())).style(Styles.outlineLabel).visible(() -> Core.settings.getBool("hints"));
					  b.row();
					  b.table(a -> {
						  a.button("@schematic.add", Icon.save, this::showSchematicSave).colspan(2).size(250f, 50f).disabled(f -> lastSchematic == null || lastSchematic.file != null);
					  });
				  }).margin(6f);
			  });
		  }
	}

	@Override
	public void drawTop(){
		Lines.stroke(1f);
		int cursorX = tileX(Core.input.mouseX());
		int cursorY = tileY(Core.input.mouseY());

		//draw break selection
		if(mode == breaking){
			drawBreakSelection(selectX, selectY, cursorX, cursorY, !Core.input.keyDown(Binding.schematic_select) ? maxLength : Vars.maxSchematicSize);
		}

		if(Core.input.keyDown(Binding.schematic_select) && !Core.scene.hasKeyboard() && mode != breaking){
			drawSelection(schemX, schemY, cursorX, cursorY, Vars.maxSchematicSize);
		}

		Draw.reset();
	}
	@Override
	public void drawBottom(){
		int cursorX = tileX(Core.input.mouseX());
		int cursorY = tileY(Core.input.mouseY());

		//draw request being moved
		if(sreq != null){
			boolean valid = validPlace(sreq.x, sreq.y, sreq.block, sreq.rotation, sreq);
			if(sreq.block.rotate){
				drawArrow(sreq.block, sreq.x, sreq.y, sreq.rotation, valid);
			}

			sreq.block.drawPlan(sreq, allRequests(), valid);

			drawSelected(sreq.x, sreq.y, sreq.block, getRequest(sreq.x, sreq.y, sreq.block.size, sreq) != null ? Pal.remove : Pal.accent);
		}

		//draw hover request
		if(!isPlacing()){
			BuildPlan req = getRequest(cursorX, cursorY);
			if(req != null){
				drawSelected(req.x, req.y, req.breaking ? req.tile().block() : req.block, Pal.accent);
			}
		}

		if(player.isBuilder()){
			//draw things that may be placed soon
			if(block != null){
				for(int i = 0; i < lineRequests.size; i++){
					BuildPlan req = lineRequests.get(i);
					if(i == lineRequests.size - 1 && req.block.rotate){
						drawArrow(block, req.x, req.y, req.rotation);
					}
					drawRequest(lineRequests.get(i));
				}
				lineRequests.each(this::drawOverRequest);
			}else if(isPlacing()){
				if(block.rotate && block.drawArrow){
					drawArrow(block, cursorX, cursorY, rotation);
				}
				Draw.color();
				boolean valid = validPlace(cursorX, cursorY, block, rotation);
				drawRequest(cursorX, cursorY, block, rotation);
				block.drawPlace(cursorX, cursorY, rotation, valid);

				if(block.saveConfig){
					Draw.mixcol(!valid ? Pal.breakInvalid : Color.white, (!valid ? 0.4f : 0.24f) + Mathf.absin(Time.globalTime, 6f, 0.28f));
					brequest.set(cursorX, cursorY, rotation, block);
					brequest.config = block.lastConfig;
					block.drawRequestConfig(brequest, allRequests());
					brequest.config = null;
					Draw.reset();
				}
			}
		}

		Draw.reset();
	}

	@Override
	public boolean isBreaking(){
		return mode == breaking;
	}

	//REGION CONTROLS
	@Override
	public void update() {
		super.update();

		if (!(player.dead() || state.isPaused())) {
			Core.camera.position.lerpDelta(player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
		}

		if (!ui.chatfrag.shown() && Math.abs(Core.input.axis(Binding.zoom)) > 0) {
			renderer.scaleCamera(Core.input.axis(Binding.zoom));
		}

		if (!paused) {
			if (manualMode) {
				manualMovement(player.unit());
				auto = AIAction.NONE;
			} else {
				aiActions(player.unit());
			}
		}
		AIPanel enabledAI = new AIPanel();

		//Heal & retreat handles
		fullyHealed = player.unit().health == player.unit().maxHealth;
		shouldRetreat = retreatInstead && player.unit().health <= respawnThreshold;

		//Shortcut keys
		if(state.isGame() && !scene.hasDialog() && !(scene.getKeyboardFocus() instanceof TextField)){
			if(Core.input.keyTap(Binding.minimap)) ui.minimapfrag.toggle();
			if(Core.input.keyTap(Binding.planet_map) && state.isCampaign()) ui.planet.toggle();
			if(Core.input.keyTap(Binding.research) && state.isCampaign()) ui.research.toggle();
			//if(Core.input.keyTap(KeyCode.h)) enabledAI.enabled = !enabledAI.enabled;
		}

		//Camera panning
		boolean locked = locked();
		boolean panCam = false;
		float camSpeed = (!Core.input.keyDown(Binding.boost) ? panSpeed : panBoostSpeed) * Time.delta;

		if(input.keyDown(Binding.pan) && !scene.hasField() && !scene.hasDialog()){
			panCam = true;
			panning = true;
		}

		if((Math.abs(Core.input.axis(Binding.move_x)) > 0 || Math.abs(Core.input.axis(Binding.move_y)) > 0 || input.keyDown(Binding.mouse_move)) && (!scene.hasField())){
			panning = false;
		}

		if(!locked){
			if(((player.dead() || state.isPaused()) && !ui.chatfrag.shown()) && !scene.hasField() && !scene.hasDialog()){
				if(input.keyDown(Binding.mouse_move)){
					panCam = true;
				}

				Core.camera.position.add(Tmp.v1.setZero().add(Core.input.axis(Binding.move_x), Core.input.axis(Binding.move_y)).nor().scl(camSpeed));
			}else if(!player.dead() && !panning){
				Core.camera.position.lerpDelta(player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
			}

			if(panCam){
				Core.camera.position.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * panScale, -1, 1) * camSpeed;
				Core.camera.position.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * panScale, -1, 1) * camSpeed;
			}

		}

		//respawn shortcut, just in case
		if(!player.dead() && !state.isPaused() && !scene.hasField() && !locked){
			//updateMovement(player.unit());

			if(Core.input.keyTap(Binding.respawn)){
				controlledType = null;
				recentRespawnTimer = 1f;
				Call.unitClear(player);
			}
		}
		//Possessing
		shouldShoot = !scene.hasMouse() && !locked;
		if(!scene.hasMouse() && !locked){
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
		if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
			Tile selected = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
			if(selected != null){
				Call.tileTap(player, selected);
			}
		}
	}

	protected void manualMovement(Unit unit) {
		if (!moveDir.isZero()) {
			unit.movePref(moveDir.scl(unit.speed()));
		}
		if (shootDir.isZero()) {
			if (!moveDir.isZero()) {
				aimLook(moveDir.add(unit));
			}
		} else {
			aimLook(shootDir.scl(1600f).add(unit));
		}
		unit.controlWeapons(false, player.shooting = shoot);

		//reset to prevent stucking and smth
		moveDir.set(0, 0);
		shootDir.set(0, 0);
		shoot = false;
		unit.mineTile = null;
	}

	protected void aiActions(Unit unit) {
		UnitType type = unit.type;
		if (type == null) return;

		player.shooting = false;
		unit.mineTile = null;

		boolean canAttack = false;
		for (var w : type.weapons) {
			if (w.bullet.damage > 0 && w.bullet.collides) {
				canAttack = true;
				break;
			}
		}
		boolean canHealBuilding = false;
		for (var h : type.weapons) {
			if (h.bullet.healPercent > 0 && h.bullet.collidesTeam) {
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

		if (current == AIAction.AUTO && updateInterval.get(20)) {
			if (retreat && shouldRetreat && !fullyHealed) {
				auto = AIAction.RETREAT;
			} else if (attack && canAttack && (target = Units.closestTarget(unit.team, unit.x, unit.y, attackRadius > 0 ? attackRadius : Float.MAX_VALUE, t -> true)) != null) {
			auto = AIAction.ATTACK;
			} else if (repair && canHealBuilding && (target = Units.findDamagedTile(unit.team, unit.x, unit.y)) != null) {
				auto = AIAction.REPAIR;
			} else if (build && unit.canBuild() && lastPlan != null ) {
				auto = AIAction.BUILD;
			} else if (mine && unit.canMine() && mineItem != null && indexer.findClosestOre(unit, mineItem) != null) {
				auto = AIAction.MINE;
			} else if (patrol && canAttack && ((state.rules.waves && spawner.countSpawns() > 0) || (indexer.getEnemy(unit.team(), BlockFlag.core) != null))) {
				auto = AIAction.PATROL;
			} else {
				auto = AIAction.IDLE;
			}
		}

		AIAction action = current != AIAction.AUTO ? current : auto;

		switch (action) {
			case RETREAT -> retreatAI(unit);
			case ATTACK -> attackAI(unit);
			case MINE -> mineAI(unit);
			case BUILD -> buildAi(unit);
			case REPAIR -> repairAi(unit);
			case PATROL -> patrolAI(unit);
		}

		unit.controlWeapons(false, player.shooting);

		//Building
		if(Core.input.keyTap(Binding.clear_building)){
			player.unit().clearBuilding();
		}
	}

	protected void retreatAI(Unit unit) {
		//retreat to heal point else to a core
		retreatTile = Geometry.findClosest(unit.x, unit.y, indexer.getAllied(unit.team(), BlockFlag.repair));
		float offset;
		if (retreatTile == null) {retreatTile = Geometry.findClosest(unit.x, unit.y, indexer.getAllied(unit.team(), BlockFlag.core));}
		offset = 60f - mineRadius; //why not

		if (retreatTile != null) {
			if (unit.type.flying) {
				float dst = unit.dst(retreatTile);
				movement.set(retreatTile).sub(unit).limit(unit.speed());
				if (dst < offset - 16f) {
					movement.scl(-1);
					unit.movePref(movement);
				} else if (dst > offset) {unit.movePref(movement);}

				aimLook(retreatTile);
			} else {
				pathfind(unit);
			}
		}
	}

	protected void attackAI(Unit unit) {
		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * 5, t -> true);
		}

		UnitType type = unit.type;

		if (target != null && type != null) {

			if (respawnThreshold >= 1 && player.unit().health <= respawnThreshold &&!retreatInstead)
				//Respawn when threshold is met to save time by not getting lock on the unit's death, still allow
				{unitClear(player);}
			else if (respawnThreshold >= 1 && player.unit().health <= respawnThreshold && !fullyHealed) {
				retreatAI(unit);} //Allow for retreating when exclusively attacking
		  	else {

				float bulletSpeed = unit.hasWeapons() ? type.weapons.first().bullet.speed : 0;

				float approachRadius = 0.95f;

				float approachRadiusClose = 0.50f;
				boolean useCloseApproach = false;
				for(var r : type.weapons) {
					if (r.minShootVelocity <= 0) {
						useCloseApproach = true;
						break;
					}
				}
				float dist = unit.range() * approachRadius;
				if (useCloseApproach) {
					dist = unit.range() * approachRadiusClose;
				}
				float angle = target.angleTo(unit);
				Tmp.v1.set(Angles.trnsx(angle, dist), Angles.trnsy(angle, dist));
				movement.set(target).add(Tmp.v1).sub(unit).limit(unit.speed());
				unit.movePref(movement);

				Vec2 intercept = Predict.intercept(unit, target, bulletSpeed);
				player.shooting = unit.within(intercept, unit.range() * 1.25f);
				aimLook(intercept);}
			}

	}

	//Yes this just BuilderAi with extra/fewer steps
	protected void buildAi(Unit unit){
		if(unit.buildPlan() != null){
			//approach request if building
			BuildPlan req = unit.buildPlan();

			boolean valid =
					!(lastPlan != null && lastPlan.removed) &&
							((req.tile() != null && req.tile().build instanceof ConstructBlock.ConstructBuild cons && cons.current == req.block) ||
									(req.breaking ?
											Build.validBreak(unit.team(), req.x, req.y) :
											Build.validPlace(req.block, unit.team(), req.x, req.y, req.rotation)));

			if(valid){
				//move toward the request
				aimLook(req);
				movement.set(0, 0).trns(req.angleTo(unit), buildingRange - 5).add(req).sub(unit).limit(unit.speed());
				unit.movePref(movement);
			}else{
				//discard invalid request
				unit.plans.removeFirst();
				lastPlan = null;
			}
		}
		//float rebuildTime = (unit.team.rules().ai ? Mathf.lerp(15f, 2f, unit.team.rules().aiTier) : 2f) * 60f;

		//find new request
		if(!unit.team.data().blocks.isEmpty() //&& timer.get(timerTarget3, rebuildTime)
		){
			Queue<Teams.BlockPlan> blocks = unit.team.data().blocks;
			Teams.BlockPlan block = blocks.first();

			//check if it's already been placed
			if(world.tile(block.x, block.y) != null && world.tile(block.x, block.y).block().id == block.block){
				blocks.removeFirst();
			}else if(Build.validPlace(content.block(block.block), unit.team(), block.x, block.y, block.rotation)){ //it's valid
				lastPlan = block;
				//add build request
				unit.addBuild(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config));
				//shift build plan to tail so next unit builds something else
				blocks.addLast(blocks.removeFirst());
			}else{
				//shift head of queue to tail, try something else next time
				blocks.addLast(blocks.removeFirst());
			}
		}
	}

	protected void repairAi(Unit unit) {
		Building target = Units.findDamagedTile(unit.team, unit.x, unit.y);
		if(target instanceof ConstructBlock.ConstructBuild) target = null;

		if(target != null){
		movement.set(target).add(Tmp.v1).sub(unit).limit(unit.speed());
		player.shooting = unit.within(target, unit.range() );
		aimLook(target);
		unit.movePref(movement);}
	}

	//Yes, yes and yes. I literally copied the MinerAI.
	protected void mineAI(Unit unit) {
		Building core = unit.closestCore();
		if(core == null) return;
		if(unit.canMine()) { //to avoid forcing units who can't mine to mine at all
			if (mining) {
				//Core doesn't need this item
				if (mineItem != null && core.acceptStack(mineItem, 1, unit) == 0) {
					if (unit.stack.amount > 0) dropItem(player, unit.rotation);
					mineItem = null;
					return;
				}

				//Mine
				if (unit.stack.amount >= unit.type.itemCapacity || (mineItem != null && !unit.acceptsItem(mineItem))) {
					mining = false;
				} else {
					if (updateInterval.get(3, 30) && mineItem != null) {
						mineTile = indexer.findClosestOre(unit, mineItem);
					}

					if (mineTile != null) {
						movement.set(0, 0).trns(mineTile.angleTo(unit), mineRadius).add(mineTile).sub(unit).limit(unit.speed());
						unit.movePref(movement);
						aimLook(Tmp.v1.set(mineTile).scl(8));

						if (mineTile.block() == Blocks.air && unit.within(mineTile, unit.type.miningRange)) {
							unit.mineTile = mineTile;
						}

						if (mineTile.block() != Blocks.air) {
							mining = false;
						}
					}
				}
			} else {
				//Unload to core
				if (unit.stack.amount == 0) {
					mining = true;
					return;
				}

				if (core.acceptStack(unit.stack.item, unit.stack.amount, unit) > 0) {
					tryDropItems(core, player.x, player.y);
				}

				movement.set(core).sub(unit).limit(unit.speed());
				unit.movePref(movement);
				aimLook(core);
			}
		}
	}
	
	protected void patrolAI(Unit unit) {
		patrolTile = Geometry.findClosest(unit.x, unit.y, indexer.getEnemy(unit.team(), BlockFlag.core));
		
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
					movement.scl(-1);
					unit.movePref(movement);
				} else if (dst > offset) {
					unit.movePref(movement);
				}
				
				aimLook(patrolTile);
			} else {
				pathfind(unit);
			}
		}
	}

	//REGION CONTROLS
	protected void pathfind(Unit unit) {
		int costType = unit.pathType();
		Tile tile = unit.tileOn();
		if (tile == null) return;
		Tile targetTile = pathfinder.getTargetTile(tile, pathfinder.getField(unit.team, costType, Pathfinder.fieldCore));
		
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
	
	/** Should be called when the ai is being disabled */
	public void finish() {
		player.shooting = false;
		player.unit().mineTile = null;
	}
	
	/** Represents the current ai action, formatted according to bundle */
	@Override
	public String toString() {
		final String first = "newcontrols.ai.action-";
		
		return manualMode ? bundle.get(first + AIAction.NONE) :
		       current == AIAction.AUTO ? bundle.format(first + current, bundle.get(first + auto)) : 
		       bundle.get(first + current);
	}

	@Override
	public boolean selectedBlock(){
		return isPlacing() && mode != breaking;
	}
}