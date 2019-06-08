package pokecube.pokeplayer;

import java.util.HashSet;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import pokecube.core.events.pokemob.EvolveEvent;
import pokecube.core.events.pokemob.RecallEvent;
import pokecube.core.events.pokemob.combat.AttackEvent;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackEntityHandler;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.network.pokemobs.PacketCommand;
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
        if (evt.moveInfo.attacked instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) evt.moveInfo.attacked;
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null)
            {
                evt.moveInfo.attacked = pokemob.getEntity();
            }
        }
    }

    @SubscribeEvent
    public void attack(AttackEntityEvent event)
    {
        PlayerEntity player = event.getPlayerEntity();
        IPokemob pokemob = proxy.getPokemob(player);
        if (pokemob == null) return;
        if (player.getEntityWorld().isRemote) PacketCommand.sendCommand(pokemob, Command.ATTACKENTITY,
                new AttackEntityHandler(event.getTarget().getEntityId()).setFromOwner(true));
        event.setCanceled(true);
    }

    @SubscribeEvent
    /** Sync attacks to the players over to the pokemobs, and also notifiy the
     * pokeinfo that the pokemob was attacked.
     * 
     * @param event */
    public void attack(LivingAttackEvent event)
    {
        if (event.getEntity().getEntityWorld().isRemote) return;
        PlayerEntity player = null;
        if (event.getEntity() instanceof PlayerEntity)
        {
            player = (PlayerEntity) event.getEntity();
            IPokemob pokemob = proxy.getPokemob(player);
            if (pokemob != null)
            {
                pokemob.getEntity().attackEntityFrom(event.getSource(), event.getAmount());
            }
        }
        else if (event.getMobEntity().getEntityData().getBoolean("isPlayer"))
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
                ServerPlayerEntity player = (ServerPlayerEntity) event.player;
                ItemStack stack = PokecubeManager.pokemobToItem(pokemob);
                PokecubeManager.heal(stack);
                pokemob = PokecubeManager.itemToPokemob(stack, event.player.getEntityWorld());
                pokemob.getEntity().isDead = false;
                pokemob.getEntity().deathTime = -1;
                proxy.setPokemob(event.player, pokemob);
                PacketTransform.sendPacket(event.player, player);
                if (!player.getEntityWorld().isRemote)
                {
                    EventsHandler.sendUpdate(player);
                    ((ServerPlayerEntity) player).sendAllContents(player.inventoryContainer,
                            player.inventoryContainer.inventoryItemStacks);
                    // // Fixes the inventories appearing to vanish
                    player.getEntityData().putLong("_pokeplayer_evolved_",
                            player.getEntityWorld().getGameTime() + 50);
                }
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
            PlayerEntity player = entity.getEntityWorld().getPlayerEntityByUUID(uuid);
            IPokemob evo = evt.mob;
            proxy.setPokemob(player, evo);
            evt.setCanceled(true);
            if (!player.getEntityWorld().isRemote)
            {
                ServerPlayerEntity playerMP = (ServerPlayerEntity) player;
                PacketTransform.sendPacket(player, playerMP);
                if (!player.getEntityWorld().isRemote)
                {
                    EventsHandler.sendUpdate(player);
                    ((ServerPlayerEntity) player).sendAllContents(player.inventoryContainer,
                            player.inventoryContainer.inventoryItemStacks);
                    // // Fixes the inventories appearing to vanish
                    player.getEntityData().putLong("_pokeplayer_evolved_",
                            player.getEntityWorld().getGameTime() + 50);
                }
            }
            return;
        }
    }

    static HashSet<UUID> syncSchedule = new HashSet<UUID>();

    @SubscribeEvent
    public void PlayerLoggedInEvent(PlayerLoggedInEvent event)
    {
        Side side = FMLCommonHandler.instance().getEffectiveSide();
        if (side == Dist.DEDICATED_SERVER)
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
        if (event.getTarget() instanceof PlayerEntity && event.getPlayerEntity().isServerWorld())
        {
            PacketTransform.sendPacket((PlayerEntity) event.getTarget(), (ServerPlayerEntity) event.getPlayerEntity());
        }
    }

    @SubscribeEvent
    public void postPlayerTick(PlayerTickEvent event)
    {
        PlayerEntity player = event.player;

        if (event.phase == Phase.START)
        {
            proxy.updateInfo(player);
        }
        else
        {
            PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
            info.postPlayerTick(player);
        }
    }

    @SubscribeEvent
    public void onEntityCapabilityAttach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof PlayerEntity)
        {
            event.addCapability(DATACAP, new DataSyncWrapper());
        }
    }

    @SubscribeEvent
    public void entityJoinWorld(EntityJoinWorldEvent evt)
    {
        if (evt.getWorld().isRemote) return;
        if (evt.getEntity().getEntityData().getBoolean("isPlayer"))
        {
            IPokemob evo = CapabilityPokemob.getPokemobFor(evt.getEntity());
            if (evo != null)
            {
                UUID uuid = UUID.fromString(evt.getEntity().getEntityData().getString("playerID"));
                PlayerEntity player = evt.getWorld().getPlayerEntityByUUID(uuid);
                proxy.setPokemob(player, evo);
                evt.setCanceled(true);
                if (!player.getEntityWorld().isRemote)
                {
                    PacketTransform.sendPacket(player, (ServerPlayerEntity) player);
                }
                return;
            }
        }
        else if (evt.getEntity() instanceof ServerPlayerEntity)
        {
            sendUpdate((PlayerEntity) evt.getEntity());
        }
    }

    public static void sendUpdate(PlayerEntity player)
    {
        PacketTransform.sendPacket(player, (ServerPlayerEntity) player);
    }
}
