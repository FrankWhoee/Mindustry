package mindustry.entities.comp;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import static mindustry.Vars.*;

@Component(base = true)
abstract class UnitComp implements Healthc, Physicsc, Hitboxc, Statusc, Teamc, Itemsc, Rotc, Unitc, Weaponsc, Drawc, Boundedc, Syncc, Shieldc, Displayable, Senseable, Ranged{

    @Import boolean hovering, dead;
    @Import float x, y, rotation, elevation, maxHealth, drag, armor, hitSize, health, ammo;
    @Import Team team;
    @Import int id;

    private UnitController controller;
    private UnitType type;
    boolean spawnedByCore;

    //TODO mark as non-transient when done
    transient double flag;

    transient Seq<Ability> abilities = new Seq<>(0);
    private transient float resupplyTime = Mathf.random(10f);

    public void moveAt(Vec2 vector){
        moveAt(vector, type.accel);
    }

    public void aimLook(Position pos){
        aim(pos);
        lookAt(pos);
    }

    public void aimLook(float x, float y){
        aim(x, y);
        lookAt(x, y);
    }

    public boolean inRange(Position other){
        return within(other, type.range);
    }

    public boolean hasWeapons(){
        return type.hasWeapons();
    }

    @Override
    public float range(){
        return type.range;
    }

    @Replace
    public float clipSize(){
        return Math.max(type.region.width * 2f, type.clipSize);
    }

    @Override
    public double sense(LAccess sensor){
        return switch(sensor){
            case totalItems -> stack().amount;
            case itemCapacity -> type.itemCapacity;
            case rotation -> rotation;
            case health -> health;
            case maxHealth -> maxHealth;
            case x -> x;
            case y -> y;
            case team -> team.id;
            case shooting -> isShooting() ? 1 : 0;
            case shootX -> aimX();
            case shootY -> aimY();
            case flag -> flag;
            default -> 0;
        };
    }

    @Override
    public Object senseObject(LAccess sensor){
        return switch(sensor){
            case type -> type;
            case name -> controller instanceof Player p ? p.name : null;
            default -> noSensed;
        };

    }

    @Override
    public double sense(Content content){
        if(content == stack().item) return stack().amount;
        return 0;
    }

    @Override
    @Replace
    public boolean canDrown(){
        return isGrounded() && !hovering && type.canDrown;
    }

    @Override
    @Replace
    public boolean canShoot(){
        //cannot shoot while boosting
        return !(type.canBoost && isFlying());
    }

    @Override
    public int itemCapacity(){
        return type.itemCapacity;
    }

    @Override
    public float bounds(){
        return hitSize *  2f;
    }

    @Override
    public void controller(UnitController next){
        this.controller = next;
        if(controller.unit() != self()) controller.unit(self());
    }

    @Override
    public UnitController controller(){
        return controller;
    }

    public void resetController(){
        controller(type.createController());
    }

    @Override
    public void set(UnitType def, UnitController controller){
        type(type);
        controller(controller);
    }

    @Override
    public void type(UnitType type){
        if(this.type == type) return;

        setStats(type);
    }

    @Override
    public UnitType type(){
        return type;
    }

    /** @return pathfinder path type for calculating costs */
    public int pathType(){
        return Pathfinder.costGround;
    }

    public void lookAt(float angle){
        rotation = Angles.moveToward(rotation, angle, type.rotateSpeed * Time.delta * speedMultiplier());
    }

    public void lookAt(Position pos){
        lookAt(angleTo(pos));
    }

    public void lookAt(float x, float y){
        lookAt(angleTo(x, y));
    }

    public boolean isAI(){
        return controller instanceof AIController;
    }

    public int count(){
        return team.data().countType(type);
    }

    public int cap(){
        return Units.getCap(team);
    }

    public void setStats(UnitType type){
        this.type = type;
        this.maxHealth = type.health;
        this.drag = type.drag;
        this.armor = type.armor;
        this.hitSize = type.hitSize;
        this.hovering = type.hovering;

        if(controller == null) controller(type.createController());
        if(mounts().length != type.weapons.size) setupWeapons(type);
        if(abilities.size != type.abilities.size){
            abilities = type.abilities.map(Ability::copy);
        }
    }

    @Override
    public void afterSync(){
        //set up type info after reading
        setStats(this.type);
        controller.unit(self());
    }

    @Override
    public void afterRead(){
        afterSync();
        //reset controller state
        controller(type.createController());
    }

    @Override
    public void add(){

        //check if over unit cap
        if(count() > cap() && !spawnedByCore && !dead){
            Call.unitCapDeath(self());
            team.data().updateCount(type, -1);
        }
    }

    @Override
    public void remove(){
        team.data().updateCount(type, -1);
        controller.removed(self());
    }

    @Override
    public void landed(){
        if(type.landShake > 0f){
            Effect.shake(type.landShake, type.landShake, this);
        }

        type.landed(self());
    }

    @Override
    public void update(){

        type.update(self());

        if(state.rules.unitAmmo && ammo < type.ammoCapacity - 0.0001f){
            resupplyTime += Time.delta;

            //resupply only at a fixed interval to prevent lag
            if(resupplyTime > 10f){
                type.ammoType.resupply(self());
                resupplyTime = 0f;
            }
        }

        if(abilities.size > 0){
            for(Ability a : abilities){
                a.update(self());
            }
        }

        drag = type.drag * (isGrounded() ? (floorOn().dragMultiplier) : 1f);

        //apply knockback based on spawns
        if(team != state.rules.waveTeam){
            float relativeSize = state.rules.dropZoneRadius + hitSize/2f + 1f;
            for(Tile spawn : spawner.getSpawns()){
                if(within(spawn.worldx(), spawn.worldy(), relativeSize)){
                    vel().add(Tmp.v1.set(this).sub(spawn.worldx(), spawn.worldy()).setLength(0.1f + 1f - dst(spawn) / relativeSize).scl(0.45f * Time.delta));
                }
            }
        }

        //simulate falling down
        if(dead || health <= 0){
            //less drag when dead
            drag = 0.01f;

            //standard fall smoke
            if(Mathf.chanceDelta(0.1)){
                Tmp.v1.setToRandomDirection().scl(hitSize);
                type.fallEffect.at(x + Tmp.v1.x, y + Tmp.v1.y);
            }

            //thruster fall trail
            if(Mathf.chanceDelta(0.2)){
                float offset = type.engineOffset/2f + type.engineOffset/2f*elevation;
                float range = Mathf.range(type.engineSize);
                type.fallThrusterEffect.at(
                    x + Angles.trnsx(rotation + 180, offset) + Mathf.range(range),
                    y + Angles.trnsy(rotation + 180, offset) + Mathf.range(range),
                    Mathf.random()
                );
            }

            //move down
            elevation -= type.fallSpeed * Time.delta;

            if(isGrounded()){
                destroy();
            }
        }

        Tile tile = tileOn();
        Floor floor = floorOn();

        if(tile != null && isGrounded() && !type.hovering){
            //unit block update
            if(tile.build != null){
                tile.build.unitOn(self());
            }

            //apply damage
            if(floor.damageTaken > 0f){
                damageContinuous(floor.damageTaken);
            }
        }

        //kill entities on tiles that are solid to them
        if(tile != null && !canPassOn()){
            //boost if possible
            if(type.canBoost){
                elevation = 1f;
            }else if(!net.client()){
                kill();
            }
        }

        //AI only updates on the server
        if(!net.client() && !dead){
            controller.updateUnit();
        }

        //clear controller when it becomes invalid
        if(!controller.isValidController()){
            resetController();
        }

        //remove units spawned by the core
        if(spawnedByCore && !isPlayer()){
            Call.unitDespawn(self());
        }
    }

    /** @return a preview icon for this unit. */
    public TextureRegion icon(){
        return type.icon(Cicon.full);
    }

    /** Actually destroys the unit, removing it and creating explosions. **/
    public void destroy(){
        float explosiveness = 2f + item().explosiveness * stack().amount / 2f;
        float flammability = item().flammability * stack().amount / 2f;
        Damage.dynamicExplosion(x, y, flammability, explosiveness, 0f, bounds() / 2f, Pal.darkFlame, state.rules.damageExplosions);

        float shake = hitSize / 3f;

        Effect.scorch(x, y, (int)(hitSize / 5));
        Fx.explosion.at(this);
        Effect.shake(shake, shake, this);
        type.deathSound.at(this);

        Events.fire(new UnitDestroyEvent(self()));

        if(explosiveness > 7f && isLocal()){
            Events.fire(Trigger.suicideBomb);
        }

        //if this unit crash landed (was flying), damage stuff in a radius
        if(type.flying){
            Damage.damage(team,x, y, Mathf.pow(hitSize, 0.94f) * 1.25f, Mathf.pow(hitSize, 0.75f) * type.crashDamageMultiplier * 5f, true, false, true);
        }

        if(!headless){
            for(int i = 0; i < type.wreckRegions.length; i++){
                if(type.wreckRegions[i].found()){
                    float range = type.hitSize /4f;
                    Tmp.v1.rnd(range);
                    Effect.decal(type.wreckRegions[i], x + Tmp.v1.x, y + Tmp.v1.y, rotation - 90);
                }
            }
        }

        remove();
    }

    @Override
    public void display(Table table){
        type.display(self(), table);
    }

    @Override
    public boolean isImmune(StatusEffect effect){
        return type.immunities.contains(effect);
    }

    @Override
    public void draw(){
        type.draw(self());
    }

    @Override
    public boolean isPlayer(){
        return controller instanceof Player;
    }

    @Nullable
    public Player getPlayer(){
        return isPlayer() ? (Player)controller : null;
    }

    @Override
    public void killed(){
        health = 0;
        dead = true;

        //don't waste time when the unit is already on the ground, just destroy it
        if(!type.flying){
            destroy();
        }
    }

    @Override
    @Replace
    public void kill(){
        if(dead || net.client()) return;

        //deaths are synced; this calls killed()
        Call.unitDeath(id);
    }
}
