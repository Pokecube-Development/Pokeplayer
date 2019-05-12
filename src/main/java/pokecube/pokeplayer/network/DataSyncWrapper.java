package pokecube.pokeplayer.network;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import thut.api.world.mobs.data.DataSync;
import thut.core.common.world.mobs.data.DataSync_Impl;
import thut.core.common.world.mobs.data.SyncHandler;

public class DataSyncWrapper extends DataSync_Impl
{
    public DataSync wrapped = this;

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing)
    {
        return capability == SyncHandler.CAP;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        return hasCapability(capability, facing) ? SyncHandler.CAP.cast(wrapped) : null;
    }
}
