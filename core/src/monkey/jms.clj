(ns monkey.jms
  "Core namespace for the MonkeyProjects JMS/ActiveMQ wrapper"
  (:import org.apache.qpid.jms.JmsConnectionFactory
           [jakarta.jms Destination JMSContext MessageListener]))

(defn- maybe-set-client-id [ctx id]
  (when id
    (.setClientID ctx id))
  ctx)

(defn ^JMSContext connect
  "Connects to the configured JMS server.  The options map takes a `url`, and
   optional `username` and `password`.  You can also specify a `client-id` for
   durable subscriptions."
  [{:keys [username password url client-id]}]
  (doto (.. (JmsConnectionFactory. username password url)
            (createContext))
    (maybe-set-client-id client-id)
    (.start)))

(defn disconnect
  "Shuts down the given connection"
  [ctx]
  (when ctx
    (.close ctx)))

(defmulti ->destination (fn [obj _] (class obj)))

(def topic-prefix "topic://")
(def queue-prefix "queue://")

(defn make-topic [s ctx]
  (.createTopic ctx s))

(defn make-queue [s ctx]
  (.createQueue ctx s))

(defmethod ->destination java.lang.String [s ctx]
  (condp #(.startsWith %2 %1) s
    topic-prefix (make-topic (subs s (count topic-prefix)) ctx)
    queue-prefix (make-queue (subs s (count queue-prefix)) ctx)))

(defmethod ->destination Destination [d _]
  d)

(defn make-listener [f]
  (reify MessageListener
    (onMessage [_ msg]
      (f msg))))

(defrecord Consumer [consumer deserializer]
  clojure.lang.IFn
  (invoke [_]
    (deserializer (.receive consumer)))
  (invoke [_ timeout]
    (some->
     (cond
       (nil? timeout) (.receive consumer)
       (= 0 timeout) (.receiveNoWait consumer)
       (number? timeout) (.receive consumer timeout)
       :else (throw (ex-info "Invalid timeout value" {:timeout timeout})))
     (deserializer)))
  java.lang.AutoCloseable
  (close [_]
    (.close consumer)))

(defmulti message->str class)

(defmethod message->str jakarta.jms.TextMessage [msg]
  (.getText msg))

(defmethod message->str jakarta.jms.BytesMessage [msg]
  (let [buf (byte-array (.getBodyLength msg))]
    (.readBytes msg buf)
    (String. buf)))

(defn set-listener
  "Explicitly sets a message listener on a consumer.  Note that when a listener
   is set, it is no longer possible to synchronously consume messages."
  [consumer l]
  (.setMessageListener (:consumer consumer) (make-listener l))
  consumer)

(defn consume
  "Starts consuming messages from the given destination.  Messages are passed
   to the listener.  Returns an `AutoCloseable` that can be used to stop 
   consuming.  The optional `opts` argument is a map that can hold an `:id`
   for a durable subscription.  Not that you need to set a client id on the
   context as well for this to work.  You can also specify a `:selector`
   for the consumer, to do broker-side message filtering.  If `:shared?` is true,
   and an `:id` is specified, a shared durable consumer is created."
  [ctx dest & [listener-or-opts opts]]
  (let [d (->destination dest ctx)
        f? (fn? listener-or-opts)
        listener (when f? listener-or-opts)
        {:keys [deserializer selector shared?]
         :or {deserializer message->str}
         :as opts} (if f? opts listener-or-opts)
        c (if-let [id (:id opts)]
            (if shared?
              (.createSharedDurableConsumer ctx d id selector)
              (.createDurableConsumer ctx d id selector false))
            (.createConsumer ctx d selector))]
    (cond-> (->Consumer c deserializer)
      listener
      (set-listener (comp listener deserializer)))))

(def make-consumer
  "Alias for `consume`, to be similar to `make-producer`."
  consume)

(defn unsubscribe
  "Unsubscribes the connection from a durable subscription."
  [ctx id]
  (.unsubscribe ctx id))

(defrecord Producer [prod msg-maker dest]
  clojure.lang.IFn
  (invoke [_ msg]
    (.send prod dest (msg-maker msg)))
  java.lang.AutoCloseable
  (close [_]
    ;; Only close if the producer implements it
    (when (instance? java.lang.AutoCloseable prod)
      (.close prod))))

(defn make-text-message [ctx s]
  (.createTextMessage ctx s))

(defn make-producer
  "Creates a producer function.  The function accepts a single argument, the
   messages to produce.  How messages are built can be specified in the options.
   The producer also implements `AutoCloseable`, to allow it to be shut down."
  [ctx dest & [opts]]
  (let [serializer (or (:serializer opts) make-text-message)]
    (->Producer (.createProducer ctx)
                (partial serializer ctx)
                (->destination dest ctx))))

(def produce
  "Alias for `make-producer` for consistency with `consume`."
  make-producer)
