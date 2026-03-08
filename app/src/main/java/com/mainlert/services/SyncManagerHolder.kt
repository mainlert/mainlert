package com.mainlert.services

import com.mainlert.data.local.sync.SyncManager

/**
 * Simple holder to provide SyncManager access to Worker without custom WorkerFactory.
 * SyncManager is set from MainLertApplication.onCreate().
 */
object SyncManagerHolder {
    var syncManager: SyncManager? = null
}
