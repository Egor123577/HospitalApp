package com.example.hospital

import android.content.Context
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
                  val specialization: String, val phone: String?, val email: String?, val hireDate: String?)

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
                       val apptDatetime: String, val status: String = "запланирован", val reason: String?)

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
@Dao interface DoctorDao : BaseDao<Doctor> { @Query("SELECT * FROM doctors ORDER BY fullName") fun all(): Flow<List<Doctor>> }
@Dao interface PatientDao : BaseDao<Patient> { @Query("SELECT * FROM patients ORDER BY fullName") fun all(): Flow<List<Patient>> }
@Dao interface WardDao : BaseDao<Ward> { @Query("SELECT * FROM wards ORDER BY wardNumber") fun all(): Flow<List<Ward>> }
@Dao interface BedDao : BaseDao<Bed> { @Query("SELECT * FROM beds ORDER BY wardId, bedNumber") fun all(): Flow<List<Bed>> }
@Dao interface DiagnosisDao : BaseDao<Diagnosis> { @Query("SELECT * FROM diagnoses ORDER BY name") fun all(): Flow<List<Diagnosis>> }
@Dao interface AppointmentDao : BaseDao<Appointment> { @Query("SELECT * FROM appointments ORDER BY apptDatetime DESC") fun all(): Flow<List<Appointment>> }
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
    version = 1, exportSchema = false)
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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO departments(name,floor,phone) VALUES ('Терапевтическое отделение',2,'+7-495-100-01-01'),('Хирургическое отделение',3,'+7-495-100-01-02'),('Кардиологическое отделение',4,'+7-495-100-01-03'),('Регистратура',1,'+7-495-100-01-00')")
                        db.execSQL("INSERT INTO doctors(departmentId,fullName,specialization,phone,email,hireDate) VALUES (1,'Иванова Анна Сергеевна','Терапевт','+7-916-111-11-11','ivanova@hospital.ru','2018-03-01'),(2,'Петров Игорь Николаевич','Хирург','+7-916-222-22-22','petrov@hospital.ru','2015-07-15'),(3,'Сидорова Мария Викторовна','Кардиолог','+7-916-333-33-33','sidorova@hospital.ru','2020-01-20'),(1,'Кузнецов Олег Павлович','Терапевт','+7-916-444-44-44','kuznetsov@hospital.ru','2019-09-10')")
                        db.execSQL("INSERT INTO patients(fullName,birthDate,gender,address,phone,policyNumber) VALUES ('Смирнов Дмитрий Андреевич','1985-04-12','М','г. Москва, ул. Ленина, д. 5','+7-926-555-11-22','1234567890123456'),('Волкова Екатерина Игоревна','1992-11-03','Ж','г. Москва, ул. Мира, д. 10','+7-926-555-22-33','2234567890123456'),('Соколов Артём Павлович','1978-06-25','М','г. Москва, пр-т Победы, д. 7','+7-926-555-33-44','3234567890123456')")
                        db.execSQL("INSERT INTO wards(departmentId,wardNumber,capacity) VALUES (1,201,4),(2,301,2),(3,401,3)")
                        db.execSQL("INSERT INTO beds(wardId,bedNumber,status) VALUES (1,1,'занята'),(1,2,'свободна'),(2,1,'занята'),(3,1,'свободна')")
                        db.execSQL("INSERT INTO diagnoses(name,icdCode) VALUES ('Острый бронхит','J20'),('Гипертоническая болезнь','I10'),('Аппендицит острый','K35')")
                        db.execSQL("INSERT INTO hospitalizations(patientId,bedId,doctorId,diagnosisId,admissionDate,dischargeDate,status) VALUES (1,1,1,1,'2026-09-01',NULL,'проходит лечение'),(3,3,2,3,'2026-08-20','2026-08-28','выписан')")
                    }
                }).build().also { inst = it }
        }
    }
}
