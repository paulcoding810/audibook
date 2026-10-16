package com.paulcoding.audibook.core.network.di

import com.paulcoding.audibook.core.network.datasource.KtorPostDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val networkModule = module {
    single<HttpClient> {
        HttpClient {
            install(ContentNegotiation) {
                json(
                    json = Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.ALL
            }
            install(DefaultRequest) {
                header("Accept", "*/*")
            }
        }.apply {
            plugin(HttpSend).intercept { request ->
                request.url.protocol = URLProtocol.HTTPS
                request.url.port = URLProtocol.HTTPS.defaultPort
                request.url.host = "jsonplaceholder.typicode.com"
                request.url.parameters.append("apikey", "123")
                execute(request)
            }
        }
    }
    singleOf(::KtorPostDataSource)
}