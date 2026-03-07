package com.mainlert.data.models

/**
 * Exception thrown when Firebase permission rules deny access to a resource.
 * This is used to halt service startup when mappings cannot be created due to permissions.
 */
class PermissionDeniedException(message: String) : Exception(message)