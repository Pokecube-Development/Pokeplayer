package pokecube.pokeplayer.client.gui;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import pokecube.core.PokecubeCore;
import pokecube.core.client.gui.GuiDisplayPokecubeInfo;
import pokecube.core.client.gui.GuiTeleport;
import pokecube.core.interfaces.IMoveNames;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackEntityHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackLocationHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackNothingHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.TeleportHandler;
import pokecube.core.network.pokemobs.PacketCommand;
import pokecube.core.utils.Tools;
import pokecube.pokeplayer.PokePlayer;
import thut.api.maths.Vector3;

public class GuiAsPokemob extends GuiDisplayPokecubeInfo
{
    public static int     moveIndex = 0;
    public static boolean useMove   = false;

    public GuiAsPokemob()
    {
        super();
    }

    @Override
    public IPokemob[] getPokemobsToDisplay()
    {
        IPokemob pokemob = PokePlayer.PROXY.getPokemob(PokecubeCore.proxy.getPlayer((UUID) null));
        if (pokemob != null) return new IPokemob[] { pokemob };
        return super.getPokemobsToDisplay();
    }

    @Override
    public IPokemob getCurrentPokemob()
    {
        IPokemob pokemob = PokePlayer.PROXY.getPokemob(PokecubeCore.proxy.getPlayer((UUID) null));
        if (pokemob == null) return super.getCurrentPokemob();
        return pokemob;
    }

    @Override
    public void pokemobAttack()
    {
        IPokemob pokemob = PokePlayer.PROXY.getPokemob(PokecubeCore.proxy.getPlayer((UUID) null));
        if (pokemob == null)
        {
            super.pokemobAttack();
            return;
        }
        if (!useMove || pokemob.getAttackCooldown() > 0) return;
        useMove = false;
        EntityPlayer player = minecraft.player;
        Entity target = Tools.getPointedEntity(player, 32);
        Vector3 targetLocation = Tools.getPointedLocation(player, 32);
        boolean sameOwner = false;
        IPokemob targetMob = CapabilityPokemob.getPokemobFor(target);
        if (targetMob != null)
        {
            sameOwner = targetMob.getPokemonOwner() == player;
        }
        if (pokemob != null)
        {
            if (pokemob.getMove(pokemob.getMoveIndex()) == null) { return; }
            if (pokemob.getMove(pokemob.getMoveIndex()).equalsIgnoreCase(IMoveNames.MOVE_TELEPORT))
            {
                if (!GuiTeleport.instance().getState())
                {
                    GuiTeleport.instance().setState(true);
                    return;
                }
                GuiTeleport.instance().setState(false);
                PacketCommand.sendCommand(pokemob, Command.TELEPORT, new TeleportHandler());
                return;
            }
        }
        if (target != null && !sameOwner && target instanceof EntityLivingBase)
        {
            PacketCommand.sendCommand(pokemob, Command.ATTACKENTITY, new AttackEntityHandler(target.getEntityId()));
        }
        else if (targetLocation != null)
        {
            PacketCommand.sendCommand(pokemob, Command.ATTACKLOCATION, new AttackLocationHandler(targetLocation));
        }
        else
        {
            PacketCommand.sendCommand(pokemob, Command.ATTACKNOTHING, new AttackNothingHandler());
        }
    }

    @Override
    public void pokemobBack()
    {
        if (!isPokemob()) super.pokemobBack();
        else
        {

        }
    }

    @Override
    public void nextMove(int i)
    {
        super.nextMove(i);
    }

    @Override
    public void previousMove(int j)
    {
        super.previousMove(j);
    }

    @Override
    public void setMove(int num)
    {
        super.setMove(num);
    }

    boolean isPokemob()
    {
        IPokemob pokemob = PokePlayer.PROXY.getPokemob(PokecubeCore.proxy.getPlayer((UUID) null));
        return pokemob != null;
    }
}
