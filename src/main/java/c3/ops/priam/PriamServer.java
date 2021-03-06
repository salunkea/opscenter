/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package c3.ops.priam;

import c3.ops.priam.aws.UpdateCleanupPolicy;
import c3.ops.priam.aws.UpdateSecuritySettings;
import c3.ops.priam.backup.CommitLogBackupTask;
import c3.ops.priam.backup.IncrementalBackup;
import c3.ops.priam.backup.Restore;
import c3.ops.priam.backup.SnapshotBackup;
import c3.ops.priam.identity.InstanceIdentity;
import c3.ops.priam.scheduler.PriamScheduler;
import c3.ops.priam.utils.CassandraMonitor;
import c3.ops.priam.utils.Sleeper;
import c3.ops.priam.utils.TuneCassandra;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start all tasks here - Property update task - Backup task - Restore task -
 * Incremental backup
 */
@Singleton
public class PriamServer {
  private static final int CASSANDRA_MONITORING_INITIAL_DELAY = 10;
  private static final Logger logger = LoggerFactory.getLogger(PriamServer.class);
  private final PriamScheduler scheduler;
  private final IConfiguration config;
  private final InstanceIdentity id;
  private final Sleeper sleeper;
  private final ICassandraProcess cassProcess;

  @Inject
  public PriamServer(IConfiguration config, PriamScheduler scheduler, InstanceIdentity id, Sleeper sleeper, ICassandraProcess cassProcess) {
    this.config = config;
    this.scheduler = scheduler;
    this.id = id;
    this.sleeper = sleeper;
    this.cassProcess = cassProcess;
  }

  public void intialize() throws Exception {
    if (id.getInstance().isOutOfService())
      return;

    // start to schedule jobs
    scheduler.start();

    // update security settings.
    if (config.isMultiDC()) {
      scheduler.runTaskNow(UpdateSecuritySettings.class);
      // sleep for 150 sec if this is a new node with new IP for SG to be updated by other seed nodes
      if (id.isReplace() || id.isTokenPregenerated())
        sleeper.sleep(150 * 1000);
      else if (UpdateSecuritySettings.firstTimeUpdated)
        sleeper.sleep(60 * 1000);

      scheduler.addTask(UpdateSecuritySettings.JOBNAME, UpdateSecuritySettings.class, UpdateSecuritySettings.getTimer(id));
    }

    // Run the task to tune Cassandra
    scheduler.runTaskNow(TuneCassandra.class);

    // restore from backup else start cassandra.
    if (!config.getRestoreSnapshot().equals(""))
      scheduler.addTask(Restore.JOBNAME, Restore.class, Restore.getTimer());
    else {
      cassProcess.start(true);
    }

        /*
         *  Run the delayed task (after 10 seconds) to Monitor Cassandra
         *  If Restore option is chosen, then Running Cassandra instance is stopped 
         *  Hence waiting for Cassandra to stop
         */
    scheduler.addTaskWithDelay(CassandraMonitor.JOBNAME, CassandraMonitor.class, CassandraMonitor.getTimer(), CASSANDRA_MONITORING_INITIAL_DELAY);

    // Start the snapshot backup schedule - Always run this. (If you want to
    // set it off, set backup hour to -1)
    if (config.getBackupHour() >= 0 && (CollectionUtils.isEmpty(config.getBackupRacs()) || config.getBackupRacs().contains(config.getRac()))) {
      scheduler.addTask(SnapshotBackup.JOBNAME, SnapshotBackup.class, SnapshotBackup.getTimer(config));

      // Start the Incremental backup schedule if enabled
      if (config.isIncrBackup())
        scheduler.addTask(IncrementalBackup.JOBNAME, IncrementalBackup.class, IncrementalBackup.getTimer());
    }

    if (config.isBackingUpCommitLogs()) {
      scheduler.addTask(CommitLogBackupTask.JOBNAME, CommitLogBackupTask.class, CommitLogBackupTask.getTimer(config));
    }

    //Set cleanup
    scheduler.addTask(UpdateCleanupPolicy.JOBNAME, UpdateCleanupPolicy.class, UpdateCleanupPolicy.getTimer());
  }

  public InstanceIdentity getId() {
    return id;
  }

  public PriamScheduler getScheduler() {
    return scheduler;
  }

  public IConfiguration getConfiguration() {
    return config;
  }

}
