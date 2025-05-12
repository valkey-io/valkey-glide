#include <glide/config.h>
#include <glide/connection_request.pb.h>

namespace glide {

/**
 * Constructs a ClusterNode object with a host and port.
 */
ClusterNode::ClusterNode(const std::string& host, const uint32_t port)
    : host(host), port(port) {}
/**
 * Copy constructor for ClusterNode.
 * Creates a new ClusterNode object as a copy of an existing one.
 */
ClusterNode::ClusterNode(const ClusterNode& other) noexcept
    : host(other.host), port(other.port) {}

/**
 * Assigns the value of another ClusterNode object to this object.
 */
ClusterNode& ClusterNode::operator=(const ClusterNode& other) noexcept {
  host = other.host;
  port = other.port;
  return *this;
}

/**
 * Move constructor for ClusterNode.
 * Creates a new ClusterNode object by transferring the resources from another
 * ClusterNode object.
 */
ClusterNode::ClusterNode(ClusterNode&& other) noexcept {
  host = std::move(other.host);
  port = other.port;
}

/**
 * Default constructor for Credential.
 * Creates a Credential object with empty username and password.
 */
Credential::Credential() = default;

/**
 * Constructs a Credential object with a username and password.
 */
Credential::Credential(const std::string& username, const std::string& password)
    : username(username), password(password) {}

/**
 * Move assignment operator for ClusterNode.
 * Transfers the resources from another ClusterNode object to this object.
 */
ClusterNode& ClusterNode::operator=(ClusterNode&& other) noexcept {
  host = std::move(other.host);
  port = other.port;
  return *this;
}

/**
 * Copy constructor for Credential.
 * Creates a new Credential object as a copy of an existing one.
 */
Credential::Credential(const Credential& other) noexcept { *this = other; }

/**
 * Copy assignment operator for Credential.
 * Assigns the value of another Credential object to this object.
 */
Credential& Credential::operator=(const Credential& other) noexcept {
  if (this == &other) {
    return *this;
  }
  username = other.username;
  password = other.password;
  return *this;
}

/**
 * Move constructor for Credential.
 * Creates a new Credential object by transferring the resources from another
 * Credential object.
 */
Credential::Credential(Credential&& other) noexcept {
  *this = std::move(other);
}

/**
 * Move assignment operator for Credential.
 * Assigns the value of another Credential object to this object by
 * transferring the resources.
 */
Credential& Credential::operator=(Credential&& other) noexcept {
  if (this == &other) {
    return *this;
  }
  username = std::move(other.username);
  password = std::move(other.password);
  return *this;
}

/**
 * Constructs a Config object with a single cluster node using a reference
 * to a std::string for the host.
 */
Config::Config(const std::string& host, const uint32_t port) {
  cluster_nodes_.push_back(ClusterNode(host, port));
}

/**
 * Constructs a Config object with multiple cluster nodes.
 */
Config::Config(std::vector<ClusterNode>& cluster_nodes)
    : cluster_nodes_(cluster_nodes) {}

/**
 * Default constructor for Config.
 * Creates a Config object with default settings.
 */
Config::Config() = default;

/**
 * Copy constructor for Config.
 * Creates a new Config object as a copy of an existing one.
 */
Config::Config(const Config& other) noexcept
    : cluster_nodes_(other.cluster_nodes_),
      credential_(other.credential_),
      tls_mode_(other.tls_mode_),
      database_(other.database_) {}

/**
 * Move constructor for Config.
 * Transfers ownership of resources from the source object to the new object.
 */
Config::Config(Config&& other) noexcept
    : cluster_nodes_(std::move(other.cluster_nodes_)),
      credential_(std::move(other.credential_)),
      tls_mode_(other.tls_mode_),
      database_(other.database_) {}

/**
 * Sets the TLS mode to InsecureTLS.
 * Default sets to NoTLS.
 */
Config& Config::withInsecureTLSMode() {
  tls_mode_ = TLSMode::InsecureTLS;
  return *this;
}

/**
 * Sets the TLS mode to SecureTLS.
 * Default sets to NoTLS.
 */
Config& Config::withSecureTLSMode() {
  tls_mode_ = TLSMode::SecureTLS;
  return *this;
}

/**
 * Sets the database ID for the configuration.
 * Default sets to 0.
 */
Config& Config::withDatabase(uint32_t database) {
  database_ = database;
  return *this;
}

/**
 * Sets the credentials for the configuration.
 */
Config& Config::withCredential(const std::string& username,
                               const std::string& password) {
  credential_ = Credential(username, password);
  return *this;
}

/**
 * Sets the client name for the configuration.
 */
Config& Config::withClientName(const std::string& client_name) {
  client_name_ = client_name;
  return *this;
}

/**
 * Sets the preferred node to read data from in a cluster.
 */
Config& Config::withReadFrom(ReadFrom read_from) {
  read_from_ = read_from;
  return *this;
}

/**
 * Serializes the configuration into a byte array using Protocol Buffers.
 */
std::optional<std::vector<uint8_t>> Config::serialize() {
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  connection_request::ConnectionRequest cr;

  // Cluster nodes.
  for (auto& i : cluster_nodes_) {
    connection_request::NodeAddress* na = cr.add_addresses();
    na->set_host(i.host);
    na->set_port(i.port);
  }

  // Credentials.
  if (!credential_.username.empty() && !credential_.password.empty()) {
    connection_request::AuthenticationInfo* ai =
        cr.mutable_authentication_info();
    ai->set_username(credential_.username);
    ai->set_password(credential_.password);
  }

  // TLS mode.
  switch (tls_mode_) {
    case glide::TLSMode::NoTLS:
      cr.set_tls_mode(connection_request::NoTls);
      break;
    case glide::TLSMode::InsecureTLS:
      cr.set_tls_mode(connection_request::InsecureTls);
      break;
    case glide::TLSMode::SecureTLS:
      cr.set_tls_mode(connection_request::SecureTls);
      break;
  }

  // Database.
  cr.set_database_id(database_);

  // Request timeout.
  cr.set_request_timeout(request_timeout_);

  // Client name.
  if (client_name_) {
    cr.set_client_name(client_name_.value());
  }

  // Read from.
  switch (read_from_) {
    case ReadFrom::Primary:
      cr.set_read_from(connection_request::Primary);
      break;
    case ReadFrom::PreferReplica:
      cr.set_read_from(connection_request::PreferReplica);
      break;
    case ReadFrom::LowestLatency:
      cr.set_read_from(connection_request::LowestLatency);
      break;
    case ReadFrom::AZAffinity:
      cr.set_read_from(connection_request::AZAffinity);
      break;
  }

  // Serializing.
  std::vector<uint8_t> output(cr.ByteSizeLong());
  bool serialization_success =
      cr.SerializeToArray(output.data(), output.size());
  google::protobuf::ShutdownProtobufLibrary();
  if (serialization_success) {
    return output;
  }
  return std::nullopt;
}

}  // namespace glide
