package pokecube.pokeplayer.tileentity;

import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IClearable;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import pokecube.core.PokecubeCore;
import pokecube.core.database.Database;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.utils.Tools;
import pokecube.pokeplayer.EventsHandler;
import pokecube.pokeplayer.PokeInfo;
import pokecube.pokeplayer.Reference;
import pokecube.pokeplayer.block.PokeTransformContainer;
import pokecube.pokeplayer.init.TileEntityInit;
import pokecube.pokeplayer.network.PacketTransform;
import thut.core.common.handlers.PlayerDataHandler;

public class TileEntityTransformer extends LockableLootTileEntity implements IClearable, INamedContainerProvider
{
    protected NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    int[]   nums     = {};
    int     lvl      = 5;
    boolean random   = false;
    boolean pubby    = false;
    boolean edit     = false;
    int     stepTick = 20;

    public TileEntityTransformer(final TileEntityType<?> tileEntityType)
    {
        super(tileEntityType);
    }

    public TileEntityTransformer()
    {
        this(TileEntityInit.TRANSFORM_TILE.get());
    }

    @Override
    public void read(final BlockState state, final CompoundNBT nbt)
    {
        super.read(state, nbt);
        if (nbt.contains("stack"))
        {
            final CompoundNBT tag = nbt.getCompound("stack");
            this.items.get(0).setTag(tag);
        }
        this.items = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        if (nbt.contains("nums")) this.nums = nbt.getIntArray("nums");
        if (nbt.contains("lvl")) this.lvl = nbt.getInt("lvl");
        this.stepTick = nbt.getInt("stepTick");
        this.random = nbt.getBoolean("random");
        this.pubby = nbt.getBoolean("public");
        ItemStackHelper.loadAllItems(nbt, this.items);
    }

    @Override
    public CompoundNBT write(final CompoundNBT compound)
    {
        super.write(compound);
        ItemStackHelper.saveAllItems(compound, this.items);
        if (this.items.get(0).isEmpty())
        {
            final CompoundNBT tag = new CompoundNBT();
            this.items.get(0).write(tag);
            compound.put("stack", tag);
        }
        if (this.nums != null) compound.putIntArray("nums", this.nums);
        compound.putInt("lvl", this.lvl);
        compound.putInt("stepTick", this.stepTick);
        compound.putBoolean("random", this.random);
        compound.putBoolean("public", this.pubby);
        return compound;
    }

    @Override
    public NonNullList<ItemStack> getItems()
    {
        return this.items;
    }

    @Override
    protected void setItems(final NonNullList<ItemStack> itemsIn)
    {
        this.items = itemsIn;
    }

    @Override
    public void markDirty()
    {
        super.markDirty();
        this.world.notifyBlockUpdate(this.pos, this.getBlockState(), this.getBlockState(),
                net.minecraftforge.common.util.Constants.BlockFlags.BLOCK_UPDATE);
    }

    @Override
    protected ITextComponent getDefaultName()
    {
        return new TranslationTextComponent("container." + Reference.ID + ".transform");
    }

    @Override
    protected Container createMenu(final int id, final PlayerInventory player)
    {
        return new PokeTransformContainer(id, player, this);
    }

    @Override
    public int getSizeInventory()
    {
        return this.items.size();
    }

    @Override
    public boolean isEmpty()
    {
        for (final ItemStack stack : this.items)
            if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStackInSlot(final int index)
    {
        return this.items.get(index);
    }

    @Override
    public ItemStack decrStackSize(final int index, final int count)
    {
        return ItemStackHelper.getAndSplit(this.items, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(final int index)
    {
        return ItemStackHelper.getAndRemove(this.items, index);
    }

    @Override
    public void setInventorySlotContents(final int index, final ItemStack stack)
    {
        final ItemStack itemStack = this.items.get(index);
        final boolean flag = !stack.isEmpty() && stack.isItemEqual(itemStack) && ItemStack.areItemStackTagsEqual(stack,
                itemStack);
        this.items.set(index, stack);
        if (stack.getCount() > this.getInventoryStackLimit()) stack.setCount(this.getInventoryStackLimit());

        if (!flag) this.markDirty();
    }

    @Override
    public boolean isUsableByPlayer(final PlayerEntity player)
    {
        if (this.world.getTileEntity(this.pos) != this) return false;
        else return player.getDistanceSq(this.pos.getX() + 0.5D, this.getPos().getY() + 0.5D, this.pos.getZ()
                + 0.5D) <= 64.0D;
    }

    @Override
    public boolean isItemValidForSlot(final int index, final ItemStack stack)
    {
        return !stack.isDamaged();
    }

    @Override
    public void clear()
    {
        super.clear();
        this.items.clear();
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket()
    {
        final CompoundNBT nbt = new CompoundNBT();
        this.write(nbt);

        return new SUpdateTileEntityPacket(this.getPos(), 1, nbt);
    }

    @Override
    public void onDataPacket(final NetworkManager net, final SUpdateTileEntityPacket pkt)
    {
        final BlockState blockState = this.world.getBlockState(this.pos);
        this.read(blockState, pkt.getNbtCompound());
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Override
    public void handleUpdateTag(final BlockState state, final CompoundNBT tag)
    {
        this.read(state, tag);
    }

    public ItemStack getStack(final ItemStack stack)
    {
        return stack;
    }

    public void onWalkedOn(final Entity entityIn)
    {
        if (this.getWorld().isRemote || this.stepTick-- > 0) return;
        this.stepTick = 50;
        final PlayerEntity player = (PlayerEntity) entityIn.getEntity();
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        final boolean isPokemob = info.getPokemob(this.world) != null;

        final boolean hasPokemob = this.random || !this.getItems().get(0).isEmpty();

        if (hasPokemob && !isPokemob)
        {
            final IPokemob pokemob = this.getPokemob();
            if (pokemob != null &&  pokemob.getHealth() != 0)
            {
                PokeInfo.setPokemob(player, pokemob);
                this.items.set(0, ItemStack.EMPTY);
            }
            else
            {
                PokecubeCore.LOGGER.debug("Invalid Pokemob!", player.getDisplayName().getString());
                return;
            }
            PokecubeCore.LOGGER.debug("Converting {} to {}", player.getDisplayName().getString(), pokemob
                    .getPokedexEntry().getName());
            EventsHandler.sendUpdate(player);
            final ServerWorld worldIn = (ServerWorld) player.getEntityWorld();
            for (final PlayerEntity player2 : worldIn.getPlayers())
                PacketTransform.sendPacket(player, (ServerPlayerEntity) player2);
            return;
        }
        if (!hasPokemob && isPokemob)
        {
            final IPokemob poke = PokeInfo.getPokemob(player);
            final CompoundNBT tag = poke.getEntity().serializeNBT();
            poke.setPokemonNickname(tag.getString("oldName"));
            PokecubeCore.LOGGER.debug("Converting {} back to a human", player.getDisplayName().getString());
            tag.remove("oldName");
            tag.remove("playerID");
            info.detach();
            final ItemStack pokemob = PokecubeManager.pokemobToItem(poke);
            if (player.abilities.allowFlying && !player.isCreative())
            {
                player.abilities.allowFlying = false;
                player.sendPlayerAbilities();
            }
            PokeInfo.setPokemob(player, null);
            this.items.set(0, pokemob);
            EventsHandler.sendUpdate(player);
            final ServerWorld worldIn = (ServerWorld) player.getEntityWorld();
            for (final PlayerEntity player2 : worldIn.getPlayers())
                PacketTransform.sendPacket(player, (ServerPlayerEntity) player2);
            return;
        }
        PokecubeCore.LOGGER.debug("Nothing happened to {}", player.getDisplayName().getString());
    }

    private IPokemob getPokemob()
    {
        if (this.random)
        {
            int num = 0;
            if (this.nums != null && this.nums.length > 0) num = this.nums[new Random().nextInt(this.nums.length)];
            else
            {
                final List<Integer> numbers = Lists.newArrayList(Database.data.keySet());
                num = numbers.get(this.getWorld().rand.nextInt(numbers.size()));
            }
            final Entity entity = PokecubeCore.createPokemob(Database.getEntry(num), this.getWorld());
            final IPokemob pokemob = CapabilityPokemob.getPokemobFor(entity);
            if (entity != null)
            {
                pokemob.setForSpawn(Tools.levelToXp(pokemob.getPokedexEntry().getEvolutionMode(), this.lvl), false);
                pokemob.spawnInit();
            }
            return pokemob;
        }
        final IPokemob pokemob = PokecubeManager.itemToPokemob(this.items.get(0), this.getWorld());
        return pokemob;
    }
}