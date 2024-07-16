package org.example

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutionException

suspend fun runApp() {
    val host = "localhost"
    val port = 6379
    val useSsl = false

    val config: GlideClientConfiguration =
        GlideClientConfiguration.builder()
            .address(NodeAddress.builder().host(host).port(port).build())
            .useTLS(useSsl)
            .build()

    try {
        val client: GlideClient = GlideClient.createClient(config).await()

        println("PING: " + client.ping().await())
        println("PING(found you): " + client.ping("found you").await())

        println("SET(apples, oranges): " + client.set("apples", "oranges").await())
        println("GET(apples): " + client.get("apples").await())
    } catch (e: ExecutionException) {
        println("Glide example failed with an exception: ")
        e.printStackTrace()
    } catch (e: InterruptedException) {
        println("Glide example failed with an exception: ")
        e.printStackTrace()
    }
}

fun main() {
    runBlocking {
        runApp()
    }
}
