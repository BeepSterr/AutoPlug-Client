/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.updater.plugins;

import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import net.lingala.zip4j.ZipFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManager {

    @NotNull
    public List<DetailedPlugin> getPlugins() {
        List<DetailedPlugin> plugins = new ArrayList<>();

        FileManager fm = new FileManager();

        // Get a list of all jar files in the /plugins dir
        List<File> plJarFiles = fm.getAllPlugins();

        // Location where each plugin.yml file will be extracted to
        File ymlFile = new File(System.getProperty("user.dir") + "/autoplug-system/plugin.yml");
        byte[] buffer = new byte[1024];
        FileInputStream fis;

        /*
        1. Extract information from each jars "plugin.yml" file
        2. Convert into a Plugin.class
        3. Add the Plugin.class to the plugins list
         */
        if (!plJarFiles.isEmpty())
            for (File jar :
                    plJarFiles) {

                try {
                    // Clean up old
                    if (ymlFile.exists()) {
                        ymlFile.delete();
                        ymlFile.createNewFile();
                    }

                    fis = new FileInputStream(jar);
                    ZipInputStream zis = new ZipInputStream(fis);
                    ZipEntry ze = zis.getNextEntry();

                    while (ze != null) {
                        if (ze.getName().equals("plugin.yml")) {
                            // Extract this plugin.yml file
                            FileOutputStream fos = new FileOutputStream(ymlFile);
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                            zis.closeEntry();

                            // Load the plugin.yml and get its details
                            final DreamYaml ymlConfig = new DreamYaml(ymlFile);
                            ymlConfig.load();

                            DYModule name = ymlConfig.put("name");
                            DYModule version = ymlConfig.put("version");
                            DYModule authorRaw = ymlConfig.put("author");
                            DYModule authorsRaw = ymlConfig.put("authors");
                            String author = null;
                            if (!authorRaw.getValues().isEmpty())
                                author = authorRaw.asString();
                            else
                                author = authorsRaw.asString(); // Returns only the first author

                            // Why this is done? Because each plugin.yml file stores its authors list differently (Array or List, or numbers idk, or some other stuff...)
                            // and all we want is just a simple list. This causes errors.
                            // We get the list as a String, remove all "[]" brackets and " "(spaces) so we get a list of names only separated by commas
                            // That is then sliced into a list.
                            // Before: [name1, name2]
                            // After: name1,name2
                            if (author != null) author = Arrays.asList(
                                    author.replaceAll("[\\[\\]]", "")
                                            .split(","))
                                    .get(0);

                            // Also check for ids in the plugin.yml
                            int spigotId = 0;
                            int bukkitId = 0;
                            DYModule mSpigotId = ymlConfig.get("spigot-id");
                            DYModule mBukkitId = ymlConfig.get("bukkit-id");
                            if (mSpigotId != null && mSpigotId.asString() != null) spigotId = mSpigotId.asInt();
                            if (mBukkitId != null && mBukkitId.asString() != null) bukkitId = mBukkitId.asInt();

                            plugins.add(new DetailedPlugin(jar.getPath(), name.asString(), version.asString(), author, spigotId, bukkitId, null));
                        }
                        // Get next file in zip
                        ze = zis.getNextEntry();
                    } // Loop end
                    // Close last ZipEntry
                    zis.closeEntry();
                    zis.close();
                    fis.close();

                } catch (Exception e) {
                    AL.warn("Failed to get plugin information for: " + jar.getName(), e);
                }
            }

        return plugins;
    }

    @NotNull
    @Deprecated
    private File extractPluginYmlFile(@NotNull File jar) throws Exception {
        // A jar file is actually a zip file, thats why we can use this method
        ZipFile zip = new ZipFile(jar);

        // The plugin yml file we will extract from the jar
        String path = System.getProperty("user.dir") + "/autoplug-system";
        File pluginYml = new File(path);
        if (pluginYml.exists()) {
            pluginYml.delete();
        }

        // This extracts the plugin yml file
        zip.extractFile("plugin.yml", path);

        return pluginYml;
    }

}
