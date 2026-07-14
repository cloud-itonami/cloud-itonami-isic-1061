(ns millops.sim
  "Simulation driver for testing the grain-mill operations actor
  end-to-end.

  For CLI: clojure -M:dev:run

  Example flow:
    1. Start with empty store
    2. Create a batch in :intake phase
    3. Propose a batch -> :mill transition with milling parameters
    4. Governor validates parameters against facts
    5. If valid, audit fact is committed
    6. CLI prints audit trail")

(defn -main [& _args]
  (println "MillOps simulation: not yet implemented.")
  (println "TODO: integrate langgraph-clj StateGraph when available."))
