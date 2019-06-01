package pokecube.pokeplayer;

import java.util.HashSet;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.relauncher.Side;
import pokecube.core.events.pokemob.EvolveEvent;
import pokecube.core.events.pokemob.RecallEvent;
import pokecube.core.events.pokemob.combat.AttackEvent;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.pokeplayer.network.DataSyncWrapper;
import pokecube.pokeplayer.network.PacketTransform;
import thut.core.common.handlers.PlayerDataHandler;

public class EventsHandler
{
    public static final ResourceLocation DATACAP = new ResourceLocation(PokecubeMod.ID, "data");
    private static Proxy                 proxy;

    public EventsHandler(Proxy proxy)
    {
        EventsHandler.proxy = proxy;
        MinecraftForge.EVENT_BUS.register(this);
        PokecubeMod.MOVE_BUS.register(this);
    }

    @SubscribeEvent
    public void pokemobAttack(AttackEvent evt)
    {
        if (evt.moveInfo.attacked instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) evt.moveInfo.attacked;
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null)
            {
                evt.moveInfo.attacked = pokemob.getEntity();
            }
        }
    }

    @SubscribeEvent
    /** Sync attacks to the players over to the pokemobs, and also notifiy the
     * pokeinfo that the pokemob was attacked.
     * 
     * @param event */
    public void onAttacked(LivingAttackEvent event)
    {
        if (event.getEntity().getEntityWorld().isRemote) return;
        EntityPlayer player = null;
        if (event.getEntity() instanceof EntityPlayer)
        {
            player = (EntityPlayer) event.getEntity();
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null)
            {
                pokemob.getEntity().attackEntityFrom(event.getSource(), event.getAmount());
            }
        }
        else if (event.getEntityLiving().getEntityData().getBoolean("isPlayer"))
        {
            IPokemob evo = CapabilityPokemob.getPokemobFor(event.getEntity());
            if (evo != null)
            {
                UUID uuid = UUID.fromString(event.getEntity().getEntityData().getString("playerID"));
                player = event.getEntity().getEntityWorld().getPlayerEntityByUUID(uuid);
            }
        }
        if (player != null)
        {
            PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
            info.lastDamage = event.getSource();
        }
    }

    @SubscribeEvent
    public void doRespawn(PlayerRespawnEvent event)
    {
        if (event.player != null && !event.player.getEntityWorld().isRemote)
        {
            IPokemob pokemob = proxy.getPokemob(event.player);
            if (pokemob != null)
            {
                ItemStack stack = PokecubeManager.pokemobToItem(pokemob);
                PokecubeManager.heal(stack);
                pokemob = PokecubeManager.itemToPokemob(stack, event.player.getEntityWorld());
                pokemob.getEntity().isDead = false;
                pokemob.getEntity().deathTime = -1;
                proxy.setPokemob(event.player, pokemob);
                PacketTransform.sendPacket(event.player, (EntityPlayerMP) event.player);
            }
        }
    }

    @SubscribeEvent
    public void recall(RecallEvent.Pre evt)
    {
        if (evt.recalled.getEntity().getEntityData().getBoolean("isPlayer")) evt.setCanceled(true);
    }

    @SubscribeEvent
    public void evolve(EvolveEvent.Post evt)
    {
        Entity entity = evt.mob.getEntity();
        if (entity.getEntityData().getBoolean("isPlayer"))
        {
            UUID uuid = UUID.fromString(entity.getEntityData().getString("playerID"));
            EntityPlayer player = entity.getEntityWorld().getPlayerEntityByUUID(uuid);
            IPokemob evo = evt.mob;
            proxy.setPokemob(player, evo);
            evt.setCanceled(true);
            if (!player.getEntityWorld().isRemote)
            {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                PacketTransform.sendPacket(player, playerMP);
            }
            return;
        }
    }

    static HashSet<UUID> syncSchedule = new HashSet<UUID>();

    @SubscribeEvent
    public void PlayerLoggedInEvent(PlayerLoggedInEvent event)
    {
        Side side = FMLCommonHandler.instance().getEffectiveSide();
        if (side == Side.SERVER)
        {
            syncSchedule.add(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void PlayerLoggedOutEvent(PlayerLoggedOutEvent event)
    {
        syncSchedule.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void startTracking(StartTracking event)
    {
        if (event.getTarget() instanceof EntityPlayer && event.getEntityPlayer().isServerWorld())
        {
            PacketTransform.sendPacket((EntityPlayer) event.getTarget(), (EntityPlayerMP) event.getEntityPlayer());
        }
    }

    @SubscribeEvent
    public void playerTick(LivingUpdateEvent event)
    {
        if (event.getEntity() instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null) pokemob.getEntity().addedToChunk = true;
            player.addedToChunk = true;
            proxy.updateInfo(player);
        }

        if (event.getEntityLiving().getEntityWorld().isRemote) return;
        if (event.getEntity() instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
            if (!syncSchedule.isEmpty() && syncSchedule.contains(player.getUniqueID()) && player.ticksExisted > 20)
            {
                IPokemob pokemob = proxy.getPokemob(player);
                if (pokemob != null)
                {
                    PokePlayer.PROXY.setPokemob(player, pokemob);
                    EventsHandler.sendUpdate(player);
                    for (EntityPlayer player2 : event.getEntity().getEntityWorld().playerEntities)
                    {
                        PacketTransform.sendPacket(player, (EntityPlayerMP) player2);
                    }
                }
                syncSchedule.remove(player.getUniqueID());
            }
        }
    }

    @SubscribeEvent
    public void onEntityCapabilityAttach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof EntityPlayer)
        {
            event.addCapability(DATACAP, new DataSyncWrapper());
        }
    }

    @SubscribeEvent
    public void entityJoinWorld(EntityJoinWorldEvent evt)
    {
        if (evt.getEntity().getEntityData().getBoolean("isPlayer"))
        {
            IPokemob evo = CapabilityPokemob.getPokemobFor(evt.getEntity());
            if (evo != null)
            {
                UUID uuid = UUID.fromString(evt.getEntity().getEntityData().getString("playerID"));
                EntityPlayer player = evt.getWorld().getPlayerEntityByUUID(uuid);
                proxy.setPokemob(player, evo);
                evt.setCanceled(true);
                if (!player.getEntityWorld().isRemote)
                {
                    PacketTransform.sendPacket(player, (EntityPlayerMP) player);
                }
                return;
            }
        }
    }

    public static void sendUpdate(EntityPlayer player)
    {
        PacketTransform.sendPacket(player, (EntityPlayerMP) player);
    }
}
