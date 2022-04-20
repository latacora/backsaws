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


[#1]: https://github.com/latacora/backsaws/pull/1
[aws-vault]: https://github.com/99designs/aws-vault
[credential_process]: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sourcing-external.html
[CredentialsProvider]: https://github.com/cognitect-labs/aws-api#credentials
[keep a changelog]: https://keepachangelog.com/en/1.0.0/
