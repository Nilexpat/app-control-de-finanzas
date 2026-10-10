package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import com.example.data.model.Transaction

@Database(
    entities = [
        Group::class,
        Member::class,
        Product::class,
        Movement::class,
        MovementAudit::class,
        TransferAccount::class,
        Invitation::class,
        Settlement::class,
        NotificationItem::class,
        Transaction::class
    ],
    version = 6,
    exportSchema = false
)
// Necesario para que Room sepa guardar la List<String> de Group.memberUids
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun movementAuditDao(): MovementAuditDao
    abstract fun transferAccountDao(): TransferAccountDao
    abstract fun invitationDao(): InvitationDao
    abstract fun settlementDao(): SettlementDao
    abstract fun notificationDao(): NotificationDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 schema migration
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (" +
                    "`id` TEXT NOT NULL, " +
                    "`groupId` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`amount` REAL NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`paymentMethod` TEXT NOT NULL, " +
                    "`customerOrDebtor` TEXT, " +
                    "`interestRate` REAL NOT NULL, " +
                    "`installments` INTEGER NOT NULL, " +
                    "`dueDate` INTEGER, " +
                    "`note` TEXT NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `settlementMode` TEXT NOT NULL DEFAULT 'DESEMBOLSO'")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `lastDisbursementAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `currentPeriodStartAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `periodStartAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `periodEndAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        // Anade la columna googleUid a members: permite reconocer la cuenta de
        // Google del usuario sin sobrescribir a otros miembros.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `members` ADD COLUMN `googleUid` TEXT NOT NULL DEFAULT ''")
            }
        }

        // Anade memberUids a groups: lista de UIDs con acceso a la empresa.
        // Es lo que permite que cada cuenta vea solo sus empresas.
        // La lista se rellena a partir de los googleUid ya guardados en members,
        // separados por coma (Room convierte List<String> con ',').
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `memberUids` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE `groups` SET `memberUids` = COALESCE((" +
                        "SELECT GROUP_CONCAT(`googleUid`, ',') FROM `members` " +
                        "WHERE `members`.`groupId` = `groups`.`id` " +
                        "AND `members`.`googleUid` <> ''" +
                        "), '')"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sociofinanzas_database_v2"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
