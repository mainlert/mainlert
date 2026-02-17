#!/bin/bash

# Color Demo Script for logger.sh
# This script demonstrates the enhanced color features

# Source the color definitions from logger.sh
source logger.sh

echo "=== MainLert Logger Color Enhancement Demo ==="
echo ""

# Test basic colored output functions
print_info "Testing basic colored output functions..."
print_success "Logger initialized successfully"
print_warning "Warning: High memory usage detected"
print_error "Error: ADB connection failed"

echo ""
print_subheader "Enhanced Output Functions"
print_status "Session configuration loaded"
print_debug "Debug mode enabled"
print_verbose "Verbose logging active"

echo ""
print_subheader "Log Level Color Functions"
echo "Testing log level color coding:"
color_log_verbose "VERBOSE: Detailed debug information"
color_log_debug "DEBUG: Service initialization started"
color_log_info "INFO: Application started successfully"
color_log_warn "WARNING: Battery level low"
color_log_error "ERROR: Network connection failed"
color_log_assert "ASSERT: Critical assertion failed"

echo ""
print_subheader "Visual Indicators"
echo -e "${BG_GREEN}${BLACK}${BOLD} >>> LOGGING STARTED <<< ${NC}"
echo -e "${BG_RED}${WHITE}${BOLD} >>> LOGGING STOPPED <<< ${NC}"
echo -e "${BG_YELLOW}${BLACK}${BOLD} Note: ADB connection required ${NC}"
echo -e "${BG_BLUE}${WHITE}${BOLD} Tip: Use -l V for verbose output ${NC}"

echo ""
print_header "Demo Complete"
print_success "All color functions working correctly"
print_info "Enhanced logger.sh is ready for use"