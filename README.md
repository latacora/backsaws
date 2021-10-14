# backsaws

<img src="https://raw.githubusercontent.com/latacora/backsaws/main/backsaws.svg">

A backsaw is a hand saw with a reinforced back, typically for precision cuts.
`backsaws` is a set of utilities for [Cognitect AWS API][awsapi].

[awsapi]: https://github.com/cognitect-labs/aws-api

## Installation

Add the most recent git sha to `deps.edn`:

```clojure
com.latacora/awsvault-cred-provider
{:git/url "https://github.com/latacora/awsvault-cred-provider.git"
 :git/sha "updateme"}
```

## aws-vault cred provider

A credentials provider backed by [`aws-vault`][awsvault].

[awsvault]: https://github.com/99designs/aws-vault

```clojure
(require '[com.latacora.backsaws.aws-vault :refer [aws-vault-provider]])

(def provider (aws-vault-provider "some-profile-name-aws-vault-groks"))
(aws/invoke
  (aws/client {:api :s3 :credentials-provider provider})
    {:op :ListBuckets :request {}})
```

## Development

Run tests, linters etc for CI:

> clojure -A:deps -T:build ci

Deploy to Clojars:

> clojure -A:deps -T:build deploy

## License

Copyright © Latacora

Distributed under the Eclipse Public License version 1.0.
