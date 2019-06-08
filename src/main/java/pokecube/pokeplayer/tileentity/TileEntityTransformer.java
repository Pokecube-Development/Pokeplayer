package pokecube.pokeplayer.tileentity;

import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;

import net.minecraft.client.renderer.texture.ITickable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.WorldServer;
import pokecube.core.blocks.TileEntityOwnable;
import pokecube.core.database.Database;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.utils.Tools;
import pokecube.pokeplayer.EventsHandler;
import pokecube.pokeplayer.PokeInfo;
import pokecube.pokeplayer.PokePlayer;
import pokecube.pokeplayer.network.PacketTransform;
import thut.core.common.handlers.PlayerDataHandler;
import thut.lib.CompatWrapper;

public class TileEntityTransformer extends TileEntityOwnable implements ITickable
{
    ItemStack stack    = ItemStack.EMPTY;
    int[]     nums     = {};
    int       lvl      = 5;
    boolean   random   = false;
    boolean   pubby    = false;
    int       stepTick = 20;

    public ItemStack getStack(ItemStack stack)
    {
        return stack;
    }

    public void onInteract(PlayerEntity player)
    {
        if (getWorld().isRemote || random) return;
        if (canEdit(player) || pubby)
        {
            if (!CompatWrapper.isValid(stack) && PokecubeManager.isFilled(player.getHeldItemMainhand()))
            {
                setStack(player.getHeldItemMainhand());
                player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
            }
            else
            {
                Tools.giveItem(player, stack);
                stack = ItemStack.EMPTY;
            }
        }
    }

    public void onStepped(PlayerEntity player)
    {
        if (getWorld().isRemote || stepTick > 0) return;
        PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        boolean isPokemob = info.getPokemob(getWorld()) != null;
        if ((CompatWrapper.isValid(stack) || random) && !isPokemob)
        {
            IPokemob pokemob = getPokemob();
            if (pokemob != null) PokePlayer.PROXY.setPokemob(player, pokemob);
            if (pokemob != null)
            {
                stack = ItemStack.EMPTY;
                stepTick = 50;
            }
            EventsHandler.sendUpdate(player);
            WorldServer world = (WorldServer) player.getEntityWorld();
            for (PlayerEntity player2 : world.getEntityTracker().getTrackingPlayers(player))
            {
                PacketTransform.sendPacket(player, (ServerPlayerEntity) player2);
            }
            return;
        }
        else if (!CompatWrapper.isValid(stack) && !random && isPokemob)
        {
            stepTick = 50;
            IPokemob poke = PokePlayer.PROXY.getPokemob(player);
            CompoundNBT tag = poke.getEntity().getEntityData();
            poke.setPokemonNickname(tag.getString("oldName"));
            tag.remove("oldName");
            tag.remove("isPlayer");
            tag.remove("playerID");
            ItemStack pokemob = PokecubeManager.pokemobToItem(poke);
            if (player.capabilities.isFlying)
            {
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
            }
            PokePlayer.PROXY.setPokemob(player, null);
            stack = pokemob;
            EventsHandler.sendUpdate(player);
            WorldServer world = (WorldServer) player.getEntityWorld();
            for (PlayerEntity player2 : world.getEntityTracker().getTrackingPlayers(player))
            {
                PacketTransform.sendPacket(player, (ServerPlayerEntity) player2);
            }
            return;
        }
        else if (random && isPokemob)
        {
            stepTick = 50;
            IPokemob poke = PokePlayer.PROXY.getPokemob(player);
            CompoundNBT tag = poke.getEntity().getEntityData();
            poke.setPokemonNickname(tag.getString("oldName"));
            tag.remove("oldName");
            tag.remove("isPlayer");
            tag.remove("playerID");
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            PokePlayer.PROXY.setPokemob(player, null);
            stack = ItemStack.EMPTY;
            EventsHandler.sendUpdate(player);
            WorldServer world = (WorldServer) player.getEntityWorld();
            for (PlayerEntity player2 : world.getEntityTracker().getTrackingPlayers(player))
            {
                PacketTransform.sendPacket(player, (ServerPlayerEntity) player2);
            }
            return;
        }
    }

    private IPokemob getPokemob()
    {
        if (random)
        {
            int num = 0;
            if (nums != null && nums.length > 0)
            {
                num = nums[new Random().nextInt(nums.length)];
            }
            else
            {
                List<Integer> numbers = Lists.newArrayList(Database.data.keySet());
                num = numbers.get(getWorld().rand.nextInt(numbers.size()));
            }
            Entity entity = PokecubeMod.core.createPokemob(Database.getEntry(num), getWorld());
            IPokemob pokemob = CapabilityPokemob.getPokemobFor(entity);
            if (entity != null)
            {
                pokemob.setForSpawn(Tools.levelToXp(pokemob.getPokedexEntry().getEvolutionMode(), lvl), false);
                pokemob.specificSpawnInit();
            }
            return pokemob;
        }
        IPokemob pokemob = PokecubeManager.itemToPokemob(stack, getWorld());
        return pokemob;
    }

    @Override
    public void readFromNBT(CompoundNBT tagCompound)
    {
        super.readFromNBT(tagCompound);
        if (tagCompound.hasKey("stack"))
        {
            CompoundNBT tag = tagCompound.getCompound("stack");
            stack = new ItemStack(tag);
        }
        if (tagCompound.hasKey("nums")) nums = tagCompound.getIntArray("nums");
        if (tagCompound.hasKey("lvl")) lvl = tagCompound.getInteger("lvl");
        stepTick = tagCompound.getInteger("stepTick");
        random = tagCompound.getBoolean("random");
        pubby = tagCompound.getBoolean("public");
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack;
    }

    @Override
    public CompoundNBT writeToNBT(CompoundNBT tagCompound)
    {
        super.writeToNBT(tagCompound);
        if (CompatWrapper.isValid(stack))
        {
            CompoundNBT tag = new CompoundNBT();
            stack.writeToNBT(tag);
            tagCompound.setTag("stack", tag);
        }
        if (nums != null) tagCompound.putIntArray("nums", nums);
        tagCompound.setInteger("lvl", lvl);
        tagCompound.setInteger("stepTick", stepTick);
        tagCompound.putBoolean("random", random);
        tagCompound.putBoolean("public", pubby);
        return tagCompound;
    }

    @Override
    public void update()
    {
        stepTick--;
    }

}
