#include <absl/status/status.h>
#include <absl/status/statusor.h>
#include <glide/client.h>
#include <gtest/gtest.h>
#include <string>

using namespace glide;

TEST(ClientTest, ConnectTest) {
  Config g("localhost", 6379);
  Client c(g);
  EXPECT_TRUE(c.connect());
}

TEST(ClientTest, SetGetTest) {
  Config g("localhost", 6379);
  Client c(g);
  EXPECT_TRUE(c.connect());
  EXPECT_TRUE(c.set("SetGetTest", "hello-world").get().ok());
  EXPECT_EQ(*c.get<std::string>("SetGetTest").get(), "hello-world");
}

TEST(ClientTest, GetDelTest) {
  Config g("localhost", 6379);
  Client c(g);
  EXPECT_TRUE(c.connect());
  EXPECT_TRUE(c.set("GetDelTest", "hello-world").get().ok());
  EXPECT_EQ(*c.getdel<std::string>("GetDelTest").get(), "hello-world");
  EXPECT_EQ(*c.get<std::string>("GetDelTest").get(), "");
}
