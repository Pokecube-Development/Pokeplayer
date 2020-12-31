package pokecube.pokeplayer;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import pokecube.core.PokecubeCore;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.pokemob.ai.GeneralStates;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.network.packets.PacketDataSync;
import pokecube.core.utils.EntityTools;
import pokecube.core.utils.PokeType;
import pokecube.pokeplayer.inventory.InventoryPlayerPokemob;
import pokecube.pokeplayer.network.DataSyncWrapper;
import pokecube.pokeplayer.network.PacketTransform;
import thut.api.world.mobs.data.Data;
import thut.api.world.mobs.data.DataSync;
import thut.core.common.handlers.PlayerDataHandler;
import thut.core.common.handlers.PlayerDataHandler.PlayerData;
import thut.core.common.world.mobs.data.SyncHandler;

public class PokeInfo extends PlayerData
{
    private ItemStack stack    = ItemStack.EMPTY;
    private IPokemob  pokemob;
    private boolean   attached = false;

    public DamageSource           lastDamage = null;
    public InventoryPlayerPokemob pokeInventory;
    public float                  originalHeight;
    public float                  originalWidth;
    public float                  originalHP;

    public PokeInfo()
    {
    }

    public void set(final IPokemob pokemob, final PlayerEntity player)
    {
        if (this.pokemob != null || pokemob == null) this.resetPlayer(player);
        if (pokemob == null || this.pokemob == pokemob) return;
        if (this.attached) return;
        this.stack = PokecubeManager.pokemobToItem(pokemob);
        this.pokemob = pokemob;
        this.pokeInventory = new InventoryPlayerPokemob(this, player.getEntityWorld());
        this.originalHeight = player.getHeight();
        this.originalWidth = player.getWidth();
        this.originalHP = player.getMaxHealth();
        pokemob.getEntity().setWorld(player.getEntityWorld());
        pokemob.getEntity().getPersistentData().putBoolean("is_a_player", true);
        pokemob.getEntity().getPersistentData().putString("playerID", player.getUniqueID().toString());
        pokemob.getEntity().getPersistentData().putString("oldName", pokemob.getPokemonNickname());
        pokemob.setPokemonNickname(player.getDisplayName().getString());
        pokemob.setOwner(player);
        pokemob.initAI();
        player.recalculateSize();
        final DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper) ((DataSyncWrapper) sync).wrapped = this.pokemob.dataSync();
        if (player instanceof ServerPlayerEntity) PacketDataSync.sendInitPacket(player, this.getIdentifier());
        this.save(player);
    }

    public void resetPlayer(final PlayerEntity player)
    {
        final DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper) ((DataSyncWrapper) sync).wrapped = sync;
        if (this.pokemob == null && !player.getEntityWorld().isRemote) return;
        player.getEyeHeight();
        player.recalculateSize();
        this.setFlying(player, false);
        this.pokemob = null;
        this.stack = ItemStack.EMPTY;
        this.pokeInventory = null;
        this.save(player);
        if (!player.getEntityWorld().isRemote) EventsHandler.sendUpdate(player);
    }

    public void setPlayer(final PlayerEntity player)
    {
        if (this.pokemob == null) return;
        final DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper) ((DataSyncWrapper) sync).wrapped = this.pokemob.dataSync();
        this.pokemob.setSize((float) (this.pokemob.getSize() / PokecubeCore.getConfig().scalefactor));
        player.stepHeight = this.pokemob.getEntity().getEyeHeight();
        this.setFlying(player, true);
        this.save(player);
        if (!player.getEntityWorld().isRemote)
        {
            EventsHandler.sendUpdate(player);
            ((ServerPlayerEntity) player).sendAllContents(player.container, player.container.inventoryItemStacks);
            // // Fixes the inventories appearing to vanish
            player.getPersistentData().putLong("_pokeplayer_evolved_", player.getEntityWorld().getGameTime() + 50);
        }
    }

    public void postPlayerTick(final PlayerEntity player)
    {
        if (this.pokemob == null) return;
        player.stepHeight = this.pokemob.getEntity().stepHeight;
    }

    public void onUpdate(final PlayerEntity player, final World world)
    {
        if (this.getPokemob(world) == null && !this.stack.isEmpty()) this.resetPlayer(player);
        if (this.pokemob == null) return;
        final MobEntity poke = this.pokemob.getEntity();

        final float eye = poke.getEyeHeight(Pose.STANDING);
        if (eye != player.getEyeHeight()) player.recalculateSize();

        // Flag the data sync dirty every so often to ensure things stay synced.
        if (poke.ticksExisted % 20 == 0) for (final Data<?> d : this.pokemob.dataSync().getAll())
            d.setDirty(true);

        // Ensure it is tamed.
        this.pokemob.setGeneralState(GeneralStates.TAMED, true);
        // No Stay mode for pokeplayers.
        this.pokemob.setGeneralState(GeneralStates.STAYING, false);
        // Update the mob.
        // Ensure the mob has correct world.
        poke.setWorld(player.getEntityWorld());
        poke.addedToChunk = true;
        // No clip to prevent collision effects from the mob itself.
        poke.noClip = true;

        // Update location
        poke.distanceWalkedModified = Integer.MAX_VALUE;
        EntityTools.copyEntityTransforms(poke, player);

        // Deal with health
        if (player.abilities.isCreativeMode)
        {
            poke.setHealth(poke.getMaxHealth());
            this.pokemob.setHungerTime(-PokecubeCore.getConfig().pokemobLifeSpan / 4);
        }
        
        float health = poke.getHealth();
        // do not manage hp for creative mode players.
        if (!player.abilities.isCreativeMode) {
        	if (player instanceof ServerPlayerEntity && player.addedToChunk)
	        {
        		setFlying(player, true);
        		
	            float playerHealth = player.getHealth();
	            
	            /** Player has healed somehow, this is fine. */
	            if (playerHealth > health && this.lastDamage == null && health > 0 && playerHealth <= poke.getMaxHealth()) 
	            {
	                if (poke.getAttackTarget() == null) {
	                	health = playerHealth;
	                }else { 
	                	playerHealth = health;
	                }
	            }
	
//	            PokecubeCore.LOGGER.debug("Damage Player:" + healthP + "    Poke:" + playerHealth);
	            
	            /**
	             * If this is going to kill the player, do it with an attack, as
	             * this will properly kill the player.
	             */
	            //dano aleatório aki
//	            if (health < playerHealth)
//	            {
//	                final DamageSource source = this.lastDamage == null ? DamageSource.GENERIC : this.lastDamage;
//	                final float amount = playerHealth - health;
//	                source.setDamageBypassesArmor().setDamageIsAbsolute();
//	                player.attackEntityFrom(source, amount);
//	            }
//	            else {
//	            	player.setHealth(health);
//	            }
	
	            // Sync pokehealth to player health.
	            playerHealth = player.getHealth();
	            poke.setHealth(playerHealth);
	
	            this.lastDamage = null;
	
	            health = playerHealth;
	            
	//            PokecubeCore.LOGGER.debug("Info Player:" + health + "Poke:" + playerHealth);
	
	            final PacketTransform packet = new PacketTransform();
	            packet.id = player.getEntityId();
	            packet.getTag().putBoolean("U", true);
	            packet.getTag().putFloat("H", health);
	            packet.getTag().putFloat("M", poke.getMaxHealth());
//	            PacketTransform.sendPacket(player, (ServerPlayerEntity) player);
	
	            // Fixes the inventories appearing to vanish
	            if (player.getPersistentData().contains("_pokeplayer_evolved_") && player.getPersistentData().getLong(
	                    "_pokeplayer_evolved_") > player.getEntityWorld().getGameTime()) ((ServerPlayerEntity) player)
	                            .sendAllContents(player.container, player.container.inventoryItemStacks);
	            else player.getPersistentData().remove("_pokeplayer_evolved_");
	        }
    	}
	        
        if (player.getHealth() > 0) player.deathTime = -1;
        poke.deathTime = player.deathTime;

        int num = this.pokemob.getHungerTime();
        final int max = PokecubeCore.getConfig().pokemobLifeSpan;
        num = Math.round((max - num) * 20 / (float) max);
        if (player.isCreative()) {
        	num = 20;
        }
        player.getFoodStats().setFoodLevel(num);

        this.updateFloating(player);
        this.updateFlying(player);
        this.updateSwimming(player);

        // Synchronize the hitbox locations
        poke.setPosition(player.getPosX(), player.getPosY(), player.getPosZ());
    }

    public void clear()
    {
        this.pokemob = null;
        this.pokeInventory = null;
        this.stack = ItemStack.EMPTY;
    }

    public void save(final PlayerEntity player)
    {
        if (!player.getEntityWorld().isRemote) PlayerDataHandler.getInstance().save(player.getCachedUniqueIdString(),
                this.getIdentifier());
    }

    private void setFlying(final PlayerEntity player, final boolean set)
    {
        if (this.pokemob == null) return;
        final boolean fly = this.pokemob.floats() || this.pokemob.flys();
        if (fly && !player.abilities.isCreativeMode)
        {
            player.abilities.allowFlying = set;
            player.sendPlayerAbilities();
        }
    }

    private void updateFlying(final PlayerEntity player)
    {
        if (this.pokemob == null) return;
        if (this.pokemob.floats() || this.pokemob.flys())
        {
            player.fallDistance = 0;
            if (player instanceof ServerPlayerEntity) ((ServerPlayerEntity) player).connection.floatingTickCount = 0;
        }
    }

    private void updateFloating(final PlayerEntity player)
    {
        if (this.pokemob == null) return;
        if (!player.isSneaking() && this.pokemob.floats() && !player.isElytraFlying())
        {
            // TODO fix floating effects
        }
    }

    private void updateSwimming(final PlayerEntity player)
    {
        if (this.pokemob == null) return;
        if (this.pokemob.getPokedexEntry().swims() || this.pokemob.isType(PokeType.getType("water"))) { 
        	player.setAir(300);
        	
        }
    }

    public ItemStack detach()
    {
        this.attached = false;
        if (this.pokemob == null) return ItemStack.EMPTY;
        this.pokemob.getEntity().getPersistentData().putBoolean("is_a_player", false);
        return PokecubeManager.pokemobToItem(this.pokemob);
    }

    public void setStack(final ItemStack stack)
    {
        this.stack = stack;
    }

    @Override
    public String dataFileName()
    {
        return "pokeplayer";
    }

    @Override
    public String getIdentifier()
    {
        return "pokeplayer-data";
    }

    @Override
    public boolean shouldSync()
    {
        return false;
    }

    @Override
    public void writeToNBT(final CompoundNBT tag)
    {
        if (this.pokemob != null)
        {
            this.stack = PokecubeManager.pokemobToItem(this.pokemob);
            this.stack.write(tag);
        }
        else if (!this.stack.isEmpty()) this.stack.write(tag);
        tag.putFloat("h", this.originalHeight);
        tag.putFloat("w", this.originalWidth);
        tag.putFloat("hp", this.originalHP);
    }

    @Override
    public void readFromNBT(final CompoundNBT tag)
    {
        this.stack = ItemStack.read(tag);
        this.originalHeight = tag.getFloat("h");
        this.originalWidth = tag.getFloat("w");
        this.originalHP = tag.getFloat("hp");
        if (this.originalHP <= 1) this.originalHP = 20;
    }

    public IPokemob getPokemob(final World world)
    {
        if (this.pokemob == null && !this.stack.isEmpty())
        {
            this.pokemob = PokecubeManager.itemToPokemob(this.stack, world);
            if (this.pokemob == null) this.stack = ItemStack.EMPTY;
        }
        return this.pokemob;
    }

    public static void setPokemob(final PlayerEntity player, final IPokemob pokemob)
    {
        PokeInfo.setMapping(player, pokemob);
    }

    public static void savePokemob(final PlayerEntity player)
    {
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        if (info != null) info.save(player);
    }

    private static void setMapping(final PlayerEntity player, final IPokemob pokemob)
    {
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        info.set(pokemob, player);
        if (pokemob != null)
        {
            info.setPlayer(player);
            info.save(player);
        }
    }

    public static IPokemob getPokemob(final PlayerEntity player)
    {
        if (player == null || player.getUniqueID() == null) return null;
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        return info.getPokemob(player.getEntityWorld());
    }

    public static void updateInfo(final PlayerEntity player, final World world)
    {
        final PokeInfo info = PlayerDataHandler.getInstance().getPlayerData(player).getData(PokeInfo.class);
        try
        {
            info.onUpdate(player, world);
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }
    }
}
