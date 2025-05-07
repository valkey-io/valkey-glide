#include <glide/client.h>

#include <cstring>
#include <iostream>

using namespace glide;

int main() {
  Config g("localhost", 6379);
  Client c(g);

  // Connect to the server.
  if (c.connect()) {
    std::cout << "Connection established!" << std::endl;
  } else {
    std::cout << "Connection failed!" << std::endl;
    return 0;
  }

  // Set a key-value pair.
  auto f1 = c.set("test", "hello-world").get();
  if (!f1.ok()) {
    std::cout << "set failed!" << f1 << std::endl;
  }

  // Get the value of the key.
  auto f2 = c.get("test").get();
  if (f2.ok()) {
    std::cout << "get: " << f2 << std::endl;
  } else {
    std::cout << "get failed!" << f2.status().message() << std::endl;
  }

  // Get the value of the key and delete the key.
  auto f3 = c.getdel("test").get();
  if (f3.ok()) {
    std::cout << "getdel: " << f3 << std::endl;
  } else {
    std::cout << "set failed!" << f3.status().message() << std::endl;
  }

  // Set multiple field-value pairs in a hash.
  std::map<std::string, std::string> field_values = {{"field1", "value1"},
                                                     {"field2", "value2"}};
  auto f4 = c.hset("test", field_values).get();
  if (!f4.ok()) {
    std::cout << "hset failed!" << f4 << std::endl;
  }

  // Get the value of a field in a hash.
  auto f5 = c.hget("test", "field1").get();
  if (f5.ok()) {
    std::cout << "hget: " << f5 << std::endl;
  } else {
    std::cout << "hget failed!" << f5.status().message() << std::endl;
  }
  auto f6 = c.hget("test", "field2").get();
  if (f6.ok()) {
    std::cout << "hget: " << f6 << std::endl;
  } else {
    std::cout << "hget failed!" << f6.status().message() << std::endl;
  }

  return 0;
}
