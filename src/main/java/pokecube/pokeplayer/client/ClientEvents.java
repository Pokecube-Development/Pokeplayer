package pokecube.pokeplayer.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.client.event.InputEvent.RawMouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import pokecube.core.PokecubeCore;
import pokecube.core.client.gui.GuiDisplayPokecubeInfo;
import pokecube.core.client.gui.GuiPokedex;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.network.pokemobs.PacketCommand;
import pokecube.pokeplayer.PokeInfo;
import pokecube.pokeplayer.network.handlers.Stance;
import thut.core.common.handlers.PlayerDataHandler;

public class ClientEvents
{	
    public IPokemob getPokemob(final PlayerEntity player)
    {
        final IPokemob ret = PokeInfo.getPokemob(player);
        if (ret != null && player.getEntityWorld().isRemote)
        {
            final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
            info.setPlayer(player);
        }
        return ret;
    }

    @SubscribeEvent
    public void onPlayerTick(final PlayerTickEvent event)
    {
        IPokemob pokemob;
        if (event.side == LogicalSide.SERVER || event.player != PokecubeCore.proxy.getPlayer(event.player.getUniqueID())
                || (pokemob = this.getPokemob(event.player)) == null) return;
        if (Minecraft.getInstance().currentScreen instanceof GuiPokedex)
        {
            ((GuiPokedex) Minecraft.getInstance().currentScreen).pokemob = pokemob;
            GuiPokedex.pokedexEntry = pokemob.getPokedexEntry();
        }
    }

    @SubscribeEvent
    public void mouseClickEvent(final RawMouseEvent event)
    {
        IPokemob pokemob = null;
        PlayerEntity player = null;
        final int button = event.getButton();

        final boolean alt = Screen.hasAltDown();
        final boolean ctrl = Screen.hasControlDown();

        if (alt && button >= 0 && (pokemob = this.getPokemob(player = PokecubeCore.proxy
                .getPlayer(
                (UUID) null))) != null)
        {
            if (button == 0 && ctrl)
            {
                GuiDisplayPokecubeInfo.instance().pokemobAttack();
                event.setCanceled(true);
            }
            if (button == 1 && ctrl)
            {
                // Our custom StanceHandler will do interaction code on -2
                PacketCommand.sendCommand(pokemob, Command.STANCE, new Stance(true, (byte) -2));

                final EntityInteractSpecific evt = new EntityInteractSpecific(player, Hand.MAIN_HAND, pokemob
                        .getEntity(), new Vector3d(0, 0, 0));
                MinecraftForge.EVENT_BUS.post(evt);
                // Apply interaction, also do not allow saddle.
                final ItemStack saddle = pokemob.getInventory().getStackInSlot(0);
                if (!saddle.isEmpty()) pokemob.getInventory().setInventorySlotContents(0, ItemStack.EMPTY);
                // PokecubeCore.RegistryEvents.registerTileEntities(evt);
                if (!saddle.isEmpty()) pokemob.getInventory().setInventorySlotContents(0, saddle);
                event.setCanceled(true);
            }
        }
    }

}