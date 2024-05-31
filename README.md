# Monkey JMS

This is a Clojure library that provides wrapper code for [JMS 3](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/messaging/).

## Why?

I've played around with [Bowerick](https://github.com/ruedigergad/bowerick) which is
nice, but one the one hand it contains too much stuff I don't need (and hence, unwanted
dependencies), and on the other hand doesn't provide functionality that I *do* need,
like durable subscribers.  So I decided to roll my own.

## Usage

Include the dependency in your project:
```clojure
{:deps {com.monkeyprojects/monkey-jms {:mvn/version "0.1.0-SNAPSHOT"}}}
```

Then require the namespace and you can create a connection (actually a `JMSContext`)
and producers and/or consumers.

```clojure
(require '[monkey.jms :as jms])

;; Connect.  The connection is auto-started.
(def ctx (jms/connect {:url "amqp://localhost:61616"
                       :username "testuser"
                       :password "verysecret"}))

;; Start consuming.  In this case, it will just print the received message.
(def consumer (jms/consume ctx "topic://test.topic" println))

;; Producer is a fn that can be closed
(def producer (jms/make-producer ctx "topic://test.topic"))
;; Send a message
(producer "Hi, I'm a test message")

;; Stop consuming and producing
(.close producer)
(.close consumer)
;; Close connection
(.close ctx)
```

Each of the objects implements `AutoCloseable`, so you can also use them in
`with-open`.

## Serialization

For simplicity the are messages always `TextMessage`s.  But it's up to you
what you put in those messages.  You can `comp`ose the listener and producer
to suit your needs.  For example:

```clojure
(require '[cheshire.core :as json])

;; Producer that encodes to json before sending
(def json-producer (comp producer json/generate-string))

;; Consumer that parses json
(def json-consumer (jms/consume ctx "topic://some.json.topic" (comp println json/parse-string)))
```

Maybe in a future version we will add the possibility for fine-grained control
over the construction of messages.

When receiving messages, we're not always sure that the messages will be `TextMessage`s.
For this I have provided a multimethod `message->str` that uses `class` as a dispatch function.
It handles `TextMessage` and `BytesMessage`.  You can add your own implementation if needed,
but you can also pass a `:deserializer` option to the `consume` function to override this
default behaviour.

```clojure
;; Consumer that explicitly calls `getText` on the incoming message
(def custom-consumer (jms/consume ctx "topic://test.topic" println {:deserializer (memfn getText)}))
```

## Durable Consumers

You can also create durable consumers, first by specifying a `client-id` in the connection
options, and then by specifying an `id` in the options to `consume`.
```clojure
(def ctx (jms/connect {:url "amqp://localhost:61616"
                       :username "testuser"
                       :password "verysecret"
                       :client-id "unique-client-id"}))
		     
(def durable-cons (c/consume ctx "topic://test.topic" my-handler {:id "durable-consumer-id")))

;; When no longer needed, you can `unsubscribe`
(jms/unsubscribe ctx "durable-consumer-id")
```

## Synchronous Consumption

Sometimes it's more useful or straightforward to actively poll received messages.
You can achieve this by not passing a listener in the `consume` function.  The
consumer is also a Clojure function that you can invoke to poll for the next
message.  You can pass a timeout (in msecs).  If you pass a timeout of zero, it
will immediately return unless a message is ready.

```clojure
;; You can still pass an options map should you so desire
(def consume (jms/consume ctx "test.topic"))

;; Receive next message
(def msg (consume 1000))

(println "The next message received is:" msg)
```

## License

Copyright (c) 2024 by [Monkey Projects BV](https://www.monkey-projects.be).

[MIT License](LICENSE)
