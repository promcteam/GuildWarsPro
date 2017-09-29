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

package co.marcin.novaguilds.command;

import co.marcin.novaguilds.command.abstractexecutor.AbstractCommandExecutor;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.impl.basic.ControlPoint;
import co.marcin.novaguilds.listener.ControlPointListener;
import org.bukkit.command.CommandSender;

public class CommandCaversiaControlPointList extends AbstractCommandExecutor {
    @Override
    public void execute(CommandSender sender, String[] args) throws Exception {
        Message.CHAT_CAVERSIA_CONTROLPOINT_LIST_HEADER.send(sender);

        for(ControlPoint controlPoint : plugin.getListenerManager().getListener(ControlPointListener.class).getControlPoints()) {
            if(controlPoint.isOwned() || !controlPoint.isVulnerable()) {
                continue;
            }

            Message.CHAT_CAVERSIA_CONTROLPOINT_LIST_ROW
                    .setVar(VarKey.NAME, controlPoint.getName())
                    .setVar(VarKey.X, controlPoint.getLocation().getBlockX())
                    .setVar(VarKey.Y, controlPoint.getLocation().getBlockY())
                    .setVar(VarKey.Z, controlPoint.getLocation().getBlockZ())
                    .send(sender);
        }
    }
}
