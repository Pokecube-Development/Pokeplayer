package pokecube.pokeplayer.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import pokecube.core.PokecubeCore;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.ai.LogicStates;
import pokecube.core.interfaces.pokemob.commandhandlers.StanceHandler;
import pokecube.core.network.pokemobs.PacketCommand;
import pokecube.pokeplayer.PokeInfo;
import thut.core.common.handlers.PlayerDataHandler;
import thut.core.common.network.NBTPacket;
import thut.core.common.network.PacketAssembly;

public class PacketTransform extends NBTPacket
{
    public int id;

    public static final PacketAssembly<PacketTransform> ASSEMBLY = PacketAssembly.registerAssembler(
            PacketTransform.class, PacketTransform::new, PokecubeCore.packets);

    public static void sendPacket(final PlayerEntity toSend, final ServerPlayerEntity sendTo)
    {
        PacketTransform.ASSEMBLY.sendTo(PacketTransform.getPacket(toSend), sendTo);
    }

    public static PacketTransform getPacket(final PlayerEntity toSend)
    {
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(toSend).getData(PokeInfo.class);
        final PacketTransform message = new PacketTransform();
        info.writeToNBT(message.getTag());
        message.getTag().putInt("__entityid__", toSend.getEntityId());
        return message;
    }

    // @Override
    // public IMessage onMessage(final PacketTransform message, final
    // MessageContext ctx)
    // {
    // PokecubeCore.proxy.getMainThreadListener().addScheduledTask(new
    // Runnable()
    // {
    // @Override
    // public void run()
    // {
    // apply(message, ctx);
    // }
    // });
    // return null;
    // }

    // public void fromBytes(ByteBuf buf) throws IOException
    // {
    // id = buf.readInt();
    // new PacketBuffer(buf).readCompoundTag();
    // }
    //
    //
    // public void toBytes(ByteBuf buf)
    // {
    // buf.writeInt(id);
    // new PacketBuffer(buf).writeCompoundTag(this.getTag());
    // }

    public PacketTransform()
    {
        super();
    }

    public PacketTransform(final CompoundNBT tag)
    {
        super();
        this.tag = tag;
    }

    public PacketTransform(final PacketBuffer buffer)
    {
        super(buffer);
    }

    @Override
    protected void onCompleteClient()
    {
        final World world = PokecubeCore.proxy.getWorld();
        this.id = this.getTag().getInt("__entityid__");
        final Entity e = PokecubeCore.getEntityProvider().getEntity(world, this.id, false);
        if (this.getTag().contains("U"))
        {
            final PlayerEntity player = PokecubeCore.proxy.getPlayer();
            if (this.getTag().contains("H"))
            {
                final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
                final IPokemob pokemob = info.getPokemob(world);
                if (pokemob == null) return;
                final float health = this.getTag().getFloat("H");
                if (pokemob.getEntity() == null) return;
                // float max = message.getTag().getFloat("M");
                // pokemob.getEntity().getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(max);
                pokemob.setHealth(health);
                player.setHealth(health);
            }
            else if (this.getTag().contains("S"))
            {
                final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
                final IPokemob pokemob = info.getPokemob(world);
                if (pokemob == null) return;
                pokemob.setLogicState(LogicStates.SITTING, this.getTag().getBoolean("S"));
            }
            return;
        }
        if (e instanceof PlayerEntity)
        {
            final PlayerEntity player = (PlayerEntity) e;
            final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
            info.clear();
            info.readFromNBT(this.getTag());
            final IPokemob pokemob = info.getPokemob(world);
            if (pokemob != null)
            {
                info.set(pokemob, player);
                // Callback to let server know to update us.
                pokemob.getEntity().setEntityId(player.getEntityId());
                PacketCommand.sendCommand(pokemob, Command.STANCE, new StanceHandler(true, (byte) -3));
            }
            else info.resetPlayer(player);
        }
    }
}
