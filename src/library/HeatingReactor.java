package library;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Eachable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.units.BuildPlan;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.heat.HeatProducer;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.production.Drill;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawHeatOutput;
import mindustry.world.draw.DrawMulti;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

public class HeatingReactor extends NuclearReactor {
    public float heatOutput = 10f;
    public float warmupRate = 0.15f;

    public HeatingReactor(String name) {
        super(name);
    }
    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.output, heatOutput, StatUnit.heatUnits);
    }
    @Override
    public void setBars(){
        super.setBars();

        addBar("heat", (HeatDrillBuild entity) -> new Bar("bar.heat", Pal.lightOrange, () -> entity.heat / heatOutput));
    }

    public class HeatDrillBuild extends NuclearReactorBuild implements HeatBlock {
        public float heat;

        @Override
        public void draw(){
            drawer.draw(this);

            Draw.color(coolColor, hotColor, heat);
            Fill.rect(x, y, size * tilesize, size * tilesize);

            Draw.color(liquids.current().color);
            Draw.alpha(liquids.currentAmount() / liquidCapacity);
            Draw.rect(topRegion, x, y);

            if(heat > flashThreshold){
                flash += (1f + ((heat - flashThreshold) / (1f - flashThreshold)) * 5.4f) * Time.delta;
                Draw.color(Color.red, Color.yellow, Mathf.absin(flash, 9f, 1f));
                Draw.alpha(0.3f);
                Draw.rect(lightsRegion, x, y);
            }
            Draw.reset();
        }

        @Override
        public void updateTile(){
            super.updateTile();

            //heat approaches target at the same speed regardless of efficiency
            heat = Mathf.approachDelta(heat, heatOutput * efficiency, warmupRate * delta());
        }

        @Override
        public float heatFrac(){
            return heat / heatOutput;
        }

        @Override
        public float heat(){
            return heat;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
        }
    }
}
