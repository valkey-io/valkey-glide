#include <gtest/gtest.h>
#include <glide/client.h>

using namespace glide;

TEST(ClientTest, ConnectTest)
{

    Config g("localhost", 6379);
    Client c(g);
    EXPECT_TRUE(c.connect());
}

TEST(ClientTest, SetGetTest)
{
    Config g("localhost", 6379);
    Client c(g);
    EXPECT_TRUE(c.connect());
    EXPECT_TRUE(c.set("SetGetTest", "hello-world").get());
    EXPECT_EQ(c.get("SetGetTest").get(), "hello-world");
}

TEST(ClientTest, GetDelTest)
{
    Config g("localhost", 6379);
    Client c(g);
    EXPECT_TRUE(c.connect());
    EXPECT_TRUE(c.set("GetDelTest", "hello-world").get());
    EXPECT_EQ(c.getdel("GetDelTest").get(), "hello-world");
    EXPECT_EQ(c.get("GetDelTest").get(), "");
}
