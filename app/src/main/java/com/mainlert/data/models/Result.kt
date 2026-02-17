package com.mainlert.data.models

/**
 * Result sealed class for handling success and failure states.
 * Used throughout the application for consistent error handling.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T?) : Result<T>()

    data class Failure<out T>(val message: String?) : Result<T>()
}

/**
 * Extension function to create a Success result
 */
fun <T> successResult(data: T?): Result<T> = Result.Success(data)

/**
 * Extension function to create a Failure result
 */
fun <T> failureResult(message: String?): Result<T> = Result.Failure(message)
