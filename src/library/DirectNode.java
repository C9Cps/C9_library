package library;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.core.Renderer;
import mindustry.entities.TargetPriority;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Placement;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.blocks.power.PowerBlock;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.meta.BlockStatus;
import mindustry.world.meta.Env;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import java.util.Arrays;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class DirectNode extends PowerBlock {
    //maximum expected range of any beam node; used for previews
    private static final int maxRange = 30;

    public int range = 5;

    public TextureRegion laser;
    public TextureRegion laserEnd;

    public Color laserColor1 = Color.white;
    public Color laserColor2 = Color.valueOf("ffd9c2");
    public float pulseScl = 7, pulseMag = 0.05f;
    public float laserWidth = 0.4f;

    public DirectNode(String name){
        super(name);
        rotate = true;
        drawArrow = replaceable = quickRotate = false;
        consumesPower = outputsPower = false;
        drawDisabled = false;
        envEnabled |= Env.space;
        allowDiagonal = false;
        underBullets = true;
        priority = TargetPriority.transport;
    }
    @Override
    public void load(){
        super.load();
        laser = Core.atlas.find("power-beam");
        laserEnd = Core.atlas.find("power-beam-end");
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

    @Override
    public void setBars(){
        super.setBars();

        addBar("power", PowerNode.makePowerBalance());
        addBar("batteries", PowerNode.makeBatteryBalance());
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.powerRange, range, StatUnit.blocks);
    }

    @Override
    public void init(){
        super.init();

        updateClipRadius((range + 1) * tilesize);
    }

    @Override
    public void changePlacementPath(Seq<Point2> points, int rotation, boolean diagonal){
        if(!diagonal){
            Placement.calculateNodes(points, this, rotation, (point, other) -> Math.max(Math.abs(point.x - other.x), Math.abs(point.y - other.y)) <= range + size - 1);
        }
    }
    public class DirectNodeBuild extends Building {
        //current links in cardinal directions
        public Building[] links = new Building[4];
        public Tile[] dests = new Tile[4];
        public int lastChange = -2;

        /** @return whether a beam could theoretically connect with the specified block at a position */
        public boolean couldConnect(int direction, Block target, int targetX, int targetY){
            int offset = -(target.size - 1) / 2;
            int minX = targetX + offset, minY = targetY + offset, maxX = targetX + offset + target.size - 1, maxY = targetY + offset + target.size - 1;
            var dir = Geometry.d4[direction];

            int rangeOffset = size/2;

            //find first block with power in range
            for(int j = 1 + rangeOffset; j <= range + rangeOffset; j++){
                var other = world.tile(tile.x + j * dir.x, tile.y + j * dir.y);

                if(other == null) return false;

                //hit insulated wall
                if((other.build != null && other.build.isInsulated()) || (other.block().hasPower && other.block().connectedPower && other.team() == team)){
                    return false;
                }

                //within target rectangle
                if(other.x >= minX && other.y >= minY && other.x <= maxX && other.y <= maxY){
                    return true;
                }
            }

            return false;
        }

        @Override
        public void updateTile(){
            //TODO this block technically does not need to update every frame, perhaps put it in a special list.
            if(lastChange != world.tileChanges){
                lastChange = world.tileChanges;
                updateDirections();
            }
        }

        @Override
        public BlockStatus status(){
            float balance = power.graph.getPowerBalance();
            if(balance > 0f) return BlockStatus.active;
            if(balance < 0f && power.graph.getLastPowerStored() > 0) return BlockStatus.noOutput;
            return BlockStatus.noInput;
        }

        @Override
        public void draw(){
            super.draw();

            if(Mathf.zero(Renderer.laserOpacity) || team == Team.derelict) return;

            Draw.z(Layer.power);
            Draw.color(laserColor1, laserColor2, (1f - power.graph.getSatisfaction()) * 0.86f + Mathf.absin(3f, 0.1f));
            Draw.alpha(Renderer.laserOpacity);
            float w = laserWidth + Mathf.absin(pulseScl, pulseMag);

            for(int ij = 0; ij < 2; ij ++){
                int rot = this.rotation;
                rot %= 2;
                int i = ij*2 + rot;
                if(dests[i] != null && links[i].wasVisible && (!(links[i].block instanceof BeamNode node) ||
                        (links[i].tileX() != tileX() && links[i].tileY() != tileY()) ||
                        (links[i].id > id && range >= node.range) || range > node.range)){

                    int dst = Math.max(Math.abs(dests[i].x - tile.x),  Math.abs(dests[i].y - tile.y));
                    //don't draw lasers for adjacent blocks
                    if(dst > 1 + size/2){
                        var point = Geometry.d4[i];
                        float poff = tilesize/2f;
                        Drawf.laser(laser, laserEnd, x + poff*size*point.x, y + poff*size*point.y, dests[i].worldx() - poff*point.x, dests[i].worldy() - poff*point.y, w);
                    }
                }
            }

            Draw.reset();
        }

        @Override
        public void pickedUp(){
            Arrays.fill(links, null);
            Arrays.fill(dests, null);
        }
        public void updateDirections(){
            for(int ij = 0; ij < 2; ij ++){
                int rot = this.rotation;
                rot %= 2;
                int i = ij*2 + rot;
                var prev = links[i];
                var dir = Geometry.d4[i];
                links[i] = null;
                dests[i] = null;
                int offset = size/2;
                //find first block with power in range
                for(int j = 1 + offset; j <= range + offset; j++){
                    var other = world.build(tile.x + j * dir.x, tile.y + j * dir.y);

                    //hit insulated wall
                    if(other != null && other.isInsulated()){
                        break;
                    }

                    //power nodes do NOT play nice with beam nodes, do not touch them as that forcefully modifies their links
                    if(other != null && other.block.hasPower && other.block.connectedPower && other.team == team && !(other.block instanceof PowerNode)){
                        links[i] = other;
                        dests[i] = world.tile(tile.x + j * dir.x, tile.y + j * dir.y);
                        break;
                    }
                }

                var next = links[i];

                if(next != prev){
                    //unlinked, disconnect and reflow
                    if(prev != null && prev.isAdded()){
                        prev.power.links.removeValue(pos());
                        power.links.removeValue(prev.pos());

                        PowerGraph newgraph = new PowerGraph();
                        //reflow from this point, covering all tiles on this side
                        newgraph.reflow(this);

                        if(prev.power.graph != newgraph){
                            //reflow power for other end
                            PowerGraph og = new PowerGraph();
                            og.reflow(prev);
                        }
                    }

                    //linked to a new one, connect graphs
                    if(next != null){
                        power.links.addUnique(next.pos());
                        next.power.links.addUnique(pos());

                        power.graph.addGraph(next.power.graph);
                    }
                }
            }
        }
    }
}
