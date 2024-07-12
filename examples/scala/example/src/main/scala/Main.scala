import glide.api.GlideClient
import glide.api.models.configuration.{NodeAddress, GlideClientConfiguration}

import java.util.concurrent.ExecutionException

object Main {
    def main(args: Array[String]): Unit = {
        val host = "localhost"
        val port = 6379
        val useSsl = false

        val config = GlideClientConfiguration.builder
            .address(NodeAddress.builder
                .host(host)
                .port(port)
                .build)
            .useTLS(useSsl)
            .build
            // This cast is required in order to pass the config to createClient because the Scala type system
            // is unable to resolve the Lombok builder result type.
            .asInstanceOf[GlideClientConfiguration]

        try {
            val client = GlideClient.createClient(config).get
            println("PING: " + client.ping.get)
            println("PING(found you): " + client.ping("found you").get)
            println("SET(apples, oranges): " + client.set("apples", "oranges").get)
            println("GET(apples): " + client.get("apples").get)
        } catch {
            case e@(_: ExecutionException | _: InterruptedException) =>
                System.out.println("Glide example failed with an exception: ")
                e.printStackTrace()
        }
    }
}
