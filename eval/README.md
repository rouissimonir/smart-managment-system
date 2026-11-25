# Retrieval evaluation

`dataset.jsonl` is the question set. `score.py` logs into a running API and prints
hit-rate: the share of scored questions whose `expected_source` file name appears
in the top `k` search hits.

```bash
python3 eval/score.py --self-test
python3 eval/score.py --base http://localhost:8080 --mode HYBRID --k 5
```

Rows with `expected_source: null` are not scored. The policy files live in
`backend/src/main/resources/demo/`. Upload them, or start the app with the demo
seeder, and wait until their status is READY. The cancellation question is written
so keyword search should miss it and a real embedding model can hit it. Do not
record a semantic score while `semanticMeaningful` is false.
