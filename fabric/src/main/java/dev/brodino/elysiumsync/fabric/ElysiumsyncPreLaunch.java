package dev.brodino.elysiumsync.fabric;

import dev.brodino.elysiumsync.ElysiumSync;
import dev.brodino.elysiumsync.util.AsyncExecutor;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PreLaunch entrypoint that runs BEFORE any other mod initialization.
 * This ensures that files are synced from the repository before KubeJS
 * or any other mod has a chance to load their scripts.
 */
public class ElysiumsyncPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("elysiumsync");

    private static boolean preLaunchSyncPerformed = false;
    private static boolean preLaunchSyncSuccessful = false;
    
    @Override
    public void onPreLaunch() {
        LOGGER.info("ElysiumSync PreLaunch: Starting early sync to ensure files are ready before other mods");
        
        try {
            // Initialize AsyncExecutor early for pre-launch sync
            AsyncExecutor.initialize();
            
            // Delegate to the main ElysiumSync early sync implementation
            preLaunchSyncSuccessful = ElysiumSync.performEarlySync();
            preLaunchSyncPerformed = true;
            
            if (preLaunchSyncSuccessful) {
                LOGGER.info("ElysiumSync PreLaunch: Sync completed successfully");
            } else {
                LOGGER.warn("ElysiumSync PreLaunch: Sync failed or was skipped");
            }
        } catch (Exception e) {
            LOGGER.error("ElysiumSync PreLaunch: Failed to perform sync", e);
            preLaunchSyncPerformed = true;
            preLaunchSyncSuccessful = false;
        }
    }
    
    public static boolean wasPreLaunchSyncPerformed() {
        return preLaunchSyncPerformed;
    }
    
    public static boolean wasPreLaunchSyncSuccessful() {
        return preLaunchSyncSuccessful;
    }
}
