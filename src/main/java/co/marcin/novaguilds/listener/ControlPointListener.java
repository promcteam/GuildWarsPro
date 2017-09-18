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
import co.marcin.novaguilds.api.basic.MessageWrapper;
import co.marcin.novaguilds.api.basic.NovaPlayer;
import co.marcin.novaguilds.api.storage.ResourceManager;
import co.marcin.novaguilds.command.CommandCaversiaControlPointList;
import co.marcin.novaguilds.command.admin.caversia.controlpoint.CommandAdminCaversiaControlPointAccess;
import co.marcin.novaguilds.command.admin.caversia.controlpoint.CommandAdminCaversiaControlPointRename;
import co.marcin.novaguilds.enums.Command;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.Permission;
import co.marcin.novaguilds.enums.RegionMode;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.impl.basic.CommandWrapperImpl;
import co.marcin.novaguilds.impl.basic.ControlPoint;
import co.marcin.novaguilds.impl.util.AbstractListener;
import co.marcin.novaguilds.manager.PlayerManager;
import co.marcin.novaguilds.util.LoggerUtils;
import co.marcin.novaguilds.util.NumberUtils;
import co.marcin.novaguilds.util.StringUtils;
import com.gotofinal.darkrise.economy.DarkRiseItem;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ControlPointListener extends AbstractListener {
    public static final CommandWrapper COMMAND_ACCESS = new CommandWrapperImpl();
    public static final CommandWrapper COMMAND_RENAME = new CommandWrapperImpl();
    public static final CommandWrapper COMMAND_LIST = new CommandWrapperImpl();

    private final Set<ControlPoint> controlPoints = new HashSet<>();
    private final Map<DarkRiseItem, Integer> rewardsWinners = new HashMap<>();
    private final Map<DarkRiseItem, Integer> rewardsLosers = new HashMap<>();

    public ControlPointListener() {
        COMMAND_ACCESS.setName("CAVERSIA_CONTROLPOINT_ACCESS");
        COMMAND_ACCESS.setUsageMessage(Message.CHAT_USAGE_NGA_CAVERSIA_CONTROLPOINT_ACCESS);
        COMMAND_ACCESS.setPermission(Permission.NOVAGUILDS_CAVERSIA_COMMAND_CONTROLPOINT_ACCESS);
        Command.ADMIN_ACCESS.getExecutor().getCommandsMap().put("caversia", COMMAND_ACCESS);
        plugin.getCommandManager().registerExecutor(COMMAND_ACCESS, new CommandAdminCaversiaControlPointAccess());

        COMMAND_RENAME.setFlags(CommandWrapper.Flag.NOCONSOLE);
        COMMAND_RENAME.setName("CAVERSIA_CONTROLPOINT_RENAME");
        COMMAND_RENAME.setUsageMessage(Message.CHAT_USAGE_NGA_CAVERSIA_CONTROLPOINT_RENAME);
        COMMAND_RENAME.setPermission(Permission.NOVAGUILDS_CAVERSIA_COMMAND_CONTROLPOINT_RENAME);
        plugin.getCommandManager().registerExecutor(COMMAND_RENAME, new CommandAdminCaversiaControlPointRename());

        COMMAND_LIST.setName("CAVERSIA_CONTROLPOINT_LIST");
        COMMAND_LIST.setUsageMessage(Message.CHAT_USAGE_NGA_CAVERSIA_CONTROLPOINT_LIST);
        COMMAND_LIST.setPermission(Permission.NOVAGUILDS_CAVERSIA_COMMAND_CONTROLPOINT_LIST);
        Command.GUILD_ACCESS.getExecutor().getCommandsMap().put("controlpoints", COMMAND_LIST);
        Command.GUILD_ACCESS.getExecutor().getCommandsMap().put("cp", COMMAND_LIST);
        plugin.getCommandManager().registerExecutor(COMMAND_LIST, new CommandCaversiaControlPointList());

        parseRewards(Config.CAVERSIA_CONTROLPOINT_REWARDS_WINNERS.getStringList(), rewardsWinners);
        parseRewards(Config.CAVERSIA_CONTROLPOINT_REWARDS_LOSERS.getStringList(), rewardsLosers);
    }

    private void parseRewards(List<String> list, Map<DarkRiseItem, Integer> map) {
        SiegeStoneListener siegeStoneListener = plugin.getListenerManager().getListener(SiegeStoneListener.class);

        for(String rewardString : list) {
            if(!rewardString.contains(" ")) {
                LoggerUtils.error(String.format("'%s' is not a valid item.", rewardString));
                continue;
            }

            String[] split = org.apache.commons.lang.StringUtils.split(rewardString, ' ');

            if(!NumberUtils.isNumeric(split[1])) {
                LoggerUtils.error(String.format("'%s' is not a valid item.", rewardString));
                continue;
            }

            DarkRiseItem item = siegeStoneListener.CAVERSIA_ECONOMY.getItems().getItemByIdOrName(split[0]);

            if(item == null) {
                LoggerUtils.error(String.format("'%s' is not a valid item.", rewardString));
                continue;
            }

            map.put(item, Integer.parseInt(split[1]));
        }
    }

    public ControlPoint getControlPoint(Location location) {
        for(ControlPoint controlPoint : controlPoints) {
            if(controlPoint.getLocation().distance(location) < 1) {
                return controlPoint;
            }
        }

        return null;
    }

    public Set<ControlPoint> getControlPoints() {
        return controlPoints;
    }

    public Map<DarkRiseItem, Integer> getRewardsWinners() {
        return rewardsWinners;
    }

    public Map<DarkRiseItem, Integer> getRewardsLosers() {
        return rewardsLosers;
    }

    public void load() {
        controlPoints.clear();
        controlPoints.addAll(plugin.getStorage().getResourceManager(ControlPoint.class).load());
        runBroadcastTasks();
        LoggerUtils.info(String.format("Loaded %d control points!", controlPoints.size()));
    }

    /**
     * Saves all control points
     */
    public void save() {
        long nanoTime = System.nanoTime();
        ResourceManager<ControlPoint> resourceManager = plugin.getStorage().getResourceManager(ControlPoint.class);
        int count = resourceManager.executeSave() + resourceManager.save(controlPoints);
        LoggerUtils.info("ControlPoint data saved in " + TimeUnit.MILLISECONDS.convert((System.nanoTime() - nanoTime), TimeUnit.NANOSECONDS) / 1000.0 + "s (" + count + " ranks)");

        nanoTime = System.nanoTime();
        count = resourceManager.executeRemoval();
        LoggerUtils.info("ControlPoint removed in " + TimeUnit.MILLISECONDS.convert((System.nanoTime() - nanoTime), TimeUnit.NANOSECONDS) / 1000.0 + "s (" + count + " ranks)");
    }

    public void runBroadcastTasks() {
        for(final ControlPoint controlPoint : getControlPoints()) {
            if(controlPoint.getTimeLeft() < Config.CAVERSIA_CONTROLPOINT_VULNERABLENOTIFY.getSeconds()) {
                controlPoint.scheduleSoonVulnerableTask();
                controlPoint.scheduleVulnerableTask();
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        NovaPlayer nPlayer = PlayerManager.getPlayer(event.getPlayer());

        if(nPlayer.getPreferences().getRegionMode() != RegionMode.CONTROLPOINT) {
            return;
        }

        ControlPoint controlPoint = new ControlPoint();
        controlPoint.setLocation(event.getBlock().getLocation());
        controlPoints.add(controlPoint);
        Message.CHAT_CAVERSIA_CONTROLPOINT_CREATED.send(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        ControlPoint controlPoint = getControlPoint(event.getClickedBlock().getLocation());

        if(controlPoint == null) {
            return;
        }

        MessageWrapper owned = controlPoint.isOwned()
                ? Message.CHAT_CAVERSIA_CONTROLPOINT_DESCRIPTION_OWNED_YES
                         .setVar(VarKey.GUILD_NAME, controlPoint.getOwningGuild().getName())
                : Message.CHAT_CAVERSIA_CONTROLPOINT_DESCRIPTION_OWNED_NO;

        MessageWrapper vulnerable = controlPoint.isVulnerable()
                ? Message.CHAT_CAVERSIA_CONTROLPOINT_DESCRIPTION_VULNERABLE_YES
                : Message.CHAT_CAVERSIA_CONTROLPOINT_DESCRIPTION_VULNERABLE_NO
                         .setVar(VarKey.TIME, StringUtils.secondsToString(controlPoint.getTimeLeft(), TimeUnit.MINUTES));

        Message.CHAT_CAVERSIA_CONTROLPOINT_DESCRIPTION_PATTERN
               .setVar(VarKey.NAME, controlPoint.getName())
               .setVar(VarKey.TAG1, owned.get())
               .setVar(VarKey.TAG2, vulnerable.get())
               .send(event.getPlayer());
    }
}
