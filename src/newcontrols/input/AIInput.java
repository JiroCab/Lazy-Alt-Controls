package newcontrols.input;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Nullable;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Blocks;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.meta.BlockFlag;
import newcontrols.ui.fragments.ActionPanel;

import static arc.Core.bundle;
import static mindustry.Vars.*;

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

	public enum AIAction {NONE, AUTO, ATTACK, MINE, REPAIR, RETREAT, BUILD, PATROL, IDLE, }

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
	public float respawnThreshold = 5;
	public boolean retreatInstead = false;


	/** Items that won't be mined */
	public Seq<Item> mineExclude = new Seq();
	
	public Unit unitTapped;
	public Building buildingTapped;
	
	public static Vec2 movement = new Vec2();

	public @Nullable Teams.BlockPlan lastPlan;

	@Override
	public boolean tap(float x, float y, int count, KeyCode button){
		//if(state.isMenu()) return false;
		
		float worldx = Core.input.mouseWorld(x, y).x, worldy = Core.input.mouseWorld(x, y).y;
		Tile cursor = Vars.world.tile(Math.round(worldx / 8), Math.round(worldy / 8));
		
		if (cursor == null || Core.scene.hasMouse(x, y)) return false;
		
		Call.tileTap(player, cursor);
		//Tile linked = cursor.build == null ? cursor : cursor.build.tile;

		//tileTappedH(linked.build);
		
		//unitTapped = selectedUnit();
		//buildingTapped = selectedControlBuild();
		
		return false;
	}

	/* No longer used, will be moved in the setting menu
   // @Anuke#4986 why the fuck does this method has default visibility
	protected boolean tileTappedH(Building build) {
		if (build == null) {
			frag.inv.hide();
			frag.config.hideConfig();
			return false;
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
		return consumed;
	}

	@Override
	public void buildPlacementUI(Table table){
    	table.image().color(Pal.gray).height(4f).colspan(2).growX();
    	table.row();
    	table.left().margin(0f).defaults().size(48f). left();
   	 	table.button(b -> b.image(() -> paused ? Icon.pause.getRegion() : Icon.play.getRegion()), Styles.clearPartiali, () -> {
       		 paused = !paused;
    	}).tooltip("@newcontrols.ai.toggle");

   		 table.button(Icon.move, Styles.clearTogglePartiali, () -> {
		        manualMode = !manualMode;
    		}).update(l -> l.setChecked(manualMode)).tooltip("@ai.manual-mode");
     }*/
	@Override
	public void buildUI(Group origin) {
		super.buildUI(origin);
		  if (manualMode)
			origin.fill(t -> {
				t.center().bottom();
				ActionPanel.buildLandscape(t, this);
			});
		  else {
			  origin.clear();
		  }
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
		boolean shouldRetreat = unit.health <= respawnThreshold && retreatInstead;

		Building core = unit.closestCore();
		if (core != null && (updateInterval.get(2, 60) || mineItem == null)) {
			mineItem = Item.getAllOres().min(i -> 
				!mineExclude.contains(i) && indexer.hasOre(i) && unit.canMine(i) && core.acceptStack(i, 1, unit) > 0, i -> core.items.get(i)
			);
		}
		
		if (current == AIAction.AUTO && updateInterval.get(20)) {
			if (retreat && shouldRetreat) {
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
			case RETREAT: patrolAI(unit); break;
			case ATTACK: attackAI(unit); break;
			case MINE: mineAI(unit); break;
			case BUILD: buildAi(unit); break;
			case REPAIR: repairAi(unit); break;
			case PATROL: patrolAI(unit); break;
		}
		
		unit.controlWeapons(false, player.shooting);
	}
	
	protected void attackAI(Unit unit) {
		if (updateInterval.get(1, 10) || Units.invalidateTarget(target, unit.team, unit.x, unit.y)) {
			target = Units.closestTarget(unit.team, unit.x, unit.y, unit.range() * 5, t -> true);
		}
		
		UnitType type = unit.type;
		
		if (target != null && type != null) {
		  //Respawn when threshold is met to save time by not getting lock on the unit's death
		  if(respawnThreshold >= 1 && unit.health <= respawnThreshold)
		    {
				unitClear(player);
			}
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
			aimLook(intercept);
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
				movement.set(0, 0).trns(req.angleTo(unit), mineRadius).add(req).sub(unit).limit(unit.speed());
				unit.movePref(movement);
			}else{
				//discard invalid request
				unit.plans.removeFirst();
				lastPlan = null;
			}
		}
		float rebuildTime = (unit.team.rules().ai ? Mathf.lerp(15f, 2f, unit.team.rules().aiTier) : 2f) * 60f;

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
				pathfind(unit, Pathfinder.fieldCore);
			}
		}
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
	
}