package com.paulcoding.audibook.core.platform

import java.io.File

/** Where the app keeps its data ([files]) and disposable files ([cache]). */
class AppDirs(val files: File, val cache: File)

/** [autoBackupNote] explains automatic platform backups in Settings, if there are any. */
data class AppInfo(val versionName: String, val isDebug: Boolean, val autoBackupNote: String? = null)
