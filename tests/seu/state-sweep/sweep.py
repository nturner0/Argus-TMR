"""Build and run SEU sweeps on the generated RocketConfig RTL."""
import argparse
import json
from pathlib import Path
import re
import subprocess

HERE = Path(__file__).resolve().parent
MODULES = ("Rocket", "Frontend", "IBuf", "TMRShiftQueue")


def replica_fields(source):
    registers = {
        name: int(high or 0) + 1
        for high, name in re.findall(r"^  reg\s+(?:\[(\d+):0\]\s+)?(\w+);", source, re.M)
    }
    fields = []
    for name, width in registers.items():
        match = re.fullmatch(r"(.+)_A(_.*)?", name)
        if match:
            declaration, suffix = match[1], match[2] or ""
            replicas = [f"{declaration}_{bank}{suffix}" for bank in "ABC"]
        elif name.startswith("bufA_"):  # IBuf's buffered fetch response
            declaration = "buf"
            replicas = [f"buf{bank}{name[4:]}" for bank in "ABC"]
        else:
            continue
        if width > 64 or any(registers.get(replica) != width for replica in replicas):
            raise ValueError(f"Unsupported replica layout: {replicas}")
        fields.append((declaration, width, replicas))
    return fields


def generate_accessors(module, rtl, work):
    source = (rtl / f"{module}.sv").read_text()
    fields = replica_fields(source)
    ports = re.findall(r"^  output\s+(?:\[\d+:0\]\s+)?(\w+)", source, re.M)
    if not fields or not ports:
        raise ValueError(f"Missing replica fields or output ports for {module}")
    if module == "Rocket":
        inventory = HERE.parents[1] / "protected-pipeline-state.json"
        expected = set(json.loads(inventory.read_text())["declarations"])
        found = {declaration for declaration, _, _ in fields}
        if found != expected:
            raise ValueError(f"Rocket inventory mismatch: missing {expected-found}, extra {found-expected}")

    header = [
        f'#include "V{module}.h"',
        f'#include "V{module}___024root.h"',
        f'using Model = V{module};',
        f'using Root = V{module}___024root;',
        f'#define TOP_{module} 1',
    ]
    accessors = []
    for declaration, width, replicas in fields:
        members = ", ".join(f"&Root::{module}__DOT__{name}" for name in replicas)
        accessors.append(f'make_site("{declaration}", "{replicas[0]}", {width}, {members}),')
    (work / "model.hpp").write_text("\n".join(header) + "\n")
    (work / "sites.inc").write_text("\n".join(accessors) + "\n")
    (work / "outputs.inc").write_text("".join(f"CHECK_PORT({name});\n" for name in ports))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rtl", type=Path, help="RocketConfig gen-collateral directory")
    parser.add_argument("output", type=Path, help="Working directory for builds and results")
    parser.add_argument("modules", nargs="*", help="Optional subset of " + ", ".join(MODULES))
    args = parser.parse_args()
    rtl = args.rtl.resolve()
    for module in args.modules or MODULES:
        if module not in MODULES:
            parser.error("Unknown module: " + module)
        work = args.output.resolve() / module
        work.mkdir(parents=True, exist_ok=True)
        generate_accessors(module, rtl, work)
        command = [
            "verilator", "--cc", "--exe", "--build", "-j", "2", "--public-flat-rw",
            "--top-module", module, "-Wno-fatal", "-DPRINTF_COND=0", "-CFLAGS", f"-I{work}",
            "-y", str(rtl), "+libext+.sv+.v", str(rtl / f"{module}.sv"), str(HERE / "sweep.cpp"),
        ]
        if module == "Frontend":
            memory_model, = rtl.glob("*.top.mems.v")
            command.extend(["-v", str(memory_model)])
        with (work / "build.log").open("w") as log:
            subprocess.run(command, cwd=work, stdout=log, stderr=subprocess.STDOUT, check=True)
        result = work / "result.txt"
        with result.open("w") as log:
            subprocess.run([str(work / "obj_dir" / f"V{module}")], cwd=work, stdout=log, check=True)
        print(module, result.read_text().strip(), flush=True)


if __name__ == "__main__":
    main()
