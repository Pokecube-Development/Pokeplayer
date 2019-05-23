package pokecube.pokeplayer.network.handlers;

import java.util.logging.Level;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import pokecube.core.events.pokemob.combat.CommandAttackEvent;
import pokecube.core.interfaces.IMoveConstants;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.Move_Base;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.core.moves.MovesUtils;
import thut.api.maths.Vector3;

// Wrapper to ensure player attacks entity as pokeplayer
public class AttackEntityHandler extends pokecube.core.interfaces.pokemob.commandhandlers.AttackEntityHandler
{
    @Override
    public void handleCommand(IPokemob pokemob)
    {
        // Use default handling, which just agros stuff.
        if (!pokemob.getEntity().getEntityData().getBoolean("isPlayer"))
        {
            if (PokecubeMod.debug) PokecubeMod.log(Level.INFO, "Directing command to default AttackEntityHandler");
            super.handleCommand(pokemob);
            return;
        }

        // Actually execute the move if needed.
        World world = pokemob.getEntity().getEntityWorld();
        Entity target = PokecubeMod.core.getEntityProvider().getEntity(world, targetId, true);
        if (target == null || !(target instanceof EntityLivingBase))
        {
            if (PokecubeMod.debug)
            {
                if (target == null) PokecubeMod.log(Level.WARNING, "Target Mob cannot be null!",
                        new IllegalArgumentException(pokemob.getEntity().toString()));
                else PokecubeMod.log(Level.WARNING, "Invalid Target!",
                        new IllegalArgumentException(pokemob.getEntity() + " " + target));
            }
            return;
        }
        int currentMove = pokemob.getMoveIndex();
        CommandAttackEvent event = new CommandAttackEvent(pokemob.getEntity(), target);
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled() && currentMove != 5 && MovesUtils.canUseMove(pokemob))
        {
            Move_Base move = MovesUtils.getMoveFromName(pokemob.getMoves()[currentMove]);
            if (move.isSelfMove())
            {
                pokemob.executeMove(pokemob.getEntity(), null, 0);
            }
            else
            {
                pokemob.getEntity().setAttackTarget((EntityLivingBase) target);
                if (target instanceof EntityLiving) ((EntityLiving) target).setAttackTarget(pokemob.getEntity());
                IPokemob targ = CapabilityPokemob.getPokemobFor(target);
                if (targ != null) targ.setCombatState(CombatStates.ANGRY, true);
                // Checks if within range
                float dist = target.getDistance(pokemob.getEntity());
                double range = (move.getAttackCategory() & IMoveConstants.CATEGORY_DISTANCE) > 0
                        ? PokecubeMod.core.getConfig().rangedAttackDistance
                        : PokecubeMod.core.getConfig().contactAttackDistance;
                range = Math.max(pokemob.getMobSizes().x, range);
                range = Math.max(1, range);
                if (dist < range)
                {
                    pokemob.executeMove(target, Vector3.getNewVector().set(target), dist);
                }
            }
        }
    }
}
