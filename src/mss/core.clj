(ns mss.core
  (:use
    [clojure.string :only (join)]
    [taoensso.timbre :as log :only (info)]
    [clojure.tools.cli :only (cli)])
  (:import
    [java.net InetSocketAddress ServerSocket Socket]
    [java.nio.channels SocketChannel ServerSocketChannel Selector SelectionKey]
    [java.nio ByteBuffer]
    [java.nio.charset Charset])
  (:gen-class))

(comment
  "public api"
  (disconnect [id])
  (auth [id name])
  (send-to [id])
  (send-to [& ids])
  (receive [id msg])
  )

(def ^ByteBuffer buf (ByteBuffer/allocate 16384))
(def ^Charset charset (Charset/forName "UTF-8"))

(defn buf->str [byte-buffer] (.toString (.decode charset byte-buffer)))
(defn str->buf [^String str] (.encode charset str))

(defn ip-with-port [^SocketChannel channel]
  (let [^Socket socket (.socket channel)
        ip (.. socket (getInetAddress) (getHostAddress))
        port (.getPort socket)]
    (str ip ":" port)))

(defn accept-connection [^ServerSocket server-socket selector]
  (let [^ServerSocketChannel channel (.. server-socket (accept) (getChannel))]
    (log/info "connection accepted from" (ip-with-port channel))
    (doto channel
      (.configureBlocking false)
      (.register selector SelectionKey/OP_READ))))

(defn disconnect [^SocketChannel channel]
  (do
    (.. channel
      (socket)
      (close))))

(defn handle-msg [str]
  (println "received: " str))

(defn read-socket [^SelectionKey key]
  (let [^SocketChannel channel (.channel key)]
    (.clear buf)
    (.read channel buf)
    (.flip buf)
    (if (zero? (.limit buf))
      (do
        (log/info "connection lost from" (ip-with-port channel))
        (disconnect channel))
      (do
        (let [msg (buf->str buf)]
          (log/info "message received from" (ip-with-port channel) ":" msg)
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
  (let [[opts extra banner]
        (cli args
             ["-h" "--help" "prints help" :flag true :default false]
             ["-p" "--port" "sets port to listen on" :parse-fn #(Integer/parseInt %) :default 8765])]
    (if (opts :help)
      (println "multi socket server\n" banner)
      (run-server (opts :port)))))
