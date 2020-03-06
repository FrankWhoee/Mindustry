package mindustry.world.blocks.units;

import arc.Core;
import arc.struct.EnumSet;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.*;

import static mindustry.Vars.tilesize;

public class RepairPoint extends Block{
    private static Rect rect = new Rect();

    public int timerTarget = timers++;

    public float repairRadius = 50f;
    public float repairSpeed = 0.3f;
    public float powerUse;
    public TextureRegion baseRegion;
    public TextureRegion laser, laserEnd;

    public RepairPoint(String name){
        super(name);
        update = true;
        solid = true;
        flags = EnumSet.of(BlockFlag.repair);
        layer = Layer.turret;
        layer2 = Layer.power;
        hasPower = true;
        outlineIcon = true;
    }

    @Override
    public void load(){
        super.load();

        baseRegion = Core.atlas.find(name + "-base");
        laser = Core.atlas.find("laser");
        laserEnd = Core.atlas.find("laser-end");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.range, repairRadius / tilesize, StatUnit.blocks);
    }

    @Override
    public void init(){
        consumes.powerCond(powerUse, entity -> ((RepairPointEntity)entity).target != null);
        super.init();
    }

    @Override
    public void drawSelect(){
        Drawf.dashCircle(x, y, repairRadius, Pal.accent);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize + offset(), y * tilesize + offset(), repairRadius, Pal.accent);
    }

    @Override
    public void draw(){
        Draw.rect(baseRegion, x, y);
    }

    @Override
    public void drawLayer(){
        Draw.rect(region, x, y, rotation - 90);
    }

    @Override
    public void drawLayer2(){
        if(target != null &&
        Angles.angleDist(angleTo(target), rotation) < 30f){
            float ang = angleTo(target);
            float len = 5f;

            Draw.color(Color.valueOf("e8ffd7"));
            Drawf.laser(laser, laserEnd,
                x + Angles.trnsx(ang, len), y + Angles.trnsy(ang, len),
                target.x(), target.y(), strength);
            Draw.color();
        }
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(name + "-base"), Core.atlas.find(name)};
    }

    @Override
    public void updateTile(){
        boolean targetIsBeingRepaired = false;
        if(target != null && (target.dead() || target.dst(tile) > repairRadius || target.health() >= target.maxHealth())){
            target = null;
        }else if(target != null && consValid()){
            target.heal(repairSpeed * Time.delta() * strength * efficiency());
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.5f);
            targetIsBeingRepaired = true;
        }

        if(target != null && targetIsBeingRepaired){
            strength = Mathf.lerpDelta(strength, 1f, 0.08f * Time.delta());
        }else{
            strength = Mathf.lerpDelta(strength, 0f, 0.07f * Time.delta());
        }

        if(timer(timerTarget, 20)){
            rect.setSize(repairRadius * 2).setCenter(x, y);
            target = Units.closest(team, x, y, repairRadius, Unitc::damaged);
        }
    }

    @Override
    public boolean shouldConsume(){
        return target != null;
    }

    public class RepairPointEntity extends TileEntity{
        public Unitc target;
        public float strength, rotation = 90;
    }
}
