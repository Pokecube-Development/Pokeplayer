package pokecube.pokeplayer;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import pokecube.core.PokecubeCore;
import pokecube.core.PokecubeItems;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.pokemob.IHasCommands;
import pokecube.core.interfaces.pokemob.IHasCommands.Command;
import pokecube.core.interfaces.pokemob.commandhandlers.ChangeFormHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.MoveIndexHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.SwapMovesHandler;
import pokecube.core.interfaces.pokemob.commandhandlers.TeleportHandler;
import pokecube.core.network.EntityProvider;
import pokecube.pokeplayer.block.BlockTransformer;
import pokecube.pokeplayer.network.EntityProviderPokeplayer;
import pokecube.pokeplayer.network.PacketTransform;
import pokecube.pokeplayer.network.handlers.AttackEntityHandler;
import pokecube.pokeplayer.network.handlers.AttackLocationHandler;
import pokecube.pokeplayer.network.handlers.AttackNothingHandler;
import pokecube.pokeplayer.network.handlers.StanceHandler;
import pokecube.pokeplayer.tileentity.TileEntityTransformer;
import thut.core.common.handlers.PlayerDataHandler;

@Mod( // @formatter:off
        modid = PokePlayer.ID, 
        name = Reference.NAME, 
        version = PokePlayer.VERSION, 
        dependencies = PokePlayer.DEPSTRING
        )// @formatter:on
public class PokePlayer
{
    public static final String ID          = Reference.ID;
    public static final String VERSION     = Reference.VERSION;
    public final static String DEPSTRING   = Reference.DEPSTRING;
    public final static String UPDATEURL   = Reference.UPDATEURL;

    @SidedProxy(clientSide = Reference.CLIENTPROXY, serverSide = Reference.SERVERPROXY)
    public static Proxy        PROXY;

    @Instance(ID)
    public static PokePlayer   INSTANCE;

    static Block               transformer = new BlockTransformer()
            .setCreativeTab(PokecubeMod.creativeTabPokecubeBlocks).setUnlocalizedName("poketransformer")
            .setRegistryName(ID, "poketransformer");

    public PokePlayer()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void load(FMLInitializationEvent evt)
    {
        new EventsHandler(PROXY);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, PROXY);
        PlayerDataHandler.dataMap.add(PokeInfo.class);
        PROXY.init();
        PokecubeMod.packetPipeline.registerMessage(PacketTransform.class, PacketTransform.class,
                PokecubeCore.getMessageID(), Side.CLIENT);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e)
    {
        PROXY.postInit();
        PokecubeMod.core
                .setEntityProvider(new EntityProviderPokeplayer((EntityProvider) PokecubeMod.core.getEntityProvider()));

        IHasCommands.COMMANDHANDLERS.put(Command.ATTACKENTITY, AttackEntityHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.ATTACKLOCATION, AttackLocationHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.ATTACKNOTHING, AttackNothingHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.CHANGEFORM, ChangeFormHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.CHANGEMOVEINDEX, MoveIndexHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.STANCE, StanceHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.SWAPMOVES, SwapMovesHandler.class);
        IHasCommands.COMMANDHANDLERS.put(Command.TELEPORT, TeleportHandler.class);
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> evt)
    {
        PokecubeItems.register(transformer, evt.getRegistry());
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> evt)
    {
        Object registry = evt.getRegistry();
        PokecubeItems.register(new ItemBlock(transformer).setRegistryName(transformer.getRegistryName()), registry);
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT)
        {
            PokecubeItems.registerItemTexture(Item.getItemFromBlock(transformer), 0,
                    new ModelResourceLocation("pokeplayer:poketransformer", "inventory"));
        }
    }

    @SubscribeEvent
    public void registerTiles(RegistryEvent.Register<Block> evt)
    {
        GameRegistry.registerTileEntity(TileEntityTransformer.class, new ResourceLocation(ID, "poketransformer"));
    }
}
