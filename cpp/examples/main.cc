#include <glide/client.h>

#include <cstring>
#include <iostream>

using namespace glide;

int main() {
  Config g("localhost", 6379);
  Client c(g);
  if (c.connect()) {
    std::cout << "Connection established!" << std::endl;
  } else {
    std::cout << "Connection failed!" << std::endl;
    return 0;
  }

  if (c.set("test", "hello-world")) {
    std::cout << "set completed!" << std::endl;
  } else {
    std::cout << "set failed!" << std::endl;
  }

  std::cout << "get: " << c.get("test") << std::endl;
  return 0;
}
