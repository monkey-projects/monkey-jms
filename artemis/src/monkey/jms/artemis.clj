(ns monkey.jms.artemis
  "ActiveMQ Artemis specific functionality"
  (:import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
           org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
           org.apache.activemq.artemis.api.core.TransportConfiguration
           org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory))

(defn transport-config [port]
  (TransportConfiguration.
   (.getName NettyAcceptorFactory)
   {"port" (str port)
    "protocols" "AMQP"}))

(defn start-broker
  "Starts an embedded Artemis broker with AMQP connector"
  [{:keys [journal-dir broker-port]
    :or {journal-dir "target/data/journal"
         broker-port 61617}}]
  (doto (EmbeddedActiveMQ.)
    (.setConfiguration
     (.. (ConfigurationImpl.)
         (setPersistenceEnabled false)
         (setJournalDirectory journal-dir)
         (setSecurityEnabled false)
         (addAcceptorConfiguration (transport-config broker-port))))
    (.start)))

(defn stop-broker [b]
  (.stop b))

(defn with-broker
  "Starts a broker with given configuration, runs `f` and finally stops the broker."
  [conf f]
  (let [b (start-broker conf)]
    (try
      (f)
      (finally 
        (stop-broker b)))))

