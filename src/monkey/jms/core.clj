(ns monkey.jms.core
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

(defn consume
  "Starts consuming messages from the given destination.  Messages are passed
   to the listener.  Returns an `AutoCloseable` that can be used to stop 
   consuming.  The optional `opts` argument is a map that can hold an `:id`
   for a durable subscription.  Not that you need to set a client id on the
   context as well for this to work."
  [ctx dest listener & [opts]]
  (let [d (->destination dest ctx)]
    (doto (if-let [id (:id opts)]
            (.createDurableConsumer ctx d id)
            (.createConsumer ctx d))
      (.setMessageListener (make-listener (comp listener (memfn getText)))))))

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
    (.close prod)))

(defn make-producer
  "Creates a producer function.  The function accepts a single argument, the
   messages to produce.  How messages are built can be specified in the options.
   The producer also implements `AutoCloseable`, to allow it to be shut down."
  [ctx dest & [opts]]
  (->Producer (.createProducer ctx)
              #(.createTextMessage ctx %)
              (->destination dest ctx)))
