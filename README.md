# EverLoad

A simple mod that automatically syncs files from a Git repository to your Minecraft instance directory.  
Best used in concomitance with mods such as [KubeJS](https://github.com/KubeJS-Mods/KubeJS/tree/1902) to load scripts and datapacks before client initialization

## Features

- **Automatic Git Sync** - Clones or pulls from a configured Git repository at game startup
- **Pre-Launch Synchronization** - Syncs files before other mods load, ensuring scripts (e.g., KubeJS) are updated before they're parsed
- **Progress UI** - Visual progress screen showing sync status, file count, and elapsed time
- **In-Game Commands** - Manually trigger syncs, check status, or reload configuration
- **Repository Change Detection** - Automatically re-clones if the configured repository URL changes

## Configuration

```json
{
  "repositoryUrl": "https://github.com/username/my-scripts",
  "branch": "main",
  "enabled": true
}
```

| Option          | Type    | Default  | Description                       |
|-----------------|---------|----------|-----------------------------------|
| `repositoryUrl` | String  | `""`     | Git repository URL to sync from   |
| `branch`        | String  | `"main"` | Branch to sync                    |
| `enabled`       | Boolean | `true`   | Enable/disable sync functionality |

## Commands

| Command             | Description                                 |
|---------------------|---------------------------------------------|
| `/everload refresh` | Manually trigger a sync                     |
| `/everload status`  | Show current sync state and repository info |
| `/everload reload`  | Reload the configuration file               |

## Use Cases

- **Modpack Developers** - Keep KubeJS scripts and configs synced across players
- **Server Administrators** - Automatically update client-side resources from a central repository

## License

See [LICENSE](LICENSE.txt) for details.
