package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.repository.Transactor

/** Si el bloque lanza una excepción, SQLite deshace todo lo que se hizo dentro. */
class SqlTransactor(private val db: ParkEasyDatabase) : Transactor {
    override fun <T> run(block: () -> T): T = db.transactionWithResult { block() }
}
