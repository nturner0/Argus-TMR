# Source preservation and baseline provenance

Original working directory: `/home/dkhalkha/chipyard/generators/rocket-chip`.
Rocket branch `feature/rocket-tmr`, HEAD `7a7c9df514d5adfe6e2cba6f040fe4fcfa5a8a1d`.
Chipyard branch `main`, HEAD `371ab92dd09917b6ab05d58bf762b173c52f4224`.
Clean baseline `55bcad0f59436de98ea510334121de8546b9e9d7` is both the parent of Daniel's committed IBuf TMR change and Chipyard's recorded Rocket submodule revision.

Working-tree status before work:

```
 M src/main/scala/rocket/Frontend.scala
 M src/main/scala/rocket/IBuf.scala
MM src/main/scala/rocket/RocketCore.scala
A  src/main/scala/rocket/TMRALU.scala
 A src/main/scala/rocket/TMRShiftQueue.scala
```

The staged RocketCore change instantiates TMRALU; the actual working-tree file instantiates ALU. The publication uses the working-tree bytes, preserving the user's authoritative implementation rather than reconstructing it from the index. TMRALU is included as preserved auxiliary source; it is not an active dependency of the instantiated datapath. No changes were made to the ALU.

No other Rocket source changes or untracked files were reported by Git. Existing uncommitted changes cannot reliably be attributed to individuals; none were discarded or edited. Chipyard also had pre-existing environment-file changes, a deleted toolchain lockfile, modified submodules (FPGA shells, constellation, axe), and untracked toolchain submodule content. These remain untouched. Source snapshots and recursive revision manifests identify the build dependencies; the environment changes are not processor modifications.

Full original status, staged and unstaged binary diffs, commit history, and source checksums were captured in the sibling local `protection/` directory before any build. Source hashes are published in source-sha256.json. Separate baseline and TMR trees were copied under the task workspace; clean Rocket tracked contents in the baseline were restored solely from a Git archive of the baseline revision. Copied generator JAR caches were moved aside before isolated builds. No branch switches, resets, cleans, or force pushes were used.
