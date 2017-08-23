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

package co.marcin.novaguilds.command.admin.caversia;

import co.marcin.novaguilds.api.basic.NovaRegion;
import co.marcin.novaguilds.command.abstractexecutor.AbstractCommandExecutor;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.listener.SiegeStoneListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class CommandAdminCaversiaStoneRename extends AbstractCommandExecutor {
    @Override
    public void execute(CommandSender sender, String[] args) throws Exception {
        if(args.length == 0) {
            getCommand().getUsageMessage().send(sender);
        }

        Player player = (Player) sender;

        Block block = player.getTargetBlock((Set<Material>) null, 20);

        if(block == null || block.getType() == Material.AIR) {
            Message.CHAT_REGION_NOREGIONHERE.send(sender);
            return;
        }

        NovaRegion region = null;

        for(NovaRegion siegeRegion : SiegeStoneListener.GUILD.getRegions()) {
            if(siegeRegion.getSiegeStone().getBlock().equals(block)) {
                region = siegeRegion;
            }
        }

        if(region == null) {
            for(NovaRegion guildRegion : plugin.getRegionManager().getRegions()) {
                if(guildRegion.getSiegeStone().getBlock().equals(block)) {
                    region = guildRegion;
                }
            }
        }

        if(region == null) {
            Message.CHAT_REGION_NOREGIONHERE.send(sender);
            return;
        }

        String name = args[0];

        if(name == null || name.isEmpty()) {
            Message.CHAT_INVALIDPARAM.send(sender);
        }

        region.getSiegeStone().setName(name);
        Message.CHAT_CAVERSIA_REGION_RENAMED.setVar(VarKey.NAME, region.getSiegeStone().getName()).send(sender);
    }
}
