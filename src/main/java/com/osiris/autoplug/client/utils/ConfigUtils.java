/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.utils.UtilsDYModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Frequently used code of config stuff.
 */
public class ConfigUtils {

    public void printAllModulesToDebug(@NotNull List<DYModule> modules) {
        try {
            UtilsDYModule utils = new UtilsDYModule();
            for (DYModule module :
                    modules) {
                AL.debug(this.getClass(), module.getKeys().toString() + " VAL: " + utils.valuesListToStringList(module.getValues()).toString() + " DEF: " + utils.valuesListToStringList(module.getDefValues()).toString());
            }
        } catch (Exception e) {
            AL.warn("Couldn't show/write ConfigModule information!", e);
        }
    }


    @NotNull
    public CoolDownReport checkIfOutOfCoolDown(int coolDownInMinutes, @NotNull SimpleDateFormat format, @Nullable String lastTasksTimestamp) {
        try {
            if (lastTasksTimestamp != null) {
                long last = format.parse(lastTasksTimestamp).getTime();
                long now = System.currentTimeMillis();
                long msSinceLast = now - last;
                long msCoolDown = ((coolDownInMinutes * 60L) * 1000);
                boolean isOutOfCoolDown = msSinceLast > msCoolDown;
                return new CoolDownReport(isOutOfCoolDown, msSinceLast, msCoolDown);
            }
        } catch (Exception e) {
            AL.warn(e);
        }
        return new CoolDownReport(true, 0, 0);
    }

}
