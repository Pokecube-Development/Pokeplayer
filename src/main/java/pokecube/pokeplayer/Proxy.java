package pokecube.pokeplayer;

import java.util.logging.Level;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.IGuiHandler;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.utils.EntityTools;
import thut.core.common.handlers.PlayerDataHandler;

public class Proxy implements IGuiHandler
{
    public void setPokemob(PlayerEntity player, IPokemob pokemob)
    {
        setMapping(player, pokemob);
    }

    public void savePokemob(PlayerEntity player)
    {
        PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        if (info != null) info.save(player);
    }

    private void setMapping(PlayerEntity player, IPokemob pokemob)
    {
        PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        info.set(pokemob, player);
        if (pokemob != null)
        {
            info.setPlayer(player);
            EntityTools.copyEntityTransforms(info.getPokemob(player.getEntityWorld()).getEntity(), player);
            info.save(player);
        }
    }

    public IPokemob getPokemob(PlayerEntity player)
    {
        if (player == null || player.getUniqueID() == null) return null;
        PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        return info.getPokemob(player.getEntityWorld());
    }

    public void updateInfo(PlayerEntity player)
    {
        PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        try
        {
            info.onUpdate(player);
        }
        catch (Exception e)
        {
            PokecubeMod.log(Level.SEVERE, "Error ticking Pokeplayer", e);
        }
    }

    public void init()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void postInit()
    {

    }

    @Override
    public Object getServerGuiElement(int ID, PlayerEntity player, World world, int x, int y, int z)
    {
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, PlayerEntity player, World world, int x, int y, int z)
    {
        return null;
    }
}
