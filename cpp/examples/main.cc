#include <glide/client.h>

#include <cstring>
#include <iostream>

using namespace glide;

std::vector<std::byte> to_bytes(const std::string& s) {
  std::vector<std::byte> result;
  result.reserve(s.size());
  for (char c : s) {
    result.push_back(static_cast<std::byte>(c));
  }
  return result;
}

std::string to_string(const std::vector<std::byte>& bytes) {
  std::string result;
  result.reserve(bytes.size());
  for (std::byte b : bytes) {
    result.push_back(static_cast<char>(b));
  }
  return result;
}

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

  // Set a key-value pair with a vector of bytes.
  auto f1v = to_bytes("hello-world");
  auto f1b = c.set("testb", f1v).get();
  if (!f1b.ok()) {
    std::cout << "set failed!" << f1b << std::endl;
  }

  auto f1g = c.get<std::vector<std::byte>>("testb").get();
  if (f1g.ok()) {
    std::cout << "get binary: " << to_string(*f1g) << std::endl;
  } else {
    std::cout << "get failed!" << f1g.status().message() << std::endl;
  }

  // Get the value of the key.
  auto f2 = c.get<std::string>("test").get();
  if (f2.ok()) {
    std::cout << "get: " << f2 << std::endl;
  } else {
    std::cout << "get failed!" << f2.status().message() << std::endl;
  }

  // Get the value of the key and delete the key.
  auto f3 = c.getdel<std::string>("test").get();
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
  auto f5 = c.hget<std::string>("test", "field1").get();
  if (f5.ok()) {
    std::cout << "hget: " << f5 << std::endl;
  } else {
    std::cout << "hget failed!" << f5.status().message() << std::endl;
  }
  auto f6 = c.hget<std::string>("test", "field2").get();
  if (f6.ok()) {
    std::cout << "hget: " << f6 << std::endl;
  } else {
    std::cout << "hget failed!" << f6.status().message() << std::endl;
  }

  // Set multiple field-value pairs in a hash - binary.
  std::map<std::string, std::vector<std::byte>> field_values_b = {
      {"field1", to_bytes("hello")},
      {"field2", to_bytes("world")}
  };
  auto fb4 = c.hset("test-hset-b", field_values_b).get();
  if (!fb4.ok()) {
    std::cout << "hset failed!" << fb4 << std::endl;
  }

  // Get the value of a field in a hash - binary.
  auto fb5 = c.hget<std::vector<std::byte>>("test-hset-b", "field1").get();
  if (fb5.ok()) {
    std::cout << "hget(b): " << to_string(*fb5) << std::endl;
  } else {
    std::cout << "hget failed!" << fb5.status().message() << std::endl;
  }
  auto fb6 = c.hget<std::vector<std::byte>>("test-hset-b", "field2").get();
  if (fb6.ok()) {
    std::cout << "hget(b): " << to_string(*fb6) << std::endl;
  } else {
    std::cout << "hget failed!" << fb6.status().message() << std::endl;
  }

  return 0;
}
