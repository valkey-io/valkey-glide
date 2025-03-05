# Valkey-Glide Language Support Policy (Draft)

## Overview

The Valkey-Glide project is unique from many other Open Source projects in that we support a wide range of languages. Each of these languages offer different release cycles and wildly varying levels of standard support for maintainance and security patches. This document outlines the recommended approach for supporting multiple programming language versioning within Valkey-Glide. The languages covered include Rust, Python, Java, Node.JS, Go, .Net (Core), and PHP. The general recommendation is to support all currently supported language versions and the prior LTS version, ensuring a balance between stability, security and the abiltiy to access newer language features. This policy is subject to change based on the specific needs of the project and the languages supported. Critical security issues may require immediate action, regardless of a language version's status.

## Language Support

### .Net (Core)

- **Vendor Support Cycle**: 3 years for LTS, 18 months for non-LTS
- **Supported Versions**: .Net 6 (eol), .Net 8 (lts), .Net 9 (maintenance)
- **Notes**: Only even-numbered versions have LTS support.

### Go

- **Vendor Support Cycle**: 1 years for LTS
- **Supported Versions**: 1.22 (eol), 1.23 (lts), 1.24 (lts)
- **Notes**: Each version is supported until there are two subsequent releases. This language would be an outlier in the general policy, but the general Go community seems more accepting of these frequent upgrades.

### Node.JS

- **Vendor Support Cycle**: 18 months for LTS, 3 years for security
- **Supported Versions**: 18 (security), 20 (security), 22 (lts), 23 (maintenance)
- **Notes**: Only even-numbered releases offer LTS. Version 16 will be dropped in April with the release of version 24.

### Python

- **Vendor Support Cycle**: 2 years for LTS, 5 years for security
- **Supported Versions**: 3.9 (security), 3.10 (security), 3.11 (security), 3.12(lts), 3.13(lts)
- **Notes**: Python has a long security support lifecycle of 5 years.

### PHP

- **Vendor Support Cycle**: 2 years for Bug fixes, 4 years for security
- **Supported Versions**: 8.1 (security), 8.2 (security), 8.3 (lts), 8.4 (lts)
- **Notes**: PHP has a long LTS period (2 years) followed by a long security period (2 years).

### Java (Oracle JDK)

- **Vendor Support Cycle**: 2 years LTS, 5 years for premier support, 6 months for maintenance releases
- **Supported Versions**: 17 (premier), 21 (LTS), 23 (maintenance)
- **Notes**: Java has long LTS cycles (2 years) and short interim releases (6 months), with 5 years of premier support.

### Rust

- **Supported Versions**: To be determined based on further research.
- **Notes**: Rust has a unique support policy that requires additional research to establish a suitable approach.

## Conclusion

The recommended approach ensures that each language is supported for 3-5 years beyond its release. This policy aims to provide a sustainable pace for developers and businesses, balancing the need for stability with the ability to adopt new features. While it would be ideal to support languages only while under active support, the short support cycles of .Net, Go, and Node.JS make this challenging. The longer lifecycles of Python, PHP, and Java offer a more sustainable pace for version upgrades.

By adhering to this policy, we can maintain a robust and flexible development environment that supports the needs of our developers and the demands of our projects.
