package com.dbflow5.adapter

import com.dbflow5.database.DatabaseWrapper
import com.dbflow5.database.FlowCursor
import com.dbflow5.query.cache.MultiKeyCacheConverter
import com.dbflow5.query.cache.ModelCache
import com.dbflow5.structure.InvalidDBConfiguration

/**
 * Description:
 */
abstract class CacheAdapter<T : Any>(val modelCache: ModelCache<T, *>,
                                     val cachingColumnSize: Int = 1,
                                     val cacheConverter: MultiKeyCacheConverter<*>? = null) {

    /**
     * @param cursor The cursor to load caching id from.
     * @return The single cache column from cursor (if single).
     */
    open fun getCachingColumnValueFromCursor(cursor: FlowCursor): Any? = Unit

    /**
     * @param model The model to load cache column data from.
     * @return The single cache column from model (if single).
     */
    open fun getCachingColumnValueFromModel(model: T): Any? = Unit

    /**
     * Loads all primary keys from the [FlowCursor] into the inValues. The size of the array must
     * match all primary keys. This method gets generated when caching is enabled.
     *
     * @param inValues The reusable array of values to populate.
     * @param cursor   The cursor to load from.
     * @return The populated set of values to load from cache.
     */
    open fun getCachingColumnValuesFromCursor(inValues: Array<Any?>, cursor: FlowCursor): Array<Any>? = null

    /**
     * Loads all primary keys from the [TModel] into the inValues. The size of the array must
     * match all primary keys. This method gets generated when caching is enabled. It converts the primary fields
     * of the [TModel] into the array of values the caching mechanism uses.
     *
     * @param inValues The reusable array of values to populate.
     * @param TModel   The model to load from.
     * @return The populated set of values to load from cache.
     */
    open fun getCachingColumnValuesFromModel(inValues: Array<Any?>, TModel: T): Array<Any>? = null

    fun storeModelInCache(model: T) {
        modelCache.addModel(getCachingId(model), model)
    }

    fun storeModelsInCache(models: Collection<T>) {
        models.onEach { storeModelInCache(it) }
    }

    fun removeModelFromCache(model: T) {
        getCachingId(model)?.let { modelCache.removeModel(it) }
    }

    fun removeModelsFromCache(models: Collection<T>) {
        models.onEach { removeModelFromCache(it) }
    }

    fun clearCache() {
        modelCache.clear()
    }

    fun getCachingId(inValues: Array<Any>?): Any? = when {
        inValues?.size == 1 -> // if it exists in cache no matter the query we will use that one
            inValues.getOrNull(0)
        inValues != null -> cacheConverter?.getCachingKey(inValues)
                ?: throw InvalidDBConfiguration("For multiple primary keys, a public static MultiKeyCacheConverter field must" +
                        "be  marked with @MultiCacheField in the corresponding model class. The resulting key" +
                        "must be a unique combination of the multiple keys, otherwise inconsistencies may occur.")
        else -> null
    }

    open fun getCachingId(model: T): Any? =
            getCachingId(getCachingColumnValuesFromModel(arrayOfNulls(cachingColumnSize), model))


    /**
     * Reloads relationships when loading from [FlowCursor] in a model that's cacheable. By having
     * relationships with cached models, the retrieval will be very fast.
     *
     * @param cursor The cursor to reload from.
     */
    open fun reloadRelationships(model: T, cursor: FlowCursor, databaseWrapper: DatabaseWrapper) = Unit

}