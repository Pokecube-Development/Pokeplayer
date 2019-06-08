package pokecube.pokeplayer.client;

import java.util.UUID;
import java.util.logging.Level;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import pokecube.core.PokecubeCore;
import pokecube.core.client.gui.GuiDisplayPokecubeInfo;
import pokecube.core.client.gui.GuiPokedex;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.commandhandlers.StanceHandler;
import pokecube.core.network.pokemobs.PacketCommand;
import pokecube.pokeplayer.PokeInfo;
import pokecube.pokeplayer.Proxy;
import pokecube.pokeplayer.client.gui.GuiAsPokemob;
import thut.core.common.handlers.PlayerDataHandler;

public class ProxyClient extends Proxy
{
    @Override
    public IPokemob getPokemob(EntityPlayer player)
    {
        IPokemob ret = super.getPokemob(player);
        if (ret != null && player.getEntityWorld().isRemote)
        {
            PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
            info.setPlayer(player);
        }
        return ret;
    }

    @Override
    public void init()
    {
        super.init();
    }

    @Override
    public void postInit()
    {
        super.postInit();
        MinecraftForge.EVENT_BUS.register(new GuiAsPokemob());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        IPokemob pokemob;
        if (event.side == Side.SERVER || event.player != PokecubeCore.proxy.getPlayer((String) null)
                || (pokemob = getPokemob(event.player)) == null)
            return;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiPokedex)
        {
            ((GuiPokedex) Minecraft.getMinecraft().currentScreen).pokemob = pokemob;
            GuiPokedex.pokedexEntry = pokemob.getPokedexEntry();
        }
    }

    @SubscribeEvent
    public void pRender(RenderPlayerEvent.Pre event)
    {
        IPokemob pokemob = getPokemob(event.getEntityPlayer());
        if (pokemob == null) return;
        event.setCanceled(true);
        boolean shadow = Minecraft.getMinecraft().getRenderManager().isRenderShadow();
        Minecraft.getMinecraft().getRenderManager().setRenderShadow(false);
        try
        {
            Minecraft.getMinecraft().getRenderManager().renderEntity(pokemob.getEntity(), event.getX(), event.getY(),
                    event.getZ(), event.getEntityPlayer().rotationYaw, event.getPartialRenderTick(), false);
        }
        catch (Exception e)
        {
            PokecubeMod.log(Level.SEVERE, "Error Rendering Pokeplayer", e);
        }
        Minecraft.getMinecraft().getRenderManager().setRenderShadow(shadow);
    }

    @SubscribeEvent
    public void renderHand(RenderHandEvent event)
    {
        IPokemob pokemob = getPokemob(PokecubeCore.proxy.getPlayer((UUID) null));
        if (pokemob == null) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void mouseClickEvent(MouseEvent event)
    {
        IPokemob pokemob = null;
        EntityPlayer player = null;
        int button = event.getButton();
        if (GuiScreen.isAltKeyDown() && button >= 0
                && (pokemob = getPokemob(player = PokecubeCore.proxy.getPlayer((UUID) null))) != null)
        {
            if (button == 0 && event.isButtonstate())
            {
                GuiDisplayPokecubeInfo.instance().pokemobAttack();
                event.setCanceled(true);
            }
            if (button == 1 && event.isButtonstate())
            {
                // Our custom StanceHandler will do interaction code on -2
                PacketCommand.sendCommand(pokemob, Command.STANCE, new StanceHandler(true, (byte) -2));

                EntityInteractSpecific evt = new EntityInteractSpecific(player, EnumHand.MAIN_HAND, pokemob.getEntity(),
                        new Vec3d(0, 0, 0));
                // Apply interaction, also do not allow saddle.
                ItemStack saddle = pokemob.getPokemobInventory().getStackInSlot(0);
                if (!saddle.isEmpty()) pokemob.getPokemobInventory().setInventorySlotContents(0, ItemStack.EMPTY);
                PokecubeCore.instance.events.interactEvent(evt);
                if (!saddle.isEmpty()) pokemob.getPokemobInventory().setInventorySlotContents(0, saddle);
                event.setCanceled(true);
            }
        }
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z)
    {
        return null;
    }
}