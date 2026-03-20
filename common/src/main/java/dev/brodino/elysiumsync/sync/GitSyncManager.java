package dev.brodino.elysiumsync.sync;

import dev.brodino.elysiumsync.ElysiumSync;
import dev.brodino.elysiumsync.util.PathUtil;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitSyncManager {
    
    private Git git;
    private final Path repositoryDirectory;
    
    public GitSyncManager() {
        this.repositoryDirectory = PathUtil.getRepositoryDirectory();
    }
    
    /**
     * Sync a repository (clone if new, pull if exists)
     * @param repositoryUrl The Git repository URL
     * @param branch The branch to sync
     * @param context The sync context for progress tracking
     * @throws IOException if file operations fail
     * @throws GitAPIException if git operations fail
     */
    public void sync(String repositoryUrl, String branch, SyncContext context) throws IOException, GitAPIException {
        ElysiumSync.LOGGER.info("Starting Git sync: {} (branch: {})", repositoryUrl, branch);

        if (!PathUtil.isValidGitUrl(repositoryUrl)) {
            throw new IllegalArgumentException("Invalid Git URL: " + repositoryUrl);
        }

        PathUtil.ensureDirectoryExists(this.repositoryDirectory.getParent());
        
        File repoDir = this.repositoryDirectory.toFile();
        
        if (this.isRepositoryInitialized()) {
            // Check if the repository URL matches the configured URL
            if (this.isRepositoryUrlMatching(repositoryUrl)) {
                context.setStatusMessage("Pulling latest changes...");
                ElysiumSync.LOGGER.info("Repository exists, pulling updates from: {}", repoDir);
                this.pullRepository(branch, context);
            } else {
                String existingUrl = this.getExistingRemoteUrl();
                ElysiumSync.LOGGER.info("Repository URL mismatch. Existing: {}, Configured: {}. Re-cloning...",  existingUrl, repositoryUrl);
                context.setStatusMessage("Repository changed, re-cloning...");
                this.cleanupRepository();
                this.cloneRepository(repositoryUrl, branch, context);
            }
        } else {
            // Repository doesn't exist, clone it
            context.setStatusMessage("Cloning repository...");
            ElysiumSync.LOGGER.info("Cloning repository to: {}", repoDir);
            this.cloneRepository(repositoryUrl, branch, context);
        }
        
        ElysiumSync.LOGGER.info("Git sync completed successfully");
    }

    private void cloneRepository(String repositoryUrl, String branch, SyncContext context) throws GitAPIException {
        File repoDir = this.repositoryDirectory.toFile();
        
        // Create progress monitor
        JGitProgressMonitor progressMonitor = new JGitProgressMonitor(context);
        
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(repoDir)
                .setBranch(branch)
                .setCloneAllBranches(false) // Only specified branch
                .setProgressMonitor(progressMonitor);
        
        // Note: Shallow clone (depth=1) may not be available in JGit 5.13.3
        // If setDepth() is available in this version, it would be: .setDepth(1)
        // For now, we'll do a full clone to ensure compatibility
        
        ElysiumSync.LOGGER.info("Executing clone command: {} -> {}", repositoryUrl, repoDir);
        
        try {
            git = cloneCommand.call();
            ElysiumSync.LOGGER.info("Clone completed: {} files in repository", this.countFiles(repositoryDirectory));
        } catch (GitAPIException e) {
            ElysiumSync.LOGGER.error("Clone failed: {}", e.getMessage(), e);
            this.cleanupRepository();
            throw e;
        }
    }

    private void pullRepository(String branch, SyncContext context) throws IOException, GitAPIException {
        // Open existing repository
        git = Git.open(repositoryDirectory.toFile());
        
        // Create progress monitor
        JGitProgressMonitor progressMonitor = new JGitProgressMonitor(context);
        
        context.setStatusMessage("Fetching updates from remote...");
        
        PullCommand pullCommand = git.pull()
                .setProgressMonitor(progressMonitor)
                .setRemote("origin")
                .setRemoteBranchName(branch);
        
        ElysiumSync.LOGGER.info("Executing pull command for branch: {}", branch);
        
        try {
            var result = pullCommand.call();
            
            if (result.isSuccessful()) {
                ElysiumSync.LOGGER.info("Pull completed successfully");
                if (result.getFetchResult() != null) {
                    this.logFetchResult(result.getFetchResult());
                }
            } else {
                ElysiumSync.LOGGER.warn("Pull completed with issues: {}", result);
            }
        } catch (GitAPIException e) {
            ElysiumSync.LOGGER.error("Pull failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private boolean isRepositoryInitialized() {
        File repoDir = repositoryDirectory.toFile();
        File gitDir = new File(repoDir, ".git");
		return repoDir.exists() && gitDir.exists() && gitDir.isDirectory();
    }

    private String getExistingRemoteUrl() {
        if (!this.isRepositoryInitialized()) {
            return null;
        }
        
        try (Git existingGit = Git.open(this.repositoryDirectory.toFile())) {
            StoredConfig config = existingGit.getRepository().getConfig();
            return config.getString("remote", "origin", "url");
        } catch (IOException e) {
            ElysiumSync.LOGGER.warn("Failed to read remote URL from existing repository: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if the configured repository URL matches the existing repository
     * @param repositoryUrl The configured repository URL
     * @return true if URLs match, false otherwise
     */
    private boolean isRepositoryUrlMatching(String repositoryUrl) {
        String existingUrl = this.getExistingRemoteUrl();
        if (existingUrl == null) {
            return false;
        }

        String normalizedConfigUrl = this.normalizeGitUrl(repositoryUrl);
        String normalizedExistingUrl = this.normalizeGitUrl(existingUrl);
        
        return normalizedConfigUrl.equalsIgnoreCase(normalizedExistingUrl);
    }

    /**
     * Normalize a Git URL for comparison, removing trailing slashes and .git suffix
     * @param url The URL to normalize
     * @return The normalized URL
     */
    private String normalizeGitUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }
    
    /**
     * Clean up repository directory (used after failed clone)
     */
    private void cleanupRepository() {
        try {
            if (Files.exists(repositoryDirectory)) {
                ElysiumSync.LOGGER.info("Cleaning up failed repository at: {}", repositoryDirectory);
                this.deleteRecursively(repositoryDirectory);
            }
        } catch (IOException e) {
            ElysiumSync.LOGGER.error("Failed to cleanup repository", e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (!Files.isDirectory(path)) {
            Files.delete(path);
            return;
        }
        try (var stream = Files.list(path)) {
            stream.forEach(child -> {
                try {
                    deleteRecursively(child);
                } catch (IOException e) {
                    ElysiumSync.LOGGER.error("Failed to delete: {}", child, e);
                }
            });
        }

        Files.delete(path); // Deletes the directory after emptying
    }

    private int countFiles(Path directory) {
        try (var stream = Files.walk(directory)) {
            return (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void logFetchResult(FetchResult result) {
        ElysiumSync.LOGGER.info("Fetch result: {}", result.getMessages());
        result.getTrackingRefUpdates().forEach(update -> {
            ElysiumSync.LOGGER.info("  Updated ref: {} -> {}", 
                update.getRemoteName(), 
                update.getResult());
        });
    }

    public void close() {
        if (git != null) {
            git.close();
            git = null;
        }
    }
    
    /**
     * JGit ProgressMonitor implementation that updates SyncContext
     */
    private static class JGitProgressMonitor implements ProgressMonitor {
        private final SyncContext context;
        private String taskName;
        private int totalWork;
        private int completed;
        
        public JGitProgressMonitor(SyncContext context) {
            this.context = context;
        }
        
        @Override
        public void start(int totalTasks) {
            ElysiumSync.LOGGER.info("Git operation started: {} tasks", totalTasks);
        }
        
        @Override
        public void beginTask(String title, int totalWork) {
            this.taskName = title;
            this.totalWork = totalWork;
            this.completed = 0;
            context.setStatusMessage(title);
            context.setProgress(0, totalWork > 0 ? totalWork : 100);
            ElysiumSync.LOGGER.info("Task started: {} (total work: {})", title, totalWork);
        }
        
        @Override
        public void update(int completed) {
            this.completed += completed;
            if (totalWork > 0) {
                context.setProgress(this.completed, totalWork);
            }
        }
        
        @Override
        public void endTask() {
            context.setProgress(totalWork, totalWork);
            ElysiumSync.LOGGER.info("Task completed: {}", taskName);
        }
        
        @Override
        public boolean isCancelled() {
            return context.getState() == SyncState.CANCELLED;
        }

        @Override
        public void showDuration(boolean b) {}
    }
}
