package co.marcin.novaguilds.runnable;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.basic.NovaRegion;
import co.marcin.novaguilds.impl.basic.SiegeStone;
import co.marcin.novaguilds.util.LoggerUtils;

import java.util.concurrent.TimeUnit;

public class RunnableUpkeepFee implements Runnable {
    private final NovaGuilds plugin = NovaGuilds.getInstance();

    @Override
    public void run() {
        int count = 0;

        for(final NovaGuild guild : plugin.getGuildManager().getGuilds()) {
            for(final NovaRegion region : guild.getRegions()) {
                SiegeStone.UpkeepFee upkeepFee = new SiegeStone.UpkeepFee();
                region.getSiegeStone().addUpkeepFee(upkeepFee);
                count++;

                SiegeStone.UpkeepFeeWorker worker = new SiegeStone.UpkeepFeeWorker(region.getSiegeStone());

                if(worker.getTimeToDisband() < TimeUnit.DAYS.toSeconds(1)) {
                    NovaGuilds.runTaskLater(new Runnable() {
                        @Override
                        public void run() {
                            guild.removeRegion(region);
                        }
                    }, worker.getTimeToDisband(), TimeUnit.SECONDS);
                }
            }
        }

        LoggerUtils.debug(String.format("Added %d upkeep fees!", count));
    }
}
