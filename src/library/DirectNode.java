package library;

import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.core.Renderer;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.blocks.power.PowerNode;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class DirectNode extends BeamNode {
    private static final int maxRange = 30;

    public DirectNode(String name) {
        super(name);
        rotate = true;
    }
    /** Iterates through linked nodes of a block at a tile. All returned buildings are beam nodes. */
    public static void getNodeLinks(Tile tile, Block block, Team team, Cons<Building> others){
        var tree = team.data().buildingTree;

        if(tree == null) return;

        float cx = tile.worldx() + block.offset, cy = tile.worldy() + block.offset, s = block.size * tilesize/2f, r = maxRange * tilesize;

        for(int i = 0; i < 4; i++){
            switch(i){
                case 0 -> Tmp.r1.set(cx - s, cy - s, r, s * 2f);
                case 1 -> Tmp.r1.set(cx - s, cy - s, s * 2f, r);
                case 2 -> Tmp.r1.set(cx + s, cy - s, -r, s * 2f).normalize();
                case 3 -> Tmp.r1.set(cx - s, cy + s, s * 2f, -r).normalize();
            }

            tempBuilds.clear();
            tree.intersect(Tmp.r1, tempBuilds);
            int fi = i;
            Building closest = tempBuilds.min(b -> b instanceof BeamNodeBuild node && node.couldConnect((fi + 2) % 4, block, tile.x, tile.y), b -> b.dst2(cx, cy));
            tempBuilds.clear();
            if(closest != null){
                others.get(closest);
            }
        }
    }

    /** Note that x1 and y1 are expected to be coordinates of the node to draw the beam from. */
    public void drawLaser(float x1, float y1, float x2, float y2, int size1, int size2){
        float w = laserWidth;
        float dst = Math.max(Math.abs(x1 - x2),  Math.abs(y2 - y1)) / tilesize;
        float sizeOff = dst * tilesize - (size1 + size2) * tilesize/2f;

        //don't draw lasers for adjacent blocks
        if(dst > 1 + size/2){
            var point = Geometry.d4(Tile.relativeTo(x1, y1, x2, y2));
            float poff = tilesize/2f;
            Drawf.laser(laser, laserEnd, x1 + poff*size*point.x, y1 + poff*size*point.y, x1 + (poff*size + sizeOff) * point.x, y1 + (poff*size + sizeOff) * point.y, w);
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        for(int i = 0; i < 2; i++){
            int maxLen = range + size/2;
            Building dest = null;
            int rot = rotation;
            rot %= 2;
            var dir = Geometry.d4[i*2 + rot];
            int dx = dir.x, dy = dir.y;
            int offset = size/2;
            for(int j = 1 + offset; j <= range + offset; j++){
                var other = world.build(x + j * dir.x, y + j * dir.y);

                //hit insulated wall
                if(other != null && other.isInsulated()){
                    break;
                }

                if(other != null && other.block.hasPower && other.team == Vars.player.team() && !(other.block instanceof PowerNode)){
                    maxLen = j;
                    dest = other;
                    break;
                }
            }
            Drawf.dashLine(Pal.placing,
                    x * tilesize + dx * (tilesize * size / 2f + 2),
                    y * tilesize + dy * (tilesize * size / 2f + 2),
                    x * tilesize + dx * (maxLen) * tilesize,
                    y * tilesize + dy * (maxLen) * tilesize
            );

            if(dest != null){
                Drawf.square(dest.x, dest.y, dest.block.size * tilesize/2f + 2.5f, 0f);
            }
        }
    }

    public class DirectNodeBuild extends BeamNodeBuild {
        //current links in cardinal directions
        public Building[] links = new Building[4];
        public Tile[] dests = new Tile[4];
        public int lastChange = -2;

        @Override
        public void updateTile(){
            //TODO this block technically does not need to update every frame, perhaps put it in a special list.
            if(lastChange != world.tileChanges){
                lastChange = world.tileChanges;
                updateDirections();
            }
        }


    }
}
