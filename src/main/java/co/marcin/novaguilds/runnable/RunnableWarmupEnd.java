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

package co.marcin.novaguilds.runnable;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.basic.NovaRegion;
import co.marcin.novaguilds.impl.basic.SiegeStone;

import java.util.concurrent.TimeUnit;

public class RunnableWarmupEnd implements Runnable {
    private final NovaRegion region;

    /**
     * This runnable is supposed to be scheduled on data load and warmup initialization.
     * It should be ran to change the warmup to an actual siege.
     *
     * @param region attacked region
     */
    public RunnableWarmupEnd(NovaRegion region) {
        this.region = region;
    }

    @Override
    public void run() {
        SiegeStone.Warmup warmup = region.getSiegeStone().getWarmup();
        NovaGuild guildDefender = region.getGuild();
        guildDefender.createRaid(warmup.getAttacker());
        guildDefender.getRaid().setRegion(region);
        warmup.setStartTime(0);
        warmup.setAttacker(null);

        if(!RunnableRaid.isRaidRunnableRunning()) {
            new RunnableRaid().schedule();
        }
    }

    public void schedule() {
        int timeLeft = region.getSiegeStone().getWarmup().getTimeLeft();

        if(timeLeft > 0) {
            NovaGuilds.runTaskLater(this, timeLeft, TimeUnit.SECONDS);
        }
    }
}
