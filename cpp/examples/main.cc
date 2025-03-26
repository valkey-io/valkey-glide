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
  auto f = c.set("test", "hello-world");
  if (f.get()) {
    std::cout << "set completed!" << std::endl;
  } else {
    std::cout << "set failed!" << std::endl;
  }

  // Get the value of the key.
  auto f2 = c.get("test");
  f2.wait();
  std::cout << "get: " << f2.get() << std::endl;

  // Get the value of the key and delete the key.
  auto f3 = c.getdel("test");
  f3.wait();
  std::cout << "getdel: " << f3.get() << std::endl;

  // Set multiple field-value pairs in a hash.
  std::map<std::string, std::string> field_values = {{"field1", "value1"},
                                                     {"field2", "value2"}};
  auto f4 = c.hset("test", field_values);
  f4.wait();
  f4.get();

  // Get the value of a field in a hash.
  auto f5 = c.hget("test", "field1");
  f5.wait();
  std::cout << "hget: " << f5.get() << std::endl;
  auto f6 = c.hget("test", "field2");
  f6.wait();
  std::cout << "hget: " << f6.get() << std::endl;

  return 0;
}
