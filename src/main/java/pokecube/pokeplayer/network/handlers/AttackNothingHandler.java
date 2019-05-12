package pokecube.pokeplayer.network.handlers;

import pokecube.core.interfaces.IPokemob;

// Wrapper to ensure player attacks entity as pokeplayer
public class AttackNothingHandler extends pokecube.core.interfaces.pokemob.commandhandlers.AttackNothingHandler
{
    @Override
    public void handleCommand(IPokemob pokemob)
    {
        super.handleCommand(pokemob);
    }
}
