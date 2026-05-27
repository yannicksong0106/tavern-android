package com.tavern.lite.data.db

import androidx.room.withTransaction

/**
 * Abstraction over Room's withTransaction to allow testing without a real RoomDatabase.
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

/**
 * Default implementation backed by Room's withTransaction.
 */
class RoomTransactionRunner(private val db: TavernDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction(block)
}
