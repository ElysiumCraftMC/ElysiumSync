package dev.brodino.elysiumsync.sync;

import dev.brodino.elysiumsync.ElysiumSync;
import dev.brodino.elysiumsync.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Claude
 */
public class FileSyncService {
    
    private final Path repositoryRoot;
    private final Path instanceRoot;
    
    public FileSyncService() {
        this.repositoryRoot = PathUtil.getRepositoryDirectory();
        this.instanceRoot = PathUtil.getGameDirectory();
    }
    
    /**
     * Sync all files from repository to instance root.
     * @param context The sync context for progress tracking
     * @throws IOException if file operations fail
     */
    public void syncFiles(SyncContext context) throws IOException {
        context.setStatusMessage("Scanning repository files...");
        ElysiumSync.LOGGER.info("Starting file sync from {} to {}", repositoryRoot, instanceRoot);

        List<Path> filesToCopy = this.collectFiles();
        int totalFiles = filesToCopy.size();
        
        context.setTotalFiles(totalFiles);
        context.setFilesCopied(0);
        context.setStatusMessage("Copying files to instance...");
        
        ElysiumSync.LOGGER.info("Found {} files to sync", totalFiles);
        
        if (totalFiles == 0) {
            ElysiumSync.LOGGER.warn("No files found in repository to sync");
            return;
        }
        
        // Copy each file
        int copied = 0;
        long totalBytes = 0;
        
        for (Path sourceFile : filesToCopy) {
            // Check if cancelled
            if (context.getState() == SyncState.CANCELLED) {
                ElysiumSync.LOGGER.info("File sync cancelled by user");
                break;
            }
            
            try {
                long bytes = copyFileToInstance(sourceFile);
                totalBytes += bytes;
                copied++;
                
                context.setFilesCopied(copied);
                context.setBytesCopied(totalBytes);
                context.setProgress(copied, totalFiles);
                
                // Log every 10 files or at milestones
                if (copied % 10 == 0 || copied == totalFiles) {
                    ElysiumSync.LOGGER.info("Synced {}/{} files ({} MB)", 
                        copied, totalFiles, totalBytes / 1024 / 1024);
                }
                
            } catch (IOException e) {
                ElysiumSync.LOGGER.error("Failed to copy file: {}", sourceFile, e);
                // Continue with other files
            }
        }
        
        ElysiumSync.LOGGER.info("File sync completed: {} files, {} MB", 
            copied, totalBytes / 1024 / 1024);
        context.setStatusMessage("File sync completed");
    }
    
    /**
     * Collect all files in the repository (excluding .git)
     */
    private List<Path> collectFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        
        if (!Files.exists(repositoryRoot)) {
            ElysiumSync.LOGGER.warn("Repository directory does not exist: {}", repositoryRoot);
            return files;
        }
        
        Files.walkFileTree(repositoryRoot, new SimpleFileVisitor<Path>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return files;
    }
    
    /**
     * Copy a single file from repository to instance root, preserving structure
     * @param sourceFile The file in the repository
     * @return Number of bytes copied
     */
    private long copyFileToInstance(Path sourceFile) throws IOException {
        Path relativePath = repositoryRoot.relativize(sourceFile);
        Path destination = instanceRoot.resolve(relativePath);
        
        // Ensure parent directories exist
        Path parentDir = destination.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        // Copy file (overwrite if exists)
        Files.copy(sourceFile, destination, 
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
        
        long size = Files.size(destination);
        
        ElysiumSync.LOGGER.debug("Copied: {} -> {} ({} bytes)", 
            sourceFile.getFileName(), relativePath, size);
        
        return size;
    }
}
