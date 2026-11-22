package com.ejemplo.parkeasy.demo

import app.cash.sqldelight.db.SqlDriver
import com.ejemplo.parkeasy.data.db.AppProfile
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.SettingRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.service.CheckInRequest
import com.ejemplo.parkeasy.domain.service.FloorService
import com.ejemplo.parkeasy.domain.service.ParkingService
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.plan.PlanPersistence
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import java.time.Clock
import java.time.Duration

/**
 * Datos de prueba (TEST_DATA) para los perfiles `demo` y `perf`. Todo es ficticio: las placas llevan el
 * prefijo TST y las observaciones de cada registro dicen TEST_DATA. En producción esto no se ejecuta.
 */
class SampleData(
    private val db: ParkEasyDatabase,
    private val driver: SqlDriver,
    private val floors: FloorRepository,
    private val spots: SpotRepository,
    private val floorService: FloorService,
    private val spotService: SpotService,
    private val parking: ParkingService,
    private val persistence: PlanPersistence,
    private val settings: SettingRepository,
    private val clock: Clock,
) {
    /** Carga los datos solo si la base no tiene pisos. Devuelve si cargó algo. */
    fun seedIfEmpty(profile: AppProfile): Boolean {
        if (!profile.isTestData || floors.getAll().isNotEmpty()) return false
        seed(profile)
        return true
    }

    /** Borra pisos, puestos, planos, registros y bitácora (las tarifas y ajustes se conservan) y vuelve a cargar. */
    fun reset(profile: AppProfile) {
        require(profile.isTestData) { "Solo se restablecen los datos de prueba" }
        db.transaction {
            listOf("ParkingRecord", "PlanElement", "Spot", "Floor", "ChangeLog").forEach { driver.execute(null, "DELETE FROM $it", 0) }
        }
        driver.notifyListeners("ParkingRecord", "PlanElement", "Spot", "Floor", "ChangeLog")
        seed(profile)
    }

    private fun seed(profile: AppProfile) {
        settings.set(MARKER, "1")
        // Una sola transacción: en disco evita un fsync por cada registro (3000+ inserciones).
        db.transaction { if (profile == AppProfile.PERF) seedLarge() else seedDemo() }
    }

    // ---- Demostración: 2 pisos, ~30 puestos cada uno ------------------------------------------

    private fun seedDemo() {
        val f1 = floorService.add("Piso 1")
        val f2 = floorService.add("Sótano 1")
        savePlan(f1, demoFloorItems("A", "Piso 1", rampUp = false, connects = f2))
        savePlan(f2, demoFloorItems("B", "Sótano 1", rampUp = true, connects = f1))

        val all = spots.getAll().sortedBy { it.label }
        fun spot(label: String) = all.first { it.label == label }
        val now = clock.instant()

        // Historial cerrado: 12 estadías de los últimos días, con cobro calculado por el servicio.
        val history = listOf(
            "A-02" to Triple("TST201", VehicleType.CAR, 50), "A-03" to Triple("TST202", VehicleType.CAR, 130),
            "A-05" to Triple("TST203", VehicleType.CAR, 61), "A-09" to Triple("TST204", VehicleType.CAR, 180),
            "A-14" to Triple("TST205", VehicleType.CAR, 600), "A-27" to Triple("TST21M", VehicleType.MOTORCYCLE, 45),
            "A-28" to Triple("TST22M", VehicleType.MOTORCYCLE, 125), "B-04" to Triple("TST206", VehicleType.CAR, 95),
            "B-07" to Triple("TST207", VehicleType.CAR, 240), "B-12" to Triple("TST208", VehicleType.CAR, 30),
            "B-27" to Triple("TST23M", VehicleType.MOTORCYCLE, 75), "B-18" to Triple("TST209", VehicleType.CAR, 1500),
        )
        history.forEachIndexed { i, (label, stay) ->
            val (plate, vehicle, minutes) = stay
            val entry = now.minus(Duration.ofHours(((i + 1) * 5).toLong())).minus(Duration.ofDays((i / 3).toLong()))
            val id = parking.checkIn(CheckInRequest(spot(label).id, plate, COLORS[i % COLORS.size], vehicle, entry, TEST_DATA))
            parking.checkOut(id, entry.plus(Duration.ofMinutes(minutes.toLong())))
        }

        // Quince vehículos dentro ahora.
        val inside = listOf(
            "A-01", "A-04", "A-06", "A-08", "A-12", "A-16", "A-20", "A-29", "B-01", "B-03", "B-08", "B-15", "B-22", "A-30", "B-30",
        )
        inside.forEachIndexed { i, label ->
            val s = spot(label)
            val moto = s.type == SpotType.MOTORCYCLE
            val plate = if (moto) "TST${10 + i}M" else "TST${100 + i}"
            parking.checkIn(
                CheckInRequest(
                    s.id, plate, COLORS[i % COLORS.size], if (moto) VehicleType.MOTORCYCLE else VehicleType.CAR,
                    now.minus(Duration.ofMinutes(20L + i * 37L)), TEST_DATA,
                ),
            )
        }

        // Dos puestos bloqueados.
        spotService.block(spot("A-10").id, "Mantenimiento de la pintura (datos de demostración)")
        spotService.block(spot("B-19").id, "Reservado (datos de demostración)")
    }

    /** Un piso con dos hileras de puestos, vía, columnas, zona techada, rampa curva, entrada, salida y título. */
    private fun demoFloorItems(prefix: String, title: String, rampUp: Boolean, connects: Long): List<PlanItem> {
        val items = mutableListOf<PlanItem>()
        fun add(type: ItemType, geometry: com.ejemplo.parkeasy.plan.model.Geometry, label: String = "", stroke: Double = 0.0, extra: (PlanItem) -> PlanItem = { it }) {
            items += extra(PlanItem("demo-${items.size}", type, geometry, label = label, strokeWidth = stroke))
        }
        add(ItemType.COVERED_ZONE, Box(1.0, 1.5, 41.0, 9.5))
        add(ItemType.ROAD, PathGeometry(PathKind.POLYLINE, listOf(Pt(1.0, 14.0), Pt(52.0, 14.0), Pt(52.0, 30.0))), stroke = 6.0)
        add(ItemType.WALL, PathGeometry(PathKind.POLYLINE, listOf(Pt(0.5, 0.5), Pt(59.5, 0.5), Pt(59.5, 39.5), Pt(0.5, 39.5), Pt(0.5, 0.5))), stroke = 0.4)
        add(ItemType.RAMP, PathGeometry(PathKind.CURVE, listOf(Pt(52.0, 30.0), Pt(55.0, 33.0), Pt(52.0, 36.0), Pt(47.0, 37.0))), stroke = 3.5) {
            it.copy(rampUp = rampUp, connectsToFloorId = connects)
        }
        listOf(14.0, 28.0, 42.0).forEach { x -> add(ItemType.COLUMN, Box(x, 11.6, 0.6, 0.6, BoxShape.ELLIPSE)) { it.copy(round = true) } }
        add(ItemType.STAIRS_ELEVATOR, Box(54.0, 2.0, 4.0, 4.0), "Escaleras")
        add(ItemType.ENTRANCE, Box(1.0, 36.0, 5.0, 1.2), "Entrada")
        add(ItemType.EXIT, Box(8.0, 36.0, 5.0, 1.2), "Salida")
        add(ItemType.TEXT, Box(18.0, 36.0, 20.0, 1.5), "$title · DATOS DE DEMOSTRACIÓN")

        // Hilera de arriba: 15 puestos de carro, dentro de la zona techada.
        repeat(15) { i -> items += spotItem("$prefix-${"%02d".format(i + 1)}", SpotType.CAR, 2.0 + i * 2.6, 3.5, 2.5, 5.0) }
        // Hilera de abajo: carros, un puesto de movilidad reducida y cuatro de moto.
        repeat(10) { i -> items += spotItem("$prefix-${"%02d".format(16 + i)}", SpotType.CAR, 2.0 + i * 2.6, 19.0, 2.5, 5.0) }
        items += spotItem("$prefix-26", SpotType.DISABLED, 2.0 + 10 * 2.6, 19.0, 3.5, 5.0)
        repeat(4) { i -> items += spotItem("$prefix-${27 + i}", SpotType.MOTORCYCLE, 33.0 + i * 1.6, 19.0, 1.2, 2.5) }
        return items
    }

    private fun spotItem(label: String, type: SpotType, x: Double, y: Double, w: Double, h: Double) = PlanItem(
        id = "spot-$label", type = ItemType.SPOT, geometry = Box(x, y, w, h), label = label, spotType = type,
    )

    // ---- Rendimiento: un parqueadero grande ---------------------------------------------------

    private fun seedLarge() {
        val floorId = floorService.add("Piso grande")
        val items = mutableListOf<PlanItem>()
        repeat(LARGE_SPOTS) { i ->
            val col = i % 25
            val row = i / 25
            items += spotItem("G-${"%03d".format(i + 1)}", SpotType.CAR, 1.0 + col * 2.35, 1.0 + row * 1.9, 2.2, 1.7)
        }
        savePlan(floorId, items)

        val all = spots.getAll().sortedBy { it.label }
        val now = clock.instant()
        // Tres mil estadías cerradas repartidas en el último año.
        repeat(LARGE_HISTORY) { i ->
            val s = all[i % all.size]
            val entry = now.minus(Duration.ofMinutes(60L * 2 + i * 170L))
            val id = parking.checkIn(CheckInRequest(s.id, plate(i), COLORS[i % COLORS.size], VehicleType.CAR, entry, TEST_DATA))
            parking.checkOut(id, entry.plus(Duration.ofMinutes(20L + (i % 300))))
        }
        // Ciento cincuenta dentro.
        all.take(150).forEachIndexed { i, s ->
            parking.checkIn(CheckInRequest(s.id, "INS${"%03d".format(i)}", COLORS[i % COLORS.size], VehicleType.CAR, now.minus(Duration.ofMinutes(15L + i)), TEST_DATA))
        }
    }

    private fun plate(i: Int): String {
        val letters = "ABCDEFGHJKLMNPRSTUVWXYZ"
        return "${letters[i / 529 % 23]}${letters[i / 23 % 23]}${letters[i % 23]}${"%03d".format(i % 1000)}"
    }

    private fun savePlan(floorId: Long, items: List<PlanItem>) {
        val doc = persistence.load(floorId)!!
        persistence.save(doc.copy(items = com.ejemplo.parkeasy.plan.ops.PlanOps.recomputeCovered(doc.copy(items = items)).items))
    }

    companion object {
        const val MARKER = "TEST_DATA"
        const val TEST_DATA = "TEST_DATA"
        const val LARGE_SPOTS = 500
        const val LARGE_HISTORY = 3000
        private val COLORS = listOf("Rojo", "Blanco", "Negro", "Gris", "Azul", "Verde", "Plateado")
    }
}
