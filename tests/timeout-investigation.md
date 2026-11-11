# Initial timeout investigation and paired retries

The first pass used a 120-second wall limit. Both processors timed out on the same two tests; the other 28 passed. A deadline result remains TIMEOUT even if a log later contains PASSED. Late markers are not counted as successful first attempts.

The process-group cleanup initially waited only for the Make leader; simulator descendants could continue briefly after it exited. The runner now kills remaining group members even if the leader has exited. A focused subprocess check confirmed that a SIGTERM-ignoring child was killed after its parent exited. No hardware source was changed.

Both affected tests were rerun on both processors with a 600-second wall limit, keeping the same ELF bytes, seed, cycle limit, configuration and simulator. The final 30-row CSVs use these verified retry outcomes; initial and retry CSVs are also published.

| Processor | Test | Initial result | Last retired PC / instruction | Late PASSED marker | Retry result | Retry seconds |
|---|---|---|---|---|---|---|
| tmr | rv64ui-p-sb | TIMEOUT (124) | 000000008000013c / 30529073 | yes | PASS (exit 0) | 24.478 |
| tmr | rv64ui-p-sh | TIMEOUT (124) | 000000008000013c / 30529073 | yes | PASS (exit 0) | 45.297 |
| baseline | rv64ui-p-sb | TIMEOUT (124) | 000000008000013c / 30529073 | yes | PASS (exit 0) | 24.72 |
| baseline | rv64ui-p-sh | TIMEOUT (124) | 000000008000013c / 30529073 | yes | PASS (exit 0) | 46.63 |

The PCs above are the last observed retired instructions, not identified failing instructions. There was no ISA assertion or simulator cycle-limit failure. Successful zero-exit retries establish that the first deadline events were not reproducible functional failures under the extended policy; exact host scheduling/cleanup timing was not profiled. The 120-second policy proved marginal for these runs. For future complete suite runs, use the documented 600-second wall limit on both processors.
