(ns com.github.kyleburton.sandbox.utils)

(defn raise [args]
  (throw (RuntimeException. (apply format args))))

(defn- get-user-home []
  (System/getProperty "user.home"))

(defn $HOME [& paths]
  (->file (apply str (cons (str (System/getProperty "user.home") "/") (apply str (interpose "/" paths))))))

(defmulti expand-file-name class)

(defmethod expand-file-name String [#^String path]
  (cond (.startsWith path "~/")
        (.replaceFirst path "^~(/|$)" (str (get-user-home) "/"))
        (.startsWith path "file://~/")
        (.replaceFirst path "^file://~/" (str "file://" (get-user-home) "/"))
        true
        path))


(defn mkdir [path]
  (let [f (->file path)]
    (if (not (.exists f))
      (do
        (log "[INFO] mkdir: creating %s" path)
        (.mkdirs f))
      (if (not (.isDirectory f))
        (log "[WARN] mkdir: %s exists and is not a directory!")
        (log "[DEBUG] mkdir: exists: %s" path)))))

(defn drain-line-reader [rdr]
  (loop [res []
         line (.readLine rdr)]
    (if line
      (recur (conj res line)
             (.readLine rdr))
      res)))

(defn exec [cmd]
  (let [proc (.exec (Runtime/getRuntime) cmd)
        rv (.waitFor proc)]
    {:error (drain-line-reader (java.io.BufferedReader. (java.io.InputStreamReader. (.getErrorStream proc))))
     :output (drain-line-reader (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream proc))))
     :exit rv}))

(defn symlink [src dst]
  (let [src (->file src)
        dst (->file dst)]
    (if (not (.exists src))
      (raise "symlink: src does not exist: %s" src))
    (if (.exists dst)
      (log "[INFO] symlink: dst exists %s => %s" src dst)
      (let [cmd (format "ln -s %s %s" src dst)
            res (exec cmd)]
        (log "[INFO] symlink: %s=>%s : %s" src dst cmd)
        (if (not (= 0 (:exit res)))
          (log "[ERROR] %s" (:error res)))))))

(defn delete [path]
  (let [path (->file path)]
    (if (.exists path)
      (.delete path))))

(defn url-get [url]
  (with-open [is (.openStream (java.net.URL. url))]
    (loop [sb (StringBuffer.)
           chr (.read is)]
      (if (= -1 chr)
        sb
        (do
          (.append sb (char chr))
          (recur sb
                 (.read is)))))))

(defn url-download [url target-dir]
  (let [cmd (format "wget -P %s -c %s" target-dir url)
        res (exec cmd)]
    (log "[INFO] wget: %s" cmd)
    (if (not (= 0 (:exit res)))
      (log "[ERROR] %s" (:error res)))))


(defn- all-groups [m]
  (for [grp (range 1 (+ 1 (.groupCount m)))]
    (.group m grp)))


(defn re-find-all [re str]
  (doall
   (loop [m (re-matcher re str)
          res []]
     (if (.find m)
       (recur m (conj res (vec (all-groups m))))
       res))))

(defn chmod [perms file]
  (let [cmd (format "chmod %s %s" perms file)
        res (exec cmd)]
    (log "[INFO] chmod: %s" cmd)
    (if (not (= 0 (:exit res)))
      (log "[ERROR] %s" (:error res)))))
