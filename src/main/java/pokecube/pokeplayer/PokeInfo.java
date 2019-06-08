package pokecube.pokeplayer;

import java.util.function.Predicate;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import pokecube.core.ai.thread.aiRunnables.combat.AIFindTarget;
import pokecube.core.ai.thread.aiRunnables.idle.AIHungry;
import pokecube.core.ai.thread.aiRunnables.idle.AIMate;
import pokecube.core.interfaces.IMoveConstants;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.AICapWrapper;
import pokecube.core.interfaces.pokemob.ai.GeneralStates;
import pokecube.core.interfaces.pokemob.ai.LogicStates;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.utils.EntityTools;
import pokecube.core.utils.PokeType;
import pokecube.pokeplayer.inventory.InventoryPlayerPokemob;
import pokecube.pokeplayer.network.DataSyncWrapper;
import pokecube.pokeplayer.network.PacketTransform;
import thut.api.entity.ai.IAIMob;
import thut.api.entity.ai.IAIRunnable;
import thut.api.world.mobs.data.Data;
import thut.api.world.mobs.data.DataSync;
import thut.core.common.handlers.PlayerDataHandler;
import thut.core.common.handlers.PlayerDataHandler.PlayerData;
import thut.core.common.world.mobs.data.SyncHandler;

public class PokeInfo extends PlayerData
{
    private ItemStack             stack;
    private IPokemob              pokemob;
    public DamageSource           lastDamage = null;
    public InventoryPlayerPokemob pokeInventory;
    public float                  originalHeight;
    public float                  originalWidth;
    public float                  originalHP;

    public PokeInfo()
    {
    }

    public void set(IPokemob pokemob, PlayerEntity player)
    {
        if (this.pokemob != null || pokemob == null) resetPlayer(player);
        if (pokemob == null || this.pokemob == pokemob) return;
        this.pokemob = pokemob;
        this.pokeInventory = new InventoryPlayerPokemob(this, player.getEntityWorld());
        this.originalHeight = player.height;
        this.originalWidth = player.width;
        this.originalHP = player.getMaxHealth();
        pokemob.getEntity().setWorld(player.getEntityWorld());
        pokemob.getEntity().getEntityData().putBoolean("isPlayer", true);
        pokemob.getEntity().getEntityData().putString("playerID", player.getUniqueID().toString());
        pokemob.getEntity().getEntityData().putString("oldName", pokemob.getPokemonNickname());
        pokemob.setPokemonNickname(player.getDisplayNameString());
        pokemob.setPokemonOwner(player);
        pokemob.initAI();
        IAIMob ai = pokemob.getEntity().getCapability(IAIMob.THUTMOBAI, null);
        if (ai instanceof AICapWrapper)
        {
            ((AICapWrapper) ai).init();
        }
        DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper)
        {
            ((DataSyncWrapper) sync).wrapped = pokemob.dataSync();
        }
        save(player);
    }

    public void resetPlayer(PlayerEntity player)
    {
        DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper)
        {
            ((DataSyncWrapper) sync).wrapped = sync;
        }
        if (pokemob == null && !player.getEntityWorld().isRemote) return;
        player.eyeHeight = player.getDefaultEyeHeight();
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(originalHP);
        float height = originalHeight;
        float width = originalWidth;
        if (player.height != height)
        {
            player.firstUpdate = true;
            player.setSize(width, height);
            player.firstUpdate = false;
        }
        setFlying(player, false);
        pokemob = null;
        stack = null;
        pokeInventory = null;
        save(player);
        if (!player.getEntityWorld().isRemote)
        {
            EventsHandler.sendUpdate(player);
        }
    }

    public void setPlayer(PlayerEntity player)
    {
        if (pokemob == null) return;
        DataSync sync = SyncHandler.getData(player);
        if (sync instanceof DataSyncWrapper)
        {
            ((DataSyncWrapper) sync).wrapped = pokemob.dataSync();
        }
        pokemob.setSize((float) (pokemob.getSize() / PokecubeMod.core.getConfig().scalefactor));

        pokemob.getAI().aiTasks.removeIf(new Predicate<IAIRunnable>()
        {
            @Override
            public boolean test(IAIRunnable t)
            {
                boolean allowed = t instanceof AIHungry;
                allowed = allowed || t instanceof AIMate;
                return !allowed;
            }
        });

        float height = pokemob.getSize() * pokemob.getPokedexEntry().height;
        float width = pokemob.getSize() * pokemob.getPokedexEntry().width;
        player.eyeHeight = pokemob.getEntity().getEyeHeight();
        width = Math.min(player.width, width);
        if (player.height != height || player.width != width)
        {
            player.firstUpdate = true;
            player.setSize(width, height);
            player.firstUpdate = false;
        }
        setFlying(player, true);
        save(player);
        if (!player.getEntityWorld().isRemote)
        {
            EventsHandler.sendUpdate(player);
            ((ServerPlayerEntity) player).sendAllContents(player.inventoryContainer,
                    player.inventoryContainer.inventoryItemStacks);
            // // Fixes the inventories appearing to vanish
            player.getEntityData().putLong("_pokeplayer_evolved_", player.getEntityWorld().getGameTime() + 50);
        }
    }

    /** This fixes PlayerEntity.updateSize() resetting the size.
     * 
     * @param player */
    public void postPlayerTick(PlayerEntity player)
    {
        if (pokemob == null) return;
        float height = pokemob.getSize() * pokemob.getPokedexEntry().height;
        float width = pokemob.getSize() * pokemob.getPokedexEntry().width;
        player.eyeHeight = pokemob.getEntity().getEyeHeight();
        width = Math.min(player.width, width);
        if (player.height != height || player.width != width)
        {
            player.firstUpdate = true;
            player.setSize(width, height);
            player.firstUpdate = false;
        }
    }

    public void onUpdate(PlayerEntity player)
    {
        if (getPokemob(player.getEntityWorld()) == null && stack != null)
        {
            resetPlayer(player);
        }
        if (pokemob == null) return;
        MobEntity poke = pokemob.getEntity();

        // Fixes pokemob sometimes targetting self.
        if (poke.getAttackTarget() == player || poke.getAttackTarget() == poke)
        {
            boolean old = AIFindTarget.handleDamagedTargets;
            AIFindTarget.handleDamagedTargets = false;
            poke.setAttackTarget(null);
            pokemob.setTargetID(-1);
            AIFindTarget.handleDamagedTargets = old;
        }

        // Flag the data sync dirty every so often to ensure things stay synced.
        if (poke.ticksExisted % 20 == 0)
        {
            for (Data<?> d : pokemob.dataSync().getAll())
                d.setDirty(true);
        }

        // Ensure it is tamed.
        pokemob.setGeneralState(GeneralStates.TAMED, true);
        // No Stay mode for pokeplayers.
        pokemob.setGeneralState(GeneralStates.STAYING, false);
        // Update the mob.
        // Ensure the mob has correct world.
        poke.setWorld(player.getEntityWorld());
        poke.addedToChunk = true;
        // No clip to prevent collision effects from the mob itself.
        poke.noClip = true;

        poke.onUpdate();

        // Update location
        poke.nextStepDistance = Integer.MAX_VALUE;
        EntityTools.copyEntityTransforms(poke, player);

        // Deal with health
        if (player.capabilities.isCreativeMode)
        {
            poke.setHealth(poke.getMaxHealth());
            pokemob.setHungerTime(-PokecubeMod.core.getConfig().pokemobLifeSpan / 4);
        }
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(poke.getMaxHealth());

        float health = poke.getHealth();
        /** do not manage hp for creative mode players. */
        if (!player.capabilities.isCreativeMode) if (player instanceof ServerPlayerEntity && player.addedToChunk)
        {
            float playerHealth = player.getHealth();

            /** Player has healed somehow, this is fine. */
            if (playerHealth > health && lastDamage == null && health > 0 && playerHealth <= poke.getMaxHealth())
            {
                if (poke.getAttackTarget() == null) health = playerHealth;
                else playerHealth = health;
            }

            /** If this is going to kill the player, do it with an attack, as
             * this will properly kill the player. */
            if (health < playerHealth)
            {
                DamageSource source = lastDamage == null ? DamageSource.GENERIC : lastDamage;
                float amount = playerHealth - health;
                source.setDamageBypassesArmor().setDamageIsAbsolute();
                player.attackEntityFrom(source, amount);
            }
            else player.setHealth(health);

            // Sync pokehealth to player health.
            playerHealth = player.getHealth();
            poke.setHealth(playerHealth);

            lastDamage = null;

            health = playerHealth;

            PacketTransform packet = new PacketTransform();
            packet.id = player.getEntityId();
            packet.data.putBoolean("U", true);
            packet.data.putFloat("H", health);
            packet.data.putFloat("M", poke.getMaxHealth());
            PokecubeMod.packetPipeline.sendTo(packet, (ServerPlayerEntity) player);

            // Fixes the inventories appearing to vanish
            if (player.getEntityData().hasKey("_pokeplayer_evolved_") && player.getEntityData()
                    .getLong("_pokeplayer_evolved_") > player.getEntityWorld().getGameTime())
            {
                ((ServerPlayerEntity) player).sendAllContents(player.inventoryContainer,
                        player.inventoryContainer.inventoryItemStacks);
            }
            else player.getEntityData().remove("_pokeplayer_evolved_");
        }
        if (player.getHealth() > 0) player.deathTime = -1;
        poke.deathTime = player.deathTime;

        int num = pokemob.getHungerTime();
        int max = PokecubeMod.core.getConfig().pokemobLifeSpan;
        num = Math.round(((max - num) * 20) / (float) max);
        if (player.capabilities.isCreativeMode) num = 20;
        player.getFoodStats().setFoodLevel(num);

        updateFloating(player);
        updateFlying(player);
        updateSwimming(player);

        // Synchronize the hitbox locations
        poke.setPosition(player.posX, player.posY, player.posZ);
    }

    public void clear()
    {
        pokemob = null;
        pokeInventory = null;
        stack = null;
    }

    public void save(PlayerEntity player)
    {
        if (!player.getEntityWorld().isRemote)
            PlayerDataHandler.getInstance().save(player.getCachedUniqueIdString(), getIdentifier());
    }

    private void setFlying(PlayerEntity player, boolean set)
    {
        if (pokemob == null) return;
        boolean fly = pokemob.floats() || pokemob.flys() || !set;
        boolean check = set ? !player.capabilities.allowFlying : player.capabilities.allowFlying;
        if (fly && check && player.getEntityWorld().isRemote && !player.capabilities.isCreativeMode)
        {
            player.capabilities.allowFlying = set;
            player.sendPlayerAbilities();
        }
    }

    private void updateFlying(PlayerEntity player)
    {
        if (pokemob == null) return;
        if (pokemob.floats() || pokemob.flys())
        {
            player.fallDistance = 0;
            if (player instanceof ServerPlayerEntity) ((ServerPlayerEntity) player).connection.floatingTickCount = 0;
        }
    }

    private void updateFloating(PlayerEntity player)
    {
        if (pokemob == null) return;
        if (!player.isSneaking() && pokemob.floats() && !player.capabilities.isFlying)
        {
            double h = pokemob.getPokedexEntry().preferedHeight;
            Vec3d start = new Vec3d(player.posX, player.posY, player.posZ);
            Vec3d end = new Vec3d(player.posX, player.posY - h, player.posZ);

            RayTraceResult position = player.getEntityWorld().rayTraceBlocks(start, end, true, true, false);
            boolean noFloat = pokemob.getLogicState(LogicStates.SITTING) || pokemob.getLogicState(LogicStates.SLEEPING)
                    || pokemob.isGrounded()
                    || (pokemob.getStatus() & (IMoveConstants.STATUS_SLP + IMoveConstants.STATUS_FRZ)) > 0;

            if (position != null && !noFloat)
            {
                double d = position.hitVec.subtract(start).lengthVector();
                if (d < 0.9 * h) player.motionY += 0.1;
                else player.motionY = 0;
            }
            else if (player.motionY < 0 && !noFloat)
            {
                player.motionY *= 0.6;
            }
        }
    }

    private void updateSwimming(PlayerEntity player)
    {
        if (pokemob == null) return;
        if (pokemob.getPokedexEntry().swims() || pokemob.isType(PokeType.getType("water"))) player.setAir(300);
    }

    @Override
    public String getIdentifier()
    {
        return "pokeplayer-data";
    }

    @Override
    public String dataFileName()
    {
        return "PokePlayer";
    }

    @Override
    public boolean shouldSync()
    {
        return false;
    }

    @Override
    public void writeToNBT(CompoundNBT tag)
    {
        if (pokemob != null)
        {
            ItemStack stack = PokecubeManager.pokemobToItem(pokemob);
            stack.writeToNBT(tag);
        }
        else if (stack != null)
        {
            stack.writeToNBT(tag);
        }
        tag.putFloat("h", originalHeight);
        tag.putFloat("w", originalWidth);
        tag.putFloat("hp", originalHP);
    }

    @Override
    public void readFromNBT(CompoundNBT tag)
    {
        stack = new ItemStack(tag);
        originalHeight = tag.getFloat("h");
        originalWidth = tag.getFloat("w");
        originalHP = tag.getFloat("hp");
        if (originalHP <= 1) originalHP = 20;
    }

    public IPokemob getPokemob(World world)
    {
        if (pokemob == null && stack != null)
        {
            pokemob = PokecubeManager.itemToPokemob(stack, world);
            if (pokemob == null) stack = null;
        }
        return pokemob;
    }
}
