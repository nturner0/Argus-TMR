#include "VRocket.h"
#include "VRocket___024root.h"
#include <array>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <stdexcept>

using Root = VRocket___024root;
struct Site {
    const char *name;
    std::array<QData Root::*,3> copies;
    QData Root::*vote;
    QData Root::*next;
    CData Root::*valid;
};
#define FIELD(x) &Root::Rocket__DOT__##x
#define SITE(s, v) {#s, {FIELD(s##_reg_pc_A),FIELD(s##_reg_pc_B),FIELD(s##_reg_pc_C)},FIELD(v),FIELD(s##_reg_pc_next),FIELD(s##_reg_valid)}
const Site sites[] = {SITE(ex,ex_reg_pc), SITE(mem,mem_reg_pc), SITE(wb,csr_io_pc)};
static void require(bool pass, const char* what) {
    if (!pass) throw std::runtime_error(what);
}
void tick(VRocket& q) {
    q.clock = 1; q.eval(); q.clock = 0; q.eval();
}
void drive(VRocket& q, unsigned n, bool valid) {
    q.io_dmem_req_ready=1; q.io_dmem_ordered=1;
    q.io_imem_resp_valid=valid;
    q.io_imem_resp_bits_mask=3;
    q.io_imem_resp_bits_pc=0x80000000ULL+4ULL*n;
    q.io_imem_resp_bits_data=((n & 0x7ff)<<20) | 0x93; // addi x1,x0,imm
    q.eval();
}
bool writesPC(const Root& r, int stage) {
    if (stage==0) return r.Rocket__DOT___GEN_20;
    if (stage==1) return !r.Rocket__DOT___GEN_22;
    return r.Rocket__DOT__mem_pc_valid;
}
void compare(VRocket& q, VRocket& ref) {
    auto& r=*q.rootp; auto& g=*ref.rootp;
    require(q.io_imem_resp_ready==ref.io_imem_resp_ready && q.io_imem_req_valid==ref.io_imem_req_valid,"fetch control differs");
    if (ref.io_imem_req_valid) require(q.io_imem_req_bits_pc==ref.io_imem_req_bits_pc,"redirect PC differs");
    for (const auto& s:sites)
        require(r.*s.vote==g.*s.vote && r.*s.valid==g.*s.valid,"stage PC or valid differs");
    require(r.Rocket__DOT__wb_valid==g.Rocket__DOT__wb_valid && r.Rocket__DOT__rf_wen==g.Rocket__DOT__rf_wen,"writeback control differs");
    if (g.Rocket__DOT__wb_valid) require(r.Rocket__DOT__wb_reg_inst==g.Rocket__DOT__wb_reg_inst,"retired instruction differs");
    if (g.Rocket__DOT__rf_wen) require(r.Rocket__DOT__rf_waddr==g.Rocket__DOT__rf_waddr && r.Rocket__DOT__rf_wdata==g.Rocket__DOT__rf_wdata,"writeback payload differs");
}
int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    Verilated::randReset(0);
    VRocket q("injected"), ref("reference");
    for (auto *m:{&q,&ref}) {
        m->clock=0; m->reset=1; drive(*m,0,false);
        for(int j=0;j<5;j++) tick(*m);
        m->reset=0;
    }
    auto& r=*q.rootp; auto& g=*ref.rootp;
    unsigned count[3][2]={}, accepted=0, retired=0, total=0;
    std::ofstream out("cases.csv");
    out<<"cycle,stage,action,replica,bit,original,upset,voted,next_reference,after_edge\n";
    for(unsigned cycle=0;cycle<30000;cycle++) {
        // Intersperse fetch bubbles so valid stage PCs both advance and hold.
        const bool supply=cycle%8<5 && total<720;
        drive(q,accepted,supply); drive(ref,accepted,supply); compare(q,ref);
        if (supply && ref.io_imem_resp_ready) ++accepted;
        if(g.Rocket__DOT__wb_valid) {
            require(g.Rocket__DOT__csr_io_pc==0x80000000ULL+4ULL*retired &&
                    g.Rocket__DOT__rf_wen && g.Rocket__DOT__rf_waddr==1 &&
                    g.Rocket__DOT__rf_wdata==(retired & 0x7ff),"unexpected reference retirement");
            ++retired;
        }
        bool injected=false;
        for(int st=0;st<3 && !injected;st++) {
            const Site& s=sites[st]; const int update=writesPC(g,st);
            unsigned &i=count[st][update];
            if(i==120 || !(g.*s.valid)) continue;
            const QData orig=g.*s.vote, next=g.*s.next;
            if(update && next==orig) continue;
            const unsigned bank=i/40, bit=i%40;
            for(auto m:s.copies) require(r.*m==orig,"pre-existing disagreement");
            r.*s.copies[bank]^=(1ULL<<bit);
            q.eval();
            for(unsigned j=0;j<3;j++) require(r.*s.copies[j]==(j==bank ? orig^(1ULL<<bit):orig),"fault did not land in exactly one replica");
            compare(q,ref);
            require(r.*s.next==next,"next state differs");
            out<<cycle<<','<<s.name<<','<<(update?"advance":"hold")<<','<<char('A'+bank)<<','<<bit<<','<<orig<<','<<(r.*s.copies[bank])<<','<<(r.*s.vote)<<','<<next;
            tick(q); tick(ref);
            for(auto m:s.copies) require(r.*m==next,"replica differs after edge");
            out<<','<<(r.*s.vote)<<'\n';
            ++i; ++total; injected=true;
        }
        if (!injected) {tick(q); tick(ref);}
        compare(q,ref);
        if(total==720 && accepted==retired) break;
    }
    for(int st=0;st<3;st++) std::printf("%s hold=%u advance=%u\n",sites[st].name,count[st][0],count[st][1]);
    std::printf("accepted=%u retired=%u injections=%u\n",accepted,retired,total);
    require(total==720 && retired==accepted && retired>0,"incomplete coverage or retirement");
    r.Rocket__DOT__ex_reg_pc_A ^= 4; r.Rocket__DOT__ex_reg_pc_B ^= 4; q.eval();
    require(r.Rocket__DOT__ex_reg_pc==(g.Rocket__DOT__ex_reg_pc^4),"two-replica negative control did not change voted PC");
    std::puts("PASS: two-replica negative control diverged as expected");
    std::puts("PASS: all single-replica cases matched the fault-free core");
}
