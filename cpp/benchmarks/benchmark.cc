#include <getopt.h>
#include <glide/client.h>
#include <glide/config.h>
#include <sysexits.h>

#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <random>
#include <thread>

using namespace glide;

// Define args.
const struct option long_options[] = {
    {"host", required_argument, nullptr, 'h'},
    {"port", required_argument, nullptr, 'p'},
    {"user", required_argument, nullptr, 'u'},
    {"parallel", required_argument, nullptr, 'a'},
    {"value-size", required_argument, nullptr, 's'},
    {nullptr, 0, nullptr, 0},
};

std::string generate_random_value(size_t length) {
  const std::string characters =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  std::random_device rd;
  std::mt19937 generator(rd());
  std::uniform_int_distribution<> dist(0, characters.size() - 1);
  std::string result;
  for (size_t i = 0; i < length; ++i) {
    result.push_back(characters[dist(generator)]);
  }
  return result;
}

void run_test(std::string host, uint32_t port, std::string id,
              unsigned int total, uint32_t value_size) {
  Config g(host, port);
  Client c(g);
  if (!c.connect()) {
    std::cerr << "Connection failed!\n";
    assert(false);
  }

  std::vector<std::thread> threads;
  for (unsigned int i = 0; i < total; ++i) {
    std::string key = "test-key-" + id + "-" + std::to_string(i);
    const std::string value = generate_random_value(value_size);
    threads.push_back(std::thread([key, value, &c]() {
      assert(c.set(key, value).get());
      assert(c.get(key).get() == value);
    }));
  }

  for (auto &t : threads) {
    t.join();
  }
}

int main(int argc, char *argv[]) {
  std::string host = "localhost";
  uint32_t port = 6379;
  uint32_t user = 10;
  uint32_t parallel = 10;
  uint32_t value_size = 1024;

  int opt;
  while ((opt = getopt_long(argc, argv, "h:p:u:l:s:", long_options, nullptr)) !=
         -1) {
    switch (opt) {
      case 'h':
        host = optarg;
        break;
      case 'p':
        port = std::stoi(optarg);
        break;
      case 'u':
        user = std::stoi(optarg);
        break;
      case 'l':
        parallel = std::stoi(optarg);
        break;
      case 's':
        value_size = std::stoi(optarg);
        break;
      default:
        std::cerr << "Unknown option!" << std::endl;
        return EX_USAGE;
    }
  }

  std::vector<std::thread> threads;
  for (uint32_t i = 0; i < user; ++i) {
    threads.push_back(std::thread([=]() {
      run_test(host, port, std::to_string(i), parallel, value_size);
    }));
  }

  for (auto &t : threads) {
    t.join();
  }

  return 0;
}
