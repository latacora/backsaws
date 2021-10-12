# com.latacora/awsvault-cred-provider

A credentials provider for the [Cognitect AWS API][awsapi] that grabs
credentials from [aws-vault][awsvault].

[awsapi]: https://github.com/cognitect-labs/aws-api
[awsvault]: https://github.com/99designs/aws-vault

## Usage

`deps.edn`:

```clojure
com.latacora/awsvault-cred-provider
{:git/url "https://github.com/latacora/awsvault-cred-provider.git"
 :git/sha "1336740f6ce37dd5d2c7dc188ab441655677fec9"}
```

```clojure
(require '[com.latacora.awsvault-cred-provider :refer [aws-vault-provider]])

(def provider (aws-vault-provider "idclip"))
(aws/invoke
  (aws/client {:api :idkfa :credentials-provider provider})
    {:op :Iddqd :request {}})
```

## Development

Run tests, linters etc for CI:

> clojure -A:deps -T:build ci

Deploy to Clojars:

> clojure -A:deps -T:build deploy

## License

Copyright © Latacora

Distributed under the Eclipse Public License version 1.0.
