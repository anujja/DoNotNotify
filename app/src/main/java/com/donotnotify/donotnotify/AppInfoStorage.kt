package com.donotnotify.donotnotify

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

class AppInfoDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "app_info.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "app_info"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_APP_NAME = "app_name"
        const val COLUMN_APP_ICON = "app_icon"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_PACKAGE_NAME + " TEXT PRIMARY KEY,"
                + COLUMN_APP_NAME + " TEXT,"
                + COLUMN_APP_ICON + " BLOB" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        onCreate(db)
    }
}

class AppInfoStorage(context: Context) {

    private val dbHelper = AppInfoDatabaseHelper(context)

    fun isAppInfoSaved(packageName: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AppInfoDatabaseHelper.TABLE_NAME,
            arrayOf(AppInfoDatabaseHelper.COLUMN_APP_NAME),
            "${AppInfoDatabaseHelper.COLUMN_PACKAGE_NAME} = ?",
            arrayOf(packageName),
            null,
            null,
            null
        )

        var appName: String? = null
        if (cursor.moveToFirst()) {
            appName = cursor.getString(cursor.getColumnIndexOrThrow(AppInfoDatabaseHelper.COLUMN_APP_NAME))
        }
        cursor.close()
        return appName
    }

    fun saveAppInfo(packageName: String, appName: String, icon: Drawable) {
        val db = dbHelper.writableDatabase
        val bitmap = drawableToBitmap(icon)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val iconBytes = stream.toByteArray()

        val values = ContentValues().apply {
            put(AppInfoDatabaseHelper.COLUMN_PACKAGE_NAME, packageName)
            put(AppInfoDatabaseHelper.COLUMN_APP_NAME, appName)
            put(AppInfoDatabaseHelper.COLUMN_APP_ICON, iconBytes)
        }

        db.insertWithOnConflict(AppInfoDatabaseHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAppIcon(packageName: String): Bitmap? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AppInfoDatabaseHelper.TABLE_NAME,
            arrayOf(AppInfoDatabaseHelper.COLUMN_APP_ICON),
            "${AppInfoDatabaseHelper.COLUMN_PACKAGE_NAME} = ?",
            arrayOf(packageName),
            null,
            null,
            null
        )

        var bitmap: Bitmap? = null
        if (cursor.moveToFirst()) {
            val iconBytes = cursor.getBlob(cursor.getColumnIndexOrThrow(AppInfoDatabaseHelper.COLUMN_APP_ICON))
            if (iconBytes != null) {
                bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
            }
        }
        cursor.close()
        return bitmap
    }
    
    fun getAppName(packageName: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AppInfoDatabaseHelper.TABLE_NAME,
            arrayOf(AppInfoDatabaseHelper.COLUMN_APP_NAME),
            "${AppInfoDatabaseHelper.COLUMN_PACKAGE_NAME} = ?",
            arrayOf(packageName),
            null,
            null,
            null
        )

        var appName: String? = null
        if (cursor.moveToFirst()) {
            appName = cursor.getString(cursor.getColumnIndexOrThrow(AppInfoDatabaseHelper.COLUMN_APP_NAME))
        }
        cursor.close()
        return appName
    }

    fun deleteAppInfo(packageName: String) {
        val db = dbHelper.writableDatabase
        db.delete(
            AppInfoDatabaseHelper.TABLE_NAME,
            "${AppInfoDatabaseHelper.COLUMN_PACKAGE_NAME} = ?",
            arrayOf(packageName)
        )
    }

    fun clearAllAppInfo() {
        val db = dbHelper.writableDatabase
        db.delete(AppInfoDatabaseHelper.TABLE_NAME, null, null)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
