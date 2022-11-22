package newcontrols.util;

import arc.Events;
import arc.math.Mathf;
import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.struct.IntSeq;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.content;
import static mindustry.Vars.world;

public class LocalBlockIndexer{
    public  int quadWidth, quadHeight;
    public IntSeq[][][] ores;
    public Seq<Building>[][] flagMap = new Seq[Team.all.length][BlockFlag.all.length];
    public ObjectIntMap<Item> allOres = new ObjectIntMap<>();

    public LocalBlockIndexer(){
        Events.on(EventType.WorldLoadEvent.class, event -> {
            ores = new IntSeq[content.items().size][][];
        });

        for(Tile tile : world.tiles){
            process(tile);

            var drop = tile.drop();

            if(drop != null){
                int qx = (tile.x / 20);
                int qy = (tile.y / 20);

                //add position of quadrant to list
                if(tile.block() == Blocks.air){
                    if(ores[drop.id] == null){
                        ores[drop.id] = new IntSeq[quadWidth][quadHeight];
                    }
                    if(ores[drop.id][qx][qy] == null){
                        ores[drop.id][qx][qy] = new IntSeq(false, 16);
                    }
                    ores[drop.id][qx][qy].add(tile.pos());
                    allOres.increment(drop);
                }
            }
        }
    }
    private void process(Tile tile){
        var team = tile.team();
        //only process entity changes with centered tiles
        if(tile.isCenter() && tile.build != null){
            var data = team.data();

            if(tile.block().flags.size > 0 && tile.isCenter()){
                var map = flagMap[team.id];

                for(BlockFlag flag : tile.block().flags.array){
                    map[flag.ordinal()].add(tile.build);
                }
            }

            //record in list of buildings
            data.buildings.add(tile.build);
            data.buildingTypes.get(tile.block(), () -> new Seq<>(false)).add(tile.build);

            //update the unit cap when new tile is registered
            data.unitCap += tile.block().unitCapModifier;

            //insert the new tile into the quadtree for targeting
            if(data.buildingTree == null){
                data.buildingTree = new QuadTree<>(new Rect(0, 0, world.unitWidth(), world.unitHeight()));
            }
            data.buildingTree.insert(tile.build);
        }
    }

    public Tile findClosestWallOre(float xp, float yp, Item item){
        quadWidth = Mathf.ceil(world.width() / (float)20);
        quadHeight = Mathf.ceil(world.height() / (float)20);

        if(ores[item.id] != null){
            float minDst = 0f;
            Tile closest = null;
            for(int qx = 0; qx < quadWidth; qx++){
                for(int qy = 0; qy < quadHeight; qy++){
                    var arr = ores[item.id][qx][qy];
                    if(arr != null && arr.size > 0){
                        Tile tile = world.tile(arr.first());
                        if(tile.block().itemDrop == item){
                            float dst = Mathf.dst2(xp, yp, tile.worldx(), tile.worldy());
                            if(closest == null || dst < minDst){
                                closest = tile;
                                minDst = dst;
                            }
                        }
                    }
                }
            }
            return closest;
        }
        //temp
        return world.tile(0,0);//null;
    }
    public Tile findClosestWallOre(Unit unit, Item item){
        return findClosestWallOre(unit.x, unit.y, item);
    }

    public static Seq<Item> getFloorWithItems(){
        //For anything things that aren't in ores like sand
        return content.blocks().select(b -> b instanceof Floor).map(b -> b.itemDrop);
    }

    public static Seq<Item> getWallWithItems(){
        //For anything things that aren't in ores like graphite
        return content.blocks().select(b -> b instanceof StaticWall).map(b -> b.itemDrop);
    }
}
