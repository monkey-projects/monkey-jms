(ns monkey.jms.core
  "Core namespace for the MonkeyProjects JMS/ActiveMQ wrapper"
  (:import org.apache.qpid.jms.JmsConnectionFactory
           [jakarta.jms Destination JMSContext MessageListener]))

(defn ^JMSContext connect
  "Connects to the configured JMS server"
  [{:keys [username password url]}]
  (doto (.. (JmsConnectionFactory. username password url)
            (createContext))
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
   consuming."
  [ctx dest listener & [opts]]
  (doto (.createConsumer ctx (->destination dest ctx))
    (.setMessageListener (make-listener (comp listener (memfn getText))))))

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
              ;; TODO Make this smarter
              #(.createTextMessage ctx %)
              (->destination dest ctx)))
