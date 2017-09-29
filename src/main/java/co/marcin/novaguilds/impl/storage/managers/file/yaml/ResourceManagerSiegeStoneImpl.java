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

package co.marcin.novaguilds.impl.storage.managers.file.yaml;

import co.marcin.novaguilds.api.storage.Storage;
import co.marcin.novaguilds.impl.basic.SiegeStone;
import co.marcin.novaguilds.util.LoggerUtils;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResourceManagerSiegeStoneImpl extends AbstractYAMLResourceManager<SiegeStone> {
    private final File file = new File(getDirectory(), "siegestone.yml");

    /**
     * The constructor
     *
     * @param storage the storage
     */
    public ResourceManagerSiegeStoneImpl(Storage storage) throws IOException {
        super(storage, SiegeStone.class, "siegestone");

        if(!file.exists() && !file.createNewFile()) {
            throw new RuntimeException("Could not create siege stone storage file");
        }
    }

    @Override
    public File getFile(SiegeStone block) {
        return file;
    }

    @Override
    public List<SiegeStone> load() {
        List<SiegeStone> siegeStoneList = new ArrayList<>();
        FileConfiguration data = getData(null);

        for(String regionUUID : data.getKeys(false)) {
            siegeStoneList.add(new SiegeStone(data.getConfigurationSection(regionUUID).getValues(true)));
        }

        return siegeStoneList;
    }

    @Override
    public boolean save(SiegeStone siegeStone) {
        try {
            if(!siegeStone.isChanged() && !isInSaveQueue(siegeStone) || siegeStone.isUnloaded()) {
                return false;
            }

            FileConfiguration data = getData(null);
            data.set(siegeStone.getUUID().toString(), siegeStone.serialize());
            data.save(getFile(null));
            return true;
        }
        catch(IOException e) {
            LoggerUtils.exception(e);
            return false;
        }
    }

    @Override
    public boolean remove(SiegeStone siegeStone) {
        getData(null).set(siegeStone.getUUID().toString(), null);
        return false;
    }
}
