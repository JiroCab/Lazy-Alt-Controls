package newcontrols.ui.fragments;


import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.Group;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.ui.Styles;
import newcontrols.NCVars;
import newcontrols.input.AIInput;
import newcontrols.input.AiEnabler;
import newcontrols.ui.NCStyles;
import newcontrols.ui.NiceSlider;
import newcontrols.ui.Spinner;
import newcontrols.ui.Toggle;
import newcontrols.util.LocalBlockIndexer;

import static arc.Core.bundle;
import static mindustry.Vars.player;

//"why so many whitespaces?"
//Because this shit is unreadable without them!
public class AIPanel {
	public static float dsize = 65f;
	public boolean shown = false;

	public void build(Group parent) {

		//Main menu (wrench)
		parent.fill(table -> {

			table.center().left();
			table.name = "ai-panel";

			table.button(Icon.wrench, Styles.cleari, () -> shown = !shown).size(dsize).left().row();

			table.collapser(panel -> {
				panel.setBackground(Styles.black3);

				panel.add("@newcontrols.ai.header").colspan(2).row();
				panel.table(h -> {

					h.add("@newcontrols.ai.status").padRight(1f);

					Prov<CharSequence> lText = () -> NCVars.enabler.enabled ? "@newcontrols.ai.enabled-ai" : "@newcontrols.ai.disabled";
					h.label(lText).height(60f).with(l -> {
						l.clicked(() -> NCVars.enabler.ToggleAi());
						l.setStyle(Styles.techLabel);
					});
					h.row();



				}).padLeft(8f).row();
				panel.collapser(control -> {

					control.table(h -> {
						h.add("@newcontrols.ai.action").padRight(5f);
						h.label(() -> AiEnabler.ai.toString()).marginBottom(5f).row();
					}).row();

					/*Ai Settings*/
					control.collapser(actions -> {

						actions.add("@newcontrols.ai.settings").row();
						//action selection
						actions.add((Element) new Spinner("@newcontrols.ai.actions-select", s -> {

							s.defaults().growX().height(40f);

							s.button("@newcontrols.ai.action-AUTO-TYPE", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.AUTO).row();
							s.button("@newcontrols.ai.action-ATTACK", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.ATTACK).row();
							s.button("@newcontrols.ai.action-MINE", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.MINE).row();
							s.button("@newcontrols.ai.action-BUILD", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.BUILD).row();
							s.button("@newcontrols.ai.action-REBUILD", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.REBUILD).row();
							s.button("@newcontrols.ai.action-ASSIST", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.ASSIST).row();
							s.button("@newcontrols.ai.action-REPAIR", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.REPAIR).row();
							s.button("@newcontrols.ai.action-RETREAT", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.RETREAT).row();
							s.button("@newcontrols.ai.action-PATROL", NCStyles.fullt, () -> AiEnabler.ai.current = AIInput.AIAction.PATROL).row();
						})).growX().row();

						//auto actions
						actions.add((Element) new Spinner("@newcontrols.ai.actions-enable", s -> {
							s.defaults().growX().height(40f);
							s.add(new Toggle(bundle.get("newcontrols.ai.action-ATTACK") , it -> AiEnabler.ai.attack, enabled -> AiEnabler.ai.attack = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-MINE") , it -> AiEnabler.ai.mine, enabled -> AiEnabler.ai.mine = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-BUILD") , it -> AiEnabler.ai.build, enabled -> AiEnabler.ai.build = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-REPAIR") , it -> AiEnabler.ai.repair, enabled -> AiEnabler.ai.repair = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-RETREAT"), it -> AiEnabler.ai.retreat, enabled -> AiEnabler.ai.retreat = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-ASSIST"), it -> AiEnabler.ai.assist, enabled -> AiEnabler.ai.assist = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-REBUILD"), it -> AiEnabler.ai.rebuild, enabled -> AiEnabler.ai.rebuild = enabled, NCStyles.toggled, NCStyles.hovered)).row();
							s.add(new Toggle(bundle.get("newcontrols.ai.action-PATROL"), it -> AiEnabler.ai.patrol, enabled -> AiEnabler.ai.patrol = enabled, NCStyles.toggled, NCStyles.hovered)).row();

						}, Color.lime)).growX().row();

						//preferences
						actions.add((Element) new Spinner("@newcontrols.ai.actions-preferences", s -> {
							s.defaults().growX().height(35f);

							//Mining Config
							s.add(new Spinner("@newcontrols.ai.prefs.mine-items",true, items -> {
								//Items selection
								items.center().top();
								Seq<Item> addedItems = new Seq<Item>(); // some items are duplicated

								items.table(picks -> {
									Seq<Item> mineables = new Seq<Item>();

									mineables.add(Item.getAllOres());
									mineables.add(LocalBlockIndexer.getFloorWithItems());
									mineables.add(LocalBlockIndexer.getWallWithItems());

									mineables.each(i -> {
										if (i == null) return;
										if (addedItems.contains(i)) return;
										addedItems.add(i);

										final Item item = i; //else it cannot be used in lambdas
										boolean shouldMine = UnitTypes.gamma.mineItems.contains(i) || i == Items.beryllium || i == Items.graphite;

										if (!shouldMine) AiEnabler.ai.mineExclude.add(i);
										picks.add(new Toggle(i.emoji(), shouldMine, enabled -> {
											if (enabled) {
												AiEnabler.ai.mineExclude.remove(item);
											} else {
												AiEnabler.ai.mineExclude.add(item);
											}
										},NCStyles.toggled, NCStyles.hovered)).size(50).get().toggle(it -> !AiEnabler.ai.mineExclude.contains(item));
										if (picks.getChildren().size % 6 == 0) picks.row();
									});
								});
								items.row();
								//Mining minimum amount, for cases to avoid the core running amount of x items first
								items.add(new NiceSlider(bundle.get("newcontrols.ai.prefs.mine-keep-minimum"),0 , 10 , 1, max -> AiEnabler.ai.mineMinimumPercent = max)
									.max(()-> 10f).process(
											max -> max == 0 ? bundle.get("newcontrols.unit.off"): Math.round(max) + "%" + "[lightgray](" +  Math.round(AiEnabler.ai.mineMinimumPercent * 0.1 * player.team().core().storageCapacity) + ")"
									).background(Styles.black5)).tooltip(bundle.get("newcontrols.ai.prefs.tooltip.useCorePos ")).row();

								//mining range
								items.add(new NiceSlider("@newcontrols.ai.prefs.mine-radius", 0, 10, 4, radius -> AiEnabler.ai.mineRadius = radius)
									.max(() -> Vars.player.unit().type == null ? 100 : Vars.player.unit().type.mineRange)
									.process(v -> Math.round(v / 8) + " " + bundle.get("unit.blocks"))).growX().row();
							})).growX().row();

							//Attack range
							s.add(new NiceSlider("@newcontrols.ai.prefs.attack-radius", 0, 1200, 16, radius -> AiEnabler.ai.attackRadius = radius)
							.max(() -> Vars.player.unit().type == null ? 1200 : Vars.player.unit().range() * 5)
							.process(v -> v <= 0 ? bundle.get("newcontrols.unit.nolimit") : Math.round(v / 8) + " " + bundle.get("unit.blocks"))).growX().row();

							//Hp threshold respawn
							s.add(new NiceSlider("@newcontrols.ai.prefs.hp-respawn", 0, 10, 1, percent -> AiEnabler.ai.respawnThreshold = percent)
							.max(() -> 10f)
							.process(v -> v <= 0 ? bundle.get("newcontrols.unit.off") : Math.round(v) + "%" )).growX().row();

							//Retreat Instead of Respawn Toggle
							s.check("@newcontrols.ai.prefs.retreat_instead", AiEnabler.ai.retreatInstead, r -> AiEnabler.ai.retreatInstead = r).checked(AiEnabler.ai.shouldRetreat).growX();
							s.row();


							//Use core as basis instead of the unit for basing of unit actions
							s.check("@newcontrols.ai.prefs.useCorePos", AiEnabler.ai.useCorePos, r -> AiEnabler.ai.useCorePos = r).checked(AiEnabler.ai.useCorePos).growX().tooltip(bundle.get("newcontrols.ai.prefs.tooltip.useCorePos "));
							s.row().background(Styles.black5);

							//Attack Ai ignores buildings
							s.check(bundle.get("newcontrols.ai.prefs.attack_ignore_buildings"), AiEnabler.ai.ignoreBuildings, r -> AiEnabler.ai.ignoreBuildings = r).checked(a -> AiEnabler.ai.ignoreBuildings).growX();
							s.row();


						})).growX().row();
						//actions.button(Icon.logic, Styles.cleari, () -> ai.manualMode = !ai.manualMode).growX().row();
					}, true, () -> !AiEnabler.ai.manualMode).growX().row();
					/*JoyStick settings*/
					control.collapser(stick-> {

						/* Auto aim like in mobile version*/
						stick.check(bundle.get("newcontrols.ai.joystick-autoAim"),  AiEnabler.ai.autoAim, r -> AiEnabler.ai.autoAim = r).checked(a -> AiEnabler.ai.autoAim).growX().row();
						/* Switch movement and Aim joy stick*/
						stick.check(bundle.get("newcontrols.ai.joystick-switch"),  r -> AiEnabler.joyStick.switchJoySticks = r).checked(a -> AiEnabler.joyStick.switchJoySticks).growX().row();

					}, true, () -> AiEnabler.ai.manualMode).growX().row();

				}, true, () -> NCVars.enabler.enabled).growX().row();
				panel.row();
				//Mobile JoyStick toggle
				panel.check("@newcontrols.ai.joystick", AiEnabler.ai.manualMode, r ->{
					AiEnabler.ai.manualMode = r;
					NCVars.enabler.ToggleJoystick();
				}).checked(a -> AiEnabler.ai.manualMode).growX();
			}, true, () -> shown).padLeft(15f).row();

		});
	}
}
