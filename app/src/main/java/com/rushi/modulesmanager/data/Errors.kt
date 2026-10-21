package com.rushi.modulesmanager.data

import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Throwable.userMessage(): String = when {
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
