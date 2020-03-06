package mindustry.world.blocks.distribution;

import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

public class ArmoredConveyor extends Conveyor{

    public ArmoredConveyor(String name){
        super(name);
    }

    @Override
    public boolean blends(int rotation, int otherx, int othery, int otherrot, Block otherblock) {
        return otherblock.outputsItems() && blendsArmored(rotation, otherx, othery, otherrot, otherblock);
    }

    public class ArmoredConveyorEntity extends ConveyorEntity{
        @Override
        public boolean acceptItem(Tilec source, Item item){
            return super.acceptItem(source, item) && (source.block() instanceof Conveyor || Edges.getFacingEdge(source.tile(), tile).relativeTo(tile) == tile.rotation());
        }
    }
}
