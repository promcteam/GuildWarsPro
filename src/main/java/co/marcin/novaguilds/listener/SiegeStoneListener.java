/*
 *     NovaGuilds - Bukkit plugin
 *     Copyright (C) 2017 Marcin (CTRL) Wieczorek
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package co.marcin.novaguilds.listener;

import co.marcin.novaguilds.api.basic.CommandWrapper;
import co.marcin.novaguilds.api.basic.ConfigWrapper;
import co.marcin.novaguilds.api.basic.MessageWrapper;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.basic.NovaPlayer;
import co.marcin.novaguilds.api.basic.NovaRegion;
import co.marcin.novaguilds.api.util.RegionSelection;
import co.marcin.novaguilds.command.admin.caversia.CommandAdminCaversiaAccess;
import co.marcin.novaguilds.command.admin.caversia.CommandAdminCaversiaStoneRename;
import co.marcin.novaguilds.enums.Command;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.Dependency;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.Permission;
import co.marcin.novaguilds.enums.RegionMode;
import co.marcin.novaguilds.enums.StoneWager;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.impl.basic.CommandWrapperImpl;
import co.marcin.novaguilds.impl.basic.NovaGuildImpl;
import co.marcin.novaguilds.impl.basic.NovaRegionImpl;
import co.marcin.novaguilds.impl.util.AbstractGUIInventory;
import co.marcin.novaguilds.impl.util.AbstractListener;
import co.marcin.novaguilds.impl.util.RegionSelectionImpl;
import co.marcin.novaguilds.manager.ConfigManager;
import co.marcin.novaguilds.manager.PlayerManager;
import co.marcin.novaguilds.manager.RegionManager;
import co.marcin.novaguilds.runnable.RunnableWarmupEnd;
import co.marcin.novaguilds.util.InventoryUtils;
import co.marcin.novaguilds.util.NumberUtils;
import co.marcin.novaguilds.util.StringUtils;
import com.gotofinal.darkrise.economy.DarkRiseEconomy;
import com.gotofinal.darkrise.economy.DarkRiseItem;
import com.gotofinal.darkrise.economy.Price;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.UUID;

public class SiegeStoneListener extends AbstractListener {
    private DarkRiseEconomy economy;
    private DarkRiseItem largeStoneWager;
    private DarkRiseItem smallStoneWager;
    public static NovaGuild GUILD;

    public static final CommandWrapper COMMAND_RENAME = new CommandWrapperImpl();
    public static final CommandWrapper COMMAND_ACCESS = new CommandWrapperImpl();

    public void init() {
        economy = plugin.getDependencyManager().get(Dependency.DARKRISE_ECONOMY, DarkRiseEconomy.class);

        if(economy == null) {
            throw new IllegalArgumentException("Could not get Economy instance");
        }

        largeStoneWager = economy.getItems().getItemByIdOrName(Config.CAVERSIA_REGION_LARGE_ITEM.getString());
        smallStoneWager = economy.getItems().getItemByIdOrName(Config.CAVERSIA_REGION_SMALL_ITEM.getString());

        if(smallStoneWager == null) {
            throw new IllegalArgumentException("Invalid item: " + Config.CAVERSIA_REGION_SMALL_ITEM.getString());
        }

        if(largeStoneWager == null) {
            throw new IllegalArgumentException("Invalid item: " + Config.CAVERSIA_REGION_LARGE_ITEM.getString());
        }

        GUILD = new NovaGuildImpl(UUID.fromString("0146f976-990b-4c38-9f7f-575def2155fc")); //Fixed UUID
        GUILD.setName("NoGuild");
        GUILD.updateInactiveTime();
        plugin.getGuildManager().add(GUILD);

        plugin.getConfigManager().registerCustomConfigDeserializer(Price.class, new ConfigManager.CustomConfigDeserializer<Price>() {
            @Override
            public Price deserialize(ConfigWrapper configWrapper) {
                return new Price(configWrapper.getMap());
            }
        });

        COMMAND_RENAME.setFlags(CommandWrapper.Flag.NOCONSOLE);
        COMMAND_RENAME.setName("CAVERSIA_RENAME");
        COMMAND_RENAME.setUsageMessage(Message.CHAT_USAGE_NGA_CAVERSIA_RENAME);
        COMMAND_RENAME.setPermission(Permission.NOVAGUILDS_CAVERSIA_COMMAND_RENAME);
        plugin.getCommandManager().registerExecutor(COMMAND_RENAME, new CommandAdminCaversiaStoneRename());

        COMMAND_ACCESS.setName("CAVERSIA_ACCESS");
        COMMAND_ACCESS.setUsageMessage(Message.CHAT_USAGE_NGA_CAVERSIA_ACCESS);
        COMMAND_ACCESS.setPermission(Permission.NOVAGUILDS_CAVERSIA_COMMAND_ACCESS);
        Command.ADMIN_ACCESS.getExecutor().getCommandsMap().put("caversia", COMMAND_ACCESS);
        plugin.getCommandManager().registerExecutor(COMMAND_ACCESS, new CommandAdminCaversiaAccess());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final NovaPlayer nPlayer = PlayerManager.getPlayer(event.getPlayer());

        if(!nPlayer.hasGuild()) {
            Message.CHAT_GUILD_NOTINGUILD.send(nPlayer);
            return;
        }

        NovaRegion region = RegionManager.get(event.getClickedBlock());

        if(region == null) {
            for(NovaRegion siegeRegion : GUILD.getRegions()) {
                if(siegeRegion.contains(event.getClickedBlock().getLocation())) {
                    region = siegeRegion;
                    break;
                }
            }
        }

        if(region == null
                || !event.getClickedBlock().equals(region.getSiegeStone().getBlock())) {
            return;
        }

        StoneWager stoneWager = region.getSiegeStone().getStoneWager();
        DarkRiseItem itemRequirement;
        int countRequirement;
        Price price;

        if(stoneWager == StoneWager.SMALL) {
            itemRequirement = smallStoneWager;
            countRequirement = Config.CAVERSIA_REGION_SMALL_MEMBERS.getInt();
            price = Config.CAVERSIA_REGION_SMALL_PRICE.get();
        }
        else {
            itemRequirement = largeStoneWager;
            countRequirement = Config.CAVERSIA_REGION_LARGE_MEMBERS.getInt();
            price = Config.CAVERSIA_REGION_LARGE_PRICE.get();
        }

        if(!InventoryUtils.containsAtLeast(event.getPlayer().getInventory(), itemRequirement.getItem(), 1)) {
            Message.CHAT_CAVERSIA_NOSIEGESTONE.setVar(VarKey.NAME, region.getSiegeStone().getName()).send(nPlayer);
            return;
        }

        if(nPlayer.getGuild().getPlayers().size() < countRequirement) {
            Message.CHAT_CAVERSIA_NOTENOUGHMEMBERS
                   .setVar(VarKey.AMOUNT, countRequirement)
                   .setVar(VarKey.NAME, region.getSiegeStone().getName())
                   .send(nPlayer);
            return;
        }

        if(region.getGuild().equals(GUILD)) {
            if(!price.getCurrency().canPay(event.getPlayer(), price.getAmount(), true)) {
                return;
            }

            price.getCurrency().pay(event.getPlayer(), price.getAmount());
            region.getGuild().removeRegion(region);
            nPlayer.getGuild().addRegion(region);
            event.getPlayer().getInventory().removeItem(itemRequirement.getItem());
            Message.CHAT_CAVERSIA_REGION_CLAIMED.setVar(VarKey.NAME, region.getSiegeStone().getName()).send(nPlayer);
        }

        final NovaRegion finalRegion = region;
        new AbstractGUIInventory(9, Message.CHAT_CAVERSIA_GUI_REGIONINFO_TITLE) {
            @Override
            public void generateContent() {
                ItemStack bannerItem = new ItemStack(Material.BANNER);
                BannerMeta bannerMeta = (BannerMeta) bannerItem.getItemMeta();
                bannerMeta.setBaseColor(DyeColor.BLACK);
                bannerMeta.setDisplayName(Message.CHAT_CAVERSIA_GUI_REGIONINFO_NAME.get());
                bannerItem.setItemMeta(bannerMeta);
                ItemStack warmupItem = bannerItem.clone();
                MessageWrapper ownedMessage, vulnerableMessage;

                ownedMessage = finalRegion.getGuild().equals(GUILD)
                        ? Message.CHAT_CAVERSIA_GUI_REGIONINFO_OWNED_NO
                        : Message.CHAT_CAVERSIA_GUI_REGIONINFO_OWNED_YES;

                vulnerableMessage = finalRegion.getSiegeStone().isVulnerable()
                        ? Message.CHAT_CAVERSIA_GUI_REGIONINFO_VULNERABLE_YES
                        : Message.CHAT_CAVERSIA_GUI_REGIONINFO_VULNERABLE_NO;

                bannerMeta.setLore(Message.CHAT_CAVERSIA_GUI_REGIONINFO_LORE
                        .setVar(VarKey.NAME, finalRegion.getSiegeStone().getName())
                        .setVar(VarKey.TAG1, ownedMessage.setVar(VarKey.GUILD_NAME, finalRegion.getGuild().getName()).get())
                        .setVar(VarKey.TAG2, vulnerableMessage
                                .setVar(VarKey.TIME, StringUtils.secondsToString(Config.RAID_TIMEREST.getSeconds() - (NumberUtils.systemSeconds() - finalRegion.getSiegeStone().getLastAttackTime())))
                                .get())
                        .getList());
                bannerItem.setItemMeta(bannerMeta);

                ItemMeta warmupMeta = warmupItem.getItemMeta();
                warmupMeta.setDisplayName(Message.CHAT_CAVERSIA_GUI_WARMUP_NAME.get());
                warmupMeta.setLore(Collections.<String>emptyList());
                warmupItem.setItemMeta(warmupMeta);

                registerAndAdd(new EmptyExecutor(bannerItem));

                if(!finalRegion.getSiegeStone().hasWarmup() && !finalRegion.getGuild().isMember(nPlayer)) {
                    registerAndAdd(new Executor(warmupItem) {
                        @Override
                        public void execute() {
                            if(!finalRegion.getSiegeStone().hasWarmup()) {
                                finalRegion.getSiegeStone().getWarmup().setStartTime((int) NumberUtils.systemSeconds());
                                finalRegion.getSiegeStone().getWarmup().setAttacker(nPlayer.getGuild());
                                new RunnableWarmupEnd(finalRegion).schedule();
                                Message.CHAT_CAVERSIA_GUI_WARMUP_STARTED.send(nPlayer);
                                close();
                            }
                        }
                    });
                }
            }
        }.open(nPlayer);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        DarkRiseItem item = economy.getItems().getItemByStack(event.getItemInHand());
        NovaPlayer nPlayer = PlayerManager.getPlayer(event.getPlayer());

        if(item == null
                || nPlayer.getPreferences().getRegionMode() != RegionMode.STONEWAGER
                || !Permission.NOVAGUILDS_CAVERSIA_STONEWAGERMODE.has(nPlayer)) {
            return;
        }

        StoneWager stoneWager = null;
        int size = 0;

        if(item.equals(smallStoneWager)) {
            stoneWager = StoneWager.SMALL;
            size = Config.CAVERSIA_REGION_SMALL_SIZE.getInt();
        }
        else if(item.equals(largeStoneWager)) {
            stoneWager = StoneWager.LARGE;
            size = Config.CAVERSIA_REGION_LARGE_SIZE.getInt();
        }

        if(stoneWager == null) {
            return;
        }

        RegionSelectionImpl regionSelection = new RegionSelectionImpl(nPlayer, RegionSelection.Type.CREATE);
        NovaRegion region;
        Location center = event.getBlock().getLocation();
        Location c1 = new Location(nPlayer.getPlayer().getWorld(), center.getBlockX() - size, 0, center.getBlockZ() - size);
        Location c2 = new Location(nPlayer.getPlayer().getWorld(), center.getBlockX() + size, 0, center.getBlockZ() + size);

        regionSelection.setCorner(0, c1);
        regionSelection.setCorner(1, c2);

        region = new NovaRegionImpl(UUID.randomUUID(), regionSelection);
        region.setWorld(center.getWorld());
        region.getSiegeStone().setBlock(event.getBlock());
        region.getSiegeStone().setStoneWager(stoneWager);
        GUILD.addRegion(region);
        Message.CHAT_CAVERSIA_REGION_CREATED.send(nPlayer);
    }
}
