package com.robomwm.prettysimpleshop.shop;

import com.robomwm.prettysimpleshop.ConfigManager;
import com.robomwm.prettysimpleshop.PrettySimpleShop;
import com.robomwm.prettysimpleshop.ReflectionHandler;
import com.robomwm.prettysimpleshop.event.ShopBreakEvent;
import com.robomwm.prettysimpleshop.event.ShopOpenCloseEvent;
import com.robomwm.prettysimpleshop.event.ShopPricedEvent;
import com.robomwm.prettysimpleshop.event.ShopSelectEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created on 2/8/2018.
 *
 * @author RoboMWM
 */
public class ShopListener implements Listener
{
    private JavaPlugin instance;
    private ShopAPI shopAPI;
    private Economy economy;
    private Map<Player, ShopInfo> selectedShop = new HashMap<>();
    private Map<Player, Double> priceSetter = new HashMap<>();
    private ConfigManager config;

    private Method asNMSCopy; //CraftItemStack#asNMSCopy(ItemStack);
    private Method saveNMSItemStack; //n.m.s.ItemStack#save(compound);
    private Class<?> NBTTagCompoundClazz; //n.m.s.NBTTagCompound;

    public ShopListener(JavaPlugin plugin, ShopAPI shopAPI, Economy economy, ConfigManager configManager)
    {
        instance = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.shopAPI = shopAPI;
        this.config = configManager;
        this.economy = economy;

        try
        {
            asNMSCopy = ReflectionHandler.getMethod("CraftItemStack", ReflectionHandler.PackageType.CRAFTBUKKIT_INVENTORY, "asNMSCopy", ItemStack.class);
            NBTTagCompoundClazz = ReflectionHandler.PackageType.MINECRAFT_SERVER.getClass("NBTTagCompound");
            saveNMSItemStack = ReflectionHandler.getMethod("ItemStack", ReflectionHandler.PackageType.MINECRAFT_SERVER, "save", NBTTagCompoundClazz);
        }
        catch (Exception e)
        {
            instance.getLogger().warning("Reflection failed, will use legacy, non-hoverable, boring text.");
            e.printStackTrace();
        }
    }

    @EventHandler
    private void cleanup(PlayerQuitEvent event)
    {
        selectedShop.remove(event.getPlayer());
    }

    private boolean isEnabledWorld(World world)
    {
        return config.isWhitelistedWorld(world);
    }

    //We don't watch BlockDamageEvent as player may be in adventure (but uh this event probably doesn't fire in adventure either so... uhm yea... hmmm.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onLeftClickChest(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();
        if (event.getAction() != Action.LEFT_CLICK_BLOCK)
            return;
        if (!isEnabledWorld(event.getPlayer().getWorld()))
            return;

        Block block = event.getClickedBlock();

        selectShop(player, block, false);
    }

    public boolean selectShop(Player player, Block block, boolean wantToBuy)
    {
        if (block.getType() != Material.CHEST)
            return false;
        Chest chest = (Chest)block.getState();
        if (!shopAPI.isShop(chest))
            return false;
        ItemStack item = shopAPI.getItemStack(chest);
        double price = shopAPI.getPrice(chest);

        if (price < 0)
        {
            config.sendMessage(player, "noPrice");
            return true;
        }
        else if (item == null)
        {
            config.sendMessage(player, "noStock");
            config.sendTip(player, "noStock");
            return true;
        }

        ShopInfo shopInfo = new ShopInfo(chest.getLocation(), item, price);

        ShopSelectEvent shopSelectEvent = new ShopSelectEvent(player, shopInfo, shopInfo.equals(selectedShop.get(player)) || wantToBuy);

        selectedShop.put(player, shopInfo);

        String textToSend = config.getString("saleInfo", PrettySimpleShop.getItemName(item), economy.format(price), Integer.toString(item.getAmount()));
        String json;
        item.setAmount(1);
        try
        {
            Object nmsItemStack = asNMSCopy.invoke(null, item); //CraftItemStack#asNMSCopy(itemStack); //nms version of the ItemStack
            Object nbtTagCompound = NBTTagCompoundClazz.newInstance(); //new NBTTagCompoundClazz(); //get a new NBTTagCompound, which will contain the nmsItemStack.
            nbtTagCompound = saveNMSItemStack.invoke(nmsItemStack, nbtTagCompound); //nmsItemStack#save(nbtTagCompound); //saves nmsItemStack into our new NBTTagCompound
            json = nbtTagCompound.toString();
        }
        catch (Throwable rock)
        {
            player.sendMessage(textToSend);
            instance.getServer().getPluginManager().callEvent(shopSelectEvent);
            return true;
        }

        BaseComponent[] hoverEventComponents = new BaseComponent[]
                {
                        new TextComponent(json)
                };
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverEventComponents);
        TextComponent text = new TextComponent(textToSend);
        text.setHoverEvent(hover);
        text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/buy " +
                chest.getLocation().getWorld().getName() + " " + chest.getLocation().getX() + " " +
                chest.getLocation().getBlockY() + " " + chest.getLocation().getBlockZ()));
        player.sendMessage(text);
        shopInfo.setHoverableText(text);
        config.sendTip(player, "saleInfo");
        instance.getServer().getPluginManager().callEvent(shopSelectEvent);
        return true;
    }

    public ShopInfo getSelectedShop(Player player)
    {
        return selectedShop.get(player);
    }

    //Clear shop selection on world change
    @EventHandler
    private void onWorldChange(PlayerChangedWorldEvent event)
    {
        selectedShop.remove(event.getPlayer());
    }

    //Collect revenues
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onOpenInventory(InventoryOpenEvent event)
    {
        if (event.getPlayer().getType() != EntityType.PLAYER)
            return;
        if (event.getInventory().getLocation() == null)
            return;
        if (!(event.getInventory().getHolder() instanceof Chest || event.getInventory().getHolder() instanceof DoubleChest))
            return;
        Player player = (Player)event.getPlayer();
        Chest chest = shopAPI.getChest(event.getInventory().getLocation());
        if (!shopAPI.isShop(chest))
        {
            if (priceSetter.remove(player) != null)
                config.sendMessage(player, "setPriceCanceled");
            return;
        }

        if (priceSetter.containsKey(player))
        {
            double newPrice = priceSetter.remove(player);
            shopAPI.setPrice(chest, newPrice);
            config.sendMessage(player, "priceApplied", economy.format(newPrice));
            instance.getServer().getPluginManager().callEvent(new ShopPricedEvent(player, chest.getLocation(), newPrice));
        }

        instance.getServer().getPluginManager().callEvent(new ShopOpenCloseEvent(player, new ShopInfo(shopAPI.getLocation(chest), shopAPI.getItemStack(chest), shopAPI.getPrice(chest)), true));

        double deposit = shopAPI.getRevenue(chest, true);
        if (deposit <= 0)
            return;
        economy.depositPlayer(player, deposit);
        config.sendMessage(player, "collectRevenue", economy.format(deposit));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onBreakShop(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST)
            return;
        Chest chest = (Chest)block.getState();
        if (!shopAPI.isShop(chest))
            return;
        instance.getServer().getPluginManager().callEvent(new ShopBreakEvent(event.getPlayer(), new ShopInfo(shopAPI.getLocation(chest), shopAPI.getItemStack(chest), shopAPI.getPrice(chest))));
        double deposit = shopAPI.getRevenue(chest, true);
        if (deposit <= 0)
            return;
        Player player = event.getPlayer();
        economy.depositPlayer(player, deposit);
        config.sendMessage(player, "collectRevenue", economy.format(deposit));
    }

    //Purely for calling the dumb event
    @EventHandler(ignoreCancelled = true)
    private void onClose(InventoryCloseEvent event)
    {
        if (event.getPlayer().getType() != EntityType.PLAYER)
            return;
        if (event.getInventory().getLocation() == null)
            return;
        if (!(event.getInventory().getHolder() instanceof Chest || event.getInventory().getHolder() instanceof DoubleChest))
            return;
        Player player = (Player)event.getPlayer();
        Chest chest = shopAPI.getChest(event.getInventory().getLocation());
        if (!shopAPI.isShop(chest))
            return;
        instance.getServer().getPluginManager().callEvent(new ShopOpenCloseEvent(player, new ShopInfo(shopAPI.getLocation(chest), shopAPI.getItemStack(chest), shopAPI.getPrice(chest)), false));
    }

    //For now we'll just prevent explosions. Might consider dropping stored revenue on explosion later.
    @EventHandler(ignoreCancelled = true)
    private void onExplode(EntityExplodeEvent event)
    {
        Iterator<Block> blockIterator = event.blockList().iterator();
        while (blockIterator.hasNext())
        {
            Block block = blockIterator.next();
            if (block.getType() != Material.CHEST)
                continue;
            if (shopAPI.isShop((Chest)block.getState()))
                blockIterator.remove();
        }
    }
    @EventHandler(ignoreCancelled = true)
    private void onExplode(BlockExplodeEvent event)
    {
        Iterator<Block> blockIterator = event.blockList().iterator();
        while (blockIterator.hasNext())
        {
            Block block = blockIterator.next();
            if (block.getType() != Material.CHEST)
                continue;
            if (shopAPI.isShop((Chest)block.getState()))
                blockIterator.remove();
        }
    }

    //Commands cuz well all the data's here so yea

    public void priceCommand(Player player, Double price)
    {
        if (price == null)
        {
            priceSetter.remove(player);
            return;
        }
        selectedShop.remove(player);
        priceSetter.put(player, price);
        config.sendMessage(player, "applyPrice");
    }


}
