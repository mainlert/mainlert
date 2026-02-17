# MainLert App Logcat Monitor

This directory contains a shell script (`logger.sh`) that provides comprehensive logcat monitoring for the MainLert Android application.

## Overview

The `logger.sh` script captures and logs all Android application logs from the MainLert app (`com.mainlert.mainlertapp`) to a file. It provides real-time monitoring with filtering capabilities and session management.

## Features

- **Real-time logcat monitoring** with timestamping
- **Configurable log level filtering** (VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT)
- **Package-specific filtering** for MainLert app only
- **Tag and message filtering** for focused debugging
- **Session management** with automatic timestamped log files
- **Process management** with start/stop functionality
- **Enhanced colored console output** for better readability and debugging
- **Visual separators and status indicators** for improved user experience
- **Smart log level color coding** for real-time log analysis
- **Automatic cleanup** and error handling

## Prerequisites

- **Android SDK Platform Tools** installed with `adb` in PATH
- **Android device connected** or emulator running
- **MainLert app installed** on the target device

## Usage

### Basic Usage

```bash
# Start logging with default settings (DEBUG level)
./logger.sh

# Start logging with verbose level
./logger.sh -l V

# Start logging to a custom file
./logger.sh -f myapp_debug.log

# Clear previous logs and start fresh
./logger.sh -c
```

### Advanced Usage

```bash
# Filter by specific tag and message content
./logger.sh -t MainLert -m "Service"

# Use INFO level with custom output file
./logger.sh -l I -f production.log

# Stop a running logger instance
./logger.sh -s
```

### Command Line Options

| Option | Long Form | Description | Default |
|--------|-----------|-------------|---------|
| `-l` | `--level` | Log level filter (V, D, I, W, E, A) | D (DEBUG) |
| `-f` | `--file` | Output log file name | logger |
| `-c` | `--clear` | Clear previous logs before starting | false |
| `-t` | `--tag` | Additional tag filter | - |
| `-m` | `--message` | Message content filter | - |
| `-b` | `--buffer` | Buffer size for in-memory logs | 1000 |
| `-s` | `--stop` | Stop running logger instance | - |
| `-h` | `--help` | Show help message | - |

## Examples

### Development Debugging

```bash
# Capture all verbose logs for detailed debugging
./logger.sh -l V -f debug_session.log

# Monitor only MainLert-related logs
./logger.sh -t MainLert

# Filter for service-related messages
./logger.sh -m "Service" -m "Accelerometer"
```

### Production Monitoring

```bash
# Capture only warnings and errors
./logger.sh -l W -f production_issues.log

# Monitor specific components
./logger.sh -t "AccelerometerService" -t "FirebaseAuth"
```

### Session Management

```bash
# Start a new logging session
./logger.sh

# Stop the current session
./logger.sh -s

# View available log files
ls -la logger_*
```

## Log File Format

The script creates a simple log file named "logger" that gets overwritten on each run:

- **File name**: `logger` (overwrites previous content)
- **Content**: Complete logcat session with timestamps
- **Format**: Plain text with session metadata

Each log file includes:
- Session header with metadata
- Timestamped log entries
- Session footer with end time

### Log Entry Format

```
[2026-02-05 02:15:30] 02-05 02:15:30.123 1234-5678/com.mainlert.mainlertapp:D/MainLert: Service started
```

## Process Management

The script creates a PID file (`logger.pid`) to track running instances:

```bash
# Check if logger is running
ps -p $(cat logger.pid) 2>/dev/null

# Stop running logger
./logger.sh -s

# Manual cleanup (if needed)
rm -f logger.pid
```

## Troubleshooting

### ADB Not Found

```bash
# Install Android SDK Platform Tools
# Ubuntu/Debian:
sudo apt install adb

# macOS:
brew install android-platform-tools

# Windows: Download from Android Developer website
```

### No Device Found

```bash
# Check device connection
adb devices

# Start emulator
emulator -avd YourAVDName

# Enable USB debugging on device
```

### Permission Issues

```bash
# Make script executable
chmod +x logger.sh

# Run with proper permissions
./logger.sh
```

## Integration with Development Workflow

### Continuous Monitoring

```bash
# Start logger in background
./logger.sh -l V > /dev/null 2>&1 &

# Monitor logs in real-time
tail -f logger

# Stop when done
./logger.sh -s
```

### Automated Testing

```bash
# Start logger before test
./logger.sh -f test_session.log &

# Run tests
./gradlew connectedAndroidTest

# Stop logger after test
./logger.sh -s

# Analyze logs
grep -i "error\|exception" test_session.log
```

## Security Notes

- Log files may contain sensitive information
- Consider log rotation for long-running sessions
- Secure log files in production environments
- Review log content before sharing

## Performance Considerations

- High verbosity levels may impact device performance
- Large log files consume storage space
- Consider filtering to reduce log volume
- Use appropriate buffer sizes for memory usage

## Support

For issues with the logger script:
1. Check ADB connection and device status
2. Verify MainLert app is installed and running
3. Review script permissions and PATH settings
4. Check log file permissions and disk space

## License

This script is part of the MainLert project and follows the same licensing terms.