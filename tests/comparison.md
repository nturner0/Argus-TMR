# ISA test comparison

| ISA Test | Category | Original Rocket | TMR Rocket |
|---|---|---|---|
| rv64ui-p-simple | Basic | PASS | PASS |
| rv64ui-p-add | Arithmetic | SKIP | SKIP |
| rv64ui-p-addi | Arithmetic | SKIP | SKIP |
| rv64ui-p-sub | Arithmetic | SKIP | SKIP |
| rv64ui-p-and | Bitwise | SKIP | SKIP |
| rv64ui-p-or | Bitwise | SKIP | SKIP |
| rv64ui-p-xor | Bitwise | SKIP | SKIP |
| rv64ui-p-sll | Shifts | SKIP | SKIP |
| rv64ui-p-srl | Shifts | SKIP | SKIP |
| rv64ui-p-sra | Shifts | SKIP | SKIP |
| rv64ui-p-beq | Branches | SKIP | SKIP |
| rv64ui-p-bne | Branches | SKIP | SKIP |
| rv64ui-p-blt | Branches | SKIP | SKIP |
| rv64ui-p-bge | Branches | SKIP | SKIP |
| rv64ui-p-jal | Jumps | SKIP | SKIP |
| rv64ui-p-jalr | Jumps | SKIP | SKIP |
| rv64ui-p-lb | Loads | SKIP | SKIP |
| rv64ui-p-lh | Loads | SKIP | SKIP |
| rv64ui-p-lw | Loads | SKIP | SKIP |
| rv64ui-p-ld | Loads | SKIP | SKIP |
| rv64ui-p-sb | Stores | SKIP | SKIP |
| rv64ui-p-sh | Stores | SKIP | SKIP |
| rv64ui-p-sw | Stores | SKIP | SKIP |
| rv64ui-p-sd | Stores | SKIP | SKIP |
| rv64um-p-mul | Multiplication | SKIP | SKIP |
| rv64um-p-mulh | Multiplication | SKIP | SKIP |
| rv64um-p-div | Division | SKIP | SKIP |
| rv64um-p-rem | Division | SKIP | SKIP |
| rv64ua-p-amoadd_d | Atomic | SKIP | SKIP |
| rv64ua-p-lrsc | Atomic | SKIP | SKIP |

Original Rocket: selected 30, attempted 1, passed 1, failed 0, skipped 29, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

TMR Rocket: selected 30, attempted 1, passed 1, failed 0, skipped 29, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

## Outcome groups

- Baseline PASS, TMR PASS: rv64ui-p-simple
- Baseline SKIP, TMR SKIP: rv64ui-p-add, rv64ui-p-addi, rv64ui-p-sub, rv64ui-p-and, rv64ui-p-or, rv64ui-p-xor, rv64ui-p-sll, rv64ui-p-srl, rv64ui-p-sra, rv64ui-p-beq, rv64ui-p-bne, rv64ui-p-blt, rv64ui-p-bge, rv64ui-p-jal, rv64ui-p-jalr, rv64ui-p-lb, rv64ui-p-lh, rv64ui-p-lw, rv64ui-p-ld, rv64ui-p-sb, rv64ui-p-sh, rv64ui-p-sw, rv64ui-p-sd, rv64um-p-mul, rv64um-p-mulh, rv64um-p-div, rv64um-p-rem, rv64ua-p-amoadd_d, rv64ua-p-lrsc
