package mindustry.world.blocks.units;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import java.io.*;

import static mindustry.Vars.net;

public class UnitFactory extends Block{
    public UnitType unitType;
    public float produceTime = 1000f;
    public float launchVelocity = 0f;
    public TextureRegion topRegion;
    public int maxSpawn = 4;
    public int[] capacities;

    public UnitFactory(String name){
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = false;
        flags = EnumSet.of(BlockFlag.producer);
    }

    @Remote(called = Loc.server)
    public static void onUnitFactorySpawn(int spawns){
        if(!(tile.entity instanceof UnitFactoryEntity) || !(tile.block() instanceof UnitFactory)) return;

        UnitFactory factory = (UnitFactory)tile.block();

        buildTime = 0f;
        spawned = spawns;

        Effects.shake(2f, 3f, entity);
        Fx.producesmoke.at(x, y);

        if(!net.client()){
            Unitc unit = factory.unitType.create(team);
            unit.set(x + Mathf.range(4), y + Mathf.range(4));
            unit.add();
            unit.vel().y = factory.launchVelocity;
            Events.fire(new UnitCreateEvent(unit));
        }
    }

    @Override
    public void init(){
        super.init();

        capacities = new int[Vars.content.items().size];
        if(consumes.has(ConsumeType.item)){
            ConsumeItems cons = consumes.get(ConsumeType.item);
            for(ItemStack stack : cons.items){
                capacities[stack.item.id] = stack.amount * 2;
            }
        }
    }

    @Override
    public void load(){
        super.load();

        topRegion = Core.atlas.find(name + "-top");
    }

    @Override
    public void setBars(){
        super.setBars();
        bars.add("progress", entity -> new Bar("bar.progress", Pal.ammo, () -> ((UnitFactoryEntity)entity).buildTime / produceTime));
        bars.add("spawned", entity -> new Bar(() -> Core.bundle.format("bar.spawned", ((UnitFactoryEntity)entity).spawned, maxSpawn), () -> Pal.command, () -> (float)((UnitFactoryEntity)entity).spawned / maxSpawn));
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(BlockStat.itemCapacity);
        stats.add(BlockStat.productionTime, produceTime / 60f, StatUnit.seconds);
        stats.add(BlockStat.maxUnits, maxSpawn, StatUnit.none);
    }

    @Override
    public void unitRemoved(Unitc unit){
        spawned--;
        spawned = Math.max(spawned, 0);
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(name), Core.atlas.find(name + "-top")};
    }

    @Override
    public void draw(){
        TextureRegion region = unitType.icon(Cicon.full);

        Draw.rect(name, x, y);

        Shaders.build.region = region;
        Shaders.build.progress = buildTime / produceTime;
        Shaders.build.color.set(Pal.accent);
        Shaders.build.color.a = speedScl;
        Shaders.build.time = -time / 20f;

        Draw.shader(Shaders.build);
        Draw.rect(region, x, y);
        Draw.shader();

        Draw.color(Pal.accent);
        Draw.alpha(speedScl);

        Lines.lineAngleCenter(
        x + Mathf.sin(time, 20f, Vars.tilesize / 2f * size - 2f),
        y,
        90,
        size * Vars.tilesize - 4f);

        Draw.reset();

        Draw.rect(topRegion, x, y);
    }

    @Override
    public void updateTile(){
        if(spawned >= maxSpawn){
            return;
        }

        if(consValid() || tile.isEnemyCheat()){
            time += delta() * speedScl * Vars.state.rules.unitBuildSpeedMultiplier * efficiency();
            buildTime += delta() * efficiency() * Vars.state.rules.unitBuildSpeedMultiplier;
            speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
        }else{
            speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
        }

        if(buildTime >= produceTime){
            buildTime = 0f;

            Call.onUnitFactorySpawn(tile, spawned + 1);
            useContent(tile, unitType);

            consume();
        }
    }

    @Override
    public int getMaximumAccepted(Item item){
        return capacities[item.id];
    }

    @Override
    public boolean shouldConsume(){
        return spawned < maxSpawn;
    }

    public class UnitFactoryEntity extends TileEntity{
        float buildTime;
        float time;
        float speedScl;
        int spawned;

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(buildTime);
            write.i(spawned);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            buildTime = read.f();
            spawned = read.i();
        }
    }
}
