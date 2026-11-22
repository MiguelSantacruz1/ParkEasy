package com.ejemplo.parkeasy

import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.data.repository.SqlChangeLogRepository
import com.ejemplo.parkeasy.data.repository.SqlFloorRepository
import com.ejemplo.parkeasy.data.repository.SqlParkingRecordRepository
import com.ejemplo.parkeasy.data.repository.SqlSpotRepository
import com.ejemplo.parkeasy.data.repository.SqlTariffRepository
import com.ejemplo.parkeasy.data.repository.SqlTransactor
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.service.ChangeLogger
import com.ejemplo.parkeasy.domain.service.CheckInRequest
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.domain.service.ParkingService
import com.ejemplo.parkeasy.domain.service.SpotService
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Reloj de pruebas que se puede mover a mano. */
class MutableClock(var now: Instant = Instant.parse("2026-11-22T15:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/** Base SQLite en memoria con todos los repositorios reales. */
class TestEnv(val clock: MutableClock = MutableClock()) : AutoCloseable {
    val driver: SqlDriver = DatabaseFactory.createInMemoryDriver()
    val db: ParkEasyDatabase = DatabaseFactory.create(driver)
    val floors = SqlFloorRepository(db, Dispatchers.Unconfined)
    val spots = SqlSpotRepository(db, Dispatchers.Unconfined)
    val records = SqlParkingRecordRepository(db, Dispatchers.Unconfined)
    val changeLog = SqlChangeLogRepository(db)
    val tariffs = SqlTariffRepository(db)
    val transactor = SqlTransactor(db)

    val logger = ChangeLogger(changeLog, clock)
    val floorService = FloorService(floors, spots, logger, transactor)
    val spotService = SpotService(spots, floors, logger, transactor)
    val parkingService = ParkingService(spots, floors, records, logger, transactor, clock)

    /** Crea un piso y un puesto de carro libre; devuelve el id del puesto. */
    fun floorWithSpot(label: String = "A-01", type: SpotType = SpotType.CAR): Long {
        val floorId = floors.getAll().firstOrNull()?.id ?: floorService.add("Piso 1")
        return spotService.add(NewSpot(floorId, label, type))
    }

    fun checkIn(
        spotId: Long,
        plate: String = "ABC123",
        vehicle: VehicleType = VehicleType.CAR,
        at: Instant = clock.now.minusSeconds(3_600),
    ): Long = parkingService.checkIn(CheckInRequest(spotId, plate, "Rojo", vehicle, at))

    override fun close() = driver.close()
}

/** Abre un entorno de pruebas y lo cierra al terminar. Devuelve Unit para que JUnit descubra el test. */
fun withEnv(block: (TestEnv) -> Unit) = TestEnv().use(block)
