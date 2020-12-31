package pokecube.pokeplayer.network.handlers;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import pokecube.core.PokecubeCore;
import pokecube.core.events.pokemob.combat.CommandAttackEvent;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.Move_Base;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.core.interfaces.pokemob.commandhandlers.AttackLocationHandler;
import pokecube.core.moves.MovesUtils;

// Wrapper to ensure player attacks entity as pokeplayer
public class AttackLocation extends AttackLocationHandler
{
	@Override
    public void handleCommand(final IPokemob pokemob)
    {
        // Use default handling, which just agros stuff.
        if (!pokemob.getEntity().getPersistentData().getBoolean("is_a_player"))
        {
            super.handleCommand(pokemob);
            return;
        }
        final int currentMove = pokemob.getMoveIndex();
        final CommandAttackEvent evt = new CommandAttackEvent(pokemob.getEntity(), null);
        PokecubeCore.POKEMOB_BUS.post(evt);

        if (!evt.isCanceled() && currentMove != 5 && MovesUtils.canUseMove(pokemob))
        {
            pokemob.setCombatState(CombatStates.EXECUTINGMOVE, false);
            pokemob.setCombatState(CombatStates.NOITEMUSE, false);
            final Move_Base move = MovesUtils.getMoveFromName(pokemob.getMoves()[currentMove]);
            // Send move use message first.
            ITextComponent mess = new TranslationTextComponent("pokemob.action.usemove", pokemob.getDisplayName(),
                    new TranslationTextComponent(MovesUtils.getUnlocalizedMove(move.getName())));
            if (this.fromOwner()) pokemob.displayMessageToOwner(mess);

            // If too hungry, send message about that.
            if (pokemob.getHungerTime() > 0)
            {
                mess = new TranslationTextComponent("pokemob.action.hungry", pokemob.getDisplayName());
                if (this.fromOwner()) pokemob.displayMessageToOwner(mess);
                return;
            }
            // Otherwise set the location for execution of move.
            pokemob.executeMove(null, this.location, 0);
        }
    }
}

//	Vector3 location;
//
//    public AttackLocation()
//    {
//    }
//
//    public AttackLocation(final Vector3 location)
//    {
//        this.location = location.copy();
//    }
//
//    @Override
//    public void handleCommand(IPokemob pokemob)
//    {
//        if (pokemob.getEntity().getPersistentData().getBoolean("is_a_player"))
//        {
//        	PokecubeCore.LOGGER.debug("Location");
//        	
//        	final int currentMove = pokemob.getMoveIndex();
//            final CommandAttackEvent evt = new CommandAttackEvent(pokemob.getEntity(), null);
//            PokecubeCore.POKEMOB_BUS.post(evt);
//
//            pokemob.setCombatState(CombatStates.EXECUTINGMOVE, false);
//            pokemob.setCombatState(CombatStates.NOITEMUSE, false);
//            final Move_Base move = MovesUtils.getMoveFromName(pokemob.getMoves()[currentMove]);
//            
//            PokecubeCore.LOGGER.debug("Confirm Location");
//            // Send move use message first.
//            ITextComponent mess = new TranslationTextComponent("pokemob.action.usemove",
//                    pokemob.getDisplayName(),
//                    new TranslationTextComponent(MovesUtils.getUnlocalizedMove(move.getName())));
//            if (fromOwner()) pokemob.displayMessageToOwner(mess);
//
//            final float value = HungerTask.calculateHunger(pokemob);
//            
//            // If too hungry, send message about that.
//            if (HungerTask.hitThreshold(value, HungerTask.HUNTTHRESHOLD))
//            {
//                mess = new TranslationTextComponent("pokemob.action.hungry", pokemob.getDisplayName());
//                if (this.fromOwner()) pokemob.displayMessageToOwner(mess);
//                return;
//            }
//
//            PokecubeCore.LOGGER.debug("Execute Location");
//            // Otherwise set the location for execution of move.
//            pokemob.executeMove(null, location, 0);
//        }
//        else
//        {
//            // Do default behaviour.
//            AttackLocationHandler defaults = new AttackLocationHandler();
//            ByteBuf buffer = Unpooled.buffer();
//            this.location.writeToBuff(buffer);
//            this.writeToBuf(buffer);
//            defaults.readFromBuf(buffer);
//            defaults.handleCommand(pokemob);
//        }
//    }
//    
//    @Override
//    public void readFromBuf(ByteBuf buf)
//    {
//        super.readFromBuf(buf);
//        this.location = Vector3.readFromBuff(buf);
//    }
//
//    @Override
//    public void writeToBuf(ByteBuf buf)
//    {
//        super.writeToBuf(buf);
//        this.location.writeToBuff(buf);
//    }
//}
