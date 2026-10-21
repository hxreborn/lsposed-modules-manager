package com.rushi.modulesmanager.data

import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Every endpoint of a repository was tried and none returned a usable document. */
class RepositoryUnavailableException(
    val sourceName: String,
    val failures: List<Throwable>,
) : IOException(failures.firstOrNull()?.message)

fun Throwable.userMessage(): String = when {
    this is RepositoryUnavailableException -> when {
        failures.isEmpty() -> "No repository URL is configured for $sourceName."
        failures.size == 1 -> failures.first().userMessage()
        else -> "All ${failures.size} endpoints failed. ${failures.first().userMessage()}"
    }
    this is UnknownHostException -> "Cannot reach the repository. Check your connection."
    this is SocketTimeoutException -> "The repository took too long to respond."
    this is SSLException -> "The repository's certificate could not be validated. Try another source."
    this is JSONException -> "That URL did not return a valid modules.json file."
    message?.contains("403") == true -> "The repository refused the request (HTTP 403). Try another source."
    message?.contains("404") == true -> "No modules.json was found at this repository URL."
    this is IOException && !message.isNullOrBlank() -> message!!
    !message.isNullOrBlank() -> message!!
    else -> "Could not load the repository."
}
