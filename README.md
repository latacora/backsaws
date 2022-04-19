# backsaws

<img align="right" width="300" height="110" src="https://raw.githubusercontent.com/latacora/backsaws/main/backsaws.svg">

A backsaw is a hand saw with a reinforced back, typically for precision cuts.
`backsaws` is a set of utilities for [Cognitect AWS API][awsapi]. *Back*ports from
a future that never was, for the Cognitect *AWS* API. Get it?

[awsapi]: https://github.com/cognitect-labs/aws-api

## Installation

Add the most recent git sha to `deps.edn`:

```clojure
com.latacora/backsaws
{:git/url "https://github.com/latacora/backsaws.git"
 :git/sha "updateme"}
```

## Pagination

Figures out how to paginate an API and do it automagically.

```clojure
(require '[com.latacora.backsaws.pagination :refer [paginated-invoke]])

(paginated-invoke
  (aws/client {:api :organizations})
  {:op :ListAccountsForParent
   :request {:ParentId "ou-xyzzy"}})
```

## aws-vault `CredentialsProvider`

A [`CredentialsProvider`][CredentialsProvider] backed by [`aws-vault`][awsvault].

```clojure
(require '[com.latacora.backsaws.aws-vault :refer [aws-vault-provider]])

(def provider (aws-vault-provider "some-profile-name-aws-vault-groks"))
(aws/invoke
  (aws/client {:api :s3 :credentials-provider provider})
    {:op :ListBuckets :request {}})
```

## `credential_process` `CredentialsProvider`

A [`CredentialsProvider`][CredentialsProvider] that supports
[`credential_process`][credential_process].

This requires the [AWS CLI profile] (which could be `default`) to have the key
`credential_process` set to a command that this provider can invoke to get valid credentials.

```clojure
(require '[com.latacora.backsaws.credential-process :as cp])

(aws/invoke
  (sso/client {:api :s3 :credentials-provider (cp/provider)})
  {:op :ListBuckets})
```

You can specify the profile name by supplying it as the first argument, for example:

```clojure
(cp/provider "some-profile-name")
```

When invoked without arguments `cp/provider` will look for a profile name in the environment
variable `AWS_PROFILE`, or the Java System Property `aws.profile`, or will fall back to `default`.


## Development

Run the tests:

    clojure -M:test

Run tests, linters etc for CI:

    clojure -A:deps -T:build ci

Deploy to Clojars:

    clojure -A:deps -T:build deploy


## License

Copyright © Latacora

Distributed under the Eclipse Public License version 1.0.


[AWS CLI Profile]: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-profiles.html
[awsvault]: https://github.com/99designs/aws-vault
[CredentialsProvider]: https://github.com/cognitect-labs/aws-api#credentials
[credential_process]: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sourcing-external.html
