package pokecube.pokeplayer.network;

import java.util.Map;
import java.util.logging.Level;

import com.google.common.collect.Maps;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import pokecube.core.PokecubeCore;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.IHasCommands.IMobCommandHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackLocationHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackNothingHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.ChangeFormHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.MoveIndexHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.MoveToHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.StanceHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.SwapMovesHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.TeleportHandler;
import pokecube.core.network.pokemobs.PacketCommand.DefaultHandler;

public class PacketDoActions implements IMessage, IMessageHandler<PacketDoActions, IMessage>
{
    public static final Map<Command, Class<? extends IMobCommandHandler>> COMMANDHANDLERS = Maps.newHashMap();

    // Register default command handlers
    static
    {
        // Only populate this if someone else hasn't override in.
        if (PacketDoActions.COMMANDHANDLERS.isEmpty())
        {
            PacketDoActions.COMMANDHANDLERS.put(Command.ATTACKENTITY, PokePlayerAttackEntityHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.ATTACKLOCATION, AttackLocationHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.ATTACKNOTHING, AttackNothingHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.CHANGEFORM, ChangeFormHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.CHANGEMOVEINDEX, MoveIndexHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.MOVETO, MoveToHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.STANCE, StanceHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.SWAPMOVES, SwapMovesHandler.class);
            PacketDoActions.COMMANDHANDLERS.put(Command.TELEPORT, TeleportHandler.class);
        }
    }

    int                entityId;
    IMobCommandHandler handler;
    Command            command;

    public static void sendCommand(IPokemob pokemob, Command command, IMobCommandHandler handler)
    {
        PacketDoActions packet = new PacketDoActions();
        packet.entityId = pokemob.getEntity().getEntityId();
        packet.command = command;
        packet.handler = handler;
        PokecubeMod.packetPipeline.sendToServer(packet);
    }

    public PacketDoActions()
    {
    }

    @Override
    public IMessage onMessage(PacketDoActions message, MessageContext ctx)
    {
        PokecubeCore.proxy.getMainThreadListener().addScheduledTask(new Runnable()
        {
            @Override
            public void run()
            {
                EntityPlayer player = ctx.getServerHandler().player;
                processMessage(player.getEntityWorld(), message);
            }
        });
        return null;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        entityId = buf.readInt();
        command = Command.values()[buf.readByte()];
        try
        {
            handler = PacketDoActions.COMMANDHANDLERS.get(command).newInstance();
            handler.readFromBuf(buf);
        }
        catch (Exception e)
        {
            PokecubeMod.log(Level.SEVERE, "Error handling a command to a pokemob", e);
            handler = new DefaultHandler();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(entityId);
        buf.writeByte(command.ordinal());
        handler.writeToBuf(buf);
    }

    private static final void processMessage(World world, PacketDoActions message)
    {
        Entity user = PokecubeMod.core.getEntityProvider().getEntity(world, message.entityId, true);
        IPokemob pokemob = CapabilityPokemob.getPokemobFor(user);
        if (pokemob == null) return;
        pokemob.handleCommand(message.command, message.handler);
    }

}