# backsaws

A backsaw is a hand saw with a reinforced back, typically for precision cuts.

A credentials provider for the [Cognitect AWS API][awsapi] that grabs
credentials from [aws-vault][awsvault].

[awsapi]: https://github.com/cognitect-labs/aws-api
[awsvault]: https://github.com/99designs/aws-vault

## Usage

### Installation

Add the most recent git sha to `deps.edn`:

```clojure
com.latacora/awsvault-cred-provider
{:git/url "https://github.com/latacora/awsvault-cred-provider.git"
 :git/sha "updateme"}
```

### aws-vault cred provider

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
