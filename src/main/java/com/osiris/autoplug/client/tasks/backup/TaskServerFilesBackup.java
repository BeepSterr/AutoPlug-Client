/*
 * Copyright Osiris Team
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import com.osiris.autoplug.client.configs.BackupConfig;
import com.osiris.autoplug.client.configs.SystemConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.minecraft.Server;
import com.osiris.autoplug.client.utils.ConfigUtils;
import com.osiris.autoplug.client.utils.CoolDownReport;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.core.logger.AL;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.betterthread.BetterWarning;
import de.kastenklicker.upload.Upload;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.lang.time.DateUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class TaskServerFilesBackup extends BetterThread {

    private final File autoplug_backups_server = new File(GD.WORKING_DIR + "/autoplug-backups/server");
    private final File autoplug_backups_plugins = new File(GD.WORKING_DIR + "/autoplug-backups/plugins");
    private final File autoplug_backups_worlds = new File(GD.WORKING_DIR + "/autoplug-backups/worlds");

    private final LocalDateTime date = LocalDateTime.now();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH.mm");
    private final String formattedDate = date.format(formatter);

    public TaskServerFilesBackup(String name, BetterThreadManager manager) {
        super(name, manager);
    }


    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        createBackupServerFiles();
    }

    private void createBackupServerFiles() throws Exception {
        if (Server.isRunning()) throw new Exception("Cannot perform backup while server is running!");

        BackupConfig config = new BackupConfig();
        // Do cool-down check stuff
        String format = "dd/MM/yyyy HH:mm:ss";
        CoolDownReport coolDownReport = new ConfigUtils().checkIfOutOfCoolDown(
                config.backup_server_files_cool_down.asInt(),
                new SimpleDateFormat(format),
                new SystemConfig().timestamp_last_server_files_backup_task.asString()); // Get the report first before saving any new values
        if (!coolDownReport.isOutOfCoolDown()) {
            this.skip("Skipped. Cool-down still active (" + (((coolDownReport.getMsRemaining() / 1000) / 60)) + " minutes remaining).");
            return;
        }
        // Update the cool-down with current time
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.timestamp_last_server_files_backup_task.setValues(LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)));
        systemConfig.save(); // Save the current timestamp to file


        String server_backup_dest = autoplug_backups_server.getAbsolutePath() + "/server-files-backup-" + formattedDate + ".zip";
        int max_days_server = config.backup_server_files_max_days.asInt();

        //Removes files older than user defined days
        if (max_days_server <= 0) {
            setStatus("Skipping delete of older backups...");
        } else {
            Date oldestAllowedFileDate = DateUtils.addDays(new Date(), -max_days_server); //minus days from current date
            Iterator<File> filesToDelete = FileUtils.iterateFiles(autoplug_backups_server, new AgeFileFilter(oldestAllowedFileDate), null);
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            int deleted_files = 0;
            while (filesToDelete.hasNext()) {
                deleted_files++;
                FileUtils.deleteQuietly(filesToDelete.next());
                setStatus("Deleting backups older than " + max_days_server + " days... Deleted: " + deleted_files + " zips");
            }  //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            setStatus("Deleting backups older than " + max_days_server + " days... Deleted: " + deleted_files + " zips");
        }
        Thread.sleep(1000);


        if (config.backup_server_files.asBoolean()) {
            setStatus("Creating backup zip...");
            List<File> serverFiles = new FileManager().serverFiles();
            ZipFile zip = new ZipFile(server_backup_dest);

            setMax(serverFiles.size());

            //Add each file to the zip
            for (File file : serverFiles) {
                setStatus("Backing up server-files... " + file.getName());
                try {
                    zip.addFile(file);
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to add " + file.getName() + " to zip."));
                }
                step();
            }

            //Upload
            if (config.backup_server_files_upload.asBoolean()) {

                setStatus("Uploading server-files backup...");

                Upload upload = new Upload(config.backup_server_files_upload_host.asString(),
                        config.backup_server_files_upload_port.asInt(),
                        config.backup_server_files_upload_user.asString(),
                        config.backup_server_files_upload_password.asString(),
                        config.backup_server_files_upload_path.asString(),
                        zip.getFile());

                String rsa = config.backup_server_files_upload_rsa.asString();
                try {
                    if (rsa == null || rsa.trim().isEmpty()) upload.ftps();
                    else upload.sftp(rsa.trim());

                    if (config.backup_server_files_upload_delete_on_complete.asBoolean())
                        zip.getFile().delete();
                } catch (Exception e) {
                    getWarnings().add(new BetterWarning(this, e, "Failed to upload server-files backup."));
                }

                if (getWarnings().size() > 0)
                    setStatus("Completed backup & upload (" + getWarnings().size() + " warnings).");
                else
                    setStatus("Completed backup & upload.");
            } else {
                if (getWarnings().size() > 0)
                    setStatus("Completed backup & skipped upload (" + getWarnings().size() + " warnings).");
                else
                    setStatus("Completed backup & skipped upload.");
            }

            AL.debug(this.getClass(), "Created server-files backup to: " + server_backup_dest);
        } else
            skip();

        finish();
    }

}
