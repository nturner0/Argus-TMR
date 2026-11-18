#include "model.hpp"
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <functional>
#include <stdexcept>

struct Site {
    const char *declaration;
    const char *field;
    unsigned width;
    std::function<uint64_t(Root&, unsigned)> read;
    std::function<void(Root&, unsigned, uint64_t)> flip;
};

template<class T>
Site make_site(const char *declaration, const char *field, unsigned width,
               T Root::*a, T Root::*b, T Root::*c) {
    std::array<T Root::*, 3> replicas{a, b, c};
    return {declaration, field, width,
        [replicas](Root& root, unsigned bank) -> uint64_t { return root.*replicas[bank]; },
        [replicas](Root& root, unsigned bank, uint64_t mask) { root.*replicas[bank] ^= mask; }};
}

const Site sites[] = {
#include "sites.inc"
};

void drive(Model& model, unsigned cycle, unsigned accepted) {
#if defined(TOP_Rocket)
    model.io_imem_resp_valid = cycle % 8 < 5;
    model.io_imem_resp_bits_mask = 3;
    model.io_imem_resp_bits_pc = 0x80000000ULL + 4ULL * accepted;
    model.io_imem_resp_bits_data = ((accepted & 0x7ff) << 20) | 0x93; // ADDI x1, x0, imm
    model.io_dmem_req_ready = 1;
    model.io_dmem_ordered = 1;
#elif defined(TOP_IBuf)
    model.io_imem_valid = cycle % 8 < 6;
    model.io_imem_bits_mask = 3;
    model.io_imem_bits_data = 0x00010001; // Two compressed NOPs
    model.io_imem_bits_pc = 0x80000000ULL + 4ULL * accepted;
    model.io_inst_0_ready = cycle % 4 != 1;
#elif defined(TOP_Frontend)
    model.io_cpu_might_request = 1;
    model.io_cpu_req_valid = cycle % 7 == 0;
    model.io_cpu_req_bits_pc = 0x80000000ULL + 4ULL * cycle;
    model.io_cpu_resp_ready = 1;
#elif defined(TOP_TMRShiftQueue)
    model.io_enq_valid = cycle % 12 < 8;
    model.io_enq_bits_data = 0x13370413u ^ (accepted * 0x1234567u);
    model.io_enq_bits_pc = 0x80000000ULL + 4ULL * accepted;
    model.io_deq_ready = cycle % 12 >= 6;
#endif
    model.eval();
}

bool transfer(const Model& model) {
#if defined(TOP_Rocket)
    return model.io_imem_resp_valid && model.io_imem_resp_ready;
#elif defined(TOP_IBuf)
    return model.io_imem_valid && model.io_imem_ready;
#elif defined(TOP_TMRShiftQueue)
    return model.io_enq_valid && model.io_enq_ready;
#else
    return model.io_cpu_req_valid;
#endif
}

void tick(Model& model) {
    model.clock = 1;
    model.eval();
    model.clock = 0;
    model.eval();
}

void compare(const Model& model, const Model& reference) {
#define CHECK_PORT(p) if (std::memcmp(&model.p, &reference.p, sizeof(model.p))) throw std::runtime_error(#p)
#include "outputs.inc"
#undef CHECK_PORT
#if defined(TOP_Rocket)
    const auto& dut = *model.rootp;
    const auto& ref = *reference.rootp;
    if (dut.Rocket__DOT__wb_valid != ref.Rocket__DOT__wb_valid ||
        (ref.Rocket__DOT__wb_valid && (dut.Rocket__DOT__wb_reg_inst != ref.Rocket__DOT__wb_reg_inst ||
                                       dut.Rocket__DOT__csr_io_pc != ref.Rocket__DOT__csr_io_pc)) ||
        dut.Rocket__DOT__rf_wen != ref.Rocket__DOT__rf_wen ||
        (ref.Rocket__DOT__rf_wen && (dut.Rocket__DOT__rf_waddr != ref.Rocket__DOT__rf_waddr ||
                                     dut.Rocket__DOT__rf_wdata != ref.Rocket__DOT__rf_wdata))) {
        throw std::runtime_error("retirement/writeback mismatch");
    }
#endif
}

bool run_case(const Site& site, unsigned bank, unsigned bit) {
    Model model("injected"), reference("reference");
    for (auto* instance : {&model, &reference}) {
        instance->clock = 0;
        instance->reset = 1;
        instance->eval();
        for (int i = 0; i < 4; ++i) tick(*instance);
        instance->reset = 0;
    }
    unsigned accepted = 0;
    const unsigned injection = 12 + (bit + bank) % 12;
    bool started_as_one = false;
    for (unsigned cycle = 0; cycle <= injection + 6; ++cycle) {
        drive(model, cycle, accepted);
        drive(reference, cycle, accepted);
        compare(model, reference);
        accepted += transfer(reference);
        if (cycle == injection) {
            const uint64_t original = site.read(*reference.rootp, 0);
            const uint64_t mask = uint64_t{1} << bit;
            started_as_one = (original & mask) != 0;
            site.flip(*model.rootp, bank, mask);
            model.eval();
            for (unsigned i = 0; i < 3; ++i) {
                const uint64_t expected = i == bank ? original ^ mask : original;
                if (site.read(*reference.rootp, i) != original || site.read(*model.rootp, i) != expected) {
                    throw std::runtime_error("injection isolation");
                }
            }
            compare(model, reference);
        }
        tick(model);
        tick(reference);
        compare(model, reference);
        if (cycle >= injection) {
            const uint64_t expected = site.read(*reference.rootp, 0);
            for (unsigned i = 0; i < 3; ++i) {
                if (site.read(*model.rootp, i) != expected || site.read(*reference.rootp, i) != expected) {
                    throw std::runtime_error("replicas after edge");
                }
            }
        }
    }
    model.final();
    reference.final();
    return started_as_one;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    Verilated::randReset(0);
    unsigned total = 0, failures = 0;
    std::ofstream out("coverage.csv");
    out.exceptions(std::ios::badbit | std::ios::failbit);
    out << "declaration,field_A,width_bits,passed,failed,flips_from_one,flips_from_zero\n";
    for (const auto& site : sites) {
        unsigned passed = 0, failed = 0, ones = 0;
        for (unsigned bank = 0; bank < 3; ++bank) {
            for (unsigned bit = 0; bit < site.width; ++bit) {
                try {
                    ones += run_case(site, bank, bit);
                    ++passed;
                } catch (const std::runtime_error& error) {
                    ++failed;
                    std::printf("FAIL %s bank=%c bit=%u: %s\n", site.field, 'A' + bank, bit, error.what());
                }
            }
        }
        out << site.declaration << ',' << site.field << ',' << site.width << ',' << passed << ','
            << failed << ',' << ones << ',' << passed - ones << '\n';
        total += passed + failed;
        failures += failed;
    }
    out.close();
    std::printf("fields=%zu attempted=%u passed=%u failed=%u\n",
                sizeof(sites)/sizeof(Site), total, total - failures, failures);
    return failures ? 1 : 0;
}
