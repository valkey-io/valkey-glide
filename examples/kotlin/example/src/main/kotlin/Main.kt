package org.example

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import glide.api.models.configuration.NodeAddress
import java.util.concurrent.ExecutionException

fun main() {
    val host = "localhost"
    val port = 6379
    val useSsl = false

    val config: GlideClientConfiguration =
        GlideClientConfiguration.builder()
            .address(NodeAddress.builder().host(host).port(port).build())
            .useTLS(useSsl)
            .build()

    try {
        val client: GlideClient = GlideClient.createClient(config).get()

        println("PING: " + client.ping().get())
        println("PING(found you): " + client.ping("found you").get())

        println("SET(apples, oranges): " + client.set("apples", "oranges").get())
        println("GET(apples): " + client.get("apples").get())
    } catch (e: ExecutionException) {
        println("Glide example failed with an exception: ")
        e.printStackTrace()
    } catch (e: InterruptedException) {
        println("Glide example failed with an exception: ")
        e.printStackTrace()
    }
}
