package library;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Eachable;
import mindustry.entities.units.BuildPlan;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.production.Drill;
import mindustry.world.draw.*;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

public class HeatDrill extends Drill {
    ///??
    public DrawBlock drawer = new DrawDefault();
    /** Base heat requirement for 100% efficiency. */
    public float heatRequirement = 10f;
    /** After heat meets this requirement, excess heat will be scaled by this number. */
    public float overheatScale = 1f;
    /** Maximum possible efficiency after overheat. */
    public float maxEfficiency = 4f;

    public HeatDrill(String name) {
        super(name);
    }
    @Override
    public void setStats(){
        super.setStats();

        if(heatRequirement > 0){
        stats.add(Stat.input, heatRequirement, StatUnit.heatUnits);
        stats.add(Stat.maxEfficiency, (int)(maxEfficiency * 100f), StatUnit.percent);
        }
    }
    @Override
    public void setBars(){
        super.setBars();
        if(heatRequirement > 0){
        addBar("heat", (HeatDrillBuild entity) ->
                new Bar(() ->
                        Core.bundle.format("bar.heatpercent", (int)(entity.heat + 0.01f), (int)(entity.efficiencyScale() * 100 + 0.01f)),
                        () -> Pal.lightOrange,
                        () -> entity.heat / heatRequirement));
    }}

    @Override
    public void load(){
        super.load();

        drawer.load(this);
    }
    @Override
    public void getRegionsToOutline(Seq<TextureRegion> out){
        drawer.getRegionsToOutline(this, out);
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        drawer.drawPlan(this, plan, list);
    }

    @Override
    public TextureRegion[] icons(){
        return drawer.finalIcons(this);
    }

    public class HeatDrillBuild extends DrillBuild implements HeatConsumer {

        //TODO sideHeat could be smooth
        public float[] sideHeat = new float[4];
        public float heat = 0f;

        @Override
        public void updateTile(){
            if (heatRequirement > 0) heat = calculateHeat(sideHeat);

            super.updateTile();
        }
        @Override
        public void draw(){
            drawer.draw(this);
        }
        @Override
        public void drawLight(){
            super.drawLight();
            drawer.drawLight(this);
        }


        @Override
        public float heatRequirement(){
            return heatRequirement;
        }

        @Override
        public float[] sideHeat(){
            return sideHeat;
        }

        @Override
        public float warmup(){
            return Mathf.clamp(heat / heatRequirement);
        }

        @Override
        public float efficiencyScale(){
            float over = Math.max(heat - heatRequirement, 0f);
            return Math.min(Mathf.clamp(heat / heatRequirement) + over / heatRequirement * overheatScale, maxEfficiency);
        }
    }
}
