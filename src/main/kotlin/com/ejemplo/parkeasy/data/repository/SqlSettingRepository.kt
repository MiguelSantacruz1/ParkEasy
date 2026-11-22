package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.repository.SettingRepository

class SqlSettingRepository(db: ParkEasyDatabase) : SettingRepository {
    private val queries = db.settingQueries

    override fun get(key: String): String? = queries.selectByKey(key).executeAsOneOrNull()

    override fun set(key: String, value: String) {
        queries.upsert(key, value)
    }

    override fun remove(key: String) {
        queries.deleteByKey(key)
    }
}
