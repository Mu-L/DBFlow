package com.raizlabs.dbflow5.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import com.raizlabs.dbflow5.config.DBFlowDatabase
import com.raizlabs.dbflow5.config.FlowManager

/**
 * Description: Wraps around the [SQLiteOpenHelper] and provides extra features for use in this library.
 */
open class AndroidSQLiteOpenHelper(
    databaseDefinition: DBFlowDatabase,
    listener: DatabaseHelperListener?)
    : SQLiteOpenHelper(FlowManager.context,
    if (databaseDefinition.isInMemory) null else databaseDefinition.databaseFileName,
    null,
    databaseDefinition.databaseVersion), OpenHelper {

    private val databaseHelperDelegate: DatabaseHelperDelegate
    private var androidDatabase: AndroidDatabase? = null

    init {
        var backupHelper: OpenHelper? = null
        if (databaseDefinition.backupEnabled()) {
            // Temp database mirrors existing
            backupHelper = BackupHelper(FlowManager.context,
                DatabaseHelperDelegate.getTempDbFileName(databaseDefinition),
                databaseDefinition.databaseVersion, databaseDefinition)
        }

        databaseHelperDelegate = DatabaseHelperDelegate(listener, databaseDefinition, backupHelper)
    }

    override fun performRestoreFromBackup() {
        databaseHelperDelegate.performRestoreFromBackup()
    }

    override val delegate: DatabaseHelperDelegate?
        get() = databaseHelperDelegate

    override val isDatabaseIntegrityOk: Boolean
        get() = databaseHelperDelegate.isDatabaseIntegrityOk

    override fun backupDB() {
        databaseHelperDelegate.backupDB()
    }

    override val database: DatabaseWrapper
        get() {
            if (androidDatabase == null || !androidDatabase!!.database.isOpen) {
                androidDatabase = AndroidDatabase.from(writableDatabase)
            }
            return androidDatabase!!
        }

    /**
     * Set a listener to listen for specific DB events and perform an action before we execute this classes
     * specific methods.
     *
     * @param helperListener
     */
    override fun setDatabaseListener(helperListener: DatabaseHelperListener?) {
        databaseHelperDelegate.setDatabaseHelperListener(helperListener)
    }

    override fun onCreate(db: SQLiteDatabase) {
        databaseHelperDelegate.onCreate(AndroidDatabase.from(db))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        databaseHelperDelegate.onUpgrade(AndroidDatabase.from(db), oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        databaseHelperDelegate.onOpen(AndroidDatabase.from(db))
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        databaseHelperDelegate.onDowngrade(AndroidDatabase.from(db), oldVersion, newVersion)
    }

    override fun closeDB() {
        androidDatabase?.database?.close()
    }

    /**
     * Simple helper to manage backup.
     */
    private inner class BackupHelper(context: Context,
                                     name: String, version: Int,
                                     databaseDefinition: DBFlowDatabase)
        : SQLiteOpenHelper(context, name, null, version), OpenHelper {

        private var androidDatabase: AndroidDatabase? = null
        private val baseDatabaseHelper: BaseDatabaseHelper = BaseDatabaseHelper(databaseDefinition)

        override val database: DatabaseWrapper
            get() {
                if (androidDatabase == null) {
                    androidDatabase = AndroidDatabase.from(writableDatabase)
                }
                return androidDatabase!!
            }

        override fun performRestoreFromBackup() = Unit

        override val delegate: DatabaseHelperDelegate?
            get() = null

        override val isDatabaseIntegrityOk: Boolean
            get() = false

        override fun backupDB() {}

        override fun setDatabaseListener(helperListener: DatabaseHelperListener?) {}

        override fun onCreate(db: SQLiteDatabase) {
            baseDatabaseHelper.onCreate(AndroidDatabase.from(db))
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            baseDatabaseHelper.onUpgrade(AndroidDatabase.from(db), oldVersion, newVersion)
        }

        override fun onOpen(db: SQLiteDatabase) {
            baseDatabaseHelper.onOpen(AndroidDatabase.from(db))
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            baseDatabaseHelper.onDowngrade(AndroidDatabase.from(db), oldVersion, newVersion)
        }

        override fun closeDB() = Unit
    }

}
