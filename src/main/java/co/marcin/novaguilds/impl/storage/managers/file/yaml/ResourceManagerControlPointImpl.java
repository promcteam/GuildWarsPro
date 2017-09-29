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
import co.marcin.novaguilds.impl.basic.ControlPoint;
import co.marcin.novaguilds.util.LoggerUtils;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResourceManagerControlPointImpl extends AbstractYAMLResourceManager<ControlPoint> {
    private final File file = new File(getDirectory(), "controlpoint.yml");

    /**
     * The constructor
     *
     * @param storage the storage
     */
    public ResourceManagerControlPointImpl(Storage storage) throws IOException {
        super(storage, ControlPoint.class, "siegestone");

        if(!file.exists() && !file.createNewFile()) {
            throw new RuntimeException("Could not create siege stone storage file");
        }
    }

    @Override
    public File getFile(ControlPoint block) {
        return file;
    }

    @Override
    public List<ControlPoint> load() {
        List<ControlPoint> siegeStoneList = new ArrayList<>();
        FileConfiguration data = getData(null);

        for(String regionUUID : data.getKeys(false)) {
            siegeStoneList.add(new ControlPoint(data.getConfigurationSection(regionUUID).getValues(true)));
        }

        return siegeStoneList;
    }

    @Override
    public boolean save(ControlPoint controlPoint) {
        try {
            if(!controlPoint.isChanged() && !isInSaveQueue(controlPoint) || controlPoint.isUnloaded()) {
                return false;
            }

            FileConfiguration data = getData(null);
            data.set(controlPoint.getUUID().toString(), controlPoint.serialize());
            data.save(getFile(null));
            return true;
        }
        catch(IOException e) {
            LoggerUtils.exception(e);
            return false;
        }
    }

    @Override
    public boolean remove(ControlPoint controlPoint) {
        getData(null).set(controlPoint.getUUID().toString(), null);
        return false;
    }
}
