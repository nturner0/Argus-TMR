#include "VTMRShiftQueue.h"
#include "VTMRShiftQueue___024root.h"
#include <cstdio>
#include <cstdlib>

int main() {
    VTMRShiftQueue q;
    q.clock = 0;
    q.reset = 1;
    q.io_enq_valid = 0;
    q.io_deq_ready = 0;
    q.eval();
    auto tick = [&]() {
        q.clock = 1; q.eval();
        q.clock = 0; q.eval();
    };
    tick();
    const unsigned instruction = 0x00100093; // addi x1, x0, 1
    q.reset = 0;
    q.io_enq_bits_data = instruction;
    q.io_enq_valid = 1;
    q.eval();
    if (!q.io_enq_ready) return 1;
    tick();
    q.io_enq_valid = 0;
    q.io_enq_bits_data = 0;
    q.eval();
    auto& a = q.rootp->TMRShiftQueue__DOT__elts_A_0_data;
    auto& b = q.rootp->TMRShiftQueue__DOT__elts_B_0_data;
    auto& c = q.rootp->TMRShiftQueue__DOT__elts_C_0_data;
    auto check = [&](const char* phase, unsigned expected_b) {
        std::printf("%s A=%08x B=%08x C=%08x output=%08x valid=%u\n",
                    phase, a, b, c, q.io_deq_bits_data, q.io_deq_valid);
        if (a != instruction || b != expected_b || c != instruction ||
            !q.io_deq_valid || q.io_deq_bits_data != instruction) std::exit(1);
    };
    check("held", instruction);
    b ^= 1u; // One deposit into simulated storage, between edges.
    q.eval();
    check("after upset", instruction ^ 1u);
    tick(); // Still held, with no enqueue or dequeue.
    check("after next edge", instruction);
    q.io_deq_ready = 1; q.eval();
    check("dequeue", instruction);
    tick();
    if (q.io_deq_valid) return 1;
    q.final();
    std::puts("PASS");
}
