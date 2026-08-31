# Changelog

All notable changes to this project will be documented in this file, which follows the conventions
of [Keep a Changelog].


## [Unreleased]

### Added

- An alternative to `invoke` named `paginated-invoke` that automatically and seamlessly handles
  pagination
- A [`CredentialsProvider`][CredentialsProvider] that supports [`aws-vault`][aws-vault]
- A [`CredentialsProvider`][CredentialsProvider] that supports
  [`credential_process`][credential_process] ([#1])
- `default-credentials-provider`: aws-api's default chain with `credential-process-provider` in
  it, which is what a caller wanting IAM Identity Center credentials actually needs. tuchos and
  gws-ingestion-monitor each carried a copy of this, docstring and Slack links included ([#16])

### Changed

- `paginated-invoke` pages S3's `ListBuckets`, which grew `MaxBuckets` and `ContinuationToken`.
  Callers whose `com.cognitect.aws/s3` is recent enough to describe those already get this, since
  the paging opts are inferred from the service descriptor rather than written down here ([#15])
- `org.clojure/data.json` is a declared dependency. `credentials-providers` reads the credential
  process' output with it and reached it through `com.cognitect.aws/api` until now ([#15])
- Dependencies and pinned GitHub Actions are current, and Clojure is 1.12.5 ([#15])

### Fixed

- CI runs again. Its job asked for `ubuntu-20.04`, which GitHub retired, and a job naming a retired
  runner sits queued until GitHub cancels it, so every run since September 2025 reported `cancelled`
  rather than failing — including the ones behind merged pull requests ([#15])
- `clojure -T:build test` and `clojure -T:build ci` run the tests instead of dying on a missing
  namespace. build-clj reaches for Cognitect's test-runner when the `:test` alias declares no
  `:main-opts`, which this one deliberately does not, so both tasks name kaocha now ([#15])
- A built jar's pom describes backsaws. `b/write-pom` synchronizes from `./pom.xml`, and that file
  still carried the name, description and repository URL of the template this project started from
  ([#15])
- Loading `build.clj` no longer fetches aws-api's `latest-releases.edn`. The result was discarded,
  so every `clojure -T:build` invocation spent a network call on nothing ([#15])


[#1]: https://github.com/latacora/backsaws/pull/1
[#15]: https://github.com/latacora/backsaws/pull/15
[#16]: https://github.com/latacora/backsaws/pull/16
[aws-vault]: https://github.com/99designs/aws-vault
[credential_process]: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sourcing-external.html
[CredentialsProvider]: https://github.com/cognitect-labs/aws-api#credentials
[keep a changelog]: https://keepachangelog.com/en/1.0.0/
