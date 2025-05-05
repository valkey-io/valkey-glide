#ifndef CONFIG_HPP_
#define CONFIG_HPP_

#include <chrono>
#include <cstdint>
#include <cstring>
#include <optional>
#include <string>
#include <type_traits>
#include <vector>

namespace glide {

/**
 * Default host for the client connection.
 */
const std::string DEFAULT_HOST = "localhost";

/**
 * Default port for the client connection.
 */
const uint32_t DEFAULT_PORT = 6379;

/**
 * TLS modes for client connections.
 */
enum class TLSMode {
  /**
   * No TLS encryption is used for the connection.
   */
  NoTLS = 0,

  /**
   * TLS encryption is used for the connection with certificate verification.
   */
  SecureTLS = 1,

  /**
   * TLS encryption is used for the connection without certificate verification.
   */
  InsecureTLS = 2,
};

/**
 * Enum class representing the preferred node to read data from in a cluster.
 * This enum specifies the strategy for selecting the node to read data from.
 */
enum class ReadFrom {
  /**
   * Primary: Read data from the primary node in the cluster.
   */
  Primary = 0,

  /**
   * PreferReplica: Prefer reading data from a replica node in the cluster, if
   * available.
   */
  PreferReplica = 1,

  /**
   * LowestLatency: Read data from the node with the lowest latency in the
   * cluster.
   */
  LowestLatency = 2,

  /**
   * AZAffinity: Read data from a node in the same availability zone as the
   * client, if possible.
   */
  AZAffinity = 3,
};

/**
 * Represents a node in the cluster with a host and port.
 * Used to define the address of a cluster node in the configuration.
 *
 * @param host The hostname of the cluster node.
 * @param port The port number of the cluster node.
 */
struct ClusterNode {
  std::string host;
  uint32_t port = DEFAULT_PORT;

  /**
   * Constructs a ClusterNode object with a host and port.
   *
   * @param host The hostname of the cluster node.
   * @param port The port number of the cluster node.
   */
  ClusterNode(const std::string& host, const uint32_t port);

  /**
   * Copy constructor for ClusterNode.
   * Creates a new ClusterNode object as a copy of an existing one.
   *
   * @param other The source ClusterNode object to copy from.
   */
  ClusterNode(const ClusterNode& other) noexcept;

  /**
   * Assigns the value of another ClusterNode object to this object.
   *
   * @param other The ClusterNode object to copy from.
   * @return A reference to this ClusterNode object.
   */
  ClusterNode& operator=(const ClusterNode& other) noexcept;

  /**
   * Move constructor for ClusterNode.
   * Creates a new ClusterNode object by transferring the resources from another
   * ClusterNode object.
   *
   * @param other The source ClusterNode object to move from.
   */
  ClusterNode(ClusterNode&& other) noexcept;

  /**
   * Move assignment operator for ClusterNode.
   * Transfers the resources from another ClusterNode object to this object.
   *
   * @param other The ClusterNode object to move from.
   * @return A reference to this ClusterNode object.
   */
  ClusterNode& operator=(ClusterNode&& other) noexcept;
};

/**
 * Represents user credentials with a username and password.
 * Used for authentication purposes in the configuration.
 */
struct Credential {
  std::string username;
  std::string password;

  /**
   * Default constructor for Credential.
   * Creates a Credential object with empty username and password.
   */
  Credential();

  /**
   * Constructs a Credential object with a username and password.
   *
   * @param username The username for authentication.
   * @param password The password for authentication.
   */
  Credential(const std::string& username, const std::string& password);

  /**
   * Copy constructor for Credential.
   *
   * Creates a new Credential object as a copy of an existing one.
   *
   * @param other The source Credential object to copy from.
   */
  Credential(const Credential& other) noexcept;

  /**
   * Copy assignment operator for Credential.
   *
   * Assigns the value of another Credential object to this object.
   *
   * @param other The Credential object to copy from.
   * @return A reference to this Credential object.
   */
  Credential& operator=(const Credential& other) noexcept;

  /**
   * Move constructor for Credential.
   *
   * Creates a new Credential object by transferring the resources from another
   * Credential object.
   *
   * @param other The source Credential object to move from.
   */
  Credential(Credential&& other) noexcept;

  /**
   * Move assignment operator for Credential.
   *
   * Assigns the value of another Credential object to this object by
   * transferring the resources.
   *
   * @param other The Credential object to move from.
   * @return A reference to this Credential object.
   */
  Credential& operator=(Credential&& other) noexcept;
};

/**
 * Configuration class for managing cluster nodes, credentials, TLS mode, and
 * database settings. Provides methods to construct configurations with single
 * or multiple cluster nodes, set TLS mode, database ID, credentials, request
 * timeout, client name, preferred read node, and serialize the configuration
 * using Protocol Buffers.
 */
class Config {
 public:
  /**
   * Constructs a Config object with a single cluster node using a reference
   * to a std::string for the host.
   *
   * @param host The hostname of the cluster node as a reference to a
   * std::string. Defaults to "localhost".
   * @param port The port number of the cluster node. Defaults to 6379.
   */
  Config(const std::string& host = DEFAULT_HOST,
         const uint32_t port = DEFAULT_PORT);

  /**
   * Constructs a Config object with multiple cluster nodes.
   *
   * @param cluster_nodes A vector of ClusterNode objects representing the
   * cluster nodes.
   */
  Config(std::vector<ClusterNode>& cluster_nodes);

  /**
   * Default constructor for Config.
   * Creates a Config object with default settings.
   */
  Config();

  /**
   * Copy constructor for Config.
   * Creates a new Config object as a copy of an existing one.
   *
   * @param other The source Config object to copy from.
   */
  Config(const Config& other) noexcept;

  /**
   * Copy assignment operator for Config.
   * Copies the contents of the source Config object to this object.
   *
   * @param other The source Config object to copy from.
   * @return A reference to this Config object.
   */
  Config& operator=(const Config& other) noexcept;

  /**
   * Move constructor for Config.
   * Transfers ownership of resources from the source object to the new object.
   *
   * @param other The source Config object to move from.
   */
  Config(Config&& other) noexcept;

  /**
   * Move assignment operator for Config.
   * Transfers ownership of resources from the source object to this object.
   *
   * @param other The source Config object to move from.
   * @return A reference to this Config object.
   */
  Config& operator=(Config&& other) noexcept;

  /**
   * Sets the TLS mode to InsecureTLS.
   * Default mode is NoTLS.
   *
   * @return A reference to the updated Config object.
   */
  Config& withInsecureTLSMode();

  /**
   * Sets the TLS mode to SecureTLS.
   * Default mode is NoTLS.
   *
   * @return A reference to the updated Config object.
   */
  Config& withSecureTLSMode();

  /**
   * Sets the database ID for the configuration.
   * Default database ID is 0.
   *
   * @param database The database ID to be set.
   * @return A reference to the updated Config object.
   */
  Config& withDatabase(uint32_t database);

  /**
   * Sets the credentials for the configuration.
   *
   * @param username The username for authentication.
   * @param password The password for authentication.
   * @return A reference to the updated Config object.
   */
  Config& withCredential(const std::string& username,
                         const std::string& password);

  /**
   * Sets the request timeout for the configuration.
   *
   * @tparam T The type of the timeout duration, which can be
   * std::chrono::nanoseconds, std::chrono::milliseconds, or
   * std::chrono::seconds.
   * @param timeout The timeout duration to be set.
   * @return A reference to the updated Config object.
   */
  template <typename T>
  typename std::enable_if<std::is_same_v<T, std::chrono::nanoseconds> ||
                              std::is_same_v<T, std::chrono::milliseconds> ||
                              std::is_same_v<T, std::chrono::seconds>,
                          Config&>::type
  withRequestTimeout(T timeout) {
    request_timeout_ = static_cast<uint32_t>(timeout.count());
    return *this;
  }

  /**
   * Sets the client name for the configuration.
   *
   * @param client_name The name of the client to be set.
   * @return A reference to the updated Config object.
   */
  Config& withClientName(const std::string& client_name);

  /**
   * Sets the preferred node to read data from in a cluster.
   *
   * @param read_from The preferred node to read from.
   * @return A reference to the updated Config object.
   */
  Config& withReadFrom(ReadFrom read_from);

  /**
   * Serializes the configuration into a byte array using Protocol Buffers.
   *
   * @return An optional vector of bytes containing the serialized data.
   *         Returns std::nullopt if serialization fails.
   */
  std::optional<std::vector<uint8_t>> serialize();

 private:
  std::vector<ClusterNode> cluster_nodes_;
  Credential credential_;
  TLSMode tls_mode_ = TLSMode::NoTLS;
  uint32_t database_ = 0;
  uint32_t request_timeout_ = 1000;
  std::optional<std::string> client_name_;
  ReadFrom read_from_ = ReadFrom::Primary;
};

}  // namespace glide

#endif  // CONFIG_HPP_
