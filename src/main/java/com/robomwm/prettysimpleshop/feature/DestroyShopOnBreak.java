package com.robomwm.prettysimpleshop.feature;

import com.robomwm.prettysimpleshop.PrettySimpleShop;
import com.robomwm.prettysimpleshop.event.ShopBreakEvent;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Created on 11/9/2019.
 *
 * @author RoboMWM
 */
public class DestroyShopOnBreak implements Listener
{
    private PrettySimpleShop plugin;

    public DestroyShopOnBreak(PrettySimpleShop plugin)
    {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    private void onShopBreak(ShopBreakEvent event)
    {
        Container shopContainer = plugin.getShopAPI().getContainer(event.getShopInfo().getLocation());

        if (shopContainer != null) {
            shopContainer.setCustomName(null);
            shopContainer.update();
        }
    }
}
