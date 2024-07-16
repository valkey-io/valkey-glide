import glide.api.GlideClient
import glide.api.models.configuration.{NodeAddress, GlideClientConfiguration}
import scala.jdk.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global

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
      for
        client <- GlideClient.createClient(config).asScala
        pingResponse <- client.ping.asScala
        _ = println("PING: " + pingResponse)
        pingWithMessageResponse <- client.ping("found you").asScala
        _ = println("PING(found you): " + pingWithMessageResponse)
        setResponse <- client.set("apples", "oranges").asScala
        _ = println("SET(apples, oranges): " + setResponse)
        getResponse <- client.get("apples").asScala
      do println("GET(apples): " + getResponse)
    } catch {
      case e@(_: ExecutionException | _: InterruptedException) =>
        System.out.println("Glide example failed with an exception: ")
        e.printStackTrace()
    }
  }
}
