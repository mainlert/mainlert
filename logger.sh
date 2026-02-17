#!/bin/bash

clear

# MainLert App Logcat Monitor Script
APP_PACKAGE="com.mainlert.mainlertapp"
LOG_FILE="logger"
LOG_LEVEL="I"
BUFFER_SIZE="1000"
DATE_FORMAT="%Y-%m-%d_%H-%M-%S"
PID_FILE="logger.pid"

# Settings
INTERACTIVE_MODE=false
CLEAR_LOGS=false
CLEAR_DEVICE_BUFFER=false
SPECIFIED_DEVICE=""
TAG_FILTER=""
MESSAGE_FILTER=""
STOP_LOGGER=false
LIST_DEVICES_ONLY=false
SELECTED_DEVICE=""
SELECTED_DEVICE_INFO=""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;37m'
DARK_GRAY='\033[1;30m'
LIGHT_RED='\033[1;31m'
LIGHT_GREEN='\033[1;32m'
LIGHT_YELLOW='\033[1;33m'
LIGHT_BLUE='\033[1;34m'
LIGHT_PURPLE='\033[1;35m'
LIGHT_CYAN='\033[1;36m'
BG_RED='\033[41m'
BG_GREEN='\033[42m'
BG_YELLOW='\033[43m'
BG_BLUE='\033[44m'
BG_PURPLE='\033[45m'
BG_CYAN='\033[46m'
BG_WHITE='\033[47m'
BOLD='\033[1m'
RESET='\033[0m'
NC='\033[0m'

print_info() { echo -e "${LIGHT_BLUE}${BOLD}[INFO]${NC} $1"; }
print_success() { echo -e "${LIGHT_GREEN}${BOLD}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${LIGHT_YELLOW}${BOLD}[WARNING]${NC} $1"; }
print_error() { echo -e "${LIGHT_RED}${BOLD}[ERROR]${NC} $1"; }
print_header() {
    echo -e "${BG_BLUE}${WHITE}${BOLD}========================================${NC}"
    echo -e "${BG_BLUE}${WHITE}${BOLD}$1${NC}"
    echo -e "${BG_BLUE}${WHITE}${BOLD}========================================${NC}"
}
print_subheader() { echo -e "${LIGHT_CYAN}${BOLD}--- $1 ---${NC}"; }
print_status() { echo -e "${LIGHT_PURPLE}${BOLD}[STATUS]${NC} $1"; }

get_connected_devices() {
    adb devices | grep -v "^List" | grep -v "^$" | awk '{print $1}'
}

get_device_info() {
    local serial="$1"
    local model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    local brand=$(adb -s "$serial" shell getprop ro.product.brand 2>/dev/null | tr -d '\r')
    if [[ -n "$model" && -n "$brand" ]]; then
        echo "$brand $model"
    elif [[ -n "$model" ]]; then
        echo "$model"
    else
        echo "Unknown Device"
    fi
}

list_available_devices() {
    local devices=($(get_connected_devices))
    local count=${#devices[@]}
    
    if [[ $count -eq 0 ]]; then
        print_error "No Android devices found"
        return 1
    fi
    
    print_info "Available Android devices:"
    echo ""
    
    for i in "${!devices[@]}"; do
        local serial="${devices[$i]}"
        local device_info=$(get_device_info "$serial")
        echo -e "  ${LIGHT_CYAN}${BOLD}$((i+1)).${NC} ${WHITE}$serial${NC} - $device_info"
    done
    
    echo ""
    return 0
}

show_usage() {
    print_header "MainLert Logger"
    echo ""
    echo -e "${LIGHT_CYAN}${BOLD}Usage:${NC} ${WHITE}$0${NC} [OPTIONS]"
    echo ""
    echo -e "  ${LIGHT_GREEN}-d, --device${NC} ${LIGHT_YELLOW}SERIAL${NC}   Device serial (optional)"
    echo -e "  ${LIGHT_GREEN}-l, --level${NC} ${LIGHT_YELLOW}LEVEL${NC}     Log level [V/D/I/W/E/A]"
    echo -e "  ${LIGHT_GREEN}-f, --file${NC} ${LIGHT_YELLOW}FILE${NC}       Output file name"
    echo -e "  ${LIGHT_GREEN}-c${NC}                     Clear log file"
    echo -e "  ${LIGHT_GREEN}-C${NC}                     Clear device buffer"
    echo -e "  ${LIGHT_GREEN}--list-devices${NC}         List devices"
    echo -e "  ${LIGHT_GREEN}-s${NC}                     Stop logger"
    echo -e "  ${LIGHT_GREEN}-h${NC}                     Help"
    echo ""
    echo -e "${LIGHT_CYAN}Examples:${NC}"
    echo -e "  $0                      # Interactive mode"
    echo -e "  $0 -d emulator-5554"
    echo -e "  $0 -d 192.168.0.104 -l D -C"
    echo -e "  $0 --list-devices"
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -l|--level) LOG_LEVEL="$2"; shift 2 ;;
            -f|--file) LOG_FILE="$2"; shift 2 ;;
            -c) CLEAR_LOGS=true; shift ;;
            -C) CLEAR_DEVICE_BUFFER=true; shift ;;
            -t|--tag) TAG_FILTER="$2"; shift 2 ;;
            -m|--message) MESSAGE_FILTER="$2"; shift 2 ;;
            -s) STOP_LOGGER=true; shift ;;
            -d|--device) SPECIFIED_DEVICE="$2"; shift 2 ;;
            --list-devices) LIST_DEVICES_ONLY=true; shift ;;
            -h|--help) show_usage; exit 0 ;;
            *) print_error "Unknown option: $1"; show_usage; exit 1 ;;
        esac
    done
}

check_adb() {
    print_subheader "Checking ADB"
    
    if ! command -v adb &> /dev/null; then
        print_error "ADB not found"
        exit 1
    fi
    
    print_success "ADB found"
    
    if [[ "$LIST_DEVICES_ONLY" == "true" ]]; then
        list_available_devices
        exit 0
    fi
    
    if [[ -n "$SPECIFIED_DEVICE" ]]; then
        # Try to match the specified device (handles IP:port, emulator names, etc.)
        if adb devices | grep -qE "^${SPECIFIED_DEVICE}[[:space:]]"; then
            SELECTED_DEVICE="$SPECIFIED_DEVICE"
        elif adb devices | grep -qE "^${SPECIFIED_DEVICE}:"; then
            SELECTED_DEVICE=$(adb devices | grep "^${SPECIFIED_DEVICE}:" | awk '{print $1}')
        else
            local matching_devices=$(adb devices | grep "^${SPECIFIED_DEVICE}" | awk '{print $1}')
            if [[ -n "$matching_devices" ]]; then
                SELECTED_DEVICE=$(echo "$matching_devices" | head -1)
            else
                print_error "Device '$SPECIFIED_DEVICE' not connected"
                list_available_devices
                exit 1
            fi
        fi
        SELECTED_DEVICE_INFO=$(get_device_info "$SELECTED_DEVICE")
        print_success "Using: $SELECTED_DEVICE ($SELECTED_DEVICE_INFO)"
    else
        # No device specified - show interactive selection
        local devices=($(get_connected_devices))
        local count=${#devices[@]}
        
        if [[ $count -eq 0 ]]; then
            print_error "No Android devices found"
            exit 1
        elif [[ $count -eq 1 ]]; then
            SELECTED_DEVICE="${devices[0]}"
            SELECTED_DEVICE_INFO=$(get_device_info "$SELECTED_DEVICE")
            print_success "Auto-selected: $SELECTED_DEVICE ($SELECTED_DEVICE_INFO)"
        else
            print_info "Multiple devices found. Please select:"
            echo ""
            for i in "${!devices[@]}"; do
                local serial="${devices[$i]}"
                local device_info=$(get_device_info "$serial")
                echo -e "  ${LIGHT_CYAN}$((i+1))${NC}. $serial - $device_info"
            done
            echo ""
            echo -e -n "${LIGHT_YELLOW}${BOLD}Enter device number (1-${count}): ${NC}"
            read -r choice
            
            if [[ -z "$choice" || ! "$choice" =~ ^[0-9]+$ ]] || [[ $choice -lt 1 || $choice -gt $count ]]; then
                print_error "Invalid selection"
                exit 1
            fi
            
            SELECTED_DEVICE="${devices[$((choice-1))]}"
            SELECTED_DEVICE_INFO=$(get_device_info "$SELECTED_DEVICE")
            print_success "Selected: $SELECTED_DEVICE ($SELECTED_DEVICE_INFO)"
        fi
    fi
}

show_config_menu() {
    print_header "Logger Configuration"
    
    echo -e "Current settings:"
    echo ""
    echo -e "  ${LIGHT_CYAN}1.${NC} Log Level:      ${LIGHT_GREEN}${LOG_LEVEL}${NC} (V=Verbose, D=Debug, I=Info, W=Warning, E=Error, A=Assert)"
    echo -e "  ${LIGHT_CYAN}2.${NC} Clear Buffer:   ${LIGHT_GREEN}$(if [[ "$CLEAR_DEVICE_BUFFER" == "true" ]]; then echo "YES"; else echo "NO"; fi)${NC}"
    echo -e "  ${LIGHT_CYAN}3.${NC} Output File:    ${LIGHT_GREEN}${LOG_FILE}${NC}"
    echo ""
    echo -e "  ${LIGHT_CYAN}4.${NC} ${WHITE}Start Logging${NC}"
    echo -e "  ${LIGHT_CYAN}5.${NC} ${WHITE}Cancel${NC}"
    echo ""
    echo -e -n "${LIGHT_YELLOW}${BOLD}Choose option (1-5): ${NC}"
    read -r choice
    
    case "$choice" in
        1)
            echo ""
            echo "Log levels: V=Verbose, D=Debug, I=Info, W=Warning, E=Error, A=Assert"
            echo -e -n "Enter level [${LOG_LEVEL}]: "
            read -r new_level
            if [[ -n "$new_level" && "$new_level" =~ ^[VDIWEA]$ ]]; then
                LOG_LEVEL="$new_level"
                print_success "Log level: $LOG_LEVEL"
            fi
            show_config_menu
            ;;
        2)
            if [[ "$CLEAR_DEVICE_BUFFER" == "true" ]]; then
                CLEAR_DEVICE_BUFFER=false
                print_success "Buffer clearing: OFF"
            else
                CLEAR_DEVICE_BUFFER=true
                print_success "Buffer clearing: ON"
            fi
            show_config_menu
            ;;
        3)
            echo -e -n "Enter output file [${LOG_FILE}]: "
            read -r new_file
            if [[ -n "$new_file" ]]; then
                LOG_FILE="$new_file"
                print_success "Output file: $LOG_FILE"
            fi
            show_config_menu
            ;;
        4)
            print_success "Starting with current config..."
            ;;
        5)
            print_warning "Cancelled"
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            show_config_menu
            ;;
    esac
}

stop_logger() {
    if [[ -f "$PID_FILE" ]]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            rm -f "$PID_FILE"
            print_success "Logger stopped"
        else
            rm -f "$PID_FILE"
        fi
    fi
    exit 0
}

setup_logging() {
    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null
    
    if [[ "$CLEAR_LOGS" == "true" && -f "$LOG_FILE" ]]; then
        > "$LOG_FILE"
    fi
    
    SESSION_LOG_FILE="$LOG_FILE"
    
    {
        echo "========================================"
        echo "MainLert Logcat Session"
        echo "Started: $(date)"
        echo "Package: $APP_PACKAGE"
        echo "Device: $SELECTED_DEVICE"
        echo "Log Level: $LOG_LEVEL"
        echo "========================================"
        echo ""
    } > "$SESSION_LOG_FILE"
}

# Tag colors for easy identification
declare -A TAG_COLORS=(
    ["AdminInitializer"]="${LIGHT_PURPLE}"
    ["DashboardViewModel"]="${LIGHT_CYAN}"
    ["AccelerometerService"]="${LIGHT_GREEN}"
    ["Dashboard"]="${LIGHT_YELLOW}"
    ["RoundedButton"]="${LIGHT_RED}"
    ["SplashActivity"]="${LIGHT_BLUE}"
    ["MainLert"]="${GREEN}"
    ["MainLertApp"]="${CYAN}"
    ["MainLertService"]="${YELLOW}"
    ["MainLertActivity"]="${PURPLE}"
    ["UserManagementScreen"]="${LIGHT_MAGENTA}"
)

colorize_line() {
    local line="$1"
    
    # Apply colors based on tag
    for tag in "${!TAG_COLORS[@]}"; do
        local color="${TAG_COLORS[$tag]}"
        # Match tag in logcat format: .../Tag( PID): message
        if [[ "$line" =~ /${tag}\( ]]; then
            line=$(echo "$line" | sed "s|/${tag}\(|/${color}${tag}${NC}(|g")
            break
        fi
    done
    
    echo "$line"
}

build_logcat_command() {
    local cmd="adb -s $SELECTED_DEVICE logcat -v threadtime"
    cmd="$cmd *:$LOG_LEVEL"
    cmd="$cmd $APP_PACKAGE:*"
    
    # Add all log tags
    for tag in "${!TAG_COLORS[@]}"; do
        cmd="$cmd ${tag}:*"
    done
    
    if [[ -n "$TAG_FILTER" ]]; then
        cmd="$cmd $TAG_FILTER:*"
    fi
    
    if [[ -n "$MESSAGE_FILTER" ]]; then
        cmd="$cmd | grep -i '$MESSAGE_FILTER'"
    fi
    
    cmd="$cmd | grep -v -E '(android\.|system\.|framework\.|com\.google\.|ActivityManager|WindowManager|PackageManager)'"
    
    # Match any of our tags
    local tags_pattern=$(echo "${!TAG_COLORS[@]}" | sed 's/ /|/g')
    cmd="$cmd | grep -E '($APP_PACKAGE|${tags_pattern})'"
    
    echo "$cmd"
}

start_logging() {
    local logcat_cmd=$(build_logcat_command)
    
    print_header "Starting Logger"
    echo -e "  Device:  $SELECTED_DEVICE ($SELECTED_DEVICE_INFO)"
    echo -e "  Level:   $LOG_LEVEL"
    echo -e "  File:    $SESSION_LOG_FILE"
    echo ""
    echo -e "${BG_GREEN}${BLACK}${BOLD} >>> LOGGING STARTED - Ctrl+C to stop <<< ${NC}"
    echo ""
    
    # Print legend
    echo -e "${BOLD}Tag Color Legend:${NC}"
    for tag in "${!TAG_COLORS[@]}"; do
        echo -e "  ${TAG_COLORS[$tag]}${tag}${NC}"
    done
    echo ""
    
    echo $$ > "$PID_FILE"
    
    eval "$logcat_cmd" | while IFS= read -r line; do
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] $line" >> "$SESSION_LOG_FILE"
        colorize_line "$line"
    done
    
    echo ""
    echo -e "${BG_RED}${WHITE}${BOLD} >>> LOGGING STOPPED <<< ${NC}"
    
    rm -f "$PID_FILE"
}

main() {
    parse_args "$@"
    
    if [[ "$STOP_LOGGER" == "true" ]]; then
        stop_logger
    fi
    
    check_adb
    
    if [[ "$LIST_DEVICES_ONLY" == "true" ]]; then
        exit 0
    fi
    
    # Show interactive config menu
    show_config_menu
    
    if [[ "$CLEAR_DEVICE_BUFFER" == "true" ]]; then
        print_info "Clearing device buffer..."
        adb -s "$SELECTED_DEVICE" logcat -c
    fi
    
    setup_logging
    start_logging
}

main "$@"
