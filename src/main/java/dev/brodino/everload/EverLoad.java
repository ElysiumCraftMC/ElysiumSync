package dev.brodino.everload;

import dev.brodino.everload.sync.SyncContext;
import dev.brodino.everload.sync.SyncScheduler;
import dev.brodino.everload.util.AsyncExecutor;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EverLoad implements ModInitializer {
	private static final int BLOCKING_SYNC_TIMEOUT_SECONDS = 300; // 5 minutes
	public static final String MOD_ID = "everload";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Config CONFIG = new Config();

	@Override
	public void onInitialize() {
		initialize();
		
		// Check if pre-launch sync was already performed
		if (EverLoadPreLaunch.wasPreLaunchSyncPerformed()) {
			if (EverLoadPreLaunch.wasPreLaunchSyncSuccessful()) {
				LOGGER.info("Pre-launch sync was successful, skipping initialization sync");
			} else {
				LOGGER.warn("Pre-launch sync failed, files may not be up to date");
			}
		} else {
			// Fallback: perform blocking sync during mod initialization if pre-launch didn't run
			// This ensures files are synced BEFORE KubeJS or other mods load their scripts
			LOGGER.info("Pre-launch sync was not performed, running sync now");
			performEarlySync();
		}
	}

	public static void initialize() {
		LOGGER.info("Initializing EverLoad");
		AsyncExecutor.initialize();
	}
	
	/**
	 * Perform an early blocking sync during mod initialization
	 * This ensures that files are synced BEFORE other mods (like KubeJS) have a chance to load their scripts
	 * This method should be called from the mod's main initialization, not from client initialization, to ensure it runs early enough
	 * @return true if sync completed successfully, false otherwise
	 */
	public static boolean performEarlySync() {
		if (CONFIG.isDisabled()) {
			LOGGER.info("Sync is disabled, skipping early sync");
			return true; // Not an error, just disabled
		}
		
		if (!CONFIG.hasRepository()) {
			LOGGER.info("No repository configured, skipping early sync");
			return true; // Not an error, just not configured
		}

		String repoUrl = CONFIG.getRepositoryUrl();
		String branch = CONFIG.getBranch();
		
		if (repoUrl == null || repoUrl.trim().isEmpty()) {
			LOGGER.info("Repository URL is empty, skipping early sync");
			return true; // Not an error, just empty
		}
		
		LOGGER.info("Performing early blocking sync to ensure files are ready before other mods load");
		LOGGER.info("Repository: {} (branch: {})", repoUrl, branch);
		
		boolean success = SyncScheduler.startBlockingSync(repoUrl, branch, BLOCKING_SYNC_TIMEOUT_SECONDS);
		
		if (success) {
			LOGGER.info("Early sync completed successfully - files are ready for other mods");
		} else {
			LOGGER.warn("Early sync failed or timed out - other mods may not have the latest files");
		}
		
		return success;
	}
	
	/**
	 * Trigger a manual sync on a background thread (e.g. from the disconnect screen button).
	 * Shows the ChangeConfirmationDialog just like the startup flow.
	 * @param onComplete Called on the calling thread when sync finishes (success or failure)
	 */
	public static void triggerManualSync(Runnable onComplete) {
		if (CONFIG.isDisabled()) {
			LOGGER.info("Sync is disabled, skipping manual sync");
			if (onComplete != null) onComplete.run();
			return;
		}

		if (!CONFIG.hasRepository()) {
			LOGGER.info("No repository configured, skipping manual sync");
			if (onComplete != null) onComplete.run();
			return;
		}

		String repoUrl = CONFIG.getRepositoryUrl();
		String branch = CONFIG.getBranch();

		LOGGER.info("Triggering manual sync from disconnect screen");

		SyncScheduler.startSync(
			repoUrl,
			branch,
			SyncContext.Type.MANUAL,
			onComplete,
			e -> {
				LOGGER.error("Manual sync failed: {}", e.getMessage(), e);
				if (onComplete != null) onComplete.run();
			}
		);
	}

	public static void shutdown() {
		LOGGER.info("Shutting down EverLoad");
		AsyncExecutor.shutdown();
	}
}
