(ns mss.core
  (:use
    [clojure.string :only (join)]
    [taoensso.timbre :as log :only (info)])
  (:import
    [java.net InetSocketAddress ServerSocket Socket]
    [java.nio.channels SocketChannel ServerSocketChannel Selector SelectionKey]
    [java.nio ByteBuffer]
    [java.nio.charset Charset])
  (:gen-class))

(def ^:private ^ByteBuffer buf (ByteBuffer/allocate 16384))
(def ^:private ^Charset charset (Charset/forName "UTF-8"))

(defn buf->str [byte-buffer] (.toString (.decode charset byte-buffer)))

(defn audit [action ^SocketChannel channel & args]
  (let [^Socket socket (.socket channel)
        ip (-> socket (.getInetAddress) (.getHostAddress))
        port (.getPort socket)]
    (log/info (str ip ":" port) action (join " " args))))

(defn accept-connection [^ServerSocket server-socket selector]
  (let [^ServerSocketChannel channel (-> server-socket (.accept) (.getChannel))]
    (audit :accepted channel)
    (doto channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_READ))))

(defn disconnect [^SelectionKey key]
  (do
    (.cancel key)
    (-> ^ServerSocketChannel (.channel key)
      (.socket)
      (.close))))

(defn handle-msg [str]
  (println "received: " str))

(defn read-socket [^SelectionKey key]
  (let [^SocketChannel socket-channel (.channel key)]
    (.clear buf)
    (.read socket-channel buf)
    (.flip buf)
    (if (zero? (.limit buf))
      (do
        (audit :lost socket-channel)
        (disconnect key))
      (do
        (let [msg (buf->str buf)]
          (audit :recv socket-channel msg)
          (handle-msg msg))))))

(defn selector [] (Selector/open))

(defn server-channel [selector]
  (let [server-channel (ServerSocketChannel/open)]
    (doto server-channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_ACCEPT))))

(defn server-socket [^ServerSocketChannel server-channel port]
  (let [^ServerSocket server-socket (.socket server-channel)]
    (doto server-socket
      (.bind (InetSocketAddress. port)))))

(defn bootstrap-server [port]
  (let [selector (selector)
        channel (server-channel selector)
         server-socket (server-socket channel port)]
    (log/info "server is up and running on port" port)
    [selector server-socket]))

(defn server-loop [^Selector selector ^ServerSocketChannel server-socket]
  (while true
    (when (> (.select selector) 0)
      (let [selected-keys (.selectedKeys selector)]
        (doseq [^SelectionKey k selected-keys]
          (cond
            (.isAcceptable k) (accept-connection server-socket selector)
            (.isReadable k) (read-socket k))
          (.clear selected-keys))))))

(defn run-server [port]
  (apply server-loop (bootstrap-server port)))

(defn -main [& args]
  (run-server 8765))
