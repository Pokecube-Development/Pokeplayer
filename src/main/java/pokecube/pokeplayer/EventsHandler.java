package pokecube.pokeplayer;

import java.util.HashSet;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import pokecube.core.PokecubeCore;
import pokecube.core.events.MoveMessageEvent;
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
    public void interactEvent(PlayerInteractEvent.RightClickItem event)
    {
        IPokemob pokemob = proxy.getPokemob(event.getEntityPlayer());
        if (pokemob == null) return;
        if (event.getEntityPlayer().isSneaking())
        {
            EntityInteractSpecific evt = new EntityInteractSpecific(event.getEntityPlayer(), event.getHand(),
                    pokemob.getEntity(), new Vec3d(0, 0, 0));
            PokecubeCore.instance.events.interactEvent(evt);
            PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(event.getEntityPlayer())
                    .getData(PokeInfo.class);
            info.save(event.getEntityPlayer());
            if (evt.isCanceled()) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        IPokemob pokemob = proxy.getPokemob(event.player);
        if (pokemob != null) pokemob.getEntity().addedToChunk = true;
        if (event.phase == Phase.END)
        {
            if (event.player.getHealth() <= 0) { return; }
            event.player.addedToChunk = true;
            // pokemob.getEntity().addedToChunk = true;
            // if (pokemob != null) {
            // checkEvolution(pokemob);
            // }
            proxy.updateInfo(event.player);
        }
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
    public void onAttacked(LivingAttackEvent event)
    {
        if (event.getEntity() instanceof EntityPlayer)
        {
            IPokemob pokemob = proxy.getPokemob((EntityPlayer) event.getEntity());
            if (pokemob != null)
            {
                pokemob.getEntity().attackEntityFrom(event.getSource(), event.getAmount());
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onDamaged(LivingDamageEvent event)
    {
        // System.out.println(event.getAmount());
        if (event.getEntity() instanceof EntityPlayer)
        {
            IPokemob pokemob = proxy.getPokemob((EntityPlayer) event.getEntity());
            if (pokemob != null)
            {
                System.out.println("D" + event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public void pokemobMoveMessage(MoveMessageEvent evt)
    {
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
    public void PlayerDeath(LivingDeathEvent evt)
    {
        if (evt.getEntityLiving().getEntityWorld().isRemote) return;
        if (!(evt.getEntityLiving() instanceof EntityPlayer))
        {
            if (evt.getEntityLiving().getEntityData().getBoolean("isPlayer")
                    && CapabilityPokemob.getPokemobFor(evt.getEntityLiving()) != null)
            {
                Entity real = evt.getEntityLiving().getEntityWorld().getEntityByID(evt.getEntity().getEntityId());
                if (real != evt.getEntity() && real instanceof EntityPlayerMP)
                {
                    EntityPlayerMP player = (EntityPlayerMP) real;
                    player.attackEntityFrom(evt.getSource(), Float.MAX_VALUE);
                }
            }
            return;
        }
        EntityPlayer player = (EntityPlayer) evt.getEntityLiving();
        if (player != null)
        {
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null)
            {
                ItemStack stack = PokecubeManager.pokemobToItem(pokemob);
                PokecubeManager.heal(stack);
                pokemob = PokecubeManager.itemToPokemob(stack, player.getEntityWorld());
                proxy.setPokemob(player, pokemob);
                PacketTransform.sendPacket(player, (EntityPlayerMP) player);
            }
        }
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
                PacketTransform.sendPacket(player, (EntityPlayerMP) player);
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
        if (event.getEntityLiving().getEntityWorld().isRemote) return;
        if (event.getEntity() instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
            if (!syncSchedule.isEmpty() && syncSchedule.contains(player.getUniqueID()) && player.ticksExisted > 5)
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
    public void PlayerJoinWorld(EntityJoinWorldEvent evt)
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
