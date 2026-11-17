package com.example.hospital

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------- ENTITIES (13 таблиц из hospital_sqlite.sql) ----------
private const val C = ForeignKey.CASCADE
private const val R = ForeignKey.RESTRICT
private const val N = ForeignKey.SET_NULL

@Entity(tableName = "departments", indices = [Index("name", unique = true)])
data class Department(@PrimaryKey(autoGenerate = true) val departmentId: Long = 0, val name: String, val floor: Int?, val phone: String?)

@Entity(tableName = "doctors", indices = [Index("departmentId")],
    foreignKeys = [ForeignKey(Department::class, ["departmentId"], ["departmentId"], onDelete = R, onUpdate = C)])
data class Doctor(@PrimaryKey(autoGenerate = true) val doctorId: Long = 0, val departmentId: Long, val fullName: String,
                  val specialization: String, val phone: String?, val email: String?, val hireDate: String?,
                  val workDays: String = "1,2,3,4,5",      // рабочие дни: 1 = пн ... 7 = вс
                  val workStart: String = "09:00", val workEnd: String = "18:00", val room: String? = null)

@Entity(tableName = "patients", indices = [Index("policyNumber", unique = true)])
data class Patient(@PrimaryKey(autoGenerate = true) val patientId: Long = 0, val fullName: String, val birthDate: String,
                   val gender: String, val address: String?, val phone: String?, val policyNumber: String?)

@Entity(tableName = "wards", indices = [Index("departmentId", "wardNumber", unique = true)],
    foreignKeys = [ForeignKey(Department::class, ["departmentId"], ["departmentId"], onDelete = C, onUpdate = C)])
data class Ward(@PrimaryKey(autoGenerate = true) val wardId: Long = 0, val departmentId: Long, val wardNumber: Int, val capacity: Int)

@Entity(tableName = "beds", indices = [Index("wardId", "bedNumber", unique = true)],
    foreignKeys = [ForeignKey(Ward::class, ["wardId"], ["wardId"], onDelete = C, onUpdate = C)])
data class Bed(@PrimaryKey(autoGenerate = true) val bedId: Long = 0, val wardId: Long, val bedNumber: Int, val status: String = "свободна")

@Entity(tableName = "diagnoses", indices = [Index("icdCode", unique = true)])
data class Diagnosis(@PrimaryKey(autoGenerate = true) val diagnosisId: Long = 0, val name: String, val icdCode: String)

@Entity(tableName = "hospitalizations", indices = [Index("patientId"), Index("bedId"), Index("doctorId"), Index("diagnosisId")],
    foreignKeys = [
        ForeignKey(Patient::class, ["patientId"], ["patientId"], onDelete = C),
        ForeignKey(Bed::class, ["bedId"], ["bedId"], onDelete = R),
        ForeignKey(Doctor::class, ["doctorId"], ["doctorId"], onDelete = R),
        ForeignKey(Diagnosis::class, ["diagnosisId"], ["diagnosisId"], onDelete = N)])
data class Hospitalization(@PrimaryKey(autoGenerate = true) val hospitalizationId: Long = 0, val patientId: Long, val bedId: Long,
                           val doctorId: Long, val diagnosisId: Long?, val admissionDate: String, val dischargeDate: String?,
                           val status: String = "проходит лечение")

@Entity(tableName = "appointments", indices = [Index("patientId"), Index("doctorId")],
    foreignKeys = [ForeignKey(Patient::class, ["patientId"], ["patientId"], onDelete = C),
        ForeignKey(Doctor::class, ["doctorId"], ["doctorId"], onDelete = R)])
data class Appointment(@PrimaryKey(autoGenerate = true) val appointmentId: Long = 0, val patientId: Long, val doctorId: Long,
                       val apptDatetime: String, val status: String = "запланирован", val reason: String?,
                       val room: String? = null, val arrivedAt: String? = null)

@Entity(tableName = "medical_records", indices = [Index("patientId"), Index("doctorId"), Index("diagnosisId")],
    foreignKeys = [ForeignKey(Patient::class, ["patientId"], ["patientId"], onDelete = C),
        ForeignKey(Doctor::class, ["doctorId"], ["doctorId"], onDelete = R),
        ForeignKey(Diagnosis::class, ["diagnosisId"], ["diagnosisId"], onDelete = N)])
data class MedicalRecord(@PrimaryKey(autoGenerate = true) val recordId: Long = 0, val patientId: Long, val doctorId: Long,
                         val diagnosisId: Long?, val recordDate: String, val notes: String?)

@Entity(tableName = "prescriptions", indices = [Index("recordId")],
    foreignKeys = [ForeignKey(MedicalRecord::class, ["recordId"], ["recordId"], onDelete = C)])
data class Prescription(@PrimaryKey(autoGenerate = true) val prescriptionId: Long = 0, val recordId: Long,
                        val medicationName: String, val dosage: String, val durationDays: Int)

@Entity(tableName = "procedures", indices = [Index("name", unique = true)])
data class MedProcedure(@PrimaryKey(autoGenerate = true) val procedureId: Long = 0, val name: String, val cost: Double)

@Entity(tableName = "procedures_performed", indices = [Index("hospitalizationId"), Index("procedureId"), Index("doctorId")],
    foreignKeys = [ForeignKey(Hospitalization::class, ["hospitalizationId"], ["hospitalizationId"], onDelete = C),
        ForeignKey(MedProcedure::class, ["procedureId"], ["procedureId"], onDelete = R),
        ForeignKey(Doctor::class, ["doctorId"], ["doctorId"], onDelete = R)])
data class ProcedurePerformed(@PrimaryKey(autoGenerate = true) val perfId: Long = 0, val hospitalizationId: Long,
                              val procedureId: Long, val doctorId: Long, val perfDate: String)

@Entity(tableName = "lab_tests", indices = [Index("patientId"), Index("doctorId")],
    foreignKeys = [ForeignKey(Patient::class, ["patientId"], ["patientId"], onDelete = C),
        ForeignKey(Doctor::class, ["doctorId"], ["doctorId"], onDelete = R)])
data class LabTest(@PrimaryKey(autoGenerate = true) val testId: Long = 0, val patientId: Long, val doctorId: Long,
                   val testType: String, val testDate: String, val result: String?)

// ---------- DAO: CRUD ----------
interface BaseDao<T> {
    @Insert suspend fun insert(x: T): Long
    @Update suspend fun update(x: T)
    @Delete suspend fun delete(x: T)
}
suspend fun <T> BaseDao<T>.save(old: Any?, x: T) { if (old == null) insert(x) else update(x) }

@Dao interface DepartmentDao : BaseDao<Department> { @Query("SELECT * FROM departments ORDER BY name") fun all(): Flow<List<Department>> }
@Dao interface DoctorDao : BaseDao<Doctor> {
    @Query("SELECT * FROM doctors ORDER BY fullName") fun all(): Flow<List<Doctor>>
    @Query("SELECT * FROM doctors WHERE doctorId = :id") suspend fun byId(id: Long): Doctor?
}
@Dao interface PatientDao : BaseDao<Patient> {
    @Query("SELECT * FROM patients ORDER BY fullName") fun all(): Flow<List<Patient>>
    @Query("SELECT COUNT(*) FROM patients WHERE policyNumber = :p") suspend fun countPolicy(p: String): Int
}
@Dao interface WardDao : BaseDao<Ward> { @Query("SELECT * FROM wards ORDER BY wardNumber") fun all(): Flow<List<Ward>> }
@Dao interface BedDao : BaseDao<Bed> { @Query("SELECT * FROM beds ORDER BY wardId, bedNumber") fun all(): Flow<List<Bed>> }
@Dao interface DiagnosisDao : BaseDao<Diagnosis> { @Query("SELECT * FROM diagnoses ORDER BY name") fun all(): Flow<List<Diagnosis>> }
// Ячейка тепловой карты: день недели (0 = вс), час, число приёмов
data class HeatRow(val dow: Int, val hour: Int, val cnt: Int)

// Строка очереди врача: приём + данные пациента
data class QueueRow(val appointmentId: Long, val fullName: String, val birthDate: String, val gender: String,
                    val apptDatetime: String, val status: String, val reason: String?, val room: String?, val arrivedAt: String?)

@Dao interface AppointmentDao : BaseDao<Appointment> {
    @Query("SELECT * FROM appointments ORDER BY apptDatetime DESC") fun all(): Flow<List<Appointment>>
    // Очередь врача за последний день, на который есть приёмы
    @Query("SELECT a.appointmentId, p.fullName, p.birthDate, p.gender, a.apptDatetime, a.status, a.reason, a.room, a.arrivedAt " +
        "FROM appointments a JOIN patients p ON p.patientId = a.patientId WHERE a.doctorId = :doctorId " +
        "AND substr(a.apptDatetime,1,10) = (SELECT max(substr(apptDatetime,1,10)) FROM appointments WHERE doctorId = :doctorId AND substr(apptDatetime,1,10) <= date('now','localtime')) " +
        "ORDER BY a.apptDatetime")
    fun queue(doctorId: Long): Flow<List<QueueRow>>
    @Query("UPDATE appointments SET status = :s, arrivedAt = :t WHERE appointmentId = :id") suspend fun setArrival(id: Long, s: String, t: String?)
    @Query("UPDATE appointments SET status = :s WHERE appointmentId = :id") suspend fun setStatus(id: Long, s: String)
    @Query("UPDATE appointments SET status = 'запланирован', arrivedAt = NULL WHERE doctorId = :doctorId") suspend fun resetQueue(doctorId: Long)
    // Занятые времена врача на дату (отменённые и «не явился» время освобождают)
    @Query("SELECT apptDatetime FROM appointments WHERE doctorId = :doctorId AND substr(apptDatetime,1,10) = :date AND status NOT IN ('отменён','не явился')")
    fun busySlots(doctorId: Long, date: String): Flow<List<String>>
    @Query("SELECT COUNT(*) FROM appointments WHERE doctorId = :doctorId AND apptDatetime = :dt AND status NOT IN ('отменён','не явился') AND appointmentId != :exceptId")
    suspend fun countBusy(doctorId: Long, dt: String, exceptId: Long): Int
    // Записи врача на неделю (для карточки врача)
    @Query("SELECT a.appointmentId, p.fullName, p.birthDate, p.gender, a.apptDatetime, a.status, a.reason, a.room, a.arrivedAt " +
        "FROM appointments a JOIN patients p ON p.patientId = a.patientId WHERE a.doctorId = :doctorId " +
        "AND substr(a.apptDatetime,1,10) BETWEEN :from AND :to AND a.status NOT IN ('отменён','не явился') ORDER BY a.apptDatetime")
    fun weekRows(doctorId: Long, from: String, to: String): Flow<List<QueueRow>>
    // Нагрузка по дням недели и часам; doctorId = 0 означает «все врачи»
    @Query("SELECT CAST(strftime('%w', substr(apptDatetime,1,10)) AS INTEGER) AS dow, CAST(substr(apptDatetime,12,2) AS INTEGER) AS hour, COUNT(*) AS cnt " +
        "FROM appointments WHERE (:doctorId = 0 OR doctorId = :doctorId) AND status NOT IN ('отменён','не явился') GROUP BY dow, hour")
    fun heat(doctorId: Long): Flow<List<HeatRow>>
    // Врачи, у которых сегодня идёт приём (пациент пришёл, приём не завершён)
    @Query("SELECT DISTINCT doctorId FROM appointments WHERE status = 'пришёл' AND substr(apptDatetime,1,10) = :today")
    fun inProgress(today: String): Flow<List<Long>>
}

// Запись на приём: проверка «время свободно» и сохранение в ОДНОЙ транзакции,
// поэтому на одно время к одному врачу вторая запись невозможна.
suspend fun AppDb.book(a: Appointment) {
    withTransaction {
        doctors().byId(a.doctorId)?.let { require(isWorkingAt(it, a.apptDatetime)) { "Врач в это время не работает" } }
        require(appointments().countBusy(a.doctorId, a.apptDatetime, a.appointmentId) == 0) { "Это время у врача уже занято" }
        if (a.appointmentId == 0L) appointments().insert(a) else appointments().update(a)
    }
}
@Dao interface MedicalRecordDao : BaseDao<MedicalRecord> { @Query("SELECT * FROM medical_records ORDER BY recordDate DESC") fun all(): Flow<List<MedicalRecord>> }
@Dao interface PrescriptionDao : BaseDao<Prescription> { @Query("SELECT * FROM prescriptions") fun all(): Flow<List<Prescription>> }
@Dao interface ProcedureDao : BaseDao<MedProcedure> { @Query("SELECT * FROM procedures ORDER BY name") fun all(): Flow<List<MedProcedure>> }
@Dao interface PerformedDao : BaseDao<ProcedurePerformed> { @Query("SELECT * FROM procedures_performed") fun all(): Flow<List<ProcedurePerformed>> }
@Dao interface LabTestDao : BaseDao<LabTest> { @Query("SELECT * FROM lab_tests ORDER BY testDate DESC") fun all(): Flow<List<LabTest>> }

// ---------- DAO: ACID / атомарные транзакции ----------
@Dao
abstract class HospitalizationDao {
    @Query("SELECT * FROM hospitalizations ORDER BY admissionDate DESC") abstract fun all(): Flow<List<Hospitalization>>
    @Insert abstract suspend fun insert(h: Hospitalization): Long
    @Update abstract suspend fun update(h: Hospitalization)
    @Delete abstract suspend fun delete(h: Hospitalization)
    @Query("UPDATE beds SET status = :s WHERE bedId = :id") abstract suspend fun setBed(id: Long, s: String)
    @Query("SELECT status FROM beds WHERE bedId = :id") abstract suspend fun bedStatus(id: Long): String?

    /** Атомарно: либо и госпитализация создана, и койка занята, либо не произошло ничего. */
    @Transaction
    open suspend fun admit(h: Hospitalization) {
        check(bedStatus(h.bedId) == "свободна") { "Койка не найдена или уже занята" }
        insert(h)
        setBed(h.bedId, "занята")
    }

    /** Атомарно: обновление записи + освобождение койки при выписке/переводе. */
    @Transaction
    open suspend fun updateAndSync(h: Hospitalization) {
        update(h)
        if (h.status != "проходит лечение") setBed(h.bedId, "свободна")
    }
}

// ---------- DATABASE ----------
@Database(
    entities = [Department::class, Doctor::class, Patient::class, Ward::class, Bed::class, Diagnosis::class,
        Hospitalization::class, Appointment::class, MedicalRecord::class, Prescription::class,
        MedProcedure::class, ProcedurePerformed::class, LabTest::class],
    version = 5, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun departments(): DepartmentDao
    abstract fun doctors(): DoctorDao
    abstract fun patients(): PatientDao
    abstract fun wards(): WardDao
    abstract fun beds(): BedDao
    abstract fun diagnoses(): DiagnosisDao
    abstract fun hospitalizations(): HospitalizationDao
    abstract fun appointments(): AppointmentDao
    abstract fun records(): MedicalRecordDao
    abstract fun prescriptions(): PrescriptionDao
    abstract fun procedures(): ProcedureDao
    abstract fun performed(): PerformedDao
    abstract fun labTests(): LabTestDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "hospital.db")
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO departments(name,floor,phone) VALUES ('Терапевтическое отделение',2,'+7-495-100-01-01'),('Хирургическое отделение',3,'+7-495-100-01-02'),('Кардиологическое отделение',4,'+7-495-100-01-03'),('Регистратура',1,'+7-495-100-01-00')")
                        db.execSQL("INSERT INTO doctors(departmentId,fullName,specialization,phone,email,hireDate,workDays,workStart,workEnd,room) VALUES (1,'Иванова Анна Сергеевна','Терапевт','+7-916-111-11-11','ivanova@hospital.ru','2018-03-01','1,2,3,4,5','09:00','18:00','201'),(2,'Петров Игорь Николаевич','Хирург','+7-916-222-22-22','petrov@hospital.ru','2015-07-15','1,3,5','10:00','16:00','301'),(3,'Сидорова Мария Викторовна','Кардиолог','+7-916-333-33-33','sidorova@hospital.ru','2020-01-20','2,3,4,5','09:00','17:00','401'),(1,'Кузнецов Олег Павлович','Терапевт','+7-916-444-44-44','kuznetsov@hospital.ru','2019-09-10','1,2,4','12:00','20:00','202')")
                        db.execSQL("INSERT INTO patients(fullName,birthDate,gender,address,phone,policyNumber) VALUES ('Смирнов Дмитрий Андреевич','1985-04-12','М','г. Москва, ул. Ленина, д. 5','+7-926-555-11-22','1234567890123456'),('Волкова Екатерина Игоревна','1992-11-03','Ж','г. Москва, ул. Мира, д. 10','+7-926-555-22-33','2234567890123456'),('Соколов Артём Павлович','1978-06-25','М','г. Москва, пр-т Победы, д. 7','+7-926-555-33-44','3234567890123456')")
                        db.execSQL("INSERT INTO wards(departmentId,wardNumber,capacity) VALUES (1,201,4),(2,301,2),(3,401,3)")
                        db.execSQL("INSERT INTO beds(wardId,bedNumber,status) VALUES (1,1,'занята'),(1,2,'свободна'),(2,1,'занята'),(3,1,'свободна')")
                        db.execSQL("INSERT INTO diagnoses(name,icdCode) VALUES ('Острый бронхит','J20'),('Гипертоническая болезнь','I10'),('Аппендицит острый','K35')")
                        db.execSQL("INSERT INTO hospitalizations(patientId,bedId,doctorId,diagnosisId,admissionDate,dischargeDate,status) VALUES (1,1,1,1,'2026-09-01',NULL,'проходит лечение'),(3,3,2,3,'2026-08-20','2026-08-28','выписан')")
                        db.execSQL("INSERT INTO appointments(patientId,doctorId,apptDatetime,status,reason,room,arrivedAt) VALUES (1,1,strftime('%Y-%m-%d','now','localtime')||' 10:00','запланирован','Бронхит, повторный приём','201',NULL),(2,1,strftime('%Y-%m-%d','now','localtime')||' 10:20','запланирован','Плановый осмотр','201',NULL),(3,1,strftime('%Y-%m-%d','now','localtime')||' 10:40','запланирован','Контроль после операции','201',NULL)")
                        db.execSQL("INSERT INTO medical_records(patientId,doctorId,diagnosisId,recordDate,notes) VALUES (1,1,1,'2026-08-25','Назначено лечение, повторный приём через 7 дней'),(3,2,3,'2026-08-20','Экстренная госпитализация, операция')")
                        db.execSQL("INSERT INTO prescriptions(recordId,medicationName,dosage,durationDays) VALUES (1,'Амброксол','30 мг 3 раза в день',7),(1,'Парацетамол','500 мг при температуре',5)")
                        db.execSQL("INSERT INTO procedures(name,cost) VALUES ('Аппендэктомия',45000.0),('ЭКГ',1500.0),('Общий анализ крови',800.0)")
                        db.execSQL("INSERT INTO procedures_performed(hospitalizationId,procedureId,doctorId,perfDate) VALUES (2,1,2,'2026-08-20')")
                        db.execSQL("INSERT INTO lab_tests(patientId,doctorId,testType,testDate,result) VALUES (1,1,'Общий анализ крови','2026-08-25','Лейкоциты повышены'),(3,2,'Общий анализ крови','2026-08-20','В пределах нормы')")
                        seedHistory(db)
                    }
                }).build().also { inst = it }
        }
    }
}


// Демо-история приёмов: 5 недель назад и неделя вперёд (для тепловой карты и недельного расписания)
private fun seedHistory(db: SupportSQLiteDatabase) {
    class D(val id: Int, val days: Set<Int>, val s: Int, val e: Int, val room: String)
    val docs = listOf(D(1, setOf(1, 2, 3, 4, 5), 9, 18, "201"), D(2, setOf(1, 3, 5), 10, 16, "301"),
        D(3, setOf(2, 3, 4, 5), 9, 17, "401"), D(4, setOf(1, 2, 4), 12, 20, "202"))
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (off in -35..7) {
        if (off == 0) continue                                   // сегодняшние приёмы уже добавлены выше
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }
        val iso = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        for (d in docs) {
            if (iso !in d.days) continue
            val used = mutableSetOf<String>()
            for (j in 0 until 2 + Math.floorMod(off * 3 + d.id, 4)) {
                val hour = d.s + Math.floorMod(off * 5 + d.id * 3 + j * 7, d.e - d.s)
                val min = listOf(0, 20, 40)[Math.floorMod(off + j + d.id, 3)]
                if (hour * 60 + min + 20 > d.e * 60 || !used.add("%02d:%02d".format(hour, min))) continue
                db.execSQL("INSERT INTO appointments(patientId,doctorId,apptDatetime,status,reason,room,arrivedAt) VALUES (?,?,?,?,?,?,NULL)",
                    arrayOf<Any?>(1 + Math.floorMod(off + j + d.id, 3), d.id, "${fmt.format(c.time)} ${"%02d:%02d".format(hour, min)}",
                        if (off < 0) "завершён" else "запланирован", "Плановый приём", d.room))
            }
        }
    }
}
