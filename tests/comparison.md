# ISA test comparison

Final outcomes after paired retries. Each initial pass had 28 PASS and 2 TIMEOUT (sb/sh at 120 seconds); both retries passed with a 600-second limit. See [timeout investigation](timeout-investigation.md) and the initial/retry CSVs.

| ISA Test | Category | Original Rocket | TMR Rocket |
|---|---|---|---|
| rv64ui-p-simple | Basic | PASS | PASS |
| rv64ui-p-add | Arithmetic | PASS | PASS |
| rv64ui-p-addi | Arithmetic | PASS | PASS |
| rv64ui-p-sub | Arithmetic | PASS | PASS |
| rv64ui-p-and | Bitwise | PASS | PASS |
| rv64ui-p-or | Bitwise | PASS | PASS |
| rv64ui-p-xor | Bitwise | PASS | PASS |
| rv64ui-p-sll | Shifts | PASS | PASS |
| rv64ui-p-srl | Shifts | PASS | PASS |
| rv64ui-p-sra | Shifts | PASS | PASS |
| rv64ui-p-beq | Branches | PASS | PASS |
| rv64ui-p-bne | Branches | PASS | PASS |
| rv64ui-p-blt | Branches | PASS | PASS |
| rv64ui-p-bge | Branches | PASS | PASS |
| rv64ui-p-jal | Jumps | PASS | PASS |
| rv64ui-p-jalr | Jumps | PASS | PASS |
| rv64ui-p-lb | Loads | PASS | PASS |
| rv64ui-p-lh | Loads | PASS | PASS |
| rv64ui-p-lw | Loads | PASS | PASS |
| rv64ui-p-ld | Loads | PASS | PASS |
| rv64ui-p-sb | Stores | PASS | PASS |
| rv64ui-p-sh | Stores | PASS | PASS |
| rv64ui-p-sw | Stores | PASS | PASS |
| rv64ui-p-sd | Stores | PASS | PASS |
| rv64um-p-mul | Multiplication | PASS | PASS |
| rv64um-p-mulh | Multiplication | PASS | PASS |
| rv64um-p-div | Division | PASS | PASS |
| rv64um-p-rem | Division | PASS | PASS |
| rv64ua-p-amoadd_d | Atomic | PASS | PASS |
| rv64ua-p-lrsc | Atomic | PASS | PASS |

Original Rocket: selected 30, attempted 30, passed 30, failed 0, skipped 0, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

TMR Rocket: selected 30, attempted 30, passed 30, failed 0, skipped 0, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

## Outcome groups

- Baseline PASS, TMR PASS: rv64ui-p-simple, rv64ui-p-add, rv64ui-p-addi, rv64ui-p-sub, rv64ui-p-and, rv64ui-p-or, rv64ui-p-xor, rv64ui-p-sll, rv64ui-p-srl, rv64ui-p-sra, rv64ui-p-beq, rv64ui-p-bne, rv64ui-p-blt, rv64ui-p-bge, rv64ui-p-jal, rv64ui-p-jalr, rv64ui-p-lb, rv64ui-p-lh, rv64ui-p-lw, rv64ui-p-ld, rv64ui-p-sb, rv64ui-p-sh, rv64ui-p-sw, rv64ui-p-sd, rv64um-p-mul, rv64um-p-mulh, rv64um-p-div, rv64um-p-rem, rv64ua-p-amoadd_d, rv64ua-p-lrsc
