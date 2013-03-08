(ns mss.core
  (:import
    [java.net InetSocketAddress]
    [java.nio.channels ServerSocketChannel Selector SelectionKey]
    [java.nio ByteBuffer]
    [java.nio.charset Charset])
  (:gen-class))

(def buf (ByteBuffer/allocate 16384))
(def charset (Charset/forName "UTF-8"))

(defn buf->str [byte-buffer] (.toString (.decode charset byte-buffer)))

(defn accept-connection [server-socket selector]
  (let [channel (-> server-socket (.accept) (.getChannel))]
    (println "connection accepted from " channel)
    (doto channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_READ))))

(defn read-socket [key]
  (let [socket-channel (.channel key)]
    (.clear buf)
    (.read (.channel key) buf)
    (.flip buf)
    (if (zero? (.limit buf))
      (do
        (println "Lost connection from" socket-channel)
        (.cancel key)
        (.close (.socket socket-channel)))
      (println "read: " (buf->str buf)))))

(defn disconnect [key]
  (do
    (.cancel key)
    (-> key
      (.channel)
      (.socket)
      (.close))))

(defn selector [] (Selector/open))

(defn server-channel [selector]
  (let [server-channel (ServerSocketChannel/open)]
    (doto server-channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_ACCEPT))))

(defn server-socket [server-channel port]
  (let [server-socket (.socket server-channel)]
    (doto server-socket
      (.bind (InetSocketAddress. port)))))

(defn bootstrap-server [port]
  (let [selector (selector)
        channel (server-channel selector)
        server-socket (server-socket channel port)]
    [selector server-socket]))

(defn server-loop [selector server-socket]
  (while true
    (when (> (.select selector) 0)
      (let [selected-keys (.selectedKeys selector)]
        (doseq [k selected-keys]
          (cond
            (.isAcceptable k) (accept-connection server-socket selector)
            (.isReadable k) (read-socket k))
          (.clear selected-keys))))))

(defn run-server [port]
  (apply server-loop (bootstrap-server port)))

(defn -main [& args]
  (run-server 8765))
