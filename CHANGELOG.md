# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions
of [keepachangelog.com](http://keepachangelog.com/).

## 0.1.2 - 2025-01-30

### Fixed

- Fix `handler-fn->interceptor` to make the interceptor compatible with Pedestal Interceptor. The handle-fn should
  always return a `context` map.
- Bump dependencies.
- Fix break-changes.

## 0.1.1 - 2025-01-24

### Fixed

- Added `:interceptors` property for consumers definition schema.

### Changed

- Using `Nippy` for message serialization/de-serialization.

## 0.1.0 - 2025-01-23

### Added

- Initial release.

