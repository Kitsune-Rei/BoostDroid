package com.boostdroid.app

import android.graphics.drawable.Drawable
import android.util.LruCache
import java.lang.ref.WeakReference

class IconCache private constructor() {
    // 6.5 Bitmaps/Drawables stored as WeakReferences
    private val cache = LruCache<String, WeakReference<Drawable>>(50)

    fun get(packageName: String): Drawable? {
        return cache.get(packageName)?.get()
    }

    fun put(packageName: String, icon: Drawable) {
        cache.put(packageName, WeakReference(icon))
    }

    private fun clearInternal() {
        cache.evictAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: IconCache? = null

        fun getInstance(): IconCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IconCache().also { INSTANCE = it }
            }
        }

        fun clear() {
            INSTANCE?.clearInternal()
        }
    }
}