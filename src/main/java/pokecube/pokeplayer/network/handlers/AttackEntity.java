package pokecube.pokeplayer.network.handlers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import pokecube.core.PokecubeCore;
import pokecube.core.ai.brain.BrainUtils;
import pokecube.core.events.pokemob.combat.CommandAttackEvent;
import pokecube.core.interfaces.IMoveConstants;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.Move_Base;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackEntityHandler;
import pokecube.core.moves.MovesUtils;
import thut.api.maths.Vector3;

// Wrapper to ensure player attacks entity as pokeplayer
public class AttackEntity extends AttackEntityHandler 
{
    @Override
    public void handleCommand(final IPokemob pokemob)
    {
        // Use default handling, which just agros stuff.
        if (!pokemob.getEntity().getPersistentData().getBoolean("is_a_player"))
        {
        	PokecubeCore.LOGGER.debug("Talvez");
            super.handleCommand(pokemob);
            return;
        }

        // Actually execute the move if needed.
        final World world = pokemob.getEntity().getEntityWorld();
        final Entity target = PokecubeCore.getEntityProvider().getEntity(world, this.targetId, true);
        final Entity real = PokecubeCore.getEntityProvider().getEntity(world, this.targetId, false);
        if (target == null || !(target instanceof LivingEntity)) return;
        final int currentMove = pokemob.getMoveIndex();
        final CommandAttackEvent event = new CommandAttackEvent(pokemob.getEntity(), target);
        MinecraftForge.EVENT_BUS.post(event);
        PokecubeCore.LOGGER.debug("Start");
        PokecubeCore.LOGGER.debug("Event: " + event.isCanceled() + " Move: " + currentMove + " Poke: " + MovesUtils.canUseMove(pokemob));
        if (currentMove != 5)
        {
            final Move_Base move = MovesUtils.getMoveFromName(pokemob.getMoves()[currentMove]);
            pokemob.setCombatState(CombatStates.EXECUTINGMOVE, false);
            pokemob.setCombatState(CombatStates.NOITEMUSE, false);
            PokecubeCore.LOGGER.debug("Test");
            if (move.isSelfMove()) {
            	pokemob.executeMove(pokemob.getEntity(), null, 0);
            }
            else
            {
                pokemob.getEntity().setAttackTarget((LivingEntity) target);
                if (target instanceof MobEntity) {
                	BrainUtils.initiateCombat((MobEntity) target, (LivingEntity) real);
                	PokecubeCore.LOGGER.debug("InitCombat");
                }
                
                final IPokemob targ = CapabilityPokemob.getPokemobFor(target);
                if (targ != null) {
                	targ.setCombatState(CombatStates.ANGRY, true);
                	PokecubeCore.LOGGER.debug("Target");
                }
                // Checks if within range
                final float dist = target.getDistance(pokemob.getEntity());
                double range = (move.getAttackCategory() & IMoveConstants.CATEGORY_DISTANCE) > 0 ? PokecubeCore
                        .getConfig().rangedAttackDistance : PokecubeCore.getConfig().contactAttackDistance;
                range = Math.max(pokemob.getMobSizes().x, range);
                range = Math.max(1, range);
                PokecubeCore.LOGGER.debug("Test Range");
                if (dist < range) {
                	pokemob.executeMove(target, Vector3.getNewVector().set(target), dist);
                	PokecubeCore.LOGGER.debug("Move");
                }
            }
        }
    }
}
//	@Override
//	public void handleCommand(IPokemob pokemob) 
//	{
//		// Use default handling, which just agros stuff.
//		if (pokemob.getEntity().getPersistentData().getBoolean("is_a_player")) 
//		{
//
//			// Actually execute the move if needed.
//			PokecubeCore.LOGGER.debug("Attack entity");
//			World world = pokemob.getEntity().getEntityWorld();
//			Entity target = PokecubeCore.getEntityProvider().getEntity(world, targetId, true);
//
//			if (target == null || !(target instanceof LivingEntity)) {
//				if (PokecubeMod.debug)
//					if (target == null)
//						PokecubeCore.LOGGER.error("Target Mob cannot be null!",
//								new IllegalArgumentException(pokemob.getEntity().toString()));
//					else
//						PokecubeCore.LOGGER.error("Invalid Target!",
//								new IllegalArgumentException(pokemob.getEntity() + " " + target));
//				return;
//			}
//
//			int currentMove = pokemob.getMoveIndex();
//			CommandAttackEvent event = new CommandAttackEvent(pokemob.getEntity(), target);
//			MinecraftForge.EVENT_BUS.post(event);
//			
//			pokemob.setCombatState(CombatStates.EXECUTINGMOVE, false);
//            pokemob.setCombatState(CombatStates.NOITEMUSE, false);
//            
//			final Move_Base move = MovesUtils.getMoveFromName(pokemob.getMoves()[currentMove]);
//			if (move.isSelfMove()) {
//				PokecubeCore.LOGGER.debug("Self Hit?");
//				pokemob.executeMove(pokemob.getEntity(), null, 0);
//			} else {
//				pokemob.getEntity().setAttackTarget((LivingEntity) target);
//				if (target instanceof MobEntity)
//					((MobEntity) target).setAttackTarget(pokemob.getEntity());
//				IPokemob targ = CapabilityPokemob.getPokemobFor(target);
//				if (targ != null)
//					targ.setCombatState(CombatStates.ANGRY, true);
//				// Checks if within range
//				float dist = target.getDistance(pokemob.getEntity());
//				double range = (move.getAttackCategory() & IMoveConstants.CATEGORY_DISTANCE) > 0
//						? PokecubeCore.getConfig().rangedAttackDistance
//						: PokecubeCore.getConfig().contactAttackDistance;
//				range = Math.max(pokemob.getMobSizes().x, range);
//				range = Math.max(1, range);
//				if (dist < range) {
//					pokemob.executeMove(target, Vector3.getNewVector().set(target), dist);
//				}
////				final ITextComponent mess = new TranslationTextComponent("pokemob.command.attack",
////						pokemob.getDisplayName(), target.getDisplayName(),
////						new TranslationTextComponent(MovesUtils.getUnlocalizedMove(move.getName())));
////				if (this.fromOwner())
////					pokemob.displayMessageToOwner(mess);
////				PokecubeCore.LOGGER.debug("acho q atacou...");
//////				BrainUtils.initiateCombat(pokemob.getEntity(), (LivingEntity) target);
////				pokemob.executeMove(target, Vector3.getNewVector().set(target), 0);
//			}
//		}
//	}
//}