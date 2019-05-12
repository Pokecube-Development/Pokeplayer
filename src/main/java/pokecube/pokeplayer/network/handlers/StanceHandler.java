package pokecube.pokeplayer.network.handlers;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.pokemob.ai.LogicStates;
import pokecube.core.network.pokemobs.PacketCommand.DefaultHandler;
import pokecube.pokeplayer.network.PacketTransform;

public class StanceHandler extends DefaultHandler
{

    boolean state;
    byte    key;

    public StanceHandler()
    {
    }

    public StanceHandler(Boolean state, Byte key)
    {
        this.state = state;
        this.key = key;
    }

    @Override
    public void handleCommand(IPokemob pokemob) throws Exception
    {
        pokecube.core.interfaces.pokemob.commandhandlers.StanceHandler defaults = new pokecube.core.interfaces.pokemob.commandhandlers.StanceHandler(
                this.state, this.key);
        defaults.handleCommand(pokemob);
        if (pokemob.getEntity().getEntityData().getBoolean("isPlayer"))
        {
            Entity entity = pokemob.getEntity().getEntityWorld().getEntityByID(pokemob.getEntity().getEntityId());
            if (entity instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) entity;
                PacketTransform packet = new PacketTransform();
                packet.id = player.getEntityId();
                packet.data.setBoolean("U", true);
                packet.data.setBoolean("S", pokemob.getLogicState(LogicStates.SITTING));
                PokecubeMod.packetPipeline.sendTo(packet, (EntityPlayerMP) player);
            }
        }
    }

    @Override
    public void writeToBuf(ByteBuf buf)
    {
        super.writeToBuf(buf);
        buf.writeBoolean(state);
        buf.writeByte(key);
    }

    @Override
    public void readFromBuf(ByteBuf buf)
    {
        super.readFromBuf(buf);
        state = buf.readBoolean();
        key = buf.readByte();
    }

}
