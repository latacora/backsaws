(ns com.latacora.backsaws.rethrow)

(defmacro rethrow
  "Convenience macro to rethrow errors from cognitect.aws.client.api.

  This is implemented as a macro so that it doesn't add to the stack trace."
  [aws-response]
  `(let [response# ~aws-response
         throwable#
         (or
          (:cognitect.aws.http.cognitect/throwable response#)
          (:cognitect.aws.client/throwable response#)
          (:throwable response#)
          ;; other kinds of errors, which might have a throwable
          ;; with a name we don't know about, or which might not have a throwable.
          (when (:Error response#)
            (ex-info
             (str "Error from AWS request: " (-> response# :Error :Message))
             response#)))]
     (if throwable#
       (throw throwable#)
       response#)))
